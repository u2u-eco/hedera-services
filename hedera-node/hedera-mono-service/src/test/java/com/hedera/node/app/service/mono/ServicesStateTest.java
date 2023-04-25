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
package com.hedera.node.app.service.mono;

import static com.hedera.node.app.service.mono.ServicesState.EMPTY_HASH;
import static com.hedera.node.app.service.mono.context.AppsManager.APPS;
import static com.hedera.node.app.service.mono.context.primitives.StateView.doBoundedIteration;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.LEDGER_TOTAL_TINY_BAR_FLOAT;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.STAKING_REWARD_HISTORY_NUM_STORED_PERIODS;
import static com.hedera.node.app.service.mono.context.properties.SemanticVersions.SEMANTIC_VERSIONS;
import static com.hedera.node.app.service.mono.context.properties.SerializableSemVers.forHapiAndHedera;
import static com.hedera.node.app.service.mono.state.migration.MapMigrationToDisk.INSERTIONS_PER_COPY;
import static com.swirlds.common.system.InitTrigger.RECONNECT;
import static com.swirlds.common.system.InitTrigger.RESTART;
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.internal.verification.VerificationModeFactory.times;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.google.protobuf.ByteString;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.node.app.service.mono.context.MutableStateChildren;
import com.hedera.node.app.service.mono.context.init.ServicesInitFlow;
import com.hedera.node.app.service.mono.context.properties.BootstrapProperties;
import com.hedera.node.app.service.mono.context.properties.PropertyNames;
import com.hedera.node.app.service.mono.ledger.accounts.staking.StakeStartupHelper;
import com.hedera.node.app.service.mono.sigs.EventExpansion;
import com.hedera.node.app.service.mono.state.DualStateAccessor;
import com.hedera.node.app.service.mono.state.forensics.HashLogger;
import com.hedera.node.app.service.mono.state.initialization.SystemAccountsCreator;
import com.hedera.node.app.service.mono.state.initialization.SystemFilesManager;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext;
import com.hedera.node.app.service.mono.state.merkle.MerkleScheduledTransactions;
import com.hedera.node.app.service.mono.state.merkle.MerkleSpecialFiles;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.state.merkle.MerkleTokenRelStatus;
import com.hedera.node.app.service.mono.state.merkle.MerkleUniqueToken;
import com.hedera.node.app.service.mono.state.migration.AccountStorageAdapter;
import com.hedera.node.app.service.mono.state.migration.MapMigrationToDisk;
import com.hedera.node.app.service.mono.state.migration.StakingInfoMapBuilder;
import com.hedera.node.app.service.mono.state.migration.StateChildIndices;
import com.hedera.node.app.service.mono.state.migration.StateVersions;
import com.hedera.node.app.service.mono.state.migration.ToDiskMigrations;
import com.hedera.node.app.service.mono.state.migration.TokenRelStorageAdapter;
import com.hedera.node.app.service.mono.state.migration.UniqueTokenMapAdapter;
import com.hedera.node.app.service.mono.state.org.StateMetadata;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.virtual.VirtualMapFactory;
import com.hedera.node.app.service.mono.stream.RecordsRunningHashLeaf;
import com.hedera.node.app.service.mono.txns.ProcessLogic;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.EntityNumPair;
import com.hedera.node.app.service.mono.utils.MiscUtils;
import com.hedera.node.app.service.mono.utils.SystemExits;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.hedera.test.utils.ClassLoaderHelper;
import com.hedera.test.utils.CryptoConfigUtils;
import com.hedera.test.utils.IdUtils;
import com.hedera.test.utils.ResponsibleVMapUser;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.RunningHash;
import com.swirlds.common.crypto.SerializablePublicKey;
import com.swirlds.common.crypto.engine.CryptoEngine;
import com.swirlds.common.exceptions.MutabilityException;
import com.swirlds.common.system.InitTrigger;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.Round;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.SwirldDualState;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.events.Event;
import com.swirlds.fchashmap.FCHashMap;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.platform.state.DualStateImpl;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateFileReader;
import com.swirlds.virtualmap.VirtualMap;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.security.PublicKey;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, LogCaptureExtension.class})
class ServicesStateTest extends ResponsibleVMapUser {
    private final String signedStateDir = "src/test/resources/signedState/";
    private final SoftwareVersion justPriorVersion = forHapiAndHedera("0.29.1", "0.29.2");
    private final SoftwareVersion currentVersion = SEMANTIC_VERSIONS.deployedSoftwareVersion();
    private final SoftwareVersion futureVersion = forHapiAndHedera("1.0.0", "1.0.0");
    private final NodeId selfId = new NodeId(false, 1L);
    private static final String bookMemo = "0.0.4";

    @Mock private StakeStartupHelper stakeStartupHelper;
    @Mock private HashLogger hashLogger;
    @Mock private Platform platform;
    @Mock private AddressBook addressBook;
    @Mock private Address address;
    @Mock private ServicesApp app;
    @Mock private MerkleSpecialFiles specialFiles;
    @Mock private MerkleNetworkContext networkContext;
    @Mock private Round round;
    @Mock private Event event;
    @Mock private EventExpansion eventExpansion;
    @Mock private SwirldDualState dualState;
    @Mock private StateMetadata metadata;
    @Mock private ProcessLogic logic;
    @Mock private FCHashMap<ByteString, EntityNum> aliases;
    @Mock private MutableStateChildren workingState;
    @Mock private DualStateAccessor dualStateAccessor;
    @Mock private ServicesInitFlow initFlow;
    @Mock private ServicesApp.Builder appBuilder;
    @Mock private MerkleMap<EntityNum, MerkleAccount> accounts;
    @Mock private VirtualMapFactory virtualMapFactory;
    @Mock private ServicesState.StakingInfoBuilder stakingInfoBuilder;
    @Mock private ServicesState.MapToDiskMigration mapToDiskMigration;
    @Mock private Function<VirtualMapFactory.JasperDbBuilderFactory, VirtualMapFactory> vmf;
    @Mock private BootstrapProperties bootstrapProperties;
    @Mock private SystemAccountsCreator accountsCreator;
    @Mock private SystemFilesManager systemFilesManager;

    @LoggingTarget private LogCaptor logCaptor;
    @LoggingSubject private ServicesState subject;

    @BeforeEach
    void setUp() {
        SEMANTIC_VERSIONS
                .deployedSoftwareVersion()
                .setProto(SemanticVersion.newBuilder().setMinor(35).build());
        SEMANTIC_VERSIONS
                .deployedSoftwareVersion()
                .setServices(SemanticVersion.newBuilder().setMinor(35).setPatch(1).build());
        subject = tracked(new ServicesState());
        setAllChildren();
    }

    @AfterEach
    void cleanup() {
        if (APPS.includes(selfId.getId())) {
            APPS.clear(selfId.getId());
        }
    }

    @AfterAll
    static void clearMocks() {
        Mockito.framework().clearInlineMocks();
    }

    @Test
    void doesNoMigrationsForLateEnoughVersion() {
        mockMigratorsOnly();
        subject.setMetadata(metadata);
        given(metadata.app()).willReturn(app);
        given(app.workingState()).willReturn(workingState);

        assertDoesNotThrow(() -> subject.migrateFrom(futureVersion));

        unmockMigrators();
    }

    @Test
    void onlyInitializedIfMetadataIsSet() {
        assertFalse(subject.isInitialized());
        subject.setMetadata(metadata);
        assertTrue(subject.isInitialized());
    }

    @Test
    void getsAliasesFromMetadata() {
        given(metadata.aliases()).willReturn(aliases);
        subject.setMetadata(metadata);
        assertSame(aliases, subject.aliases());
    }

    @Test
    void logsSummaryAsExpectedWithAppAvailable() {
        // setup:
        final var consTime = Instant.ofEpochSecond(1_234_567L);
        subject.setMetadata(metadata);

        subject.setChild(StateChildIndices.NETWORK_CTX, networkContext);

        given(metadata.app()).willReturn(app);
        given(app.hashLogger()).willReturn(hashLogger);
        given(app.dualStateAccessor()).willReturn(dualStateAccessor);
        given(networkContext.getStateVersion()).willReturn(StateVersions.CURRENT_VERSION);
        given(networkContext.consensusTimeOfLastHandledTxn()).willReturn(consTime);
        given(networkContext.summarizedWith(dualStateAccessor)).willReturn("IMAGINE");

        // when:
        subject.logSummary();

        // then:
        verify(hashLogger).logHashesFor(subject);
        assertEquals("IMAGINE", logCaptor.infoLogs().get(0));
        assertEquals(consTime, subject.getTimeOfLastHandledTxn());
        assertEquals(StateVersions.CURRENT_VERSION, subject.getStateVersion());
    }

    @Test
    void logsSummaryAsExpectedWithNoAppAvailable() {
        subject.setChild(StateChildIndices.NETWORK_CTX, networkContext);

        given(networkContext.summarized()).willReturn("IMAGINE");

        // when:
        subject.logSummary();

        // then:
        assertEquals("IMAGINE", logCaptor.infoLogs().get(0));
    }

    @Test
    void getsAccountIdAsExpected() {
        // setup:
        subject.setChild(StateChildIndices.ADDRESS_BOOK, addressBook);

        given(addressBook.getAddress(selfId.getId())).willReturn(address);
        given(address.getMemo()).willReturn("0.0.3");

        // when:
        final var parsedAccount = subject.getAccountFromNodeId(selfId);

        // then:
        assertEquals(IdUtils.asAccount("0.0.3"), parsedAccount);
    }

    @Test
    void onReleaseAndArchiveNoopIfMetadataNull() {
        mockAllMaps(mock(MerkleMap.class), mock(VirtualMap.class));
        Assertions.assertDoesNotThrow(subject::archive);
        Assertions.assertDoesNotThrow(subject::destroyNode);
    }

    @Test
    void onReleaseForwardsToMetadataIfNonNull() {
        // setup:
        subject.setMetadata(metadata);

        // when:
        subject.destroyNode();

        // then:
        verify(metadata).release();
    }

    @Test
    void archiveForwardsToMetadataAndMerkleMaps() {
        final MerkleMap<?, ?> mockMm = mock(MerkleMap.class);

        subject.setMetadata(metadata);
        mockAllMaps(mockMm, mock(VirtualMap.class));

        // when:
        subject.archive();

        // then:
        verify(metadata).release();
        verify(mockMm, times(6)).archive();
    }

    @Test
    void preHandleUsesEventExpansion() {
        subject.setMetadata(metadata);
        given(metadata.app()).willReturn(app);
        given(app.eventExpansion()).willReturn(eventExpansion);

        subject.preHandle(event);

        verify(eventExpansion).expandAllSigs(event, subject);
    }

    @Test
    void handleThrowsIfImmutable() {
        tracked(subject.copy());

        assertThrows(
                MutabilityException.class, () -> subject.handleConsensusRound(round, dualState));
    }

    @Test
    void handlesRoundAsExpected() {
        subject.setMetadata(metadata);

        given(metadata.app()).willReturn(app);
        given(app.logic()).willReturn(logic);
        given(app.dualStateAccessor()).willReturn(dualStateAccessor);

        subject.handleConsensusRound(round, dualState);
        verify(dualStateAccessor).setDualState(dualState);
        verify(logic).incorporateConsensus(round);
    }

    @Test
    void addressBookCopyWorks() {
        given(addressBook.copy()).willReturn(addressBook);
        // and:
        subject.setChild(StateChildIndices.ADDRESS_BOOK, addressBook);

        // when:
        final var bookCopy = subject.getAddressBookCopy();

        // then:
        assertSame(addressBook, bookCopy);
        verify(addressBook).copy();
    }

    @Test
    void minimumVersionIsRelease030() {
        // expect:
        assertEquals(StateVersions.RELEASE_030X_VERSION, subject.getMinimumSupportedVersion());
    }

    @Test
    void minimumChildCountsAsExpected() {
        assertEquals(StateChildIndices.NUM_025X_CHILDREN, subject.getMinimumChildCount());
    }

    @Test
    void merkleMetaAsExpected() {
        // expect:
        assertEquals(0x8e300b0dfdafbb1aL, subject.getClassId());
        assertEquals(StateVersions.CURRENT_VERSION, subject.getVersion());
    }

    @Test
    void doesntThrowWhenDualStateIsNull() {
        subject.setChild(StateChildIndices.SPECIAL_FILES, specialFiles);
        subject.setChild(StateChildIndices.NETWORK_CTX, networkContext);
        subject.setChild(StateChildIndices.ACCOUNTS, accounts);

        given(app.hashLogger()).willReturn(hashLogger);
        given(app.initializationFlow()).willReturn(initFlow);
        given(app.dualStateAccessor()).willReturn(dualStateAccessor);
        given(app.sysFilesManager()).willReturn(systemFilesManager);
        given(platform.getSelfId()).willReturn(selfId);
        given(app.stakeStartupHelper()).willReturn(stakeStartupHelper);

        APPS.save(selfId.getId(), app);

        assertDoesNotThrow(
                () -> subject.init(platform, addressBook, null, RESTART, currentVersion));
    }

    @Test
    void genesisInitCreatesChildren() {
        // setup:
        ServicesState.setAppBuilder(() -> appBuilder);

        given(addressBook.getSize()).willReturn(3);
        given(addressBook.getAddress(anyLong())).willReturn(address);
        given(address.getMemo()).willReturn(bookMemo);
        given(appBuilder.bootstrapProps(any())).willReturn(appBuilder);
        given(appBuilder.crypto(any())).willReturn(appBuilder);
        given(appBuilder.staticAccountMemo(bookMemo)).willReturn(appBuilder);
        given(appBuilder.consoleCreator(any())).willReturn(appBuilder);
        given(appBuilder.initialHash(EMPTY_HASH)).willReturn(appBuilder);
        given(appBuilder.platform(platform)).willReturn(appBuilder);
        given(appBuilder.selfId(1L)).willReturn(appBuilder);
        given(appBuilder.build()).willReturn(app);
        // and:
        given(app.hashLogger()).willReturn(hashLogger);
        given(app.initializationFlow()).willReturn(initFlow);
        given(app.dualStateAccessor()).willReturn(dualStateAccessor);
        given(platform.getSelfId()).willReturn(selfId);
        given(app.sysAccountsCreator()).willReturn(accountsCreator);
        given(app.workingState()).willReturn(workingState);
        given(app.sysFilesManager()).willReturn(systemFilesManager);
        given(app.stakeStartupHelper()).willReturn(stakeStartupHelper);

        // when:
        subject.init(platform, addressBook, dualState, InitTrigger.GENESIS, null);

        // then:
        assertFalse(subject.isImmutable());
        // and:
        assertSame(addressBook, subject.addressBook());
        assertNotNull(subject.accounts());
        assertNotNull(subject.storage());
        assertNotNull(subject.topics());
        assertNotNull(subject.tokens());
        assertNotNull(subject.tokenAssociations());
        assertNotNull(subject.scheduleTxs());
        assertNotNull(subject.networkCtx());
        assertNotNull(subject.runningHashLeaf());
        assertNotNull(subject.contractStorage());
        assertNotNull(subject.stakingInfo());
        assertNull(subject.networkCtx().consensusTimeOfLastHandledTxn());
        assertEquals(1001L, subject.networkCtx().seqNo().current());
        assertNotNull(subject.specialFiles());
        // and:
        verify(dualStateAccessor).setDualState(dualState);
        verify(initFlow).runWith(eq(subject), any());
        verify(appBuilder).bootstrapProps(any());
        verify(appBuilder).initialHash(EMPTY_HASH);
        verify(appBuilder).platform(platform);
        verify(appBuilder).selfId(selfId.getId());
        // and:
        assertTrue(APPS.includes(selfId.getId()));

        // cleanup:
        ServicesState.setAppBuilder(DaggerServicesApp::builder);
    }

    @Test
    void genesisWhenVirtualNftsEnabled() {
        // setup:
        subject = tracked(new ServicesState(bootstrapProperties));
        given(bootstrapProperties.getBooleanProperty(PropertyNames.TOKENS_NFTS_USE_VIRTUAL_MERKLE))
                .willReturn(true);
        given(bootstrapProperties.getBooleanProperty(PropertyNames.TOKENS_STORE_RELS_ON_DISK))
                .willReturn(false);
        given(bootstrapProperties.getBooleanProperty(PropertyNames.ACCOUNTS_STORE_ON_DISK))
                .willReturn(false);
        ServicesState.setAppBuilder(() -> appBuilder);

        given(addressBook.getSize()).willReturn(3);
        given(addressBook.getAddress(anyLong())).willReturn(address);
        given(address.getMemo()).willReturn(bookMemo);
        given(appBuilder.bootstrapProps(any())).willReturn(appBuilder);
        given(appBuilder.crypto(any())).willReturn(appBuilder);
        given(appBuilder.staticAccountMemo(bookMemo)).willReturn(appBuilder);
        given(appBuilder.consoleCreator(any())).willReturn(appBuilder);
        given(appBuilder.initialHash(EMPTY_HASH)).willReturn(appBuilder);
        given(appBuilder.platform(platform)).willReturn(appBuilder);
        given(appBuilder.selfId(1L)).willReturn(appBuilder);
        given(appBuilder.build()).willReturn(app);
        // and:
        given(app.hashLogger()).willReturn(hashLogger);
        given(app.initializationFlow()).willReturn(initFlow);
        given(app.dualStateAccessor()).willReturn(dualStateAccessor);
        given(platform.getSelfId()).willReturn(selfId);
        given(app.sysAccountsCreator()).willReturn(accountsCreator);
        given(app.workingState()).willReturn(workingState);
        given(app.sysFilesManager()).willReturn(systemFilesManager);
        given(app.stakeStartupHelper()).willReturn(stakeStartupHelper);

        // when:
        subject.init(platform, addressBook, dualState, InitTrigger.GENESIS, null);
        setAllChildren();

        // then:
        assertTrue(subject.uniqueTokens().isVirtual());

        // cleanup:
        ServicesState.setAppBuilder(DaggerServicesApp::builder);
    }

    @Test
    void nonGenesisInitReusesContextIfPresent() {
        subject.setChild(StateChildIndices.SPECIAL_FILES, specialFiles);
        subject.setChild(StateChildIndices.NETWORK_CTX, networkContext);
        subject.setChild(StateChildIndices.ACCOUNTS, accounts);

        given(app.hashLogger()).willReturn(hashLogger);
        given(app.initializationFlow()).willReturn(initFlow);
        given(app.dualStateAccessor()).willReturn(dualStateAccessor);
        given(platform.getSelfId()).willReturn(selfId);
        // and:
        APPS.save(selfId.getId(), app);

        // when:
        subject.init(platform, addressBook, dualState, RECONNECT, currentVersion);

        // then:
        assertSame(addressBook, subject.addressBook());
        assertSame(app, subject.getMetadata().app());
        // and:
        verify(initFlow).runWith(eq(subject), any());
        verify(hashLogger).logHashesFor(subject);
    }

    @Test
    void nonGenesisInitExitsIfStateVersionLaterThanCurrentSoftware() {
        final var mockExit = mock(SystemExits.class);

        subject.setChild(StateChildIndices.SPECIAL_FILES, specialFiles);
        subject.setChild(StateChildIndices.NETWORK_CTX, networkContext);
        subject.setChild(StateChildIndices.ACCOUNTS, accounts);

        given(platform.getSelfId()).willReturn(selfId);
        given(app.systemExits()).willReturn(mockExit);
        given(app.dualStateAccessor()).willReturn(dualStateAccessor);
        // and:
        APPS.save(selfId.getId(), app);

        // when:
        subject.init(platform, addressBook, dualState, RESTART, futureVersion);

        verify(mockExit).fail(1);
    }

    @Test
    void nonGenesisInitDoesNotClearPreparedUpgradeIfSameVersion() {
        subject.setChild(StateChildIndices.SPECIAL_FILES, specialFiles);
        subject.setChild(StateChildIndices.NETWORK_CTX, networkContext);
        subject.setChild(StateChildIndices.ACCOUNTS, accounts);

        final var when = Instant.ofEpochSecond(1_234_567L, 890);
        given(dualState.getFreezeTime()).willReturn(when);
        given(dualState.getLastFrozenTime()).willReturn(when);

        given(app.hashLogger()).willReturn(hashLogger);
        given(app.initializationFlow()).willReturn(initFlow);
        given(app.dualStateAccessor()).willReturn(dualStateAccessor);
        given(platform.getSelfId()).willReturn(selfId);
        given(app.sysFilesManager()).willReturn(systemFilesManager);
        given(app.stakeStartupHelper()).willReturn(stakeStartupHelper);
        // and:
        APPS.save(selfId.getId(), app);

        // when:
        subject.init(platform, addressBook, dualState, RESTART, currentVersion);

        verify(networkContext, never()).discardPreparedUpgradeMeta();
        verify(dualState, never()).setFreezeTime(null);
    }

    @Test
    void nonGenesisInitClearsPreparedUpgradeIfDeployedIsLaterVersion() {
        mockMigrators();
        subject.setChild(StateChildIndices.SPECIAL_FILES, specialFiles);
        subject.setChild(StateChildIndices.NETWORK_CTX, networkContext);
        subject.setChild(StateChildIndices.ACCOUNTS, accounts);

        final var when = Instant.ofEpochSecond(1_234_567L, 890);
        given(dualState.getFreezeTime()).willReturn(when);
        given(dualState.getLastFrozenTime()).willReturn(when);
        given(app.workingState()).willReturn(workingState);

        given(app.hashLogger()).willReturn(hashLogger);
        given(app.initializationFlow()).willReturn(initFlow);
        given(app.dualStateAccessor()).willReturn(dualStateAccessor);
        given(platform.getSelfId()).willReturn(selfId);
        given(app.sysFilesManager()).willReturn(systemFilesManager);
        given(app.stakeStartupHelper()).willReturn(stakeStartupHelper);
        // and:
        APPS.save(selfId.getId(), app);

        // when:
        subject.init(platform, addressBook, dualState, RESTART, justPriorVersion);

        verify(networkContext).discardPreparedUpgradeMeta();
        verify(dualState).setFreezeTime(null);
        unmockMigrators();
    }

    @Test
    void nonGenesisInitWithOldVersionMarksMigrationRecordsNotStreamed() {
        mockMigrators();
        subject.setMetadata(metadata);
        given(app.workingState()).willReturn(workingState);

        subject.setChild(StateChildIndices.SPECIAL_FILES, specialFiles);
        subject.setChild(StateChildIndices.NETWORK_CTX, networkContext);
        subject.setChild(StateChildIndices.ACCOUNTS, accounts);
        subject.setDeserializedStateVersion(StateVersions.RELEASE_030X_VERSION);

        final var when = Instant.ofEpochSecond(1_234_567L, 890);
        given(dualState.getFreezeTime()).willReturn(when);
        given(dualState.getLastFrozenTime()).willReturn(when);

        given(app.hashLogger()).willReturn(hashLogger);
        given(app.initializationFlow()).willReturn(initFlow);
        given(app.dualStateAccessor()).willReturn(dualStateAccessor);
        given(platform.getSelfId()).willReturn(selfId);
        given(app.sysFilesManager()).willReturn(systemFilesManager);
        given(app.stakeStartupHelper()).willReturn(stakeStartupHelper);
        // and:
        APPS.save(selfId.getId(), app);

        // when:
        subject.init(platform, addressBook, dualState, RESTART, justPriorVersion);

        verify(networkContext).discardPreparedUpgradeMeta();
        verify(networkContext).markMigrationRecordsNotYetStreamed();
        verify(dualState).setFreezeTime(null);

        unmockMigrators();
    }

    @Test
    void nonGenesisInitThrowsWithUnsupportedStateVersionUsed() {
        subject.setDeserializedStateVersion(StateVersions.RELEASE_030X_VERSION - 1);

        assertThrows(
                IllegalStateException.class,
                () -> subject.init(platform, addressBook, dualState, RESTART, null));
    }

    @Test
    void nonGenesisInitDoesntClearPreparedUpgradeIfNotUpgrade() {
        subject.setChild(StateChildIndices.SPECIAL_FILES, specialFiles);
        subject.setChild(StateChildIndices.NETWORK_CTX, networkContext);
        subject.setChild(StateChildIndices.ACCOUNTS, accounts);

        given(app.hashLogger()).willReturn(hashLogger);
        given(app.initializationFlow()).willReturn(initFlow);
        given(app.dualStateAccessor()).willReturn(dualStateAccessor);
        given(platform.getSelfId()).willReturn(selfId);
        // and:
        APPS.save(selfId.getId(), app);

        // when:
        subject.init(platform, addressBook, dualState, RECONNECT, currentVersion);

        verify(networkContext, never()).discardPreparedUpgradeMeta();
    }

    @Test
    void nonGenesisInitHandlesNftMigration() {
        subject = tracked(new ServicesState(bootstrapProperties));
        given(bootstrapProperties.getBooleanProperty(PropertyNames.TOKENS_NFTS_USE_VIRTUAL_MERKLE))
                .willReturn(true);
        given(bootstrapProperties.getBooleanProperty(PropertyNames.ACCOUNTS_STORE_ON_DISK))
                .willReturn(true);
        given(bootstrapProperties.getBooleanProperty(PropertyNames.TOKENS_STORE_RELS_ON_DISK))
                .willReturn(false);
        ServicesState.setMapToDiskMigration(mapToDiskMigration);
        ServicesState.setVmFactory(vmf);
        given(vmf.apply(any())).willReturn(virtualMapFactory);

        final var vmap = mock(VirtualMap.class);
        mockAllMaps(mock(MerkleMap.class), vmap);
        subject.setChild(StateChildIndices.NETWORK_CTX, networkContext);
        subject.setChild(StateChildIndices.STORAGE, vmap);
        subject.setChild(StateChildIndices.CONTRACT_STORAGE, vmap);

        final var when = Instant.ofEpochSecond(1_234_567L, 890);
        given(dualState.getFreezeTime()).willReturn(when);
        given(dualState.getLastFrozenTime()).willReturn(when);

        given(app.hashLogger()).willReturn(hashLogger);
        given(app.initializationFlow()).willReturn(initFlow);
        given(app.dualStateAccessor()).willReturn(dualStateAccessor);
        given(platform.getSelfId()).willReturn(selfId);
        given(app.sysFilesManager()).willReturn(systemFilesManager);
        given(app.stakeStartupHelper()).willReturn(stakeStartupHelper);
        // and:
        APPS.save(selfId.getId(), app);

        // when:
        subject.init(platform, addressBook, dualState, RESTART, currentVersion);
        assertTrue(subject.uniqueTokens().isVirtual());
        verify(mapToDiskMigration)
                .migrateToDiskAsApropos(
                        INSERTIONS_PER_COPY,
                        subject,
                        new ToDiskMigrations(true, false),
                        virtualMapFactory,
                        ServicesState.accountMigrator,
                        ServicesState.tokenRelMigrator);

        ServicesState.setVmFactory(VirtualMapFactory::new);
        ServicesState.setMapToDiskMigration(MapMigrationToDisk::migrateToDiskAsApropos);
    }

    @Test
    void nonGenesisInitHandlesTokenRelMigrationToDisk() {
        subject = tracked(new ServicesState(bootstrapProperties));
        given(bootstrapProperties.getBooleanProperty(PropertyNames.TOKENS_NFTS_USE_VIRTUAL_MERKLE))
                .willReturn(false);
        given(bootstrapProperties.getBooleanProperty(PropertyNames.ACCOUNTS_STORE_ON_DISK))
                .willReturn(false);
        given(bootstrapProperties.getBooleanProperty(PropertyNames.TOKENS_STORE_RELS_ON_DISK))
                .willReturn(true);
        ServicesState.setMapToDiskMigration(mapToDiskMigration);
        ServicesState.setVmFactory(vmf);
        given(vmf.apply(any())).willReturn(virtualMapFactory);

        final var vmap = mock(VirtualMap.class);
        mockAllMaps(mock(MerkleMap.class), vmap);
        subject.setChild(StateChildIndices.NETWORK_CTX, networkContext);
        subject.setChild(StateChildIndices.STORAGE, vmap);
        subject.setChild(StateChildIndices.CONTRACT_STORAGE, vmap);

        final var when = Instant.ofEpochSecond(1_234_567L, 890);
        given(dualState.getFreezeTime()).willReturn(when);
        given(dualState.getLastFrozenTime()).willReturn(when);

        given(app.hashLogger()).willReturn(hashLogger);
        given(app.initializationFlow()).willReturn(initFlow);
        given(app.dualStateAccessor()).willReturn(dualStateAccessor);
        given(platform.getSelfId()).willReturn(selfId);
        given(app.sysFilesManager()).willReturn(systemFilesManager);
        given(app.stakeStartupHelper()).willReturn(stakeStartupHelper);
        // and:
        APPS.save(selfId.getId(), app);

        // when:
        subject.init(platform, addressBook, dualState, RESTART, currentVersion);
        verify(mapToDiskMigration)
                .migrateToDiskAsApropos(
                        INSERTIONS_PER_COPY,
                        subject,
                        new ToDiskMigrations(false, true),
                        virtualMapFactory,
                        ServicesState.accountMigrator,
                        ServicesState.tokenRelMigrator);

        ServicesState.setVmFactory(VirtualMapFactory::new);
        ServicesState.setMapToDiskMigration(MapMigrationToDisk::migrateToDiskAsApropos);
    }

    @Test
    void copySetsMutabilityAsExpected() {
        // when:
        final var copy = tracked(subject.copy());

        // then:
        assertTrue(subject.isImmutable());
        assertFalse(copy.isImmutable());
    }

    @Test
    void copyUpdateCtxWithNonNullMeta() {
        // setup:
        subject.setMetadata(metadata);

        given(metadata.app()).willReturn(app);
        given(app.workingState()).willReturn(workingState);

        // when:
        final var copy = tracked(subject.copy());

        // then:
        verify(workingState).updateFrom(copy);
    }

    @Test
    void copiesNonNullChildren() {
        subject.setChild(StateChildIndices.ADDRESS_BOOK, addressBook);
        subject.setChild(StateChildIndices.NETWORK_CTX, networkContext);
        subject.setChild(StateChildIndices.SPECIAL_FILES, specialFiles);
        // and:
        subject.setMetadata(metadata);
        subject.setDeserializedStateVersion(10);

        given(addressBook.copy()).willReturn(addressBook);
        given(networkContext.copy()).willReturn(networkContext);
        given(specialFiles.copy()).willReturn(specialFiles);
        given(metadata.copy()).willReturn(metadata);
        given(metadata.app()).willReturn(app);
        given(app.workingState()).willReturn(workingState);

        // when:
        final var copy = tracked(subject.copy());

        // then:
        assertEquals(10, copy.getDeserializedStateVersion());
        assertSame(metadata, copy.getMetadata());
        verify(metadata).copy();
        // and:
        assertSame(addressBook, copy.addressBook());
        assertSame(networkContext, copy.networkCtx());
        assertSame(specialFiles, copy.specialFiles());
    }

    @Test
    void testLoading0305State() {
        ClassLoaderHelper.loadClassPathDependencies();
        final AtomicReference<SignedState> ref = new AtomicReference<>();
        assertDoesNotThrow(
                () -> ref.set(loadSignedState(signedStateDir + "v0.30.5/SignedState.swh")));
        final var mockPlatform = createMockPlatformWithCrypto();
        tracked((ServicesState) ref.get().getSwirldState())
                .init(
                        mockPlatform,
                        createPretendBookFrom(mockPlatform, false),
                        new DualStateImpl(),
                        RESTART,
                        forHapiAndHedera("0.30.0", "0.30.5"));
    }

    @Test
    void fixesNftsOwnedForAccountsWithActuallyZeroNfts() {
        final MerkleMap<EntityNum, MerkleToken> tokens = new MerkleMap<>();
        final MerkleMap<EntityNum, MerkleAccount> accounts = new MerkleMap<>();
        final MerkleMap<EntityNumPair, MerkleUniqueToken> nfts = new MerkleMap<>();
        final var rels = TokenRelStorageAdapter.fromInMemory(new MerkleMap<>());
        final var nftsAdapter = UniqueTokenMapAdapter.wrap(nfts);

        final var account = new MerkleAccount();
        account.setNftsOwned(2L);
        final var accountWithExtraNftsOwned = EntityNum.fromLong(1234L);
        accounts.put(accountWithExtraNftsOwned, account);

        // Must be empty, no NFT's are in the given state
        final var stats = ServicesState.countByOwnershipIn(nftsAdapter, rels, tokens);
        ServicesState.logNftStats(
                stats, AccountStorageAdapter.fromInMemory(accounts), rels, tokens);
        final var updatedAccount = accounts.get(accountWithExtraNftsOwned);
        assertEquals(0, updatedAccount.getNftsOwned());
    }

    @Test
    void fixesNftsOwnedByTypeForRelsWithActuallyZeroNfts() {
        final MerkleMap<EntityNum, MerkleToken> tokens = new MerkleMap<>();
        final MerkleMap<EntityNum, MerkleAccount> accounts = new MerkleMap<>();
        final MerkleMap<EntityNumPair, MerkleUniqueToken> nfts = new MerkleMap<>();
        final var rels = TokenRelStorageAdapter.fromInMemory(new MerkleMap<>());
        final var nftsAdapter = UniqueTokenMapAdapter.wrap(nfts);
        final var ownerNum = EntityNum.fromLong(1234L);

        final var nftNum = EntityNum.fromLong(2345l);
        final var nonFungibleToken = new MerkleToken();
        nonFungibleToken.setTokenType(TokenType.NON_FUNGIBLE_UNIQUE);
        tokens.put(nftNum, nonFungibleToken);

        final var ftNum = EntityNum.fromLong(3456L);
        final var fungibleToken = new MerkleToken();
        fungibleToken.setTokenType(TokenType.FUNGIBLE_COMMON);
        tokens.put(ftNum, fungibleToken);

        final var missingNum = EntityNum.fromLong(4567L);

        final var nftRel = new MerkleTokenRelStatus();
        nftRel.setBalance(2L);
        final var nftRelKey = EntityNumPair.fromNums(ownerNum, nftNum);
        rels.put(nftRelKey, nftRel);

        final var ftRel = new MerkleTokenRelStatus();
        ftRel.setBalance(2L);
        final var ftRelKey = EntityNumPair.fromNums(ownerNum, ftNum);
        rels.put(ftRelKey, ftRel);

        final var invalidRel = new MerkleTokenRelStatus();
        invalidRel.setBalance(2L);
        final var invalidRelKey = EntityNumPair.fromNums(ownerNum, missingNum);
        rels.put(invalidRelKey, invalidRel);

        // Must be empty, no NFT's are in the given state
        final var stats = ServicesState.countByOwnershipIn(nftsAdapter, rels, tokens);
        ServicesState.logNftStats(
                stats, AccountStorageAdapter.fromInMemory(accounts), rels, tokens);
        final var updatedFtRel = rels.get(ftRelKey);
        assertEquals(2, updatedFtRel.getBalance());
        final var updatedNftRel = rels.get(nftRelKey);
        assertEquals(0, updatedNftRel.getBalance());
    }

    @Test
    void stillCountsOwnershipForNftsOfDeletedTokensIfOwnerRemainsAssociated() {
        // Set up a token with a deleted NFT, and an account that owns that NFT
        // and is still associated with the token
        final var deletedTokenNum = EntityNum.fromLong(1234L);
        final var accountNumWithDeletedNft = EntityNum.fromLong(2345L);
        final var deletedNftSerialNo = EntityNum.fromLong(666L);

        final MerkleMap<EntityNum, MerkleToken> tokens = new MerkleMap<>();
        final var deletedToken = new MerkleToken();
        deletedToken.setDeleted(true);
        tokens.put(deletedTokenNum, deletedToken);

        final MerkleMap<EntityNumPair, MerkleUniqueToken> nfts = new MerkleMap<>();
        final var nft = new MerkleUniqueToken();
        nft.setOwner(accountNumWithDeletedNft.toEntityId());
        nfts.put(EntityNumPair.fromNums(deletedTokenNum, deletedNftSerialNo), nft);
        final var nftsAdapter = UniqueTokenMapAdapter.wrap(nfts);

        final var knownRels = TokenRelStorageAdapter.fromInMemory(new MerkleMap<>());
        final var relWithDeleted = new MerkleTokenRelStatus();
        relWithDeleted.setBalance(1);
        knownRels.put(
                EntityNumPair.fromNums(accountNumWithDeletedNft, deletedTokenNum), relWithDeleted);

        final var stats = ServicesState.countByOwnershipIn(nftsAdapter, knownRels, tokens);
        final var ownedNfts =
                Optional.ofNullable(stats.totalOwned().get(accountNumWithDeletedNft)).orElse(0);
        assertEquals(1, ownedNfts);
    }

    @Test
    void skipsDeletedTokens() {
        final var emptyRels = TokenRelStorageAdapter.fromInMemory(new MerkleMap<>());
        final MerkleMap<EntityNumPair, MerkleUniqueToken> nfts = new MerkleMap<>();
        // Doesn't matter, just something that won't be found in the token map
        nfts.put(EntityNumPair.fromLongs(1L, 2L), new MerkleUniqueToken());
        final var uniqueTokens = UniqueTokenMapAdapter.wrap(nfts);
        final MerkleMap<EntityNum, MerkleToken> tokens = new MerkleMap<>();
        final var deletedToken = new MerkleToken();
        deletedToken.setTreasury(EntityId.MISSING_ENTITY_ID);
        deletedToken.setDeleted(true);
        tokens.put(EntityNum.fromLong(1234L), deletedToken);
        // Doesn't matter, just something that won't be found in the token map
        nfts.put(EntityNumPair.fromLongs(1L, 2L), new MerkleUniqueToken());
        // Doesn't matter, just reference the deleted token
        nfts.put(EntityNumPair.fromLongs(1234L, 2L), new MerkleUniqueToken());
        assertDoesNotThrow(() -> ServicesState.countByOwnershipIn(uniqueTokens, emptyRels, tokens));

        final var pretendStats =
                new ServicesState.NftStats(new HashMap<>(), new HashMap<>(), new HashMap<>());
        // Doesn't matter, just something that won't be found in the accounts map
        pretendStats.totalOwned().put(EntityNum.fromLong(1L), 1);
        // Doesn't matter, just something that won't be found in the tokens map
        pretendStats.totalSupply().put(EntityNum.fromLong(1L), 1);
        // Doesn't matter, just something that won't be found in the associations map
        pretendStats.totalOwnedByType().put(EntityNumPair.fromLongs(2L, 3L), 1);

        assertDoesNotThrow(
                () ->
                        ServicesState.logNftStats(
                                pretendStats,
                                AccountStorageAdapter.fromInMemory(new MerkleMap<>()),
                                emptyRels,
                                tokens));
    }

    @Test
    void warnsOnMissingEntities() {
        final var emptyRels = TokenRelStorageAdapter.fromInMemory(new MerkleMap<>());

        final MerkleMap<EntityNumPair, MerkleUniqueToken> nfts = new MerkleMap<>();
        // Doesn't matter, just something that won't be found in the token map
        nfts.put(EntityNumPair.fromLongs(1L, 2L), new MerkleUniqueToken());
        final var uniqueTokens = UniqueTokenMapAdapter.wrap(nfts);
        final MerkleMap<EntityNum, MerkleToken> tokens = new MerkleMap<>();
        assertDoesNotThrow(() -> ServicesState.countByOwnershipIn(uniqueTokens, emptyRels, tokens));

        final var pretendStats =
                new ServicesState.NftStats(new HashMap<>(), new HashMap<>(), new HashMap<>());
        // Doesn't matter, just something that won't be found in the accounts map
        pretendStats.totalOwned().put(EntityNum.fromLong(1L), 1);
        // Doesn't matter, just something that won't be found in the tokens map
        pretendStats.totalSupply().put(EntityNum.fromLong(1L), 1);
        // Doesn't matter, just something that won't be found in the associations map
        pretendStats.totalOwnedByType().put(EntityNumPair.fromLongs(2L, 3L), 1);

        assertDoesNotThrow(
                () ->
                        ServicesState.logNftStats(
                                pretendStats,
                                AccountStorageAdapter.fromInMemory(new MerkleMap<>()),
                                emptyRels,
                                tokens));
    }

    @Test
    void testLoading0350State() {
        ClassLoaderHelper.loadClassPathDependencies();
        final AtomicReference<SignedState> ref = new AtomicReference<>();
        assertDoesNotThrow(
                () -> ref.set(loadSignedState(signedStateDir + "v0.35.0/SignedState.swh")));
        final var mockPlatform = createMockPlatformWithCrypto();
        final var mutableState = (ServicesState) ref.get().getSwirldState();
        final var aCounts =
                ServicesState.countByOwnershipIn(
                        mutableState.uniqueTokens(),
                        mutableState.tokenAssociations(),
                        mutableState.tokens());

        tracked(mutableState)
                .init(
                        mockPlatform,
                        createPretendBookFrom(mockPlatform, false),
                        new DualStateImpl(),
                        RESTART,
                        forHapiAndHedera("0.35.0", "0.35.0"));

        final var bCounts =
                countByBoundedIterationOver(
                        mutableState.accounts(),
                        mutableState.tokenAssociations(),
                        mutableState.tokens());
        aCounts.totalOwned()
                .forEach(
                        (k, v) ->
                                assertEquals(
                                        v,
                                        bCounts.totalOwned().get(k),
                                        "Mismatch for " + k + " in totalOwned"));
        aCounts.totalSupply()
                .forEach(
                        (k, v) ->
                                assertEquals(
                                        v,
                                        bCounts.totalSupply().get(k),
                                        "Mismatch for " + k + " in totalSupply"));
        aCounts.totalOwnedByType()
                .forEach(
                        (k, v) ->
                                assertEquals(
                                        v,
                                        bCounts.totalOwnedByType().get(k),
                                        "Mismatch for " + k + " in totalOwnedByType"));
    }

    private ServicesState.NftStats countByBoundedIterationOver(
            final AccountStorageAdapter accounts,
            final TokenRelStorageAdapter tokenAssociations,
            final MerkleMap<EntityNum, MerkleToken> tokens) {
        final var stats =
                new ServicesState.NftStats(new HashMap<>(), new HashMap<>(), new HashMap<>());
        MiscUtils.forEach(
                Objects.requireNonNull(accounts.getInMemoryAccounts()),
                (num, account) -> {
                    stats.totalOwned().put(num, (int) account.getNftsOwned());
                    doBoundedIteration(
                            tokenAssociations,
                            tokens,
                            account,
                            (token, rel) -> {
                                final var type = token.tokenType();
                                if (type == TokenType.NON_FUNGIBLE_UNIQUE) {
                                    final var tokenNum =
                                            EntityNum.fromLong(rel.getRelatedTokenNum());
                                    final var numPair =
                                            EntityNumPair.fromLongs(
                                                    account.getKey().longValue(),
                                                    tokenNum.longValue());
                                    stats.totalOwnedByType().put(numPair, (int) rel.getBalance());
                                }
                            });
                });
        MiscUtils.forEach(
                tokens,
                (tokenNum, token) -> stats.totalSupply().put(tokenNum, (int) token.totalSupply()));
        return stats;
    }

    @Test
    void testGenesisState() {
        ClassLoaderHelper.loadClassPathDependencies();
        final var servicesState = tracked(new ServicesState());
        final var recordsRunningHashLeaf = new RecordsRunningHashLeaf();
        recordsRunningHashLeaf.setRunningHash(new RunningHash(EMPTY_HASH));
        servicesState.setChild(
                StateChildIndices.RECORD_STREAM_RUNNING_HASH, recordsRunningHashLeaf);
        final var platform = createMockPlatformWithCrypto();
        final var app = createApp(platform);

        APPS.save(platform.getSelfId().getId(), app);
        assertDoesNotThrow(
                () ->
                        servicesState.init(
                                platform,
                                createPretendBookFrom(platform, true),
                                new DualStateImpl(),
                                InitTrigger.GENESIS,
                                null));
    }

    @Test
    void testUniqueTokensWhenVirtual() {
        final var vmap = new VirtualMap<>();
        subject.setChild(StateChildIndices.UNIQUE_TOKENS, vmap);
        assertTrue(subject.uniqueTokens().isVirtual());
        assertSame(vmap, subject.uniqueTokens().virtualMap());
    }

    @Test
    void testUniqueTokensWhenMerkleMap() {
        final var mmap = new MerkleMap<>();
        subject.setChild(StateChildIndices.UNIQUE_TOKENS, mmap);
        assertFalse(subject.uniqueTokens().isVirtual());
        assertSame(mmap, subject.uniqueTokens().merkleMap());
    }

    private AddressBook createPretendBookFrom(
            final Platform platform, final boolean withKeyDetails) {
        final var pubKey = mock(PublicKey.class);
        given(pubKey.getAlgorithm()).willReturn("EC");
        if (withKeyDetails) {
            given(pubKey.getEncoded()).willReturn(Longs.toByteArray(Long.MAX_VALUE));
        }
        final var nodeId = platform.getSelfId().getId();
        final var address =
                new Address(
                        nodeId,
                        "",
                        "",
                        1L,
                        false,
                        null,
                        -1,
                        Ints.toByteArray(123456789),
                        -1,
                        null,
                        -1,
                        null,
                        -1,
                        new SerializablePublicKey(pubKey),
                        null,
                        new SerializablePublicKey(pubKey),
                        "");
        return new AddressBook(List.of(address));
    }

    private static ServicesApp createApp(final Platform platform) {
        return DaggerServicesApp.builder()
                .initialHash(new Hash())
                .platform(platform)
                .crypto(CryptographyHolder.get())
                .consoleCreator((ignore, visible) -> null)
                .selfId(platform.getSelfId().getId())
                .staticAccountMemo("memo")
                .bootstrapProps(new BootstrapProperties())
                .build();
    }

    private Platform createMockPlatformWithCrypto() {
        final var platform = mock(Platform.class);
        when(platform.getSelfId()).thenReturn(new NodeId(false, 0));
        when(platform.getCryptography())
                .thenReturn(
                        new CryptoEngine(
                                getStaticThreadManager(), CryptoConfigUtils.MINIMAL_CRYPTO_CONFIG));
        assertNotNull(platform.getCryptography());
        return platform;
    }

    private static SignedState loadSignedState(final String path) throws IOException {
        final var signedPair = SignedStateFileReader.readStateFile(Paths.get(path));
        // Because it's possible we are loading old data, we cannot check equivalence of the hash.
        Assertions.assertNotNull(signedPair.signedState());
        return signedPair.signedState();
    }

    private void mockAllMaps(final MerkleMap<?, ?> mockMm, final VirtualMap<?, ?> mockVm) {
        subject.setChild(StateChildIndices.ACCOUNTS, mockMm);
        subject.setChild(StateChildIndices.TOKEN_ASSOCIATIONS, mockMm);
        subject.setChild(StateChildIndices.TOKENS, mockMm);
        subject.setChild(StateChildIndices.UNIQUE_TOKENS, mockMm);
        subject.setChild(StateChildIndices.STORAGE, mockVm);
        subject.setChild(StateChildIndices.TOPICS, mockMm);
        subject.setChild(StateChildIndices.SCHEDULE_TXS, mock(MerkleScheduledTransactions.class));
        subject.setChild(StateChildIndices.STAKING_INFO, mockMm);
    }

    private void setAllChildren() {
        given(addressBook.getSize()).willReturn(1);
        given(addressBook.getAddress(0)).willReturn(address);
        given(address.getId()).willReturn(0L);
        given(bootstrapProperties.getLongProperty(LEDGER_TOTAL_TINY_BAR_FLOAT))
                .willReturn(3_000_000_000L);
        given(bootstrapProperties.getIntProperty(STAKING_REWARD_HISTORY_NUM_STORED_PERIODS))
                .willReturn(2);
        final File databaseFolder = new File("database");
        try {
            if (!databaseFolder.exists()) {
                databaseFolder.mkdir();
            }
            FileUtils.cleanDirectory(new File("database"));
        } catch (final IllegalArgumentException | IOException e) {
            System.err.println("Exception thrown while cleaning up directory");
            e.printStackTrace();
        }
        subject.createGenesisChildren(addressBook, 0, bootstrapProperties);
    }

    private void mockMigrators() {
        mockMigratorsOnly();
    }

    private void mockMigratorsOnly() {
        ServicesState.setMapToDiskMigration(mapToDiskMigration);
        ServicesState.setVmFactory(vmf);
        ServicesState.setStakingInfoBuilder(stakingInfoBuilder);
        ServicesState.setMapToDiskMigration(mapToDiskMigration);
    }

    private void unmockMigrators() {
        ServicesState.setMapToDiskMigration(MapMigrationToDisk::migrateToDiskAsApropos);
        ServicesState.setVmFactory(VirtualMapFactory::new);
        ServicesState.setStakingInfoBuilder(StakingInfoMapBuilder::buildStakingInfoMap);
    }
}
