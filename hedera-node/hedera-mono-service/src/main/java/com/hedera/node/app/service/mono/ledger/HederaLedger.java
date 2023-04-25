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
package com.hedera.node.app.service.mono.ledger;

import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.ALIAS;
import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.AUTO_RENEW_PERIOD;
import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.EXPIRY;
import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.IS_DELETED;
import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.IS_RECEIVER_SIG_REQUIRED;
import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.IS_SMART_CONTRACT;
import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.KEY;
import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.MAX_AUTOMATIC_ASSOCIATIONS;
import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.MEMO;
import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.NUM_NFTS_OWNED;
import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.NUM_POSITIVE_BALANCES;
import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.NUM_TREASURY_TITLES;
import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.USED_AUTOMATIC_ASSOCIATIONS;
import static com.hedera.node.app.service.mono.ledger.properties.TokenRelProperty.TOKEN_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.mono.context.SideEffectsTracker;
import com.hedera.node.app.service.mono.exceptions.DeletedAccountException;
import com.hedera.node.app.service.mono.exceptions.DetachedAccountException;
import com.hedera.node.app.service.mono.exceptions.InsufficientFundsException;
import com.hedera.node.app.service.mono.exceptions.MissingEntityException;
import com.hedera.node.app.service.mono.ledger.accounts.HederaAccountCustomizer;
import com.hedera.node.app.service.mono.ledger.backing.BackingTokenRels;
import com.hedera.node.app.service.mono.ledger.ids.EntityIdSource;
import com.hedera.node.app.service.mono.ledger.properties.AccountProperty;
import com.hedera.node.app.service.mono.ledger.properties.NftProperty;
import com.hedera.node.app.service.mono.ledger.properties.TokenProperty;
import com.hedera.node.app.service.mono.ledger.properties.TokenRelProperty;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.records.RecordsHistorian;
import com.hedera.node.app.service.mono.state.EntityCreator;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.state.migration.HederaTokenRel;
import com.hedera.node.app.service.mono.state.migration.UniqueTokenAdapter;
import com.hedera.node.app.service.mono.state.submerkle.CurrencyAdjustments;
import com.hedera.node.app.service.mono.store.contracts.MutableEntityAccess;
import com.hedera.node.app.service.mono.store.models.NftId;
import com.hedera.node.app.service.mono.store.tokens.TokenStore;
import com.hedera.node.app.service.mono.txns.crypto.AutoCreationLogic;
import com.hedera.node.app.service.mono.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import java.util.Comparator;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Provides a ledger for Hedera Services crypto and smart contract accounts with transactional
 * semantics. Changes to the ledger are <b>only</b> allowed in the scope of a transaction.
 *
 * <p>All changes that are made during a transaction are summarized as per-account changesets. These
 * changesets are committed to a wrapped {@link TransactionalLedger}; or dropped entirely in case of
 * a rollback.
 *
 * <p>The ledger delegates history of each transaction to an injected {@link RecordsHistorian} by
 * invoking its {@code addNewRecords} immediately before the final {@link
 * TransactionalLedger#commit()}.
 *
 * <p>We should think of the ledger as using double-booked accounting, (e.g., via the {@link
 * HederaLedger#doTransfer(AccountID, AccountID, long)} method); but it is necessary to provide
 * "unsafe" single-booked methods like {@link HederaLedger#adjustBalance(AccountID, long)} in order
 * to match transfer semantics the EVM expects.
 */
public class HederaLedger {
    public static final String NO_ACTIVE_TXN_CHANGE_SET = "{*NO ACTIVE TXN*}";

    public static final Comparator<AccountID> ACCOUNT_ID_COMPARATOR =
            Comparator.comparingLong(AccountID::getAccountNum)
                    .thenComparingLong(AccountID::getShardNum)
                    .thenComparingLong(AccountID::getRealmNum);
    public static final Comparator<TokenID> TOKEN_ID_COMPARATOR =
            Comparator.comparingLong(TokenID::getTokenNum)
                    .thenComparingLong(TokenID::getRealmNum)
                    .thenComparingLong(TokenID::getShardNum);
    public static final Comparator<ContractID> CONTRACT_ID_COMPARATOR =
            Comparator.comparingLong(ContractID::getContractNum)
                    .thenComparingLong(ContractID::getShardNum)
                    .thenComparingLong(ContractID::getRealmNum);

    private final TokenStore tokenStore;
    private final TransferLogic transferLogic;
    private final EntityIdSource ids;
    private final OptionValidator validator;
    private final SideEffectsTracker sideEffectsTracker;
    private final RecordsHistorian historian;
    private final TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokensLedger;
    private final TransactionalLedger<AccountID, AccountProperty, HederaAccount> accountsLedger;

    private MutableEntityAccess mutableEntityAccess;
    private TransactionalLedger<NftId, NftProperty, UniqueTokenAdapter> nftsLedger = null;
    private TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, HederaTokenRel>
            tokenRelsLedger = null;

    private final AutoCreationLogic autoCreationLogic;

    public HederaLedger(
            final TokenStore tokenStore,
            final EntityIdSource ids,
            final EntityCreator creator,
            final OptionValidator validator,
            final SideEffectsTracker sideEffectsTracker,
            final RecordsHistorian historian,
            final TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokensLedger,
            final TransactionalLedger<AccountID, AccountProperty, HederaAccount> accountsLedger,
            final TransferLogic transferLogic,
            final AutoCreationLogic autoCreationLogic) {
        this.ids = ids;
        this.validator = validator;
        this.historian = historian;
        this.tokenStore = tokenStore;
        this.tokensLedger = tokensLedger;
        this.accountsLedger = accountsLedger;
        this.sideEffectsTracker = sideEffectsTracker;
        this.transferLogic = transferLogic;
        this.autoCreationLogic = autoCreationLogic;

        creator.setLedger(this);
        historian.setCreator(creator);
        tokenStore.setAccountsLedger(accountsLedger);
        tokenStore.setHederaLedger(this);
    }

    public void setMutableEntityAccess(final MutableEntityAccess mutableEntityAccess) {
        this.mutableEntityAccess = mutableEntityAccess;
    }

    public void setNftsLedger(
            final TransactionalLedger<NftId, NftProperty, UniqueTokenAdapter> nftsLedger) {
        this.nftsLedger = nftsLedger;
    }

    public void setTokenRelsLedger(
            final TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, HederaTokenRel>
                    tokenRelsLedger) {
        this.tokenRelsLedger = tokenRelsLedger;
    }

    public TransactionalLedger<AccountID, AccountProperty, HederaAccount> getAccountsLedger() {
        return accountsLedger;
    }

    public TransactionalLedger<NftId, NftProperty, UniqueTokenAdapter> getNftsLedger() {
        return nftsLedger;
    }

    public TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, HederaTokenRel>
            getTokenRelsLedger() {
        return tokenRelsLedger;
    }

    /* -- TRANSACTIONAL SEMANTICS -- */
    public void begin() {
        autoCreationLogic.reset();
        accountsLedger.begin();
        tokensLedger.begin();
        if (tokenRelsLedger != null) {
            tokenRelsLedger.begin();
        }
        if (nftsLedger != null) {
            nftsLedger.begin();
        }
        mutableEntityAccess.startAccess();
    }

    public void rollback() {
        accountsLedger.rollback();
        if (tokensLedger.isInTransaction()) {
            tokensLedger.rollback();
        }
        if (tokenRelsLedger != null && tokenRelsLedger.isInTransaction()) {
            tokenRelsLedger.rollback();
        }
        if (nftsLedger != null && nftsLedger.isInTransaction()) {
            nftsLedger.rollback();
        }
    }

    /** Commits the pending change sets in the {@link TransactionalLedger} implementations. */
    public void commit() {
        // The ledger interceptors track side effects, hence must be committed before saving a
        // record
        accountsLedger.commit();
        if (tokensLedger.isInTransaction()) {
            tokensLedger.commit();
        }
        if (tokenRelsLedger != null && tokenRelsLedger.isInTransaction()) {
            tokenRelsLedger.commit();
        }
        if (nftsLedger != null && nftsLedger.isInTransaction()) {
            nftsLedger.commit();
        }
        historian.saveExpirableTransactionRecords();
        historian.noteNewExpirationEvents();
    }

    public String currentChangeSet() {
        if (accountsLedger.isInTransaction()) {
            final var sb =
                    new StringBuilder("--- ACCOUNTS ---\n").append(accountsLedger.changeSetSoFar());
            if (tokenRelsLedger != null) {
                sb.append("\n--- TOKEN RELATIONSHIPS ---\n")
                        .append(tokenRelsLedger.changeSetSoFar());
            }
            if (nftsLedger != null) {
                sb.append("\n--- NFTS ---\n").append(nftsLedger.changeSetSoFar());
            }
            sb.append("\n--- TOKENS ---\n").append(mutableEntityAccess.currentManagedChangeSet());
            return sb.toString();
        } else {
            return NO_ACTIVE_TXN_CHANGE_SET;
        }
    }

    /* -- CURRENCY MANIPULATION -- */
    public long getBalance(final AccountID id) {
        return (long) accountsLedger.get(id, BALANCE);
    }

    public void adjustBalance(final AccountID id, final long adjustment) {
        final long newBalance = computeNewBalance(id, adjustment);
        setBalance(id, newBalance);
    }

    void doTransfer(final AccountID from, final AccountID to, final long adjustment) {
        final long newFromBalance = computeNewBalance(from, -1 * adjustment);
        final long newToBalance = computeNewBalance(to, adjustment);

        setBalance(from, newFromBalance);
        setBalance(to, newToBalance);
    }

    /* --- TOKEN MANIPULATION --- */
    public long getTokenBalance(final AccountID aId, final TokenID tId) {
        final var relationship = BackingTokenRels.asTokenRel(aId, tId);
        return (long) tokenRelsLedger.get(relationship, TOKEN_BALANCE);
    }

    public boolean allTokenBalancesVanish(final AccountID aId) {
        if (tokenRelsLedger == null) {
            throw new IllegalStateException("Ledger has no manageable token relationships!");
        }
        final var positiveBalances = (int) accountsLedger.get(aId, NUM_POSITIVE_BALANCES);
        return positiveBalances == 0;
    }

    public void incrementNumTreasuryTitles(final AccountID aId) {
        changeNumTreasuryTitles(aId, +1);
    }

    public void decrementNumTreasuryTitles(final AccountID aId) {
        changeNumTreasuryTitles(aId, -1);
    }

    private void changeNumTreasuryTitles(final AccountID aId, final int delta) {
        final var numTreasuryTitles = (int) accountsLedger.get(aId, NUM_TREASURY_TITLES);
        accountsLedger.set(aId, NUM_TREASURY_TITLES, numTreasuryTitles + delta);
    }

    public boolean isKnownTreasury(final AccountID aId) {
        return (int) accountsLedger.get(aId, NUM_TREASURY_TITLES) > 0;
    }

    public boolean hasAnyFungibleTokenBalance(final AccountID aId) {
        return (int) accountsLedger.get(aId, NUM_POSITIVE_BALANCES) > 0;
    }

    public boolean hasAnyNfts(final AccountID aId) {
        return (long) accountsLedger.get(aId, NUM_NFTS_OWNED) > 0L;
    }

    public ResponseCodeEnum adjustTokenBalance(
            final AccountID aId, final TokenID tId, final long adjustment) {
        return tokenStore.adjustBalance(aId, tId, adjustment);
    }

    public ResponseCodeEnum grantKyc(final AccountID aId, final TokenID tId) {
        return tokenStore.grantKyc(aId, tId);
    }

    public ResponseCodeEnum freeze(final AccountID aId, final TokenID tId) {
        return tokenStore.freeze(aId, tId);
    }

    public ResponseCodeEnum unfreeze(final AccountID aId, final TokenID tId) {
        return tokenStore.unfreeze(aId, tId);
    }

    public void dropPendingTokenChanges() {
        TransferLogic.dropTokenChanges(
                sideEffectsTracker, nftsLedger, accountsLedger, tokenRelsLedger);
    }

    public ResponseCodeEnum doTokenTransfer(
            final TokenID tId, final AccountID from, final AccountID to, final long adjustment) {
        ResponseCodeEnum validity = adjustTokenBalance(from, tId, -adjustment);
        if (validity == OK) {
            validity = adjustTokenBalance(to, tId, adjustment);
        }

        if (validity != OK) {
            dropPendingTokenChanges();
        }
        return validity;
    }

    public void doZeroSum(final List<BalanceChange> changes) {
        transferLogic.doZeroSum(changes);
    }

    /* -- ACCOUNT META MANIPULATION -- */
    public AccountID create(
            final AccountID sponsor, final long balance, final HederaAccountCustomizer customizer) {
        final long newSponsorBalance = computeNewBalance(sponsor, -1 * balance);
        setBalance(sponsor, newSponsorBalance);

        final var id = ids.newAccountId(sponsor);
        spawn(id, balance, customizer);

        return id;
    }

    public void spawn(
            final AccountID id, final long balance, final HederaAccountCustomizer customizer) {
        accountsLedger.create(id);
        setBalance(id, balance);
        customizer.customize(id, accountsLedger);
    }

    public void customize(final AccountID id, final HederaAccountCustomizer customizer) {
        if ((boolean) accountsLedger.get(id, IS_DELETED)) {
            throw new DeletedAccountException(id);
        }
        customizer.customize(id, accountsLedger);
    }

    /**
     * Updates the provided {@link AccountID} with the {@link HederaAccountCustomizer}. All
     * properties from the customizer are applied to the {@link MerkleAccount} provisionally
     *
     * @param id target account
     * @param customizer properties to update
     */
    public void customizePotentiallyDeleted(
            final AccountID id, final HederaAccountCustomizer customizer) {
        customizer.customize(id, accountsLedger);
    }

    public void delete(final AccountID id, final AccountID beneficiary) {
        doTransfer(id, beneficiary, getBalance(id));
        accountsLedger.set(id, IS_DELETED, true);
    }

    /* -- ACCOUNT PROPERTY ACCESS -- */
    public boolean exists(final AccountID id) {
        return accountsLedger.exists(id);
    }

    public long expiry(final AccountID id) {
        return (long) accountsLedger.get(id, EXPIRY);
    }

    public long autoRenewPeriod(final AccountID id) {
        return (long) accountsLedger.get(id, AUTO_RENEW_PERIOD);
    }

    public boolean isSmartContract(final AccountID id) {
        return (boolean) accountsLedger.get(id, IS_SMART_CONTRACT);
    }

    public boolean isReceiverSigRequired(final AccountID id) {
        return (boolean) accountsLedger.get(id, IS_RECEIVER_SIG_REQUIRED);
    }

    public int maxAutomaticAssociations(final AccountID id) {
        return (int) accountsLedger.get(id, MAX_AUTOMATIC_ASSOCIATIONS);
    }

    public int alreadyUsedAutomaticAssociations(final AccountID id) {
        return (int) accountsLedger.get(id, USED_AUTOMATIC_ASSOCIATIONS);
    }

    public void setMaxAutomaticAssociations(final AccountID id, final int max) {
        accountsLedger.set(id, MAX_AUTOMATIC_ASSOCIATIONS, max);
    }

    public void setAlreadyUsedAutomaticAssociations(final AccountID id, final int usedCount) {
        accountsLedger.set(id, USED_AUTOMATIC_ASSOCIATIONS, usedCount);
    }

    public boolean isDeleted(final AccountID id) {
        return (boolean) accountsLedger.get(id, IS_DELETED);
    }

    public boolean isDetached(final AccountID id) {
        return validator.expiryStatusGiven(accountsLedger, id) != OK;
    }

    public ResponseCodeEnum usabilityOf(final AccountID id) {
        try {
            final var isDeleted = (boolean) accountsLedger.get(id, IS_DELETED);
            if (isDeleted) {
                final var isContract = (boolean) accountsLedger.get(id, IS_SMART_CONTRACT);
                return isContract ? CONTRACT_DELETED : ACCOUNT_DELETED;
            }
            return validator.expiryStatusGiven(accountsLedger, id);
        } catch (final MissingEntityException ignore) {
            return INVALID_ACCOUNT_ID;
        }
    }

    public JKey key(final AccountID id) {
        return (JKey) accountsLedger.get(id, KEY);
    }

    public String memo(final AccountID id) {
        return (String) accountsLedger.get(id, MEMO);
    }

    public ByteString alias(final AccountID id) {
        return (ByteString) accountsLedger.get(id, ALIAS);
    }

    public void clearAlias(final AccountID id) {
        accountsLedger.set(id, ALIAS, ByteString.EMPTY);
    }

    public boolean isPendingCreation(final AccountID id) {
        return accountsLedger.existsPending(id);
    }

    /* -- HELPERS -- */
    private boolean isLegalToAdjust(final long balance, final long adjustment) {
        return (balance + adjustment >= 0);
    }

    private long computeNewBalance(final AccountID id, final long adjustment) {
        if ((boolean) accountsLedger.get(id, IS_DELETED)) {
            throw new DeletedAccountException(id);
        }
        if (isDetached(id)) {
            throw new DetachedAccountException(id);
        }
        final long balance = getBalance(id);
        if (!isLegalToAdjust(balance, adjustment)) {
            throw new InsufficientFundsException(id, adjustment);
        }
        return balance + adjustment;
    }

    private void setBalance(final AccountID id, final long newBalance) {
        accountsLedger.set(id, BALANCE, newBalance);
    }

    /* -- Only used by unit tests --- */
    CurrencyAdjustments netTransfersInTxn() {
        return sideEffectsTracker.getNetTrackedHbarChanges();
    }

    List<TokenTransferList> netTokenTransfersInTxn() {
        return sideEffectsTracker.getNetTrackedTokenUnitAndOwnershipChanges();
    }
}
