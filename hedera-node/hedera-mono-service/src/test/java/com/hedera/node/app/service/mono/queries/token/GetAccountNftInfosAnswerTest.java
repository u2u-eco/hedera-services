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
package com.hedera.node.app.service.mono.queries.token;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseHeader;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.TokenGetAccountNftInfosQuery;
import com.hederahashgraph.api.proto.java.TokenGetAccountNftInfosResponse;
import org.junit.jupiter.api.Test;

class GetAccountNftInfosAnswerTest {
    GetAccountNftInfosAnswer subject = new GetAccountNftInfosAnswer();
    Query query = Query.getDefaultInstance();

    @Test
    void neverDoesOrNeedsAnything() {
        // expect:
        assertNull(GetAccountNftInfosAnswer.IRRELEVANT_PAYMENT_EXTRACTOR.apply(query));
        assertNull(GetAccountNftInfosAnswer.IRRELEVANT_RESPONSE_TYPE_EXTRACTOR.apply(query));
        assertFalse(subject.needsAnswerOnlyCost(query));
        assertFalse(subject.requiresNodePayment(query));
        assertFalse(subject.extractPaymentFrom(query).isPresent());
    }

    @Test
    void extractsValidity() {
        // given:
        final Response response =
                Response.newBuilder()
                        .setTokenGetAccountNftInfos(
                                TokenGetAccountNftInfosResponse.newBuilder()
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
        final Query costAnswer = getQuery(COST_ANSWER);
        final Query answerOnly = getQuery(ANSWER_ONLY);

        // when:
        final Response costAnswerResponse = subject.responseGiven(costAnswer, null, OK, 0L);
        final Response answerOnlyResponse = subject.responseGiven(answerOnly, null, OK, 0L);

        // then:
        assertEquals(
                COST_ANSWER,
                costAnswerResponse.getTokenGetAccountNftInfos().getHeader().getResponseType());
        assertEquals(
                ANSWER_ONLY,
                answerOnlyResponse.getTokenGetAccountNftInfos().getHeader().getResponseType());
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
        assertEquals(HederaFunctionality.TokenGetAccountNftInfos, subject.canonicalFunction());
    }

    private Query getQuery(final ResponseType type) {
        final TokenGetAccountNftInfosQuery.Builder op = TokenGetAccountNftInfosQuery.newBuilder();
        op.setHeader(QueryHeader.newBuilder().setResponseType(type));
        return Query.newBuilder().setTokenGetAccountNftInfos(op).build();
    }
}
