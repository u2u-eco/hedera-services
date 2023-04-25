/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.ledger;

import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.CRYPTO_ALLOWANCES;
import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.FUNGIBLE_TOKEN_ALLOWANCES;
import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.NUM_ASSOCIATIONS;
import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.NUM_NFTS_OWNED;
import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.NUM_POSITIVE_BALANCES;
import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.NUM_TREASURY_TITLES;
import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.USED_AUTOMATIC_ASSOCIATIONS;
import static com.hedera.node.app.service.mono.ledger.properties.NftProperty.SPENDER;
import static com.hedera.node.app.service.mono.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.mono.context.SideEffectsTracker;
import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.fees.charging.FeeDistribution;
import com.hedera.node.app.service.mono.ledger.accounts.AliasManager;
import com.hedera.node.app.service.mono.ledger.properties.AccountProperty;
import com.hedera.node.app.service.mono.ledger.properties.NftProperty;
import com.hedera.node.app.service.mono.ledger.properties.TokenRelProperty;
import com.hedera.node.app.service.mono.records.RecordsHistorian;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.state.migration.HederaTokenRel;
import com.hedera.node.app.service.mono.state.migration.UniqueTokenAdapter;
import com.hedera.node.app.service.mono.state.submerkle.FcTokenAllowanceId;
import com.hedera.node.app.service.mono.store.models.NftId;
import com.hedera.node.app.service.mono.store.tokens.TokenStore;
import com.hedera.node.app.service.mono.txns.crypto.AutoCreationLogic;
import com.hedera.node.app.service.mono.txns.validation.OptionValidator;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.tuple.Pair;

@Singleton
public class TransferLogic {
    public static final List<AccountProperty> TOKEN_TRANSFER_SIDE_EFFECTS =
            List.of(
                    NUM_POSITIVE_BALANCES,
                    NUM_ASSOCIATIONS,
                    NUM_NFTS_OWNED,
                    USED_AUTOMATIC_ASSOCIATIONS,
                    NUM_TREASURY_TITLES);

    private final TokenStore tokenStore;
    private final AutoCreationLogic autoCreationLogic;
    private final SideEffectsTracker sideEffectsTracker;
    private final RecordsHistorian recordsHistorian;
    private final MerkleAccountScopedCheck scopedCheck;
    private final TransactionalLedger<AccountID, AccountProperty, HederaAccount> accountsLedger;
    private final TransactionalLedger<NftId, NftProperty, UniqueTokenAdapter> nftsLedger;
    private final TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, HederaTokenRel>
            tokenRelsLedger;
    private final TransactionContext txnCtx;
    private final AliasManager aliasManager;
    private final FeeDistribution feeDistribution;

    @Inject
    public TransferLogic(
            final TransactionalLedger<AccountID, AccountProperty, HederaAccount> accountsLedger,
            final TransactionalLedger<NftId, NftProperty, UniqueTokenAdapter> nftsLedger,
            final TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, HederaTokenRel>
                    tokenRelsLedger,
            final TokenStore tokenStore,
            final SideEffectsTracker sideEffectsTracker,
            final OptionValidator validator,
            final @Nullable AutoCreationLogic autoCreationLogic,
            final RecordsHistorian recordsHistorian,
            final TransactionContext txnCtx,
            final AliasManager aliasManager,
            final FeeDistribution feeDistribution) {
        this.tokenStore = tokenStore;
        this.nftsLedger = nftsLedger;
        this.accountsLedger = accountsLedger;
        this.tokenRelsLedger = tokenRelsLedger;
        this.recordsHistorian = recordsHistorian;
        this.autoCreationLogic = autoCreationLogic;
        this.sideEffectsTracker = sideEffectsTracker;
        this.txnCtx = txnCtx;
        this.aliasManager = aliasManager;
        this.feeDistribution = feeDistribution;

        scopedCheck = new MerkleAccountScopedCheck(validator, nftsLedger);
    }

    public void doZeroSum(final List<BalanceChange> changes) {
        final var topLevelPayer = txnCtx.activePayer();
        var validity = OK;
        var autoCreationFee = 0L;
        var updatedPayerBalance = Long.MIN_VALUE;
        for (final var change : changes) {
            // If the change consists of any repeated aliases, replace the alias with the account
            // number
            replaceAliasWithIdIfExisting(change);

            // create a new account for alias when the no account is already created using the alias
            if (change.hasAlias()) {
                if (autoCreationLogic == null) {
                    throw new IllegalStateException(
                            "Cannot auto-create account from "
                                    + change
                                    + " with null autoCreationLogic");
                }
                final var result = autoCreationLogic.create(change, accountsLedger, changes);
                validity = result.getKey();
                autoCreationFee += result.getValue();
                if (validity == OK && (change.isForToken())) {
                    validity = tokenStore.tryTokenChange(change);
                }
            } else if (change.isForHbar()) {
                validity =
                        accountsLedger.validate(
                                change.accountId(), scopedCheck.setBalanceChange(change));
                if (change.affectsAccount(topLevelPayer)) {
                    updatedPayerBalance = change.getNewBalance();
                }
            } else {
                validity =
                        accountsLedger.validate(
                                change.accountId(), scopedCheck.setBalanceChange(change));

                if (validity == OK) {
                    validity = tokenStore.tryTokenChange(change);
                }
            }
            if (validity != OK) {
                break;
            }
        }

        if (validity == OK && autoCreationFee > 0) {
            updatedPayerBalance =
                    (updatedPayerBalance == Long.MIN_VALUE)
                            ? (long) accountsLedger.get(topLevelPayer, BALANCE)
                            : updatedPayerBalance;
            if (autoCreationFee > updatedPayerBalance) {
                validity = INSUFFICIENT_PAYER_BALANCE;
            }
        }

        if (validity == OK) {
            adjustBalancesAndAllowances(changes);
            if (autoCreationFee > 0) {
                payAutoCreationFee(autoCreationFee);
                autoCreationLogic.submitRecordsTo(recordsHistorian);
            }
        } else {
            dropTokenChanges(sideEffectsTracker, nftsLedger, accountsLedger, tokenRelsLedger);
            if (autoCreationLogic != null && autoCreationLogic.reclaimPendingAliases()) {
                accountsLedger.undoCreations();
            }
            throw new InvalidTransactionException(validity);
        }
    }

    public void payAutoCreationFee(final long autoCreationFee) {
        feeDistribution.distributeChargedFee(autoCreationFee, accountsLedger);

        // deduct the auto creation fee from payer of the transaction
        final var payerBalance = (long) accountsLedger.get(txnCtx.activePayer(), BALANCE);
        accountsLedger.set(txnCtx.activePayer(), BALANCE, payerBalance - autoCreationFee);
        txnCtx.addFeeChargedToPayer(autoCreationFee);
    }

    private void adjustBalancesAndAllowances(final List<BalanceChange> changes) {
        for (final var change : changes) {
            final var accountId = change.accountId();
            if (change.isForHbar()) {
                final var newBalance = change.getNewBalance();
                accountsLedger.set(accountId, BALANCE, newBalance);
                if (change.isApprovedAllowance()) {
                    adjustCryptoAllowance(change, accountId);
                }
            } else if (change.isApprovedAllowance() && change.isForFungibleToken()) {
                adjustFungibleTokenAllowance(change, accountId);
            } else if (change.isForNft()) {
                // wipe the allowance on this uniqueToken
                nftsLedger.set(change.nftId(), SPENDER, MISSING_ENTITY_ID);
            }
        }
    }

    public static void dropTokenChanges(
            final SideEffectsTracker sideEffectsTracker,
            final TransactionalLedger<NftId, NftProperty, UniqueTokenAdapter> nftsLedger,
            final TransactionalLedger<AccountID, AccountProperty, HederaAccount> accountsLedger,
            final TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, HederaTokenRel>
                    tokenRelsLedger) {
        if (tokenRelsLedger.isInTransaction()) {
            tokenRelsLedger.rollback();
        }
        if (nftsLedger.isInTransaction()) {
            nftsLedger.rollback();
        }
        accountsLedger.undoChangesOfType(TOKEN_TRANSFER_SIDE_EFFECTS);
        sideEffectsTracker.resetTrackedTokenChanges();
    }

    @SuppressWarnings("unchecked")
    private void adjustCryptoAllowance(final BalanceChange change, final AccountID ownerID) {
        final var payerNum = EntityNum.fromAccountId(change.getPayerID());
        final var hbarAllowances =
                new TreeMap<>(
                        (Map<EntityNum, Long>) accountsLedger.get(ownerID, CRYPTO_ALLOWANCES));
        final var currentAllowance = hbarAllowances.get(payerNum);
        final var newAllowance = currentAllowance + change.getAllowanceUnits();
        if (newAllowance != 0) {
            hbarAllowances.put(payerNum, newAllowance);
        } else {
            hbarAllowances.remove(payerNum);
        }
        accountsLedger.set(ownerID, CRYPTO_ALLOWANCES, hbarAllowances);
    }

    @SuppressWarnings("unchecked")
    private void adjustFungibleTokenAllowance(final BalanceChange change, final AccountID ownerID) {
        final var allowanceId =
                FcTokenAllowanceId.from(
                        change.getToken().asEntityNum(),
                        EntityNum.fromAccountId(change.getPayerID()));
        final var fungibleAllowances =
                new TreeMap<>(
                        (Map<FcTokenAllowanceId, Long>)
                                accountsLedger.get(ownerID, FUNGIBLE_TOKEN_ALLOWANCES));
        final var currentAllowance = fungibleAllowances.get(allowanceId);
        final var newAllowance = currentAllowance + change.getAllowanceUnits();
        if (newAllowance == 0) {
            fungibleAllowances.remove(allowanceId);
        } else {
            fungibleAllowances.put(allowanceId, newAllowance);
        }
        accountsLedger.set(ownerID, FUNGIBLE_TOKEN_ALLOWANCES, fungibleAllowances);
    }

    /**
     * Checks if the alias is a known alias i.e, if the alias is already used in any cryptoTransfer
     * transaction that has led to account creation
     *
     * @param change change that contains alias
     */
    private void replaceAliasWithIdIfExisting(final BalanceChange change) {
        final var alias = change.getNonEmptyAliasIfPresent();

        if (alias != null) {
            final var aliasNum = aliasManager.lookupIdBy(alias);
            if (aliasNum != EntityNum.MISSING_NUM) {
                change.replaceNonEmptyAliasWith(aliasNum);
            }
        }
    }
}
