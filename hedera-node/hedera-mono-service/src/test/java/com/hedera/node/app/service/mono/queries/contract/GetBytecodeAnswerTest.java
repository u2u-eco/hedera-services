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
package com.hedera.node.app.service.mono.queries.contract;

import static com.hedera.test.factories.scenarios.TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT;
import static com.hedera.test.utils.IdUtils.asContract;
import static com.hedera.test.utils.TxnUtils.payerSponsoredTransfer;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.RESULT_SIZE_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.ledger.accounts.AliasManager;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.migration.AccountStorageAdapter;
import com.hedera.node.app.service.mono.txns.validation.OptionValidator;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hederahashgraph.api.proto.java.ContractGetBytecodeQuery;
import com.hederahashgraph.api.proto.java.ContractGetBytecodeResponse;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Transaction;
import com.swirlds.merkle.map.MerkleMap;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GetBytecodeAnswerTest {
    private Transaction paymentTxn;
    private final String node = "0.0.3";
    private final String payer = "0.0.12345";
    private final String target = "0.0.123";
    private final long fee = 1_234L;
    private final byte[] bytecode = "A Supermarket in California".getBytes();

    OptionValidator optionValidator;
    StateView view;
    MerkleMap<EntityNum, MerkleAccount> contracts;
    private AliasManager aliasManager;

    GetBytecodeAnswer subject;

    @BeforeEach
    void setup() {
        contracts = mock(MerkleMap.class);

        view = mock(StateView.class);
        given(view.contracts()).willReturn(AccountStorageAdapter.fromInMemory(contracts));
        optionValidator = mock(OptionValidator.class);
        aliasManager = mock(AliasManager.class);

        subject = new GetBytecodeAnswer(aliasManager, optionValidator);
    }

    @Test
    void recognizesFunction() {
        // expect:
        assertEquals(HederaFunctionality.ContractGetBytecode, subject.canonicalFunction());
    }

    @Test
    void requiresAnswerOnlyCostAsExpected() throws Throwable {
        // expect:
        assertTrue(subject.needsAnswerOnlyCost(validQuery(COST_ANSWER, 0, target)));
        assertFalse(subject.needsAnswerOnlyCost(validQuery(ANSWER_ONLY, 0, target)));
    }

    @Test
    void requiresAnswerOnlyPayment() throws Throwable {
        // expect:
        assertFalse(subject.requiresNodePayment(validQuery(COST_ANSWER, 0, target)));
        assertTrue(subject.requiresNodePayment(validQuery(ANSWER_ONLY, 0, target)));
    }

    @Test
    void getsValidity() {
        // given:
        final Response response =
                Response.newBuilder()
                        .setContractGetBytecodeResponse(
                                ContractGetBytecodeResponse.newBuilder()
                                        .setHeader(
                                                subject.answerOnlyHeader(
                                                        RESULT_SIZE_LIMIT_EXCEEDED)))
                        .build();

        // expect:
        assertEquals(RESULT_SIZE_LIMIT_EXCEEDED, subject.extractValidityFrom(response));
    }

    @Test
    void getsExpectedPayment() throws Throwable {
        // given:
        final Query query = validQuery(COST_ANSWER, fee, target);

        // expect:
        assertEquals(paymentTxn, subject.extractPaymentFrom(query).get().getSignedTxnWrapper());
    }

    @Test
    void getsTheBytecode() throws Throwable {
        // setup:
        final Query query = validQuery(ANSWER_ONLY, fee, target);

        given(view.bytecodeOf(EntityNum.fromLong(123))).willReturn(Optional.of(bytecode));

        // when:
        final Response response = subject.responseGiven(query, view, OK, fee);

        // then:
        assertTrue(response.hasContractGetBytecodeResponse());
        assertTrue(
                response.getContractGetBytecodeResponse().hasHeader(), "Missing response header!");
        assertEquals(
                OK,
                response.getContractGetBytecodeResponse()
                        .getHeader()
                        .getNodeTransactionPrecheckCode());
        assertEquals(
                ANSWER_ONLY,
                response.getContractGetBytecodeResponse().getHeader().getResponseType());
        assertEquals(fee, response.getContractGetBytecodeResponse().getHeader().getCost());
        // and:
        final var actual = response.getContractGetBytecodeResponse().getBytecode().toByteArray();
        assertTrue(Arrays.equals(bytecode, actual));
    }

    @Test
    void getsCostAnswerResponse() throws Throwable {
        // setup:
        final Query query = validQuery(COST_ANSWER, fee, target);

        // when:
        final Response response = subject.responseGiven(query, view, OK, fee);

        // then:
        assertTrue(response.hasContractGetBytecodeResponse());
        assertEquals(
                OK,
                response.getContractGetBytecodeResponse()
                        .getHeader()
                        .getNodeTransactionPrecheckCode());
        assertEquals(
                COST_ANSWER,
                response.getContractGetBytecodeResponse().getHeader().getResponseType());
        assertEquals(fee, response.getContractGetBytecodeResponse().getHeader().getCost());
    }

    @Test
    void getsInvalidResponse() throws Throwable {
        // setup:
        final Query query = validQuery(COST_ANSWER, fee, target);

        // when:
        final Response response = subject.responseGiven(query, view, CONTRACT_DELETED, fee);

        // then:
        assertTrue(response.hasContractGetBytecodeResponse());
        assertEquals(
                CONTRACT_DELETED,
                response.getContractGetBytecodeResponse()
                        .getHeader()
                        .getNodeTransactionPrecheckCode());
        assertEquals(
                COST_ANSWER,
                response.getContractGetBytecodeResponse().getHeader().getResponseType());
        assertEquals(fee, response.getContractGetBytecodeResponse().getHeader().getCost());
    }

    @Test
    void usesValidator() throws Throwable {
        // setup:
        final Query query = validQuery(COST_ANSWER, fee, target);

        given(optionValidator.queryableContractStatus(eq(EntityNum.fromLong(123)), any()))
                .willReturn(CONTRACT_DELETED);

        // when:
        final ResponseCodeEnum validity = subject.checkValidity(query, view);

        // then:
        assertEquals(CONTRACT_DELETED, validity);
    }

    private Query validQuery(final ResponseType type, final long payment, final String idLit)
            throws Throwable {
        this.paymentTxn = payerSponsoredTransfer(payer, COMPLEX_KEY_ACCOUNT_KT, node, payment);
        final QueryHeader.Builder header =
                QueryHeader.newBuilder().setPayment(this.paymentTxn).setResponseType(type);
        final ContractGetBytecodeQuery.Builder op =
                ContractGetBytecodeQuery.newBuilder()
                        .setHeader(header)
                        .setContractID(asContract(idLit));
        return Query.newBuilder().setContractGetBytecode(op).build();
    }
}
