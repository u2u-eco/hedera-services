/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.mono.ServicesState;
import com.hedera.node.app.service.mono.config.NetworkInfo;
import com.hedera.node.app.service.mono.context.MutableStateChildren;
import com.hedera.node.app.service.mono.context.StateChildren;
import com.hedera.node.app.service.mono.exceptions.NoValidSignedStateException;
import com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext;
import com.hedera.node.app.service.mono.state.merkle.MerkleScheduledTransactions;
import com.hedera.node.app.service.mono.state.merkle.MerkleSpecialFiles;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.state.merkle.MerkleTopic;
import com.hedera.node.app.service.mono.state.migration.AccountStorageAdapter;
import com.hedera.node.app.service.mono.state.migration.StateVersions;
import com.hedera.node.app.service.mono.state.migration.TokenRelStorageAdapter;
import com.hedera.node.app.service.mono.state.migration.UniqueTokenMapAdapter;
import com.hedera.node.app.service.mono.state.virtual.ContractKey;
import com.hedera.node.app.service.mono.state.virtual.IterableContractValue;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobKey;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobValue;
import com.hedera.node.app.service.mono.store.schedule.ScheduleStore;
import com.hedera.node.app.service.mono.stream.RecordsRunningHashLeaf;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.utility.AutoCloseableWrapper;
import com.swirlds.fchashmap.FCHashMap;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class})
class SignedStateViewFactoryTest {
    private SignedStateViewFactory factory;

    @Mock private Platform platform;
    @Mock private ScheduleStore scheduleStore;
    @Mock private NetworkInfo networkInfo;
    @Mock private ServicesState state;
    @Mock private ServicesState secondState;
    @Mock private AccountStorageAdapter accounts;
    @Mock private VirtualMap<VirtualBlobKey, VirtualBlobValue> storage;
    @Mock private VirtualMap<ContractKey, IterableContractValue> contractStorage;
    @Mock private MerkleMap<EntityNum, MerkleTopic> topics;
    @Mock private MerkleMap<EntityNum, MerkleToken> tokens;
    @Mock private TokenRelStorageAdapter tokenAssociations;
    @Mock private MerkleScheduledTransactions scheduleTxs;
    @Mock private MerkleNetworkContext networkCtx;
    @Mock private AddressBook addressBook;
    @Mock private MerkleSpecialFiles specialFiles;
    @Mock private UniqueTokenMapAdapter uniqueTokens;
    @Mock private RecordsRunningHashLeaf runningHashLeaf;
    @Mock private FCHashMap<ByteString, EntityNum> aliases;

    @BeforeEach
    void setUp() {
        factory = new SignedStateViewFactory(platform, scheduleStore, networkInfo);
    }

    @Test
    void checksIfStateIsValid() {
        given(state.getTimeOfLastHandledTxn()).willReturn(Instant.now());
        given(state.getStateVersion()).willReturn(StateVersions.CURRENT_VERSION);
        given(state.isInitialized()).willReturn(true);
        assertTrue(factory.isUsable(state));
    }

    @Test
    void failsIfInvalidState() {
        given(state.getTimeOfLastHandledTxn()).willReturn(Instant.now());
        given(state.getStateVersion()).willReturn(StateVersions.CURRENT_VERSION);
        given(state.isInitialized()).willReturn(false);
        assertFalse(factory.isUsable(state));

        given(state.getStateVersion()).willReturn(StateVersions.MINIMUM_SUPPORTED_VERSION);
        assertFalse(factory.isUsable(state));

        given(state.getTimeOfLastHandledTxn()).willReturn(null);
        assertFalse(factory.isUsable(state));
    }

    @Test
    void canUpdateSuccessfully() {
        given(platform.getLatestImmutableState())
                .willReturn(new AutoCloseableWrapper<>(state, () -> {}));
        final var childrenToUpdate = new MutableStateChildren();
        givenStateWithMockChildren();
        given(state.getTimeOfLastHandledTxn()).willReturn(Instant.now());
        given(state.getStateVersion()).willReturn(StateVersions.CURRENT_VERSION);
        given(state.isInitialized()).willReturn(true);
        assertTrue(factory.isUsable(state));
        assertDoesNotThrow(() -> factory.tryToUpdateToLatestSignedChildren(childrenToUpdate));
        assertChildrenAreExpectedMocks(childrenToUpdate);
    }

    @Test
    void throwsIfUpdatedWithInvalidState() {
        given(platform.getLatestImmutableState())
                .willReturn(new AutoCloseableWrapper<>(state, () -> {}));
        given(state.getTimeOfLastHandledTxn()).willReturn(null);
        assertFalse(factory.isUsable(state));
        assertThrows(
                NoValidSignedStateException.class,
                () -> factory.tryToUpdateToLatestSignedChildren(new MutableStateChildren()));
    }

    @Test
    void returnsEmptyWhenGettingFromInvalidState() {
        given(platform.getLatestImmutableState())
                .willReturn(new AutoCloseableWrapper<>(state, () -> {}));
        given(state.getTimeOfLastHandledTxn()).willReturn(null);
        assertFalse(factory.isUsable(state));
        final var children = factory.childrenOfLatestSignedState();
        assertEquals(Optional.empty(), children);
    }

    @Test
    void getsLatestImmutableChildren() {
        given(platform.getLatestImmutableState())
                .willReturn(new AutoCloseableWrapper<>(state, () -> {}));
        given(state.getTimeOfLastHandledTxn()).willReturn(Instant.ofEpochSecond(12345));
        given(state.getStateVersion()).willReturn(StateVersions.CURRENT_VERSION);
        given(state.isInitialized()).willReturn(true);
        givenStateWithMockChildren();
        final var children = factory.childrenOfLatestSignedState();
        assertChildrenAreExpectedMocks(children.get());
    }

    @Test
    void getsNewStateEachTime() {
        given(platform.getLatestImmutableState())
                .willReturn(new AutoCloseableWrapper<>(state, () -> {}))
                .willReturn(new AutoCloseableWrapper<>(secondState, () -> {}));
        final var firstHandleTime = Instant.ofEpochSecond(12345);
        final var secondHandleTime = Instant.ofEpochSecond(20000);
        given(state.getTimeOfLastHandledTxn()).willReturn(firstHandleTime);
        given(state.getStateVersion()).willReturn(StateVersions.CURRENT_VERSION);
        given(state.isInitialized()).willReturn(true);
        given(secondState.getTimeOfLastHandledTxn()).willReturn(secondHandleTime);
        given(secondState.getStateVersion()).willReturn(StateVersions.CURRENT_VERSION);
        given(secondState.isInitialized()).willReturn(true);
        givenStateWithMockChildren();
        final var firstChildren = factory.childrenOfLatestSignedState();
        final var secondChildren = factory.childrenOfLatestSignedState();
        assertNotSame(firstChildren, secondChildren);
        assertChildrenAreExpectedMocks(firstChildren.get());
        assertEquals(firstHandleTime, firstChildren.get().signedAt());
        assertEquals(secondHandleTime, secondChildren.get().signedAt());
    }

    @Test
    void updatesUsingNewStateEachTime() throws NoValidSignedStateException {
        given(platform.getLatestImmutableState())
                .willReturn(new AutoCloseableWrapper<>(state, () -> {}))
                .willReturn(new AutoCloseableWrapper<>(secondState, () -> {}));
        final var firstHandleTime = Instant.ofEpochSecond(12345);
        final var secondHandleTime = Instant.ofEpochSecond(20000);
        given(state.getTimeOfLastHandledTxn()).willReturn(firstHandleTime);
        given(state.getStateVersion()).willReturn(StateVersions.CURRENT_VERSION);
        given(state.isInitialized()).willReturn(true);
        given(secondState.getTimeOfLastHandledTxn()).willReturn(secondHandleTime);
        given(secondState.getStateVersion()).willReturn(StateVersions.CURRENT_VERSION);
        given(secondState.isInitialized()).willReturn(true);
        givenStateWithMockChildren();
        final var firstChildrenToUpdate = new MutableStateChildren();
        final var secondChildrenToUpdate = new MutableStateChildren();
        factory.tryToUpdateToLatestSignedChildren(firstChildrenToUpdate);
        factory.tryToUpdateToLatestSignedChildren(secondChildrenToUpdate);
        assertNotSame(firstChildrenToUpdate, secondChildrenToUpdate);
        assertChildrenAreExpectedMocks(firstChildrenToUpdate);
        assertEquals(firstHandleTime, firstChildrenToUpdate.signedAt());
        assertEquals(secondHandleTime, secondChildrenToUpdate.signedAt());
    }

    @Test
    void canConstructStateView() {
        given(state.getTimeOfLastHandledTxn()).willReturn(Instant.now());
        given(state.getStateVersion()).willReturn(StateVersions.CURRENT_VERSION);
        given(state.isInitialized()).willReturn(true);
        assertTrue(factory.isUsable(state));
        given(platform.getLatestImmutableState())
                .willReturn(new AutoCloseableWrapper<>(state, () -> {}));
        final var stateView = factory.latestSignedStateView();
        assertFalse(stateView.isEmpty());
    }

    @Test
    void failsToConstructStateViewIfChildrenEmpty() {
        given(state.getTimeOfLastHandledTxn()).willReturn(null);
        assertFalse(factory.isUsable(state));
        given(platform.getLatestImmutableState())
                .willReturn(new AutoCloseableWrapper<>(state, () -> {}));
        final var stateView = factory.latestSignedStateView();
        assertTrue(stateView.isEmpty());
    }

    private void givenStateWithMockChildren() {
        given(state.accounts()).willReturn(accounts);
        given(state.storage()).willReturn(storage);
        given(state.contractStorage()).willReturn(contractStorage);
        given(state.topics()).willReturn(topics);
        given(state.tokens()).willReturn(tokens);
        given(state.tokenAssociations()).willReturn(tokenAssociations);
        given(state.scheduleTxs()).willReturn(scheduleTxs);
        given(state.networkCtx()).willReturn(networkCtx);
        given(state.addressBook()).willReturn(addressBook);
        given(state.specialFiles()).willReturn(specialFiles);
        given(state.uniqueTokens()).willReturn(uniqueTokens);
        given(state.runningHashLeaf()).willReturn(runningHashLeaf);
        given(state.aliases()).willReturn(aliases);
    }

    private void assertChildrenAreExpectedMocks(final StateChildren children) {
        assertSame(accounts, children.accounts());
        assertSame(storage, children.storage());
        assertSame(contractStorage, children.contractStorage());
        assertSame(topics, children.topics());
        assertSame(tokens, children.tokens());
        assertSame(tokenAssociations, children.tokenAssociations());
        assertSame(scheduleTxs, children.schedules());
        assertSame(networkCtx, children.networkCtx());
        assertSame(addressBook, children.addressBook());
        assertSame(specialFiles, children.specialFiles());
        assertSame(uniqueTokens, children.uniqueTokens());
        assertSame(runningHashLeaf, children.runningHashLeaf());
        assertSame(aliases, children.aliases());
    }
}
