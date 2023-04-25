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

import static com.hedera.test.factories.scenarios.TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asToken;
import static com.hedera.test.utils.TxnUtils.payerSponsoredTransfer;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NFT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.RESULT_SIZE_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenGetNftInfoQuery;
import com.hederahashgraph.api.proto.java.TokenGetNftInfoResponse;
import com.hederahashgraph.api.proto.java.TokenNftInfo;
import com.hederahashgraph.api.proto.java.Transaction;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GetTokenNftInfoAnswerTest {
    private Transaction paymentTxn;
    private final String node = "0.0.3";
    private final String payer = "0.0.12345";
    private NftID nftId =
            NftID.newBuilder().setTokenID(asToken("1.2.3")).setSerialNumber(2).build();
    private final long fee = 1_234L;
    private final AccountID owner = asAccount("3.4.5");
    private final AccountID spender = asAccount("5.6.7");
    private final ByteString metadata = ByteString.copyFromUtf8("some metadata");
    private final ByteString ledgerId = ByteString.copyFromUtf8("0xff");

    StateView view;
    OptionValidator optionValidator;

    TokenNftInfo info;

    GetTokenNftInfoAnswer subject;

    @BeforeEach
    void setup() {
        info =
                TokenNftInfo.newBuilder()
                        .setLedgerId(ledgerId)
                        .setNftID(nftId)
                        .setCreationTime(Timestamp.newBuilder().setSeconds(1).setNanos(2))
                        .setAccountID(owner)
                        .setSpenderId(spender)
                        .setMetadata(metadata)
                        .build();

        view = mock(StateView.class);
        optionValidator = mock(OptionValidator.class);

        subject = new GetTokenNftInfoAnswer();
    }

    @Test
    void getsTheInfo() throws Throwable {
        // setup:
        final Query query = validQuery(ANSWER_ONLY, fee, nftId);

        given(view.infoForNft(nftId)).willReturn(Optional.of(info));

        // when:
        final Response response = subject.responseGiven(query, view, OK, fee);

        // then:
        assertTrue(response.hasTokenGetNftInfo());
        assertTrue(response.getTokenGetNftInfo().hasHeader(), "Missing response header!");
        assertEquals(
                OK, response.getTokenGetNftInfo().getHeader().getNodeTransactionPrecheckCode());
        assertEquals(ANSWER_ONLY, response.getTokenGetNftInfo().getHeader().getResponseType());
        assertEquals(fee, response.getTokenGetNftInfo().getHeader().getCost());
        // and:
        final var actual = response.getTokenGetNftInfo().getNft();
        assertEquals(info, actual);
    }

    @Test
    void usesViewToValidate() throws Throwable {
        // setup:
        final Query query = validQuery(COST_ANSWER, fee, nftId);

        given(view.nftExists(nftId)).willReturn(false);

        // when:
        final ResponseCodeEnum validity = subject.checkValidity(query, view);

        // then:
        assertEquals(INVALID_NFT_ID, validity);
    }

    @Test
    void validatesInexistingTokenId() throws Throwable {
        // setup:
        nftId = NftID.newBuilder().setSerialNumber(2).build();
        final Query query = validQuery(COST_ANSWER, fee, nftId);

        // when:
        final ResponseCodeEnum validity = subject.checkValidity(query, view);

        // then:
        assertEquals(INVALID_TOKEN_ID, validity);
    }

    @Test
    void validatesWrongSerialNumber() throws Throwable {
        // setup:
        nftId = NftID.newBuilder().setTokenID(nftId.getTokenID()).build();
        final Query query = validQuery(COST_ANSWER, fee, nftId);

        // when:
        final ResponseCodeEnum validity = subject.checkValidity(query, view);

        // then:
        assertEquals(INVALID_TOKEN_NFT_SERIAL_NUMBER, validity);
    }

    @Test
    void getsExpectedPayment() throws Throwable {
        // given:
        final Query query = validQuery(COST_ANSWER, fee, nftId);

        // expect:
        assertEquals(paymentTxn, subject.extractPaymentFrom(query).get().getSignedTxnWrapper());
    }

    @Test
    void getsInfoFromCtxWhenAvailable() throws Throwable {
        // setup:
        final Query sensibleQuery = validQuery(ANSWER_ONLY, 5L, nftId);
        final Map<String, Object> ctx = new HashMap<>();

        // given:
        ctx.put(GetTokenNftInfoAnswer.NFT_INFO_CTX_KEY, info);

        // when:
        final Response response = subject.responseGiven(sensibleQuery, view, OK, 0L, ctx);

        // then:
        final var opResponse = response.getTokenGetNftInfo();
        assertTrue(opResponse.hasHeader(), "Missing response header!");
        assertEquals(OK, opResponse.getHeader().getNodeTransactionPrecheckCode());
        assertSame(info, opResponse.getNft());
        // and:
        verify(view, never()).infoForNft(any());
    }

    @Test
    void validatesInvalidNftIdInContext() throws Throwable {
        // setup:
        final Query sensibleQuery = validQuery(ANSWER_ONLY, 5L, nftId);
        final Map<String, Object> ctx = new HashMap<>();

        // when:
        final Response response = subject.responseGiven(sensibleQuery, view, OK, 0L, ctx);

        // then:
        final var opResponse = response.getTokenGetNftInfo();
        assertTrue(opResponse.hasHeader(), "Missing response header!");
        assertEquals(INVALID_NFT_ID, opResponse.getHeader().getNodeTransactionPrecheckCode());
        // and:
        verify(view, never()).infoForNft(any());
    }

    @Test
    void doesNotGetInfoWithInvalidValidity() throws Throwable {
        // setup:
        final Query sensibleQuery = validQuery(ANSWER_ONLY, 5L, nftId);
        final Map<String, Object> ctx = new HashMap<>();

        // given:
        ctx.put(GetTokenNftInfoAnswer.NFT_INFO_CTX_KEY, info);

        // when:
        final Response response =
                subject.responseGiven(sensibleQuery, view, INVALID_NFT_ID, 0L, ctx);

        // then:
        final var opResponse = response.getTokenGetNftInfo();
        assertTrue(opResponse.hasHeader(), "Missing response header!");
        assertEquals(INVALID_NFT_ID, opResponse.getHeader().getNodeTransactionPrecheckCode());
        // and:
        verify(view, never()).infoForNft(any());
    }

    @Test
    void doesNotGetInfoWithEmptyContext() throws Throwable {
        // setup:
        final Query sensibleQuery = validQuery(ANSWER_ONLY, 5L, nftId);

        // when:
        final Response response = subject.responseGiven(sensibleQuery, view, INVALID_NFT_ID, 0L);

        // then:
        final var opResponse = response.getTokenGetNftInfo();
        assertTrue(opResponse.hasHeader(), "Missing response header!");
        assertEquals(INVALID_NFT_ID, opResponse.getHeader().getNodeTransactionPrecheckCode());
        // and:
        verify(view, never()).infoForNft(any());
    }

    @Test
    void validatesEmptyInfo() throws Throwable {
        // setup:
        final Query sensibleQuery = validQuery(ANSWER_ONLY, 5L, nftId);

        given(view.infoForNft(nftId)).willReturn(Optional.empty());

        // when:
        final Response response = subject.responseGiven(sensibleQuery, view, OK);

        // then:
        final var opResponse = response.getTokenGetNftInfo();
        assertTrue(opResponse.hasHeader(), "Missing response header!");
        assertEquals(INVALID_NFT_ID, opResponse.getHeader().getNodeTransactionPrecheckCode());
    }

    @Test
    void getsCostAnswerResponse() throws Throwable {
        // setup:
        final Query query = validQuery(COST_ANSWER, fee, nftId);

        // when:
        final Response response = subject.responseGiven(query, view, OK, fee);

        // then:
        assertTrue(response.hasTokenGetNftInfo());
        assertEquals(
                OK, response.getTokenGetNftInfo().getHeader().getNodeTransactionPrecheckCode());
        assertEquals(COST_ANSWER, response.getTokenGetNftInfo().getHeader().getResponseType());
        assertEquals(fee, response.getTokenGetNftInfo().getHeader().getCost());
    }

    @Test
    void recognizesFunction() {
        // expect:
        assertEquals(HederaFunctionality.TokenGetNftInfo, subject.canonicalFunction());
    }

    @Test
    void getsValidity() {
        // given:
        final Response response =
                Response.newBuilder()
                        .setTokenGetNftInfo(
                                TokenGetNftInfoResponse.newBuilder()
                                        .setHeader(
                                                subject.answerOnlyHeader(
                                                        RESULT_SIZE_LIMIT_EXCEEDED)))
                        .build();

        // expect:
        assertEquals(RESULT_SIZE_LIMIT_EXCEEDED, subject.extractValidityFrom(response));
    }

    @Test
    void requiresAnswerOnlyPayment() throws Throwable {
        // expect:
        assertFalse(subject.requiresNodePayment(validQuery(COST_ANSWER, 0, nftId)));
        assertTrue(subject.requiresNodePayment(validQuery(ANSWER_ONLY, 0, nftId)));
    }

    @Test
    void requiresAnswerOnlyCostAsExpected() throws Throwable {
        // expect:
        assertTrue(subject.needsAnswerOnlyCost(validQuery(COST_ANSWER, 0, nftId)));
        assertFalse(subject.needsAnswerOnlyCost(validQuery(ANSWER_ONLY, 0, nftId)));
    }

    private Query validQuery(final ResponseType type, final long payment, final NftID nftId)
            throws Throwable {
        this.paymentTxn = payerSponsoredTransfer(payer, COMPLEX_KEY_ACCOUNT_KT, node, payment);
        final QueryHeader.Builder header =
                QueryHeader.newBuilder().setPayment(this.paymentTxn).setResponseType(type);
        final TokenGetNftInfoQuery.Builder op =
                TokenGetNftInfoQuery.newBuilder().setHeader(header).setNftID(nftId);
        return Query.newBuilder().setTokenGetNftInfo(op).build();
    }
}
