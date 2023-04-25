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
package com.hedera.node.app.service.mono.contracts.operation;

import static com.hedera.node.app.service.mono.context.BasicTransactionContext.EMPTY_KEY;
import static com.hedera.node.app.service.mono.contracts.operation.AbstractRecordingCreateOperation.haltWith;
import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.ETHEREUM_NONCE;
import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.IS_SMART_CONTRACT;
import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.KEY;
import static com.hedera.node.app.service.mono.state.EntityCreator.EMPTY_MEMO;
import static com.hedera.node.app.service.mono.txns.contract.ContractCreateTransitionLogic.STANDIN_CONTRACT_ID_KEY;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.ILLEGAL_STATE_CHANGE;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.INSUFFICIENT_GAS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.mono.context.SideEffectsTracker;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.ledger.TransactionalLedger;
import com.hedera.node.app.service.mono.ledger.accounts.ContractAliases;
import com.hedera.node.app.service.mono.ledger.accounts.ContractCustomizer;
import com.hedera.node.app.service.mono.ledger.properties.AccountProperty;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.legacy.core.jproto.TxnReceipt;
import com.hedera.node.app.service.mono.records.RecordsHistorian;
import com.hedera.node.app.service.mono.state.EntityCreator;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.ExpirableTxnRecord;
import com.hedera.node.app.service.mono.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.node.app.service.mono.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.node.app.service.mono.utils.EntityIdUtils;
import com.hedera.node.app.service.mono.utils.MiscUtils;
import com.hedera.node.app.service.mono.utils.SidecarUtils;
import com.hedera.services.stream.proto.SidecarType;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.Words;
import org.hyperledger.besu.evm.operation.Operation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AbstractRecordingCreateOperationTest {
    @Mock private SyntheticTxnFactory syntheticTxnFactory;
    @Mock private GasCalculator gasCalculator;
    @Mock private EVM evm;
    @Mock private MessageFrame frame;
    @Mock private EvmAccount recipientAccount;
    @Mock private MutableAccount mutableAccount;
    @Mock private HederaStackedWorldStateUpdater updater;
    @Mock private Deque<MessageFrame> stack;
    @Mock private BlockValues blockValues;
    @Mock private EntityCreator creator;
    @Mock private RecordsHistorian recordsHistorian;
    @Mock private ContractCustomizer contractCustomizer;
    @Mock private GlobalDynamicProperties dynamicProperties;
    @Mock private ContractAliases aliases;
    @Mock private TransactionalLedger<AccountID, AccountProperty, HederaAccount> accounts;

    private static final long childStipend = 1_000_000L;
    private static final Wei gasPrice = Wei.of(1000L);
    private static final long value = 123_456L;
    private static final ContractID lastAllocated = IdUtils.asContract("0.0.1234");
    private static final Address recipient = Address.BLAKE2B_F_COMPRESSION;
    private static final Operation.OperationResult EMPTY_HALT_RESULT =
            new Operation.OperationResult(Subject.PRETEND_COST, null);
    private static final EntityId autoRenewId = new EntityId(0, 0, 8);

    private static final JKey nonEmptyKey =
            MiscUtils.asFcKeyUnchecked(
                    Key.newBuilder()
                            .setEd25519(
                                    ByteString.copyFrom(
                                            "01234567890123456789012345678901".getBytes()))
                            .build());
    private Subject subject;

    @BeforeEach
    void setUp() {
        subject =
                new Subject(
                        0xF0,
                        "ħCREATE",
                        3,
                        1,
                        1,
                        gasCalculator,
                        creator,
                        syntheticTxnFactory,
                        recordsHistorian,
                        dynamicProperties);
    }

    @Test
    void returnsUnderflowWhenStackSizeTooSmall() {
        given(frame.stackSize()).willReturn(2);

        assertSame(Subject.UNDERFLOW_RESPONSE, subject.execute(frame, evm));
    }

    @Test
    void returnsInvalidWhenDisabled() {
        subject.isEnabled = false;

        assertSame(Subject.INVALID_RESPONSE, subject.execute(frame, evm));
    }

    @Test
    void haltsOnStaticFrame() {
        given(frame.stackSize()).willReturn(3);
        given(frame.isStatic()).willReturn(true);

        final var expected = haltWith(Subject.PRETEND_COST, ILLEGAL_STATE_CHANGE);

        assertSameResult(expected, subject.execute(frame, evm));
    }

    @Test
    void haltsOnInsufficientGas() {
        given(frame.stackSize()).willReturn(3);
        given(frame.getRemainingGas()).willReturn(Subject.PRETEND_GAS_COST - 1);

        final var expected = haltWith(Subject.PRETEND_COST, INSUFFICIENT_GAS);

        assertSameResult(expected, subject.execute(frame, evm));
    }

    @Test
    void failsWithInsufficientRecipientBalanceForValue() {
        given(frame.stackSize()).willReturn(3);
        given(frame.getStackItem(anyInt())).willReturn(Bytes.ofUnsignedLong(1));
        given(frame.getRemainingGas()).willReturn(Subject.PRETEND_GAS_COST);
        given(frame.getStackItem(0)).willReturn(Bytes.ofUnsignedLong(value));
        given(frame.getRecipientAddress()).willReturn(recipient);
        given(frame.getWorldUpdater()).willReturn(updater);
        given(updater.getAccount(recipient)).willReturn(recipientAccount);
        given(recipientAccount.getMutable()).willReturn(mutableAccount);
        given(mutableAccount.getBalance()).willReturn(Wei.ONE);

        assertSameResult(EMPTY_HALT_RESULT, subject.execute(frame, evm));
        verify(frame).pushStackItem(UInt256.ZERO);
    }

    @Test
    void failsWithExcessStackDepth() {
        givenSpawnPrereqs();
        given(frame.getMessageStackDepth()).willReturn(1024);

        assertSameResult(EMPTY_HALT_RESULT, subject.execute(frame, evm));
        verify(frame).pushStackItem(UInt256.ZERO);
    }

    @Test
    void hasExpectedChildCompletionOnSuccessWithSidecarEnabled() {
        final var trackerCaptor = ArgumentCaptor.forClass(SideEffectsTracker.class);
        final var liveRecord =
                ExpirableTxnRecord.newBuilder()
                        .setReceiptBuilder(
                                TxnReceipt.newBuilder()
                                        .setStatus(TxnReceipt.REVERTED_SUCCESS_LITERAL));
        final var mockCreation =
                TransactionBody.newBuilder()
                        .setContractCreateInstance(
                                ContractCreateTransactionBody.newBuilder()
                                        .setAutoRenewAccountId(autoRenewId.toGrpcAccountId()));
        final var frameCaptor = ArgumentCaptor.forClass(MessageFrame.class);
        givenSpawnPrereqs();
        givenBuilderPrereqs();
        givenUpdaterWithAliases(EntityIdUtils.parseAccount("0.0.1234"), nonEmptyKey);
        given(updater.customizerForPendingCreation()).willReturn(contractCustomizer);
        given(updater.idOfLastNewAddress()).willReturn(lastAllocated);
        given(syntheticTxnFactory.contractCreation(contractCustomizer)).willReturn(mockCreation);
        given(creator.createSuccessfulSyntheticRecord(any(), any(), any())).willReturn(liveRecord);
        final var initCode = "initCode".getBytes();
        given(frame.readMemory(anyLong(), anyLong())).willReturn(Bytes.wrap(initCode));
        final var newContractMock = mock(Account.class);
        final var runtimeCode = "runtimeCode".getBytes();
        given(newContractMock.getCode()).willReturn(Bytes.of(runtimeCode));
        given(updater.get(Subject.PRETEND_CONTRACT_ADDRESS)).willReturn(newContractMock);
        final var sidecarRecord =
                TransactionSidecarRecord.newBuilder()
                        .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(666L).build());
        final var sidecarUtilsMockedStatic = mockStatic(SidecarUtils.class);
        sidecarUtilsMockedStatic
                .when(
                        () ->
                                SidecarUtils.createContractBytecodeSidecarFrom(
                                        lastAllocated, initCode, runtimeCode))
                .thenReturn(sidecarRecord);
        given(dynamicProperties.enabledSidecars())
                .willReturn(Set.of(SidecarType.CONTRACT_BYTECODE));

        assertSameResult(EMPTY_HALT_RESULT, subject.execute(frame, evm));

        verify(stack).addFirst(frameCaptor.capture());
        final var childFrame = frameCaptor.getValue();
        // when:
        childFrame.setState(MessageFrame.State.COMPLETED_SUCCESS);
        childFrame.notifyCompletion();
        // then:
        verify(frame).pushStackItem(Words.fromAddress(Subject.PRETEND_CONTRACT_ADDRESS));
        verify(creator)
                .createSuccessfulSyntheticRecord(
                        eq(Collections.emptyList()), trackerCaptor.capture(), eq(EMPTY_MEMO));
        verify(updater)
                .manageInProgressRecord(
                        recordsHistorian, liveRecord, mockCreation, List.of(sidecarRecord));
        // and:
        final var tracker = trackerCaptor.getValue();
        assertTrue(tracker.hasTrackedContractCreation());
        assertEquals(lastAllocated, tracker.getTrackedNewContractId());
        assertArrayEquals(
                Subject.PRETEND_CONTRACT_ADDRESS.toArrayUnsafe(),
                tracker.getNewEntityAlias().toByteArray());
        // and:
        assertTrue(liveRecord.shouldNotBeExternalized());
        sidecarUtilsMockedStatic.close();
    }

    @Test
    void hasExpectedChildCompletionOnSuccessWithoutSidecarEnabled() {
        final var trackerCaptor = ArgumentCaptor.forClass(SideEffectsTracker.class);
        final var liveRecord =
                ExpirableTxnRecord.newBuilder()
                        .setReceiptBuilder(
                                TxnReceipt.newBuilder()
                                        .setStatus(TxnReceipt.REVERTED_SUCCESS_LITERAL));
        final var mockCreation =
                TransactionBody.newBuilder()
                        .setContractCreateInstance(
                                ContractCreateTransactionBody.newBuilder()
                                        .setAutoRenewAccountId(autoRenewId.toGrpcAccountId()));
        final var frameCaptor = ArgumentCaptor.forClass(MessageFrame.class);
        givenSpawnPrereqs();
        givenBuilderPrereqs();
        givenUpdaterWithAliases(EntityIdUtils.parseAccount("0.0.1234"), nonEmptyKey);
        given(updater.customizerForPendingCreation()).willReturn(contractCustomizer);
        given(updater.idOfLastNewAddress()).willReturn(lastAllocated);
        given(syntheticTxnFactory.contractCreation(contractCustomizer)).willReturn(mockCreation);
        given(creator.createSuccessfulSyntheticRecord(any(), any(), any())).willReturn(liveRecord);
        given(dynamicProperties.enabledSidecars()).willReturn(Set.of());

        assertSameResult(EMPTY_HALT_RESULT, subject.execute(frame, evm));

        verify(stack).addFirst(frameCaptor.capture());
        final var childFrame = frameCaptor.getValue();
        // when:
        childFrame.setState(MessageFrame.State.COMPLETED_SUCCESS);
        childFrame.notifyCompletion();
        // then:
        verify(frame).pushStackItem(Words.fromAddress(Subject.PRETEND_CONTRACT_ADDRESS));
        verify(creator)
                .createSuccessfulSyntheticRecord(
                        eq(Collections.emptyList()), trackerCaptor.capture(), eq(EMPTY_MEMO));
        verify(updater)
                .manageInProgressRecord(
                        recordsHistorian, liveRecord, mockCreation, Collections.emptyList());
        // and:
        final var tracker = trackerCaptor.getValue();
        assertTrue(tracker.hasTrackedContractCreation());
        assertEquals(lastAllocated, tracker.getTrackedNewContractId());
        assertArrayEquals(
                Subject.PRETEND_CONTRACT_ADDRESS.toArrayUnsafe(),
                tracker.getNewEntityAlias().toByteArray());
        // and:
        assertTrue(liveRecord.shouldNotBeExternalized());
    }

    @Test
    void hasExpectedHollowAccountCompletionOnSuccessWithSidecarEnabled() {
        final var trackerCaptor = ArgumentCaptor.forClass(SideEffectsTracker.class);
        final var liveRecord =
                ExpirableTxnRecord.newBuilder()
                        .setReceiptBuilder(
                                TxnReceipt.newBuilder()
                                        .setStatus(TxnReceipt.REVERTED_SUCCESS_LITERAL));
        final var mockCreation =
                TransactionBody.newBuilder()
                        .setContractCreateInstance(
                                ContractCreateTransactionBody.newBuilder()
                                        .setAutoRenewAccountId(autoRenewId.toGrpcAccountId()));
        final var frameCaptor = ArgumentCaptor.forClass(MessageFrame.class);
        givenSpawnPrereqs();
        givenBuilderPrereqs();
        given(dynamicProperties.isLazyCreationEnabled()).willReturn(true);
        final var hollowAccountId = EntityIdUtils.parseAccount("0.0.5678");
        givenUpdaterWithAliases(hollowAccountId, EMPTY_KEY);
        given(updater.customizerForPendingCreation()).willReturn(contractCustomizer);
        given(syntheticTxnFactory.contractCreation(contractCustomizer)).willReturn(mockCreation);
        given(creator.createSuccessfulSyntheticRecord(any(), any(), any())).willReturn(liveRecord);
        final var initCode = "initCode".getBytes();
        given(frame.readMemory(anyLong(), anyLong())).willReturn(Bytes.wrap(initCode));
        final var newContractMock = mock(Account.class);
        final var runtimeCode = "runtimeCode".getBytes();
        given(newContractMock.getCode()).willReturn(Bytes.of(runtimeCode));
        given(updater.get(Subject.PRETEND_CONTRACT_ADDRESS)).willReturn(newContractMock);
        final var sidecarRecord =
                TransactionSidecarRecord.newBuilder()
                        .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(666L).build());
        final var sidecarUtilsMockedStatic = mockStatic(SidecarUtils.class);
        sidecarUtilsMockedStatic
                .when(
                        () ->
                                SidecarUtils.createContractBytecodeSidecarFrom(
                                        EntityIdUtils.asContract(hollowAccountId),
                                        initCode,
                                        runtimeCode))
                .thenReturn(sidecarRecord);
        given(dynamicProperties.enabledSidecars())
                .willReturn(Set.of(SidecarType.CONTRACT_BYTECODE));

        assertSameResult(EMPTY_HALT_RESULT, subject.execute(frame, evm));

        verify(stack).addFirst(frameCaptor.capture());
        final var childFrame = frameCaptor.getValue();
        // when:
        childFrame.setState(MessageFrame.State.COMPLETED_SUCCESS);
        childFrame.notifyCompletion();
        // then:
        verify(frame).pushStackItem(Words.fromAddress(Subject.PRETEND_CONTRACT_ADDRESS));
        verify(creator)
                .createSuccessfulSyntheticRecord(
                        eq(Collections.emptyList()), trackerCaptor.capture(), eq(EMPTY_MEMO));
        verify(updater).reclaimLatestContractId();
        verify(updater.trackingAccounts()).set(hollowAccountId, IS_SMART_CONTRACT, true);
        verify(updater.trackingAccounts()).set(hollowAccountId, KEY, STANDIN_CONTRACT_ID_KEY);
        verify(updater.trackingAccounts()).set(hollowAccountId, ETHEREUM_NONCE, 1L);
        verify(updater)
                .manageInProgressRecord(
                        recordsHistorian, liveRecord, mockCreation, List.of(sidecarRecord));
        // and:
        final var tracker = trackerCaptor.getValue();
        assertTrue(tracker.hasTrackedContractCreation());
        assertEquals(EntityIdUtils.asContract(hollowAccountId), tracker.getTrackedNewContractId());
        assertArrayEquals(
                Subject.PRETEND_CONTRACT_ADDRESS.toArrayUnsafe(),
                tracker.getNewEntityAlias().toByteArray());
        // and:
        assertTrue(liveRecord.shouldNotBeExternalized());
        sidecarUtilsMockedStatic.close();
    }

    @Test
    void hasExpectedHollowAccountCompletionOnSuccessWithoutSidecarEnabled() {
        final var trackerCaptor = ArgumentCaptor.forClass(SideEffectsTracker.class);
        final var liveRecord =
                ExpirableTxnRecord.newBuilder()
                        .setReceiptBuilder(
                                TxnReceipt.newBuilder()
                                        .setStatus(TxnReceipt.REVERTED_SUCCESS_LITERAL));
        final var mockCreation =
                TransactionBody.newBuilder()
                        .setContractCreateInstance(
                                ContractCreateTransactionBody.newBuilder()
                                        .setAutoRenewAccountId(autoRenewId.toGrpcAccountId()));
        final var frameCaptor = ArgumentCaptor.forClass(MessageFrame.class);
        givenSpawnPrereqs();
        givenBuilderPrereqs();
        given(dynamicProperties.isLazyCreationEnabled()).willReturn(true);
        final var hollowAccountId = EntityIdUtils.parseAccount("0.0.5678");
        givenUpdaterWithAliases(hollowAccountId, EMPTY_KEY);
        given(updater.customizerForPendingCreation()).willReturn(contractCustomizer);
        given(syntheticTxnFactory.contractCreation(contractCustomizer)).willReturn(mockCreation);
        given(creator.createSuccessfulSyntheticRecord(any(), any(), any())).willReturn(liveRecord);
        given(dynamicProperties.enabledSidecars()).willReturn(Set.of());

        assertSameResult(EMPTY_HALT_RESULT, subject.execute(frame, evm));

        verify(stack).addFirst(frameCaptor.capture());
        final var childFrame = frameCaptor.getValue();
        // when:
        childFrame.setState(MessageFrame.State.COMPLETED_SUCCESS);
        childFrame.notifyCompletion();
        // then:
        verify(frame).pushStackItem(Words.fromAddress(Subject.PRETEND_CONTRACT_ADDRESS));
        verify(creator)
                .createSuccessfulSyntheticRecord(
                        eq(Collections.emptyList()), trackerCaptor.capture(), eq(EMPTY_MEMO));
        verify(updater).reclaimLatestContractId();
        verify(updater.trackingAccounts()).set(hollowAccountId, IS_SMART_CONTRACT, true);
        verify(updater.trackingAccounts()).set(hollowAccountId, KEY, STANDIN_CONTRACT_ID_KEY);
        verify(updater.trackingAccounts()).set(hollowAccountId, ETHEREUM_NONCE, 1L);
        verify(updater)
                .manageInProgressRecord(
                        recordsHistorian, liveRecord, mockCreation, Collections.emptyList());
        // and:
        final var tracker = trackerCaptor.getValue();
        assertTrue(tracker.hasTrackedContractCreation());
        assertEquals(EntityIdUtils.asContract(hollowAccountId), tracker.getTrackedNewContractId());
        assertArrayEquals(
                Subject.PRETEND_CONTRACT_ADDRESS.toArrayUnsafe(),
                tracker.getNewEntityAlias().toByteArray());
        // and:
        assertTrue(liveRecord.shouldNotBeExternalized());
    }

    @Test
    void hasExpectedHollowAccountCompletionWithoutLazyCreationEnabled() {
        given(frame.stackSize()).willReturn(3);
        given(frame.getStackItem(anyInt())).willReturn(Bytes.ofUnsignedLong(1));
        given(frame.getRemainingGas()).willReturn(Subject.PRETEND_GAS_COST);
        given(frame.getRecipientAddress()).willReturn(recipient);
        given(frame.getWorldUpdater()).willReturn(updater);
        given(updater.getAccount(recipient)).willReturn(recipientAccount);
        given(recipientAccount.getMutable()).willReturn(mutableAccount);
        given(mutableAccount.getBalance()).willReturn(Wei.of(value));
        given(frame.getMessageFrameStack()).willReturn(stack);
        given(updater.updater()).willReturn(updater);
        given(frame.getOriginatorAddress()).willReturn(recipient);
        given(frame.getGasPrice()).willReturn(gasPrice);
        given(frame.getBlockValues()).willReturn(blockValues);
        given(frame.getMiningBeneficiary()).willReturn(recipient);
        given(frame.getBlockHashLookup()).willReturn(l -> Hash.ZERO);
        given(dynamicProperties.isLazyCreationEnabled()).willReturn(false);
        final var hollowAccountId = EntityIdUtils.parseAccount("0.0.5678");
        given(updater.aliases()).willReturn(aliases);
        given(aliases.resolveForEvm(any()))
                .willReturn(EntityIdUtils.asTypedEvmAddress(hollowAccountId));
        given(updater.trackingAccounts()).willReturn(accounts);
        given(accounts.contains(hollowAccountId)).willReturn(false);

        assertSameResult(EMPTY_HALT_RESULT, subject.execute(frame, evm));
    }

    @Test
    void hasExpectedChildCompletionOnFailure() {
        final var captor = ArgumentCaptor.forClass(MessageFrame.class);
        givenSpawnPrereqs();
        givenBuilderPrereqs();
        givenUpdaterWithAliases(EntityIdUtils.parseAccount("0.0.1234"), nonEmptyKey);

        assertSameResult(EMPTY_HALT_RESULT, subject.execute(frame, evm));

        verify(stack).addFirst(captor.capture());
        final var childFrame = captor.getValue();
        // when:
        childFrame.setState(MessageFrame.State.COMPLETED_FAILED);
        childFrame.notifyCompletion();
        verify(frame).pushStackItem(UInt256.ZERO);
    }

    @Test
    void failsWhenMatchingHollowAccountExistsAndLazyCreationDisabled() {
        given(frame.stackSize()).willReturn(3);
        given(frame.getRemainingGas()).willReturn(Subject.PRETEND_GAS_COST);
        given(frame.getStackItem(0)).willReturn(Bytes.ofUnsignedLong(value));
        given(frame.getRecipientAddress()).willReturn(recipient);
        given(frame.getWorldUpdater()).willReturn(updater);
        given(updater.getAccount(recipient)).willReturn(recipientAccount);
        given(recipientAccount.getMutable()).willReturn(mutableAccount);
        given(mutableAccount.getBalance()).willReturn(Wei.of(value));
        given(frame.getMessageStackDepth()).willReturn(1023);
        given(frame.getStackItem(anyInt())).willReturn(Bytes.ofUnsignedLong(1));
        final var hollowAccountId = EntityIdUtils.parseAccount("0.0.5678");
        givenUpdaterWithAliases(hollowAccountId, EMPTY_KEY);

        subject.execute(frame, evm);

        verify(frame).readMutableMemory(1L, 1L);
        verify(frame).popStackItems(3);
        verify(frame).pushStackItem(UInt256.ZERO);
    }

    private void givenBuilderPrereqs() {
        given(frame.getMessageFrameStack()).willReturn(stack);
        given(updater.updater()).willReturn(updater);
        given(gasCalculator.gasAvailableForChildCreate(anyLong())).willReturn(childStipend);
        given(frame.getOriginatorAddress()).willReturn(recipient);
        given(frame.getGasPrice()).willReturn(gasPrice);
        given(frame.getBlockValues()).willReturn(blockValues);
        given(frame.getMiningBeneficiary()).willReturn(recipient);
        given(frame.getBlockHashLookup()).willReturn(l -> Hash.ZERO);
    }

    private void givenSpawnPrereqs() {
        given(frame.stackSize()).willReturn(3);
        given(frame.getStackItem(anyInt())).willReturn(Bytes.ofUnsignedLong(1));
        given(frame.getRemainingGas()).willReturn(Subject.PRETEND_GAS_COST);
        given(frame.getStackItem(0)).willReturn(Bytes.ofUnsignedLong(value));
        given(frame.getRecipientAddress()).willReturn(recipient);
        given(frame.getWorldUpdater()).willReturn(updater);
        given(updater.getAccount(recipient)).willReturn(recipientAccount);
        given(recipientAccount.getMutable()).willReturn(mutableAccount);
        given(mutableAccount.getBalance()).willReturn(Wei.of(value));
        given(frame.getMessageStackDepth()).willReturn(1023);
    }

    private void givenUpdaterWithAliases(
            final AccountID expectedAccountId, final JKey expectedKey) {
        given(updater.aliases()).willReturn(aliases);
        given(aliases.resolveForEvm(any()))
                .willReturn(EntityIdUtils.asTypedEvmAddress(expectedAccountId));
        given(updater.trackingAccounts()).willReturn(accounts);
        given(accounts.contains(expectedAccountId)).willReturn((expectedKey != null));
        given(accounts.get(expectedAccountId, AccountProperty.KEY)).willReturn(expectedKey);
    }

    private void assertSameResult(
            final Operation.OperationResult expected, final Operation.OperationResult actual) {
        assertEquals(expected.getGasCost(), actual.getGasCost());
        assertEquals(expected.getHaltReason(), actual.getHaltReason());
    }

    static class Subject extends AbstractRecordingCreateOperation {
        static final long PRETEND_GAS_COST = 123L;
        static final Address PRETEND_CONTRACT_ADDRESS = Address.ALTBN128_ADD;
        static final long PRETEND_COST = PRETEND_GAS_COST;

        boolean isEnabled = true;

        protected Subject(
                final int opcode,
                final String name,
                final int stackItemsConsumed,
                final int stackItemsProduced,
                final int opSize,
                final GasCalculator gasCalculator,
                final EntityCreator creator,
                final SyntheticTxnFactory syntheticTxnFactory,
                final RecordsHistorian recordsHistorian,
                final GlobalDynamicProperties dynamicProperties) {
            super(
                    opcode,
                    name,
                    stackItemsConsumed,
                    stackItemsProduced,
                    opSize,
                    gasCalculator,
                    creator,
                    syntheticTxnFactory,
                    recordsHistorian,
                    dynamicProperties);
        }

        @Override
        protected boolean isEnabled() {
            return isEnabled;
        }

        @Override
        protected long cost(final MessageFrame frame) {
            return PRETEND_GAS_COST;
        }

        @Override
        protected Address targetContractAddress(final MessageFrame frame) {
            return PRETEND_CONTRACT_ADDRESS;
        }
    }
}
