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
package com.hedera.node.app.service.mono.sigs;

import static com.hedera.node.app.service.mono.sigs.order.CodeOrderResultFactory.CODE_ORDER_RESULT_FACTORY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.KEY_PREFIX_MISMATCH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.hedera.node.app.service.mono.ledger.SigImpactHistorian;
import com.hedera.node.app.service.mono.legacy.core.jproto.JEd25519Key;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.sigs.factories.ReusableBodySigningFactory;
import com.hedera.node.app.service.mono.sigs.order.LinkedRefs;
import com.hedera.node.app.service.mono.sigs.order.SigRequirements;
import com.hedera.node.app.service.mono.sigs.order.SigningOrderResult;
import com.hedera.node.app.service.mono.sigs.sourcing.PubKeyToSigBytes;
import com.hedera.node.app.service.mono.sigs.verification.SyncVerifier;
import com.hedera.node.app.service.mono.utils.RationalizedSigMeta;
import com.hedera.node.app.service.mono.utils.accessors.PlatformTxnAccessor;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.crypto.TransactionSignature;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RationalizationTest {
    private final JKey payerKey = TxnHandlingScenario.MISC_ACCOUNT_KT.asJKeyUnchecked();
    private final TransactionBody txn = TransactionBody.getDefaultInstance();
    private final SigningOrderResult<ResponseCodeEnum> generalError =
            CODE_ORDER_RESULT_FACTORY.forGeneralError();
    private final SigningOrderResult<ResponseCodeEnum> othersError =
            CODE_ORDER_RESULT_FACTORY.forImmutableContract();

    @Mock private PlatformTxnAccessor txnAccessor;
    @Mock private SyncVerifier syncVerifier;
    @Mock private SigRequirements keyOrderer;
    @Mock private ReusableBodySigningFactory sigFactory;
    @Mock private PubKeyToSigBytes pkToSigFn;
    @Mock private SigningOrderResult<ResponseCodeEnum> mockOrderResult;
    @Mock private SigImpactHistorian sigImpactHistorian;
    @Mock private AccountID payer;
    @Mock private LinkedRefs linkedRefs;

    private Rationalization subject;

    @BeforeEach
    void setUp() {
        subject = new Rationalization(syncVerifier, sigImpactHistorian, keyOrderer, sigFactory);
    }

    @Test
    void resetWorks() {
        final List<TransactionSignature> mockSigs = new ArrayList<>();
        final JKey fake = new JEd25519Key("FAKE".getBytes(StandardCharsets.UTF_8));

        subject = new Rationalization(syncVerifier, keyOrderer, sigFactory);

        given(txnAccessor.getPkToSigsFn()).willReturn(pkToSigFn);
        // and:
        subject.getRealPayerSigs().add(null);
        subject.getRealOtherPartySigs().add(null);
        subject.setReqPayerSig(fake);
        subject.setReqOthersSigs(List.of(fake));
        subject.setLastOrderResult(CODE_ORDER_RESULT_FACTORY.forGeneralError());
        subject.setFinalStatus(INVALID_ACCOUNT_ID);
        subject.setVerifiedSync(true);

        // when:
        subject.resetFor(txnAccessor);

        // then:
        assertSame(txnAccessor, subject.getTxnAccessor());
        assertSame(syncVerifier, subject.getSyncVerifier());
        assertSame(keyOrderer, subject.getSigReqs());
        assertSame(pkToSigFn, subject.getPkToSigFn());
        assertEquals(mockSigs, subject.getTxnSigs());
        // and:
        assertTrue(subject.getRealPayerSigs().isEmpty());
        assertTrue(subject.getRealOtherPartySigs().isEmpty());
        // and:
        assertFalse(subject.usedSyncVerification());
        assertNull(subject.finalStatus());
        assertNull(subject.getReqPayerSig());
        assertNull(subject.getReqOthersSigs());
        assertNull(subject.getLastOrderResult());
        // and:
        verify(sigFactory).resetFor(txnAccessor);
        verify(pkToSigFn).resetAllSigsToUnused();
    }

    @Test
    void doesNothingIfLinkedRefsAvailableAndUnchanged() {
        given(txnAccessor.getLinkedRefs()).willReturn(linkedRefs);
        given(txnAccessor.getExpandedSigStatus()).willReturn(KEY_PREFIX_MISMATCH);
        given(linkedRefs.haveNoChangesAccordingTo(sigImpactHistorian)).willReturn(true);
        subject.setVerifiedSync(true);

        subject.performFor(txnAccessor);

        verifyNoMoreInteractions(txnAccessor);
        assertEquals(KEY_PREFIX_MISMATCH, subject.finalStatus());
        assertFalse(subject.usedSyncVerification());
    }

    @Test
    void setsUnavailableMetaIfCannotListPayerKey() {
        given(txnAccessor.getLinkedRefs()).willReturn(linkedRefs);
        ArgumentCaptor<RationalizedSigMeta> captor =
                ArgumentCaptor.forClass(RationalizedSigMeta.class);

        given(txnAccessor.getTxn()).willReturn(txn);
        given(txnAccessor.getPayer()).willReturn(payer);
        given(txnAccessor.getPkToSigsFn()).willReturn(pkToSigFn);
        given(keyOrderer.keysForPayer(txn, CODE_ORDER_RESULT_FACTORY, null, payer))
                .willReturn(generalError);

        // when:
        subject.performFor(txnAccessor);

        // then:
        assertEquals(generalError.getErrorReport(), subject.finalStatus());
        // and:
        verify(txnAccessor).setSigMeta(captor.capture());
        assertSame(RationalizedSigMeta.noneAvailable(), captor.getValue());
    }

    @Test
    void propagatesFailureIfCouldNotExpandOthersKeys() {
        given(txnAccessor.getLinkedRefs()).willReturn(linkedRefs);
        given(linkedRefs.haveNoChangesAccordingTo(sigImpactHistorian)).willReturn(true);
        ArgumentCaptor<RationalizedSigMeta> captor =
                ArgumentCaptor.forClass(RationalizedSigMeta.class);

        given(txnAccessor.getTxn()).willReturn(txn);
        given(txnAccessor.getPayer()).willReturn(payer);
        given(txnAccessor.getPkToSigsFn()).willReturn(pkToSigFn);
        given(mockOrderResult.getPayerKey()).willReturn(payerKey);
        given(keyOrderer.keysForPayer(txn, CODE_ORDER_RESULT_FACTORY, null, payer))
                .willReturn(mockOrderResult);
        given(keyOrderer.keysForOtherParties(txn, CODE_ORDER_RESULT_FACTORY, null, payer))
                .willReturn(othersError);

        // when:
        subject.performFor(txnAccessor);

        // then:
        assertEquals(othersError.getErrorReport(), subject.finalStatus());
        // and:
        verify(txnAccessor).setSigMeta(captor.capture());
        final var sigMeta = captor.getValue();
        assertTrue(sigMeta.couldRationalizePayer());
        assertFalse(sigMeta.couldRationalizeOthers());
        assertSame(payerKey, sigMeta.payerKey());
    }
}
