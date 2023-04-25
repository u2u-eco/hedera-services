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
package com.hedera.node.app.service.mono.fees.calculation.schedule.txns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.hedera.node.app.hapi.fees.usage.SigUsage;
import com.hedera.node.app.hapi.fees.usage.schedule.ScheduleOpsUsage;
import com.hedera.node.app.hapi.utils.fee.SigValueObj;
import com.hedera.node.app.service.mono.config.MockGlobalDynamicProps;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.ScheduleCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScheduleCreateResourceUsageTest {

    ScheduleCreateResourceUsage subject;

    StateView view;
    ScheduleOpsUsage scheduleOpsUsage;
    TransactionBody nonScheduleCreateTxn;
    TransactionBody scheduleCreateTxn;

    int numSigs = 10, sigsSize = 100, numPayerKeys = 3;
    SigValueObj obj = new SigValueObj(numSigs, numPayerKeys, sigsSize);
    SigUsage sigUsage = new SigUsage(numSigs, sigsSize, numPayerKeys);
    MockGlobalDynamicProps props = new MockGlobalDynamicProps();
    FeeData expected;

    @BeforeEach
    void setup() {
        expected = mock(FeeData.class);
        view = mock(StateView.class);
        scheduleCreateTxn = mock(TransactionBody.class);
        scheduleOpsUsage = mock(ScheduleOpsUsage.class);
        given(scheduleCreateTxn.hasScheduleCreate()).willReturn(true);
        given(scheduleCreateTxn.getScheduleCreate())
                .willReturn(ScheduleCreateTransactionBody.getDefaultInstance());

        nonScheduleCreateTxn = mock(TransactionBody.class);

        given(
                        scheduleOpsUsage.scheduleCreateUsage(
                                scheduleCreateTxn, sigUsage, props.scheduledTxExpiryTimeSecs()))
                .willReturn(expected);

        subject = new ScheduleCreateResourceUsage(scheduleOpsUsage, props);
    }

    @Test
    void recognizesApplicableQuery() {
        // expect:
        assertTrue(subject.applicableTo(scheduleCreateTxn));
        assertFalse(subject.applicableTo(nonScheduleCreateTxn));
    }

    @Test
    void delegatesToCorrectEstimate() throws Exception {
        // expect:
        assertEquals(expected, subject.usageGiven(scheduleCreateTxn, obj, view));
    }

    @Test
    void handlesExpirationTime() throws Exception {
        props.enableSchedulingLongTerm();

        given(scheduleCreateTxn.getScheduleCreate())
                .willReturn(
                        ScheduleCreateTransactionBody.newBuilder()
                                .setExpirationTime(
                                        Timestamp.newBuilder().setSeconds(2L).setNanos(0))
                                .build());

        given(scheduleCreateTxn.getTransactionID())
                .willReturn(
                        TransactionID.newBuilder()
                                .setTransactionValidStart(
                                        Timestamp.newBuilder().setSeconds(1L).setNanos(0))
                                .build());

        given(scheduleOpsUsage.scheduleCreateUsage(scheduleCreateTxn, sigUsage, 1L))
                .willReturn(expected);

        assertEquals(expected, subject.usageGiven(scheduleCreateTxn, obj, view));
    }

    @Test
    void ignoresExpirationTimeLongTermDisabled() throws Exception {

        given(scheduleCreateTxn.getScheduleCreate())
                .willReturn(
                        ScheduleCreateTransactionBody.newBuilder()
                                .setExpirationTime(
                                        Timestamp.newBuilder().setSeconds(2L).setNanos(0))
                                .build());

        given(scheduleCreateTxn.getTransactionID())
                .willReturn(
                        TransactionID.newBuilder()
                                .setTransactionValidStart(
                                        Timestamp.newBuilder().setSeconds(1L).setNanos(0))
                                .build());

        assertEquals(expected, subject.usageGiven(scheduleCreateTxn, obj, view));
    }

    @Test
    void handlesPastExpirationTime() throws Exception {
        props.enableSchedulingLongTerm();

        given(scheduleCreateTxn.getScheduleCreate())
                .willReturn(
                        ScheduleCreateTransactionBody.newBuilder()
                                .setExpirationTime(
                                        Timestamp.newBuilder().setSeconds(2L).setNanos(0))
                                .build());

        given(scheduleCreateTxn.getTransactionID())
                .willReturn(
                        TransactionID.newBuilder()
                                .setTransactionValidStart(
                                        Timestamp.newBuilder().setSeconds(5L).setNanos(0))
                                .build());

        given(scheduleOpsUsage.scheduleCreateUsage(scheduleCreateTxn, sigUsage, 0L))
                .willReturn(expected);

        assertEquals(expected, subject.usageGiven(scheduleCreateTxn, obj, view));
    }
}
