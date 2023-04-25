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
package com.hedera.node.app.service.mono.queries.meta;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseHeader;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.TransactionGetFastRecordQuery;
import com.hederahashgraph.api.proto.java.TransactionGetFastRecordResponse;
import org.junit.jupiter.api.Test;

class GetFastTxnRecordAnswerTest {
    GetFastTxnRecordAnswer subject = new GetFastTxnRecordAnswer();
    Query query = Query.getDefaultInstance();

    @Test
    void neverDoesOrNeedsAnything() {
        // expect:
        assertFalse(subject.needsAnswerOnlyCost(query));
        assertFalse(subject.requiresNodePayment(query));
        assertFalse(subject.extractPaymentFrom(query).isPresent());
    }

    @Test
    void extractsValidity() {
        // given:
        final Response response =
                Response.newBuilder()
                        .setTransactionGetFastRecord(
                                TransactionGetFastRecordResponse.newBuilder()
                                        .setHeader(
                                                ResponseHeader.newBuilder()
                                                        .setNodeTransactionPrecheckCode(FAIL_FEE)))
                        .build();

        // expect:
        assertEquals(FAIL_FEE, subject.extractValidityFrom(response));
    }

    @Test
    void respectsTypeOfUnsupportedQuery() {
        // given:
        final Query costAnswer = getFastTxnRecordQuery(COST_ANSWER);
        final Query answerOnly = getFastTxnRecordQuery(ANSWER_ONLY);

        // when:
        final Response costAnswerResponse = subject.responseGiven(costAnswer, null, OK, 0L);
        final Response answerOnlyResponse = subject.responseGiven(answerOnly, null, OK, 0L);

        // then:
        assertEquals(
                COST_ANSWER,
                costAnswerResponse.getTransactionGetFastRecord().getHeader().getResponseType());
        assertEquals(
                ANSWER_ONLY,
                answerOnlyResponse.getTransactionGetFastRecord().getHeader().getResponseType());
        // and:
        assertEquals(NOT_SUPPORTED, subject.extractValidityFrom(costAnswerResponse));
        assertEquals(NOT_SUPPORTED, subject.extractValidityFrom(answerOnlyResponse));
    }

    @Test
    void alwaysUnsupported() {
        // expect:
        assertEquals(NOT_SUPPORTED, subject.checkValidity(query, null));
    }

    @Test
    void recognizesFunction() {
        // expect:
        assertEquals(HederaFunctionality.TransactionGetRecord, subject.canonicalFunction());
    }

    private Query getFastTxnRecordQuery(final ResponseType type) {
        final TransactionGetFastRecordQuery.Builder op = TransactionGetFastRecordQuery.newBuilder();
        op.setHeader(QueryHeader.newBuilder().setResponseType(type));
        return Query.newBuilder().setTransactionGetFastRecord(op).build();
    }
}
