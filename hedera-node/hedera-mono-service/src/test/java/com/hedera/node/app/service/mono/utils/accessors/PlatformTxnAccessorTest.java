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
package com.hedera.node.app.service.mono.utils.accessors;

import static com.hedera.node.app.service.mono.utils.MiscUtils.FUNCTION_EXTRACTOR;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asTopic;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusCreateTopic;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusSubmitMessage;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.mock;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.node.app.hapi.utils.CommonUtils;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.ledger.accounts.AliasManager;
import com.hedera.node.app.service.mono.sigs.order.LinkedRefs;
import com.hedera.node.app.service.mono.utils.RationalizedSigMeta;
import com.hedera.test.utils.IdUtils;
import com.hedera.test.utils.TxnUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ConsensusCreateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.ConsensusSubmitMessageTransactionBody;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.codec.binary.StringUtils;
import org.junit.jupiter.api.Test;

class PlatformTxnAccessorTest {
    private static final byte[] NONSENSE = "Jabberwocky".getBytes();

    TransactionBody someTxn =
            TransactionBody.newBuilder()
                    .setTransactionID(TransactionID.newBuilder().setAccountID(asAccount("0.0.2")))
                    .setMemo("Hi!")
                    .build();

    @Test
    void delegatesSettingSpanMap() {
        final var delegate = mock(TxnAccessor.class);
        given(delegate.getSigMap()).willReturn(SignatureMap.getDefaultInstance());
        final var subject = new PlatformTxnAccessor(delegate);
        final var newMap = Map.of("1", new Object());
        subject.setRationalizedSpanMap(newMap);
        verify(delegate).setRationalizedSpanMap(newMap);
    }

    @Test
    void delegatesGettingEthTxData() {
        final var txn = TxnUtils.buildTransactionFrom(TxnUtils.ethereumTransactionOp());
        final var delegate = SignedTxnAccessor.uncheckedFrom(txn);
        final var subject = new PlatformTxnAccessor(delegate);
        assertEquals(delegate.opEthTxData(), subject.opEthTxData());
        assertFalse(subject.hasConsequentialUnknownFields());
    }

    @Test
    void hasSpanMap() throws InvalidProtocolBufferException {
        // setup:
        final var contents = Transaction.newBuilder().setBodyBytes(someTxn.toByteString()).build();

        final PlatformTxnAccessor subject = PlatformTxnAccessor.from(contents);

        // expect:
        assertThat(subject.getSpanMap(), instanceOf(HashMap.class));
    }

    @Test
    void sigMetaGetterSetterCheck() throws InvalidProtocolBufferException {
        // setup:
        final var signedTxnWithBody =
                Transaction.newBuilder().setBodyBytes(someTxn.toByteString()).build();

        // given:
        final PlatformTxnAccessor subject = PlatformTxnAccessor.from(signedTxnWithBody);

        // when:
        subject.setSigMeta(RationalizedSigMeta.noneAvailable());

        // then:
        assertSame(RationalizedSigMeta.noneAvailable(), subject.getSigMeta());
    }

    @Test
    void extractorReturnsNoneWhenExpected() {
        // expect:
        assertEquals(HederaFunctionality.NONE, FUNCTION_EXTRACTOR.apply(someTxn));
    }

    @Test
    void hasExpectedSignedBytes() throws InvalidProtocolBufferException {
        // given:
        final Transaction signedTxnWithBody =
                Transaction.newBuilder().setBodyBytes(someTxn.toByteString()).build();

        // when:
        final SignedTxnAccessor subject = SignedTxnAccessor.from(signedTxnWithBody.toByteArray());

        // then:
        assertArrayEquals(signedTxnWithBody.toByteArray(), subject.getSignedTxnWrapperBytes());
    }

    @Test
    void extractorReturnsExpectedFunction() {
        // given:
        someTxn =
                someTxn.toBuilder()
                        .setConsensusCreateTopic(ConsensusCreateTopicTransactionBody.newBuilder())
                        .build();

        // expect:
        assertEquals(ConsensusCreateTopic, FUNCTION_EXTRACTOR.apply(someTxn));
    }

    @Test
    void allowsUncheckedConstruction() {
        // setup:
        final Transaction validTxn = Transaction.getDefaultInstance();

        // expect:
        assertDoesNotThrow(() -> SignedTxnAccessor.uncheckedFrom(validTxn));
    }

    @Test
    void failsWithIllegalStateOnUncheckedConstruction() {
        // expect:
        assertThrows(
                InvalidProtocolBufferException.class, () -> PlatformTxnAccessor.from(NONSENSE));
    }

    @Test
    void failsOnInvalidTxn() {
        // given:
        final Transaction signedNonsenseTxn =
                Transaction.newBuilder().setBodyBytes(ByteString.copyFrom(NONSENSE)).build();

        // then:
        assertThrows(
                InvalidProtocolBufferException.class,
                () -> PlatformTxnAccessor.from(signedNonsenseTxn.toByteArray()));
    }

    @Test
    void usesBodyBytesCorrectly() throws InvalidProtocolBufferException {
        // given:
        final Transaction signedTxnWithBody =
                Transaction.newBuilder().setBodyBytes(someTxn.toByteString()).build();

        // when:
        final PlatformTxnAccessor subject = PlatformTxnAccessor.from(signedTxnWithBody);

        // then:
        assertEquals(someTxn, subject.getTxn());
        assertThat(List.of(subject.getTxnBytes()), contains(someTxn.toByteArray()));
    }

    @Test
    void getsCorrectLoggableForm() throws Exception {
        final Transaction signedTxnWithBody =
                Transaction.newBuilder()
                        .setBodyBytes(someTxn.toByteString())
                        .setSigMap(
                                SignatureMap.newBuilder()
                                        .addSigPair(
                                                SignaturePair.newBuilder()
                                                        .setPubKeyPrefix(
                                                                ByteString.copyFrom(
                                                                        "UNREAL".getBytes()))
                                                        .setEd25519(
                                                                ByteString.copyFrom(
                                                                        "FAKE".getBytes()))))
                        .build();

        // when:
        final PlatformTxnAccessor subject = PlatformTxnAccessor.from(signedTxnWithBody);
        final Transaction signedTxn4Log = subject.getSignedTxnWrapper();
        final Transaction asBodyBytes =
                signedTxn4Log.toBuilder()
                        .setBodyBytes(CommonUtils.extractTransactionBodyByteString(signedTxn4Log))
                        .build();

        // then:
        assertEquals(someTxn, CommonUtils.extractTransactionBody(signedTxn4Log));
        assertEquals(signedTxnWithBody, asBodyBytes);
    }

    @Test
    void getsCorrectLoggableFormWithSignedTransactionBytes() throws Exception {
        final SignedTransaction signedTxn =
                SignedTransaction.newBuilder()
                        .setBodyBytes(someTxn.toByteString())
                        .setSigMap(
                                SignatureMap.newBuilder()
                                        .addSigPair(
                                                SignaturePair.newBuilder()
                                                        .setPubKeyPrefix(
                                                                ByteString.copyFrom(
                                                                        "UNREAL".getBytes()))
                                                        .setEd25519(
                                                                ByteString.copyFrom(
                                                                        "FAKE".getBytes()))
                                                        .build()))
                        .build();

        final Transaction txn =
                Transaction.newBuilder()
                        .setSignedTransactionBytes(signedTxn.toByteString())
                        .build();

        // when:
        final PlatformTxnAccessor subject = PlatformTxnAccessor.from(txn);
        final Transaction signedTxn4Log = subject.getSignedTxnWrapper();

        final ByteString signedTxnBytes = signedTxn4Log.getSignedTransactionBytes();
        final Transaction asBodyBytes =
                signedTxn4Log.toBuilder()
                        .setSignedTransactionBytes(
                                CommonUtils.extractTransactionBodyByteString(signedTxn4Log))
                        .build();

        // then:
        assertEquals(signedTxnBytes, txn.getSignedTransactionBytes());
        assertEquals(signedTxn.getBodyBytes(), asBodyBytes.getSignedTransactionBytes());
    }

    @Test
    void getsPayer() throws InvalidProtocolBufferException {
        // given:
        final AccountID payer = asAccount("0.0.2");
        final Transaction signedTxnWithBody =
                Transaction.newBuilder().setBodyBytes(someTxn.toByteString()).build();

        // when:
        final PlatformTxnAccessor subject = PlatformTxnAccessor.from(signedTxnWithBody);

        // then:
        assertEquals(payer, subject.getPayer());
    }

    @Test
    @SuppressWarnings("java:S5961")
    void delegatesToSignedTxnAccessor() throws InvalidProtocolBufferException {
        final AccountID payer = asAccount("0.0.2");
        final TransactionBody someTxn =
                TransactionBody.newBuilder()
                        .setTransactionID(
                                TransactionID.newBuilder().setAccountID(asAccount("0.0.2")))
                        .setMemo("Hi!")
                        .setTransactionFee(10L)
                        .setConsensusSubmitMessage(
                                ConsensusSubmitMessageTransactionBody.newBuilder()
                                        .setTopicID(asTopic("0.0.10"))
                                        .build())
                        .build();
        final ByteString canonicalSig =
                ByteString.copyFromUtf8(
                        "0123456789012345678901234567890123456789012345678901234567890123");
        final SignatureMap onePairSigMap =
                SignatureMap.newBuilder()
                        .addSigPair(
                                SignaturePair.newBuilder()
                                        .setPubKeyPrefix(ByteString.copyFromUtf8("a"))
                                        .setEd25519(canonicalSig))
                        .build();
        final Transaction signedTxnWithBody =
                Transaction.newBuilder()
                        .setBodyBytes(someTxn.toByteString())
                        .setSigMap(onePairSigMap)
                        .build();
        final var aliasManager = mock(AliasManager.class);
        final var stateView = mock(StateView.class);

        // when:
        final PlatformTxnAccessor subject = PlatformTxnAccessor.from(signedTxnWithBody);
        final var delegate = subject.getDelegate();

        // then:
        assertEquals(onePairSigMap, subject.getSigMap());
        assertEquals(delegate.getSigMap(), subject.getSigMap());

        assertEquals(ConsensusSubmitMessage, subject.getFunction());
        assertEquals(delegate.getFunction(), subject.getFunction());

        assertEquals(delegate.getOfferedFee(), subject.getOfferedFee());

        assertEquals(SubType.DEFAULT, subject.getSubType());
        assertEquals(delegate.getSubType(), subject.getSubType());

        assertEquals("Hi!", subject.getMemo());
        assertEquals(delegate.getMemo(), subject.getMemo());

        assertArrayEquals(StringUtils.getBytesUtf8("Hi!"), subject.getMemoUtf8Bytes());
        assertArrayEquals(delegate.getMemoUtf8Bytes(), subject.getMemoUtf8Bytes());

        assertEquals(false, subject.memoHasZeroByte());
        assertEquals(delegate.memoHasZeroByte(), subject.memoHasZeroByte());

        assertEquals(delegate.getOfferedFee(), subject.getOfferedFee());
        assertEquals(10L, subject.getOfferedFee());

        assertEquals(delegate.getSignedTxnWrapperBytes(), subject.getSignedTxnWrapperBytes());
        assertEquals(delegate.getGasLimitForContractTx(), subject.getGasLimitForContractTx());
        assertEquals(delegate.areImplicitCreationsCounted(), subject.areImplicitCreationsCounted());

        final var sigMeta = mock(RationalizedSigMeta.class);
        final var refs = mock(LinkedRefs.class);
        final var scheduleRef = IdUtils.asSchedule("0.0.123");

        subject.setExpandedSigStatus(OK);
        subject.setScheduleRef(scheduleRef);
        subject.setNumImplicitCreations(2);
        subject.setPayer(payer);
        subject.setLinkedRefs(refs);
        subject.setSigMeta(sigMeta);
        subject.setStateView(stateView);

        assertTrue(delegate.isTriggeredTxn());
        assertTrue(subject.isTriggeredTxn());
        assertEquals(OK, subject.getExpandedSigStatus());
        assertEquals(2, subject.getNumImplicitCreations());
        assertEquals(scheduleRef, subject.getScheduleRef());
        assertEquals(payer, subject.getPayer());
        assertEquals(refs, subject.getLinkedRefs());
        assertEquals(sigMeta, subject.getSigMeta());
        assertEquals(false, subject.canTriggerTxn());
        assertEquals(delegate.canTriggerTxn(), subject.canTriggerTxn());
        assertEquals(delegate.getScheduleRef(), subject.getScheduleRef());

        assertEquals(delegate.usageGiven(2), subject.usageGiven(2));
        assertEquals(1, subject.usageGiven(2).numSigs());

        assertEquals(delegate.baseUsageMeta(), subject.baseUsageMeta());
        assertEquals(delegate.getMemoUtf8Bytes().length, subject.baseUsageMeta().memoUtf8Bytes());

        assertEquals(delegate.availSubmitUsageMeta(), subject.availSubmitUsageMeta());
        assertEquals(
                someTxn.getConsensusSubmitMessage().getMessage().size(),
                subject.availSubmitUsageMeta().numMsgBytes());

        assertEquals(delegate, subject.getDelegate());

        subject.countImplicitCreationsWith(aliasManager);

        subject.markCongestionExempt();
        subject.markThrottleExempt();
        assertTrue(subject.congestionExempt());
        assertTrue(subject.throttleExempt());

        assertEquals(delegate.throttleExempt(), subject.throttleExempt());
        assertEquals(delegate.congestionExempt(), subject.congestionExempt());
        assertEquals(delegate.getStateView(), subject.getStateView());
        assertEquals(stateView, subject.getStateView());
    }

    @Test
    void toLoggableStringWorks() throws InvalidProtocolBufferException {
        final TransactionBody someTxn =
                TransactionBody.newBuilder()
                        .setTransactionID(
                                TransactionID.newBuilder().setAccountID(asAccount("0.0.2")))
                        .setMemo("Hi!")
                        .setTransactionFee(10L)
                        .setConsensusSubmitMessage(
                                ConsensusSubmitMessageTransactionBody.newBuilder()
                                        .setTopicID(asTopic("0.0.10"))
                                        .build())
                        .build();
        final ByteString canonicalSig =
                ByteString.copyFromUtf8(
                        "0123456789012345678901234567890123456789012345678901234567890123");
        final SignatureMap onePairSigMap =
                SignatureMap.newBuilder()
                        .addSigPair(
                                SignaturePair.newBuilder()
                                        .setPubKeyPrefix(ByteString.copyFromUtf8("a"))
                                        .setEd25519(canonicalSig))
                        .build();
        final Transaction signedTxnWithBody =
                Transaction.newBuilder()
                        .setBodyBytes(someTxn.toByteString())
                        .setSigMap(onePairSigMap)
                        .build();

        // when:
        final PlatformTxnAccessor subject = PlatformTxnAccessor.from(signedTxnWithBody);
        final var expectedString =
                "PlatformTxnAccessor{delegate=SignedTxnAccessor{sigMapSize=71, numSigPairs=1,"
                    + " numImplicitCreations=-1, hash=[111, -123, -70, 79, 75, -80, -114, -49, 88,"
                    + " -76, -82, -23, 43, 103, -21, 52, -31, -60, 98, -55, -26, -18, -101, -108,"
                    + " -51, 24, 49, 72, 18, -69, 21, -84, -68, -118, 31, -53, 91, -61, -71, -56,"
                    + " 100, -52, -104, 87, -85, -33, -73, -124], txnBytes=[10, 4, 18, 2, 24, 2,"
                    + " 24, 10, 50, 3, 72, 105, 33, -38, 1, 4, 10, 2, 24, 10], utf8MemoBytes=[72,"
                    + " 105, 33], memo=Hi!, memoHasZeroByte=false, signedTxnWrapper=sigMap {\n"
                    + "  sigPair {\n"
                    + "    pubKeyPrefix: \"a\"\n"
                    + "    ed25519:"
                    + " \"0123456789012345678901234567890123456789012345678901234567890123\"\n"
                    + "  }\n"
                    + "}\n"
                    + "bodyBytes: \"\\n"
                    + "\\004\\022\\002\\030\\002\\030\\n"
                    + "2\\003Hi!\\332\\001\\004\\n"
                    + "\\002\\030\\n"
                    + "\"\n"
                    + ", hash=[111, -123, -70, 79, 75, -80, -114, -49, 88, -76, -82, -23, 43, 103,"
                    + " -21, 52, -31, -60, 98, -55, -26, -18, -101, -108, -51, 24, 49, 72, 18, -69,"
                    + " 21, -84, -68, -118, 31, -53, 91, -61, -71, -56, 100, -52, -104, 87, -85,"
                    + " -33, -73, -124], txnBytes=[10, 4, 18, 2, 24, 2, 24, 10, 50, 3, 72, 105, 33,"
                    + " -38, 1, 4, 10, 2, 24, 10], sigMap=sigPair {\n"
                    + "  pubKeyPrefix: \"a\"\n"
                    + "  ed25519:"
                    + " \"0123456789012345678901234567890123456789012345678901234567890123\"\n"
                    + "}\n"
                    + ", txnId=accountID {\n"
                    + "  accountNum: 2\n"
                    + "}\n"
                    + ", txn=transactionID {\n"
                    + "  accountID {\n"
                    + "    accountNum: 2\n"
                    + "  }\n"
                    + "}\n"
                    + "transactionFee: 10\n"
                    + "memo: \"Hi!\"\n"
                    + "consensusSubmitMessage {\n"
                    + "  topicID {\n"
                    + "    topicNum: 10\n"
                    + "  }\n"
                    + "}\n"
                    + ", submitMessageMeta=SubmitMessageMeta[numMsgBytes=0], xferUsageMeta=null,"
                    + " txnUsageMeta=BaseTransactionMeta[memoUtf8Bytes=3, numExplicitTransfers=0],"
                    + " function=ConsensusSubmitMessage,"
                    + " pubKeyToSigBytes=PojoSigMapPubKeyToSigBytes{pojoSigMap=PojoSigMap{keyTypes=[ED25519],"
                    + " rawMap=[[[97], [48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52,"
                    + " 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51,"
                    + " 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50,"
                    + " 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51]]]}, used=[false]},"
                    + " payer=accountNum: 2\n"
                    + ", scheduleRef=null, view=null}, linkedRefs=null, expandedSigStatus=null,"
                    + " pubKeyToSigBytes=PojoSigMapPubKeyToSigBytes{pojoSigMap=PojoSigMap{keyTypes=[ED25519],"
                    + " rawMap=[[[97], [48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52,"
                    + " 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51,"
                    + " 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50,"
                    + " 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51]]]}, used=[false]},"
                    + " sigMeta=null}";

        assertEquals(expectedString, subject.toLoggableString());
    }
}
