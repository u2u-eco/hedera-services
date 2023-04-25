/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hedera.node.app.service.mono.store.contracts.precompile;

import static com.hedera.node.app.service.evm.store.tokens.TokenType.NON_FUNGIBLE_UNIQUE;
import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateFalse;
import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateFalseOrRevert;
import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrueOrRevert;
import static com.hedera.node.app.service.mono.ledger.TransferLogic.dropTokenChanges;
import static com.hedera.node.app.service.mono.ledger.backing.BackingTokenRels.asTokenRel;
import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.NUM_TREASURY_TITLES;
import static com.hedera.node.app.service.mono.ledger.properties.TokenRelProperty.TOKEN_BALANCE;
import static com.hedera.node.app.service.mono.store.tokens.HederaTokenStore.affectsExpiryAtMost;
import static com.hedera.node.app.service.mono.store.tokens.TokenStore.MISSING_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.node.app.service.mono.context.SideEffectsTracker;
import com.hedera.node.app.service.mono.ledger.SigImpactHistorian;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.store.contracts.WorldLedgers;
import com.hedera.node.app.service.mono.store.models.Id;
import com.hedera.node.app.service.mono.store.models.NftId;
import com.hedera.node.app.service.mono.store.tokens.HederaTokenStore;
import com.hedera.node.app.service.mono.store.tokens.annotations.AreTreasuryWildcardsEnabled;
import com.hedera.node.app.service.mono.txns.util.TokenUpdateValidator;
import com.hedera.node.app.service.mono.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Optional;
import javax.inject.Inject;

public class TokenUpdateLogic {
    private final OptionValidator validator;
    private final HederaTokenStore tokenStore;
    private final WorldLedgers worldLedgers;
    private final SideEffectsTracker sideEffectsTracker;
    private final SigImpactHistorian sigImpactHistorian;
    boolean allowChangedTreasuryToOwnNfts;

    @Inject
    public TokenUpdateLogic(
            final @AreTreasuryWildcardsEnabled boolean allowChangedTreasuryToOwnNfts,
            OptionValidator validator,
            HederaTokenStore tokenStore,
            WorldLedgers worldLedgers,
            SideEffectsTracker sideEffectsTracker,
            SigImpactHistorian sigImpactHistorian) {
        this.validator = validator;
        this.tokenStore = tokenStore;
        this.worldLedgers = worldLedgers;
        this.sideEffectsTracker = sideEffectsTracker;
        this.sigImpactHistorian = sigImpactHistorian;
        this.allowChangedTreasuryToOwnNfts = allowChangedTreasuryToOwnNfts;
    }

    public void updateToken(TokenUpdateTransactionBody op, long now) {
        final var tokenID = tokenValidityCheck(op);
        if (op.hasExpiry()) {
            validateTrueOrRevert(validator.isValidExpiry(op.getExpiry()), INVALID_EXPIRATION_TIME);
        }
        MerkleToken token = tokenStore.get(tokenID);
        checkTokenPreconditions(token, op);

        assertAutoRenewValidity(op, token);
        Optional<AccountID> replacedTreasury = Optional.empty();
        ResponseCodeEnum outcome;
        if (op.hasTreasury()) {
            var newTreasury = op.getTreasury();
            validateFalseOrRevert(isDetached(newTreasury), ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);

            if (!tokenStore.associationExists(newTreasury, tokenID)) {
                outcome = tokenStore.autoAssociate(newTreasury, tokenID);
                if (outcome != OK) {
                    abortWith(outcome);
                }
            }
            var existingTreasury = token.treasury().toGrpcAccountId();
            if (!allowChangedTreasuryToOwnNfts && token.tokenType() == NON_FUNGIBLE_UNIQUE) {
                var existingTreasuryBalance = getTokenBalance(existingTreasury, tokenID);
                if (existingTreasuryBalance > 0L) {
                    abortWith(CURRENT_TREASURY_STILL_OWNS_NFTS);
                }
            }
            if (!newTreasury.equals(existingTreasury)) {
                validateFalseOrRevert(
                        isDetached(existingTreasury), ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);

                outcome = prepTreasuryChange(tokenID, token, newTreasury, existingTreasury);
                if (outcome != OK) {
                    abortWith(outcome);
                }
                replacedTreasury = Optional.of(token.treasury().toGrpcAccountId());
            }
        }

        outcome = tokenStore.update(op, now);
        if (outcome == OK && replacedTreasury.isPresent()) {
            final var oldTreasury = replacedTreasury.get();
            long replacedTreasuryBalance = getTokenBalance(oldTreasury, tokenID);
            if (replacedTreasuryBalance > 0) {
                if (token.tokenType().equals(TokenType.FUNGIBLE_COMMON)) {
                    outcome =
                            doTokenTransfer(
                                    tokenID,
                                    oldTreasury,
                                    op.getTreasury(),
                                    replacedTreasuryBalance);
                } else {
                    outcome =
                            tokenStore.changeOwnerWildCard(
                                    new NftId(
                                            tokenID.getShardNum(),
                                            tokenID.getRealmNum(),
                                            tokenID.getTokenNum(),
                                            -1),
                                    oldTreasury,
                                    op.getTreasury());
                }
            }
        }
        if (outcome != OK) {
            abortWith(outcome);
        }
        sigImpactHistorian.markEntityChanged(tokenID.getTokenNum());
    }

    public void updateTokenExpiryInfo(TokenUpdateTransactionBody op) {
        final var tokenID = tokenStore.resolve(op.getToken());
        validateTrueOrRevert(!tokenID.equals(MISSING_TOKEN), INVALID_TOKEN_ID);
        if (op.hasExpiry()) {
            validateTrueOrRevert(validator.isValidExpiry(op.getExpiry()), INVALID_EXPIRATION_TIME);
        }
        MerkleToken token = tokenStore.get(tokenID);
        checkTokenPreconditions(token, op);
        assertAutoRenewValidity(op, token);

        var outcome = tokenStore.updateExpiryInfo(op);
        if (outcome != OK) {
            abortWith(outcome);
        }
        sigImpactHistorian.markEntityChanged(tokenID.getTokenNum());
    }

    public void updateTokenKeys(TokenUpdateTransactionBody op, long now) {
        final var tokenID = tokenValidityCheck(op);
        MerkleToken token = tokenStore.get(tokenID);
        checkTokenPreconditions(token, op);
        final var outcome = tokenStore.update(op, now);

        if (outcome != OK) {
            abortWith(outcome);
        }
        sigImpactHistorian.markEntityChanged(tokenID.getTokenNum());
    }

    private TokenID tokenValidityCheck(TokenUpdateTransactionBody op) {
        final var tokenID = Id.fromGrpcToken(op.getToken()).asGrpcToken();
        validateFalse(tokenID.equals(MISSING_TOKEN), INVALID_TOKEN_ID);
        return tokenID;
    }

    private void checkTokenPreconditions(MerkleToken token, TokenUpdateTransactionBody op) {
        if (!token.hasAdminKey())
            validateTrueOrRevert((affectsExpiryAtMost(op)), TOKEN_IS_IMMUTABLE);
        validateFalseOrRevert(token.isDeleted(), TOKEN_WAS_DELETED);
        validateFalseOrRevert(token.isPaused(), TOKEN_IS_PAUSED);
    }

    public ResponseCodeEnum validate(TransactionBody txnBody) {
        return TokenUpdateValidator.validate(txnBody, validator);
    }

    private void assertAutoRenewValidity(TokenUpdateTransactionBody op, MerkleToken token) {
        if (op.hasAutoRenewAccount()) {
            final var newAutoRenew = op.getAutoRenewAccount();
            validateTrueOrRevert(
                    worldLedgers.accounts().contains(newAutoRenew), INVALID_AUTORENEW_ACCOUNT);
            validateFalseOrRevert(isDetached(newAutoRenew), ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);

            if (token.hasAutoRenewAccount()) {
                final var existingAutoRenew = token.autoRenewAccount().toGrpcAccountId();
                validateFalseOrRevert(
                        isDetached(existingAutoRenew), ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
            }
        }
    }

    private boolean isDetached(AccountID accountID) {
        return validator.expiryStatusGiven(worldLedgers.accounts(), accountID) != OK;
    }

    private ResponseCodeEnum prepTreasuryChange(
            final TokenID id,
            final MerkleToken token,
            final AccountID newTreasury,
            final AccountID oldTreasury) {
        var status = OK;
        if (token.hasFreezeKey()) {
            status = tokenStore.unfreeze(newTreasury, id);
        }
        if (status == OK && token.hasKycKey()) {
            status = tokenStore.grantKyc(newTreasury, id);
        }
        if (status == OK) {
            decrementNumTreasuryTitles(oldTreasury);
            incrementNumTreasuryTitles(newTreasury);
        }
        return status;
    }

    private void abortWith(ResponseCodeEnum cause) {
        dropTokenChanges(
                sideEffectsTracker,
                worldLedgers.nfts(),
                worldLedgers.accounts(),
                worldLedgers.tokenRels());
        throw new InvalidTransactionException(cause);
    }

    private ResponseCodeEnum doTokenTransfer(
            TokenID tId, AccountID from, AccountID to, long adjustment) {
        ResponseCodeEnum validity = tokenStore.adjustBalance(from, tId, -adjustment);
        if (validity == OK) {
            validity = tokenStore.adjustBalance(to, tId, adjustment);
        }

        if (validity != OK) {
            dropTokenChanges(
                    sideEffectsTracker,
                    worldLedgers.nfts(),
                    worldLedgers.accounts(),
                    worldLedgers.tokenRels());
        }
        return validity;
    }

    public long getTokenBalance(AccountID aId, TokenID tId) {
        var relationship = asTokenRel(aId, tId);
        return (long) worldLedgers.tokenRels().get(relationship, TOKEN_BALANCE);
    }

    private void incrementNumTreasuryTitles(final AccountID aId) {
        changeNumTreasuryTitles(aId, +1);
    }

    private void decrementNumTreasuryTitles(final AccountID aId) {
        changeNumTreasuryTitles(aId, -1);
    }

    private void changeNumTreasuryTitles(final AccountID aId, final int delta) {
        final var numTreasuryTitles = (int) worldLedgers.accounts().get(aId, NUM_TREASURY_TITLES);
        worldLedgers.accounts().set(aId, NUM_TREASURY_TITLES, numTreasuryTitles + delta);
    }
}
