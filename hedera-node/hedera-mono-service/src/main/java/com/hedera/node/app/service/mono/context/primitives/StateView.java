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
package com.hedera.node.app.service.mono.context.primitives;

import static com.hedera.node.app.service.mono.context.properties.StaticPropertiesHolder.STATIC_PROPERTIES;
import static com.hedera.node.app.service.mono.ledger.accounts.AliasManager.tryAddressRecovery;
import static com.hedera.node.app.service.mono.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static com.hedera.node.app.service.mono.store.models.Id.MISSING_ID;
import static com.hedera.node.app.service.mono.store.schedule.ScheduleStore.MISSING_SCHEDULE;
import static com.hedera.node.app.service.mono.txns.crypto.helpers.AllowanceHelpers.getCryptoGrantedAllowancesList;
import static com.hedera.node.app.service.mono.txns.crypto.helpers.AllowanceHelpers.getFungibleGrantedTokenAllowancesList;
import static com.hedera.node.app.service.mono.txns.crypto.helpers.AllowanceHelpers.getNftGrantedAllowancesList;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.EVM_ADDRESS_SIZE;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.asAccount;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.asHexedEvmAddress;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.readableId;
import static com.hedera.node.app.service.mono.utils.EntityNum.fromAccountId;
import static com.hedera.node.app.service.mono.utils.EvmTokenUtil.asEvmTokenInfo;
import static com.hedera.node.app.service.mono.utils.EvmTokenUtil.evmCustomFees;
import static com.hedera.node.app.service.mono.utils.MiscUtils.asKeyUnchecked;
import static com.hedera.node.app.service.mono.utils.MiscUtils.isRecoveredEvmAddress;
import static com.swirlds.common.utility.CommonUtils.hex;
import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableMap;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.ByteStringUtils;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmTokenInfo;
import com.hedera.node.app.service.evm.utils.EthSigsUtils;
import com.hedera.node.app.service.mono.config.NetworkInfo;
import com.hedera.node.app.service.mono.context.StateChildren;
import com.hedera.node.app.service.mono.contracts.sources.AddressKeyedMapFactory;
import com.hedera.node.app.service.mono.files.DataMapFactory;
import com.hedera.node.app.service.mono.files.HFileMeta;
import com.hedera.node.app.service.mono.files.MetadataMapFactory;
import com.hedera.node.app.service.mono.files.store.FcBlobsBytesStore;
import com.hedera.node.app.service.mono.ledger.accounts.AliasManager;
import com.hedera.node.app.service.mono.ledger.accounts.staking.RewardCalculator;
import com.hedera.node.app.service.mono.ledger.backing.BackingAccounts;
import com.hedera.node.app.service.mono.ledger.backing.BackingNfts;
import com.hedera.node.app.service.mono.ledger.backing.BackingStore;
import com.hedera.node.app.service.mono.ledger.backing.BackingTokenRels;
import com.hedera.node.app.service.mono.ledger.backing.BackingTokens;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKeyList;
import com.hedera.node.app.service.mono.sigs.sourcing.KeyType;
import com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext;
import com.hedera.node.app.service.mono.state.merkle.MerkleStakingInfo;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.state.merkle.MerkleTopic;
import com.hedera.node.app.service.mono.state.migration.AccountStorageAdapter;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.state.migration.HederaTokenRel;
import com.hedera.node.app.service.mono.state.migration.RecordsStorageAdapter;
import com.hedera.node.app.service.mono.state.migration.TokenRelStorageAdapter;
import com.hedera.node.app.service.mono.state.migration.UniqueTokenAdapter;
import com.hedera.node.app.service.mono.state.migration.UniqueTokenMapAdapter;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.RawTokenRelationship;
import com.hedera.node.app.service.mono.state.virtual.ContractKey;
import com.hedera.node.app.service.mono.state.virtual.IterableContractValue;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobKey;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobValue;
import com.hedera.node.app.service.mono.store.models.NftId;
import com.hedera.node.app.service.mono.store.schedule.ScheduleStore;
import com.hedera.node.app.service.mono.utils.EntityIdUtils;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.EntityNumPair;
import com.hedera.node.app.service.mono.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ConsensusTopicInfo;
import com.hederahashgraph.api.proto.java.ContractGetInfoResponse;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileGetInfoResponse;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.GetAccountDetailsResponse;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.ScheduleInfo;
import com.hederahashgraph.api.proto.java.StakingInfo;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenFreezeStatus;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenInfo;
import com.hederahashgraph.api.proto.java.TokenKycStatus;
import com.hederahashgraph.api.proto.java.TokenNftInfo;
import com.hederahashgraph.api.proto.java.TokenPauseStatus;
import com.hederahashgraph.api.proto.java.TokenRelationship;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.TopicID;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

public class StateView {

    private static final Logger log = LogManager.getLogger(StateView.class);

    private static final String FAILURE_TOKEN_INFO =
            "Unexpected failure getting info for token {}!";

    /* EVM storage maps from 256-bit (32-byte) keys to 256-bit (32-byte) values */
    public static final long BYTES_PER_EVM_KEY_VALUE_PAIR = 64L;
    public static final AccountID WILDCARD_OWNER = AccountID.newBuilder().setAccountNum(0L).build();

    static final byte[] EMPTY_BYTES = new byte[0];

    public static final JKey EMPTY_WACL = new JKeyList();
    public static final MerkleToken REMOVED_TOKEN =
            new MerkleToken(0L, 0L, 0, "", "", false, false, MISSING_ENTITY_ID);

    private final ScheduleStore scheduleStore;
    private final StateChildren stateChildren;

    private final NetworkInfo networkInfo;

    Map<byte[], byte[]> contractBytecode;

    Map<FileID, byte[]> fileContents;
    Map<FileID, HFileMeta> fileAttrs;
    private BackingStore<TokenID, MerkleToken> backingTokens = null;

    private BackingStore<AccountID, HederaAccount> backingAccounts = null;
    private BackingStore<NftId, UniqueTokenAdapter> backingNfts = null;
    private BackingStore<Pair<AccountID, TokenID>, HederaTokenRel> backingRels = null;

    public StateView(
            final ScheduleStore scheduleStore,
            final StateChildren stateChildren,
            final NetworkInfo networkInfo) {
        this.scheduleStore = scheduleStore;
        this.stateChildren = stateChildren;
        this.networkInfo = networkInfo;

        final Map<String, byte[]> blobStore = unmodifiableMap(new FcBlobsBytesStore(this::storage));

        fileContents = DataMapFactory.dataMapFrom(blobStore);
        fileAttrs = MetadataMapFactory.metaMapFrom(blobStore);
        contractBytecode = AddressKeyedMapFactory.bytecodeMapFrom(blobStore);
    }

    public NetworkInfo getNetworkInfo() {
        return networkInfo;
    }

    public Optional<HFileMeta> attrOf(final FileID id) {
        return Optional.ofNullable(fileAttrs.get(id));
    }

    public Optional<byte[]> contentsOf(final FileID id) {
        final var specialFiles = stateChildren.specialFiles();
        if (specialFiles.contains(id)) {
            return Optional.ofNullable(specialFiles.get(id));
        } else {
            return Optional.ofNullable(fileContents.get(id));
        }
    }

    public Optional<byte[]> bytecodeOf(final EntityNum contractId) {
        return Optional.ofNullable(contractBytecode.get(contractId.toRawEvmAddress()));
    }

    public Optional<MerkleToken> tokenWith(final TokenID id) {
        return Optional.ofNullable(stateChildren.tokens().get(EntityNum.fromTokenId(id)));
    }

    public Optional<EvmTokenInfo> evmInfoForToken(final TokenID tokenId) {
        try {
            final var tokens = stateChildren.tokens();
            final var token = tokens.get(EntityNum.fromTokenId(tokenId));
            if (token == null) {
                return Optional.empty();
            }
            return Optional.of(asEvmTokenInfo(token, networkInfo.ledgerId()));
        } catch (Exception unexpected) {
            log.warn(FAILURE_TOKEN_INFO, readableId(tokenId), unexpected);
            return Optional.empty();
        }
    }

    public Optional<TokenInfo> infoForToken(final TokenID tokenId) {
        try {
            final var tokens = stateChildren.tokens();
            final var token = tokens.get(EntityNum.fromTokenId(tokenId));
            if (token == null) {
                return Optional.empty();
            }
            return Optional.of(token.asTokenInfo(tokenId, networkInfo.ledgerId()));
        } catch (Exception unexpected) {
            log.warn(FAILURE_TOKEN_INFO, readableId(tokenId), unexpected);
            return Optional.empty();
        }
    }

    public Optional<ConsensusTopicInfo> infoForTopic(final TopicID topicID) {
        final var merkleTopic = topics().get(EntityNum.fromTopicId(topicID));
        if (merkleTopic == null) {
            return Optional.empty();
        }

        final var info = ConsensusTopicInfo.newBuilder();
        if (merkleTopic.hasMemo()) {
            info.setMemo(merkleTopic.getMemo());
        }
        if (merkleTopic.hasAdminKey()) {
            info.setAdminKey(asKeyUnchecked(merkleTopic.getAdminKey()));
        }
        if (merkleTopic.hasSubmitKey()) {
            info.setSubmitKey(asKeyUnchecked(merkleTopic.getSubmitKey()));
        }
        info.setAutoRenewPeriod(
                Duration.newBuilder().setSeconds(merkleTopic.getAutoRenewDurationSeconds()));
        if (merkleTopic.hasAutoRenewAccountId()) {
            info.setAutoRenewAccount(asAccount(merkleTopic.getAutoRenewAccountId()));
        }
        info.setExpirationTime(merkleTopic.getExpirationTimestamp().toGrpc());
        info.setSequenceNumber(merkleTopic.getSequenceNumber());
        info.setRunningHash(ByteString.copyFrom(merkleTopic.getRunningHash()));
        info.setLedgerId(networkInfo.ledgerId());

        return Optional.of(info.build());
    }

    public Optional<ScheduleInfo> infoForSchedule(final ScheduleID scheduleID) {
        try {
            final var id = scheduleStore.resolve(scheduleID);
            if (id == MISSING_SCHEDULE) {
                return Optional.empty();
            }
            final var schedule = scheduleStore.get(id);
            final var signatories = schedule.signatories();
            final var signatoriesList = KeyList.newBuilder();

            signatories.forEach(pubKey -> signatoriesList.addKeys(grpcKeyReprOf(pubKey)));

            final var info =
                    ScheduleInfo.newBuilder()
                            .setLedgerId(networkInfo.ledgerId())
                            .setScheduleID(id)
                            .setScheduledTransactionBody(schedule.scheduledTxn())
                            .setScheduledTransactionID(schedule.scheduledTransactionId())
                            .setCreatorAccountID(schedule.schedulingAccount().toGrpcAccountId())
                            .setPayerAccountID(schedule.effectivePayer().toGrpcAccountId())
                            .setSigners(signatoriesList)
                            .setExpirationTime(
                                    Timestamp.newBuilder()
                                            .setSeconds(
                                                    schedule.calculatedExpirationTime()
                                                            .getSeconds())
                                            .setNanos(
                                                    schedule.calculatedExpirationTime().getNanos()))
                            .setWaitForExpiry(schedule.calculatedWaitForExpiry());
            schedule.memo().ifPresent(info::setMemo);
            if (schedule.isDeleted()) {
                info.setDeletionTime(schedule.deletionTime());
            } else if (schedule.isExecuted()) {
                info.setExecutionTime(schedule.executionTime());
            }

            final var adminCandidate = schedule.adminKey();
            adminCandidate.ifPresent(k -> info.setAdminKey(asKeyUnchecked(k)));

            return Optional.of(info.build());
        } catch (Exception unexpected) {
            log.warn(
                    "Unexpected failure getting info for schedule {}!",
                    readableId(scheduleID),
                    unexpected);
            return Optional.empty();
        }
    }

    private Key grpcKeyReprOf(final byte[] publicKey) {
        if (publicKey.length == KeyType.ECDSA_SECP256K1.getLength()) {
            return Key.newBuilder().setECDSASecp256K1(ByteString.copyFrom(publicKey)).build();
        } else {
            return Key.newBuilder().setEd25519(ByteString.copyFrom(publicKey)).build();
        }
    }

    public Optional<TokenNftInfo> infoForNft(final NftID target) {
        final var currentNfts = uniqueTokens();
        final var tokenId = EntityNum.fromTokenId(target.getTokenID());
        final var targetKey =
                NftId.withDefaultShardRealm(tokenId.longValue(), target.getSerialNumber());
        if (!currentNfts.containsKey(targetKey)) {
            return Optional.empty();
        }
        final var targetNft = currentNfts.get(targetKey);
        var accountId = targetNft.getOwner().toGrpcAccountId();

        if (WILDCARD_OWNER.equals(accountId)) {
            var merkleToken = tokens().get(tokenId);
            if (merkleToken == null) {
                return Optional.empty();
            }
            accountId = merkleToken.treasury().toGrpcAccountId();
        }

        final var spenderId = targetNft.getSpender().toGrpcAccountId();

        final var info =
                TokenNftInfo.newBuilder()
                        .setLedgerId(networkInfo.ledgerId())
                        .setNftID(target)
                        .setAccountID(accountId)
                        .setCreationTime(targetNft.getCreationTime().toGrpc())
                        .setMetadata(ByteString.copyFrom(targetNft.getMetadata()))
                        .setSpenderId(spenderId)
                        .build();
        return Optional.of(info);
    }

    public boolean nftExists(final NftID id) {
        return uniqueTokens().containsKey(NftId.fromGrpc(id));
    }

    public Optional<TokenType> tokenType(final TokenID tokenId) {
        try {
            final var optionalToken = tokenWith(tokenId);
            if (optionalToken.isEmpty()) {
                return Optional.empty();
            }
            return optionalToken.map(token -> TokenType.forNumber(token.tokenType().ordinal()));
        } catch (Exception unexpected) {
            log.warn(FAILURE_TOKEN_INFO, readableId(tokenId), unexpected);
            return Optional.empty();
        }
    }

    public boolean tokenExists(final TokenID id) {
        return stateChildren != null
                && stateChildren.tokens().containsKey(EntityNum.fromTokenId(id));
    }

    public boolean scheduleExists(final ScheduleID id) {
        return scheduleStore != null && scheduleStore.resolve(id) != MISSING_SCHEDULE;
    }

    public Optional<FileGetInfoResponse.FileInfo> infoForFile(final FileID id) {
        try {
            return getFileInfo(id);
        } catch (NullPointerException e) {
            log.warn("View used without a properly initialized VirtualMap", e);
            return Optional.empty();
        }
    }

    private Optional<FileGetInfoResponse.FileInfo> getFileInfo(final FileID id) {
        final var attr = fileAttrs.get(id);
        if (attr == null) {
            return Optional.empty();
        }
        final var contents = contentsOf(id).orElse(EMPTY_BYTES);
        final var info =
                FileGetInfoResponse.FileInfo.newBuilder()
                        .setLedgerId(networkInfo.ledgerId())
                        .setFileID(id)
                        .setDeleted(attr.isDeleted())
                        .setExpirationTime(Timestamp.newBuilder().setSeconds(attr.getExpiry()))
                        .setSize(contents.length);
        if (!attr.getWacl().isEmpty()) {
            info.setKeys(MiscUtils.asKeyUnchecked(attr.getWacl()).getKeyList());
        }
        if (!stateChildren.specialFiles().contains(id)) {
            info.setMemo(attr.getMemo());
        } else {
            // The "memo" of a special upgrade file is its hexed SHA-384 hash for DevOps convenience
            final var upgradeHash = hex(CryptographyHolder.get().digestSync(contents).getValue());
            info.setMemo(upgradeHash);
        }
        return Optional.of(info.build());
    }

    public Optional<CryptoGetInfoResponse.AccountInfo> infoForAccount(
            final AccountID id,
            final AliasManager aliasManager,
            final int maxTokensForAccountInfo,
            final RewardCalculator rewardCalculator) {
        final var accountNum =
                id.getAlias().isEmpty()
                        ? fromAccountId(id)
                        : aliasManager.lookupIdBy(id.getAlias());
        final var account = accounts().get(accountNum);
        if (account == null) {
            return Optional.empty();
        }

        final AccountID accountID = id.getAlias().isEmpty() ? id : accountNum.toGrpcAccountId();
        final var info =
                CryptoGetInfoResponse.AccountInfo.newBuilder()
                        .setLedgerId(networkInfo.ledgerId())
                        .setKey(asKeyUnchecked(account.getAccountKey()))
                        .setAccountID(accountID)
                        .setReceiverSigRequired(account.isReceiverSigRequired())
                        .setDeleted(account.isDeleted())
                        .setMemo(account.getMemo())
                        .setAutoRenewPeriod(
                                Duration.newBuilder().setSeconds(account.getAutoRenewSecs()))
                        .setBalance(account.getBalance())
                        .setExpirationTime(Timestamp.newBuilder().setSeconds(account.getExpiry()))
                        .setContractAccountID(
                                getContractAccountId(
                                        account.getAccountKey(), accountID, account.getAlias()))
                        .setOwnedNfts(account.getNftsOwned())
                        .setMaxAutomaticTokenAssociations(account.getMaxAutomaticAssociations())
                        .setEthereumNonce(account.getEthereumNonce());
        Optional.ofNullable(account.getProxy())
                .map(EntityId::toGrpcAccountId)
                .ifPresent(info::setProxyAccountID);
        if (account.getAlias().size() != EVM_ADDRESS_SIZE) {
            info.setAlias(account.getAlias());
        }
        final var tokenRels = tokenRels(this, account, maxTokensForAccountInfo);
        if (!tokenRels.isEmpty()) {
            info.addAllTokenRelationships(tokenRels);
        }
        info.setStakingInfo(stakingInfo(account, rewardCalculator));

        return Optional.of(info.build());
    }

    private String getContractAccountId(
            final JKey key, final AccountID accountID, final ByteString alias) {
        if (alias.size() == EVM_ADDRESS_SIZE) {
            return CommonUtils.hex(ByteStringUtils.unwrapUnsafelyIfPossible(alias));
        }
        // If we can recover an Ethereum EOA address from the account key, we should return that
        final var evmAddress = tryAddressRecovery(key, EthSigsUtils::recoverAddressFromPubKey);
        if (isRecoveredEvmAddress(evmAddress)) {
            return Bytes.wrap(evmAddress).toUnprefixedHexString();
        } else {
            return asHexedEvmAddress(accountID);
        }
    }

    /**
     * Builds {@link StakingInfo} object for the {@link
     * com.hederahashgraph.api.proto.java.CryptoGetInfo} and {@link
     * com.hederahashgraph.api.proto.java.ContractGetInfo}
     *
     * @param account given account for which info is queried
     * @return staking info
     */
    public StakingInfo stakingInfo(
            final HederaAccount account, final RewardCalculator rewardCalculator) {
        // will be updated with pending_reward in future PR
        final var stakingInfo =
                StakingInfo.newBuilder()
                        .setDeclineReward(account.isDeclinedReward())
                        .setStakedToMe(account.getStakedToMe());

        final var stakedNum = account.getStakedId();
        if (stakedNum < 0) {
            // Staked num for a node is (-nodeId -1)
            stakingInfo.setStakedNodeId(-stakedNum - 1);
            addNodeStakeMeta(stakingInfo, account, rewardCalculator);
        } else if (stakedNum > 0) {
            stakingInfo.setStakedAccountId(STATIC_PROPERTIES.scopedAccountWith(stakedNum));
        }

        return stakingInfo.build();
    }

    public List<CustomFee> infoForTokenCustomFees(final TokenID tokenId) {
        try {
            final var tokens = stateChildren.tokens();
            final var token = tokens.get(EntityNum.fromTokenId(tokenId));
            if (token == null) {
                return emptyList();
            }
            return evmCustomFees(token.grpcFeeSchedule());
        } catch (Exception unexpected) {
            log.warn(
                    "Unexpected failure getting custom fees for token {}!",
                    readableId(tokenId),
                    unexpected);
            return emptyList();
        }
    }

    private void addNodeStakeMeta(
            final StakingInfo.Builder stakingInfo,
            final HederaAccount account,
            final RewardCalculator rewardCalculator) {
        final var startSecond =
                rewardCalculator.epochSecondAtStartOfPeriod(account.getStakePeriodStart());
        stakingInfo.setStakePeriodStart(Timestamp.newBuilder().setSeconds(startSecond).build());
        if (account.mayHavePendingReward()) {
            final var info = stateChildren.stakingInfo();
            final var nodeStakingInfo =
                    info.get(EntityNum.fromLong(account.getStakedNodeAddressBookId()));
            final var pendingReward =
                    rewardCalculator.estimatePendingRewards(account, nodeStakingInfo);
            stakingInfo.setPendingReward(pendingReward);
        }
    }

    public Optional<GetAccountDetailsResponse.AccountDetails> accountDetails(
            final AccountID id,
            final AliasManager aliasManager,
            final int maxTokensForAccountInfo) {
        final var accountNum =
                id.getAlias().isEmpty()
                        ? fromAccountId(id)
                        : aliasManager.lookupIdBy(id.getAlias());
        final var account = accounts().get(accountNum);
        if (account == null) {
            return Optional.empty();
        }

        final AccountID accountID = id.getAlias().isEmpty() ? id : accountNum.toGrpcAccountId();
        final var details =
                GetAccountDetailsResponse.AccountDetails.newBuilder()
                        .setLedgerId(networkInfo.ledgerId())
                        .setKey(asKeyUnchecked(account.getAccountKey()))
                        .setAccountId(accountID)
                        .setAlias(account.getAlias())
                        .setReceiverSigRequired(account.isReceiverSigRequired())
                        .setDeleted(account.isDeleted())
                        .setMemo(account.getMemo())
                        .setAutoRenewPeriod(
                                Duration.newBuilder().setSeconds(account.getAutoRenewSecs()))
                        .setBalance(account.getBalance())
                        .setExpirationTime(Timestamp.newBuilder().setSeconds(account.getExpiry()))
                        .setContractAccountId(asHexedEvmAddress(accountID))
                        .setOwnedNfts(account.getNftsOwned())
                        .setMaxAutomaticTokenAssociations(account.getMaxAutomaticAssociations());
        Optional.ofNullable(account.getProxy())
                .map(EntityId::toGrpcAccountId)
                .ifPresent(details::setProxyAccountId);
        final var tokenRels = tokenRels(this, account, maxTokensForAccountInfo);
        if (!tokenRels.isEmpty()) {
            details.addAllTokenRelationships(tokenRels);
        }
        setAllowancesIfAny(details, account);
        return Optional.of(details.build());
    }

    private void setAllowancesIfAny(
            final GetAccountDetailsResponse.AccountDetails.Builder details,
            final HederaAccount account) {
        details.addAllGrantedCryptoAllowances(getCryptoGrantedAllowancesList(account));
        details.addAllGrantedTokenAllowances(getFungibleGrantedTokenAllowancesList(account));
        details.addAllGrantedNftAllowances(getNftGrantedAllowancesList(account));
    }

    public long numNftsOwnedBy(AccountID target) {
        final var account = accounts().get(fromAccountId(target));
        if (account == null) {
            return 0L;
        }
        return account.getNftsOwned();
    }

    public Optional<ContractGetInfoResponse.ContractInfo> infoForContract(
            final ContractID id,
            final AliasManager aliasManager,
            final int maxTokensForAccountInfo,
            final RewardCalculator rewardCalculator) {
        final var contractId = EntityIdUtils.unaliased(id, aliasManager);
        final var contract = contracts().get(contractId);
        if (contract == null) {
            return Optional.empty();
        }

        final var mirrorId = contractId.toGrpcAccountId();
        final var storageSize = contract.getNumContractKvPairs() * BYTES_PER_EVM_KEY_VALUE_PAIR;
        final var info =
                ContractGetInfoResponse.ContractInfo.newBuilder()
                        .setLedgerId(networkInfo.ledgerId())
                        .setAccountID(mirrorId)
                        .setDeleted(contract.isDeleted())
                        .setContractID(contractId.toGrpcContractID())
                        .setMemo(contract.getMemo())
                        .setStorage(storageSize)
                        .setAutoRenewPeriod(
                                Duration.newBuilder().setSeconds(contract.getAutoRenewSecs()))
                        .setBalance(contract.getBalance())
                        .setExpirationTime(Timestamp.newBuilder().setSeconds(contract.getExpiry()))
                        .setMaxAutomaticTokenAssociations(contract.getMaxAutomaticAssociations());
        if (contract.hasAutoRenewAccount()) {
            info.setAutoRenewAccountId(
                    Objects.requireNonNull(contract.getAutoRenewAccount()).toGrpcAccountId());
        }
        if (contract.hasAlias()) {
            info.setContractAccountID(hex(contract.getAlias().toByteArray()));
        } else {
            info.setContractAccountID(asHexedEvmAddress(mirrorId));
        }
        final var tokenRels = tokenRels(this, contract, maxTokensForAccountInfo);
        if (!tokenRels.isEmpty()) {
            info.addAllTokenRelationships(tokenRels);
        }

        info.setStakingInfo(stakingInfo(contract, rewardCalculator));

        try {
            final var adminKey = JKey.mapJKey(contract.getAccountKey());
            info.setAdminKey(adminKey);
        } catch (Exception ignore) {
            // Leave the admin key empty if it can't be decoded
        }

        return Optional.of(info.build());
    }

    public MerkleMap<EntityNum, MerkleTopic> topics() {
        return Objects.requireNonNull(stateChildren).topics();
    }

    public AccountStorageAdapter accounts() {
        return Objects.requireNonNull(stateChildren).accounts();
    }

    public RecordsStorageAdapter payerRecords() {
        return Objects.requireNonNull(stateChildren).payerRecords();
    }

    public AccountStorageAdapter contracts() {
        return Objects.requireNonNull(stateChildren).accounts();
    }

    public TokenRelStorageAdapter tokenAssociations() {
        return Objects.requireNonNull(stateChildren).tokenAssociations();
    }

    public UniqueTokenMapAdapter uniqueTokens() {
        return Objects.requireNonNull(stateChildren).uniqueTokens();
    }

    public VirtualMap<VirtualBlobKey, VirtualBlobValue> storage() {
        return Objects.requireNonNull(stateChildren).storage();
    }

    public VirtualMap<ContractKey, IterableContractValue> contractStorage() {
        return Objects.requireNonNull(stateChildren).contractStorage();
    }

    public MerkleMap<EntityNum, MerkleToken> tokens() {
        return Objects.requireNonNull(stateChildren).tokens();
    }

    public BackingStore<TokenID, MerkleToken> asReadOnlyTokenStore() {
        if (backingTokens == null) {
            backingTokens = new BackingTokens(stateChildren::tokens);
        }
        return backingTokens;
    }

    public BackingStore<AccountID, HederaAccount> asReadOnlyAccountStore() {
        if (backingAccounts == null) {
            backingAccounts =
                    new BackingAccounts(stateChildren::accounts, stateChildren::payerRecords);
        }
        return backingAccounts;
    }

    public BackingStore<NftId, UniqueTokenAdapter> asReadOnlyNftStore() {
        if (backingNfts == null) {
            backingNfts = new BackingNfts(stateChildren::uniqueTokens);
        }
        return backingNfts;
    }

    public BackingStore<Pair<AccountID, TokenID>, HederaTokenRel> asReadOnlyAssociationStore() {
        if (backingRels == null) {
            backingRels = new BackingTokenRels(stateChildren::tokenAssociations);
        }
        return backingRels;
    }

    public static TokenFreezeStatus tokenFreeStatusFor(final boolean flag) {
        return flag ? TokenFreezeStatus.Frozen : TokenFreezeStatus.Unfrozen;
    }

    public static TokenKycStatus tokenKycStatusFor(final boolean flag) {
        return flag ? TokenKycStatus.Granted : TokenKycStatus.Revoked;
    }

    public static TokenPauseStatus tokenPauseStatusOf(final boolean flag) {
        return flag ? TokenPauseStatus.Paused : TokenPauseStatus.Unpaused;
    }

    /**
     * Returns the most recent token relationships formed by the given account in the given view of
     * the state, up to maximum of {@code maxRels} relationships.
     *
     * @param view a view of the world state
     * @param account the account of interest
     * @param maxRels the maximum token relationships to return
     * @return a list of the account's newest token relationships up to the given limit
     */
    static List<TokenRelationship> tokenRels(
            final StateView view, final HederaAccount account, final int maxRels) {
        final List<TokenRelationship> grpcRels = new ArrayList<>();
        var firstRel = account.getLatestAssociation();
        doBoundedIteration(
                view.tokenAssociations(),
                view.tokens(),
                firstRel,
                maxRels,
                (token, rel) -> {
                    final var grpcRel =
                            new RawTokenRelationship(
                                            rel.getBalance(),
                                            STATIC_PROPERTIES.getShard(),
                                            STATIC_PROPERTIES.getRealm(),
                                            rel.getRelatedTokenNum(),
                                            rel.isFrozen(),
                                            rel.isKycGranted(),
                                            rel.isAutomaticAssociation())
                                    .asGrpcFor(token);
                    grpcRels.add(grpcRel);
                });

        return grpcRels;
    }

    /**
     * Given tokens and account-token relationships, iterates the "map-value-linked-list" of token
     * relationships for the given account, exposing its token relationships to the given Visitor in
     * reverse chronological order.
     *
     * @param tokenRels the source of token relationship information
     * @param tokens the source of token information
     * @param account the account of interest
     * @param visitor a consumer of token and token relationship information
     */
    public static void doBoundedIteration(
            final TokenRelStorageAdapter tokenRels,
            final MerkleMap<EntityNum, MerkleToken> tokens,
            final HederaAccount account,
            final BiConsumer<MerkleToken, HederaTokenRel> visitor) {
        final var maxRels = account.getNumAssociations();
        final var firstRel = account.getLatestAssociation();
        doBoundedIteration(tokenRels, tokens, firstRel, maxRels, visitor);
    }

    /**
     * Given tokens and account-token relationships, iterates the "map-value-linked-list" of token
     * relationships starting from a given key exposing token relationships to the given Visitor in
     * reverse chronological order. Terminates when there are no more reachable relationships, or
     * the maximum number have been visited.
     *
     * @param tokenRels the source of token relationship information
     * @param tokens the source of token information
     * @param firstRel the first relationship of interest
     * @param maxRels the maximum number of relationships to visit
     * @param visitor a consumer of token and token relationship information
     */
    public static void doBoundedIteration(
            final TokenRelStorageAdapter tokenRels,
            final MerkleMap<EntityNum, MerkleToken> tokens,
            final EntityNumPair firstRel,
            final int maxRels,
            final BiConsumer<MerkleToken, HederaTokenRel> visitor) {
        final var accountNum = firstRel.getHiOrderAsLong();
        var tokenNum = firstRel.getLowOrderAsLong();
        var key = firstRel;
        var counter = 0;
        while (tokenNum != MISSING_ID.num() && counter < maxRels) {
            final var rel = tokenRels.get(key);
            final var token = tokens.getOrDefault(key.getLowOrderAsNum(), REMOVED_TOKEN);
            visitor.accept(token, rel);
            tokenNum = rel.getNext();
            key = EntityNumPair.fromLongs(accountNum, tokenNum);
            counter++;
        }
    }

    public Map<ByteString, EntityNum> aliases() {
        return stateChildren.aliases();
    }

    @VisibleForTesting
    public MerkleNetworkContext networkCtx() {
        return stateChildren.networkCtx();
    }

    @VisibleForTesting
    public MerkleMap<EntityNum, MerkleStakingInfo> stakingInfo() {
        return stateChildren.stakingInfo();
    }
}
