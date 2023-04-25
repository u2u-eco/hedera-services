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

import static com.hedera.node.app.service.mono.utils.EntityIdUtils.isAlias;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;

import com.google.common.base.MoreObjects;
import com.google.protobuf.ByteString;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.TransferPrecompile;
import com.hedera.node.app.service.mono.store.models.Id;
import com.hedera.node.app.service.mono.store.models.NftId;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.common.utility.CommonUtils;
import edu.umd.cs.findbugs.annotations.Nullable;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Process object that encapsulates a balance change, either ℏ or token unit .
 *
 * <p>Includes an optional override for the {@link ResponseCodeEnum} to be used in the case that the
 * change is determined to result to an insufficient balance; and a field to contain the new balance
 * that will result from the change. (This field is helpful to simplify work done in {@link
 * HederaLedger}.)
 *
 * <p>The {@code tokenId} and {@code accountId} fields are temporary, needed to interact with the
 * {@code BackingAccounts} and {@code BackingTokenRels} components whose APIs still use gRPC types.
 */
public class BalanceChange {
    public static final AccountID DEFAULT_PAYER = AccountID.getDefaultInstance();
    public static final boolean DEFAULT_ALLOWANCE_APPROVAL = false;

    static final TokenID NO_TOKEN_FOR_HBAR_ADJUST = TokenID.getDefaultInstance();

    private Id token;

    private Id account;
    private long originalUnits;
    private long newBalance;
    private long aggregatedUnits;
    private long allowanceUnits;
    private boolean exemptFromCustomFees = false;
    private NftId nftId = null;
    private TokenID tokenId = null;
    private AccountID accountId;
    private AccountID counterPartyAccountId = null;
    private ByteString counterPartyAlias;
    private ResponseCodeEnum codeForInsufficientBalance;
    private ByteString alias;
    private int expectedDecimals = -1;
    private boolean isApprovedAllowance = false;
    private boolean isForCustomFee = false;
    private AccountID payerID = null;

    // This is used to indicate if the balance change is for a custom fee that is a fallback fee
    // This is used to enforce receiver signature requirements for fallback fees
    private boolean includesFallbackFee = false;

    public static BalanceChange changingHbar(final AccountAmount aa, final AccountID payerID) {
        return new BalanceChange(null, aa, INSUFFICIENT_ACCOUNT_BALANCE, payerID);
    }

    public static BalanceChange changingFtUnits(
            final Id token,
            final TokenID tokenId,
            final AccountAmount aa,
            final AccountID payerID) {
        final var tokenChange = new BalanceChange(token, aa, INSUFFICIENT_TOKEN_BALANCE, payerID);
        tokenChange.tokenId = tokenId;
        return tokenChange;
    }

    public static BalanceChange hbarCustomFeeAdjust(final Id id, final long amount) {
        return new BalanceChange(
                id,
                amount,
                DEFAULT_PAYER,
                DEFAULT_ALLOWANCE_APPROVAL,
                true,
                INSUFFICIENT_ACCOUNT_BALANCE);
    }

    public static BalanceChange changingNftOwnership(
            final Id token,
            final TokenID tokenId,
            final NftTransfer nftTransfer,
            final AccountID payerID) {
        final var nftChange =
                new BalanceChange(
                        token,
                        nftTransfer.getSenderAccountID(),
                        nftTransfer.getReceiverAccountID(),
                        nftTransfer.getSerialNumber(),
                        SENDER_DOES_NOT_OWN_NFT_SERIAL_NO);
        nftChange.tokenId = tokenId;
        nftChange.isApprovedAllowance = nftTransfer.getIsApproval();
        if (nftTransfer.getIsApproval()) {
            nftChange.allowanceUnits = -1;
        }
        nftChange.payerID = payerID;
        return nftChange;
    }

    public static BalanceChange tokenCustomFeeAdjust(
            final Id account, final Id token, final long amount) {
        return tokenAdjust(account, token, amount, DEFAULT_PAYER, DEFAULT_ALLOWANCE_APPROVAL, true);
    }

    public static BalanceChange tokenAdjust(
            final Id account,
            final Id token,
            final long amount,
            final AccountID payerID,
            final boolean isApprovedAllowance,
            final boolean isForCustomFee) {
        final var change =
                new BalanceChange(
                        account,
                        amount,
                        payerID,
                        isApprovedAllowance,
                        isForCustomFee,
                        INSUFFICIENT_TOKEN_BALANCE);
        change.payerID = payerID;
        change.token = token;
        change.tokenId = token.asGrpcToken();
        return change;
    }

    /* ℏ constructor */
    private BalanceChange(
            final Id account,
            final long amount,
            final AccountID payerID,
            final boolean isApprovedAllowance,
            final boolean isForCustomFee,
            final ResponseCodeEnum code) {
        this.token = null;
        this.account = account;
        this.accountId = account.asGrpcAccount();
        this.alias = accountId.getAlias();
        this.originalUnits = amount;
        this.isApprovedAllowance = isApprovedAllowance;
        this.isForCustomFee = isForCustomFee;
        this.payerID = payerID;
        this.codeForInsufficientBalance = code;
        this.aggregatedUnits = amount;
        // Only set allowanceUnits if it is an allowance transfer and the account is the sender.
        if (isApprovedAllowance && amount < 0) {
            this.allowanceUnits = amount;
        }
    }

    /* HTS constructor */
    private BalanceChange(
            final Id token,
            final AccountAmount aa,
            final ResponseCodeEnum code,
            final AccountID payerID) {
        this.token = token;
        this.accountId = aa.getAccountID();
        this.alias = accountId.getAlias();
        this.account = Id.fromGrpcAccount(accountId);
        this.isApprovedAllowance = aa.getIsApproval();
        this.originalUnits = aa.getAmount();
        this.codeForInsufficientBalance = code;
        this.payerID = payerID;
        this.aggregatedUnits = aa.getAmount();
        // Only set allowanceUnits if it is an allowance transfer and the account is the sender.
        if (isApprovedAllowance && originalUnits < 0) {
            this.allowanceUnits = originalUnits;
        }
    }

    /* NFT constructor */
    private BalanceChange(
            final Id token,
            final AccountID sender,
            final AccountID receiver,
            final long serialNo,
            final ResponseCodeEnum code) {
        this.token = token;
        this.nftId = new NftId(token.shard(), token.realm(), token.num(), serialNo);
        this.accountId = sender;
        this.counterPartyAccountId = receiver;
        this.counterPartyAlias = receiver.getAlias();
        this.account = Id.fromGrpcAccount(accountId);
        this.alias = accountId.getAlias();
        this.codeForInsufficientBalance = code;
        this.aggregatedUnits = serialNo;
    }

    public void replaceNonEmptyAliasWith(final EntityNum createdId) {
        if (isAlias(accountId)) {
            accountId = createdId.toGrpcAccountId();
            account = Id.fromGrpcAccount(accountId);
        } else if (hasNonEmptyCounterPartyAlias()) {
            counterPartyAccountId = createdId.toGrpcAccountId();
        }
    }

    /**
     * If this balance change is an hbar or fungible token debit, or an NFT ownership change;
     * <i>and</i> it is not already an approved change, converts this to an approved change.
     *
     * <p>We need this so that when a {@link TransferPrecompile} is running without access to the
     * top-level {@link SignatureMap}, inside a {@code ContractCall} that previously relied on
     * top-level signatures, it can keep working by "setting up" the {@code ContractCall} with
     * appropriate allowances that we will use automatically.
     */
    public void switchToApproved() {
        if (isApprovedAllowance) {
            return;
        }
        if (token == null || nftId == null) {
            if (originalUnits < 0L) {
                isApprovedAllowance = true;
                allowanceUnits = originalUnits;
            }
        } else {
            isApprovedAllowance = true;
            allowanceUnits = -1;
        }
    }

    @Nullable
    public Pair<ByteString, AccountID> getAliasToNewId() {
        if (accountId != null && !alias.isEmpty()) {
            return Pair.of(alias, accountId);
        } else if (counterPartyAccountId != null && counterPartyAlias != null) {
            return Pair.of(counterPartyAlias, counterPartyAccountId);
        } else {
            return null;
        }
    }

    public boolean isForHbar() {
        return token == null;
    }

    public boolean isForFungibleToken() {
        return token != null && counterPartyAccountId == null;
    }

    public boolean isForNft() {
        return token != null && counterPartyAccountId != null;
    }

    public boolean isForToken() {
        return isForFungibleToken() || isForNft();
    }

    public boolean affectsAccount(final AccountID accountId) {
        return accountId.equals(this.accountId);
    }

    public NftId nftId() {
        return nftId;
    }

    public long originalUnits() {
        return originalUnits;
    }

    public long serialNo() {
        return aggregatedUnits;
    }

    public long getNewBalance() {
        return newBalance;
    }

    public void setNewBalance(final long newBalance) {
        this.newBalance = newBalance;
    }

    public TokenID tokenId() {
        return (tokenId != null) ? tokenId : NO_TOKEN_FOR_HBAR_ADJUST;
    }

    public AccountID accountId() {
        return accountId;
    }

    public ByteString alias() {
        return alias;
    }

    public AccountID counterPartyAccountId() {
        return counterPartyAccountId;
    }

    public ByteString counterPartyAlias() {
        return counterPartyAlias;
    }

    public Id getAccount() {
        return account;
    }

    public Id getToken() {
        return token;
    }

    public ResponseCodeEnum codeForInsufficientBalance() {
        return codeForInsufficientBalance;
    }

    public boolean hasExpectedDecimals() {
        return expectedDecimals != -1;
    }

    public int getExpectedDecimals() {
        return expectedDecimals;
    }

    public void setExpectedDecimals(final int expectedDecimals) {
        this.expectedDecimals = expectedDecimals;
    }

    /**
     * allowanceUnits are always non-positive. If negative that accountId has some allowanceUnits to
     * be taken off from its allowanceMap with the respective payer. It will be -1 for nft ownership
     * changes.
     *
     * @return true if negative allowanceUnits
     */
    public boolean isApprovedAllowance() {
        return this.allowanceUnits < 0;
    }

    public boolean isForCustomFee() {
        return this.isForCustomFee;
    }

    public AccountID getPayerID() {
        return payerID;
    }

    public void aggregateUnits(long amount) {
        this.aggregatedUnits += amount;
    }

    public long getAggregatedUnits() {
        return this.aggregatedUnits;
    }

    public void addAllowanceUnits(long amount) {
        this.allowanceUnits += amount;
    }

    public long getAllowanceUnits() {
        return this.allowanceUnits;
    }

    /* NOTE: The object methods below are only overridden to improve readability of unit tests;
    this model object is not used in hash-based collections, so the performance of these
    methods doesn't matter. */

    @Override
    public boolean equals(final Object obj) {
        return EqualsBuilder.reflectionEquals(this, obj);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public String toString() {
        if (counterPartyAccountId == null) {
            return MoreObjects.toStringHelper(BalanceChange.class)
                    .add("token", token == null ? "ℏ" : token)
                    .add("account", account)
                    .add("alias", CommonUtils.hex(alias.toByteArray()))
                    .add("units", aggregatedUnits)
                    .add("expectedDecimals", expectedDecimals)
                    .toString();
        } else {
            return MoreObjects.toStringHelper(BalanceChange.class)
                    .add("nft", token)
                    .add("serialNo", aggregatedUnits)
                    .add("from", account)
                    .add("to", Id.fromGrpcAccount(counterPartyAccountId))
                    .add("counterPartyAlias", CommonUtils.hex(counterPartyAlias.toByteArray()))
                    .toString();
        }
    }

    public void setCodeForInsufficientBalance(ResponseCodeEnum codeForInsufficientBalance) {
        this.codeForInsufficientBalance = codeForInsufficientBalance;
    }

    public void setExemptFromCustomFees(boolean exemptFromCustomFees) {
        this.exemptFromCustomFees = exemptFromCustomFees;
    }

    public boolean isExemptFromCustomFees() {
        return exemptFromCustomFees;
    }

    public boolean hasAlias() {
        return isAlias(accountId) || hasNonEmptyCounterPartyAlias();
    }

    public boolean hasNonEmptyCounterPartyAlias() {
        return counterPartyAccountId != null && isAlias(counterPartyAccountId);
    }

    /**
     * Since a change can have either an unknown alias or a counterPartyAlias (but not both),
     * returns any non-empty unknown alias in the change.
     *
     * @return non-empty alias
     */
    public ByteString getNonEmptyAliasIfPresent() {
        if (isAlias(accountId)) return alias;
        else if (hasNonEmptyCounterPartyAlias()) return counterPartyAlias;
        else return null;
    }

    /**
     * Boolean flag to indicate if the change is for a custom fee that includes a fallback fee.
     *
     * @return true if the change is for a custom fee that includes a fallback fee
     */
    public boolean includesFallbackFee() {
        return includesFallbackFee;
    }
    /**
     * Sets the flag to indicate if the change is for a custom fee that includes a fallback fee.
     * This is used to enforce the receiver signature requirement in a crypto transfer of an NFT
     * with a fallback royalty fee.
     */
    public void setIncludesFallbackFee() {
        this.includesFallbackFee = true;
    }
}
