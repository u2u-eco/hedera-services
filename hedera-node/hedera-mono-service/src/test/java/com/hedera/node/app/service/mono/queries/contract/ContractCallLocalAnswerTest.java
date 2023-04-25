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
package com.hedera.node.app.service.mono.queries.contract;

import static com.hedera.test.factories.scenarios.TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT;
import static com.hedera.test.utils.TxnUtils.payerSponsoredTransfer;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_NEGATIVE_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_GAS_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.evm.contracts.execution.BlockMetaSource;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.context.properties.NodeLocalProperties;
import com.hedera.node.app.service.mono.contracts.execution.CallLocalEvmTxProcessor;
import com.hedera.node.app.service.mono.contracts.execution.StaticBlockMetaProvider;
import com.hedera.node.app.service.mono.contracts.execution.TransactionProcessingResult;
import com.hedera.node.app.service.mono.ledger.accounts.AliasManager;
import com.hedera.node.app.service.mono.ledger.ids.EntityIdSource;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.migration.AccountStorageAdapter;
import com.hedera.node.app.service.mono.store.AccountStore;
import com.hedera.node.app.service.mono.store.contracts.EntityAccess;
import com.hedera.node.app.service.mono.store.models.Account;
import com.hedera.node.app.service.mono.store.models.Id;
import com.hedera.node.app.service.mono.txns.validation.OptionValidator;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.ContractCallLocalQuery;
import com.hederahashgraph.api.proto.java.ContractCallLocalResponse;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseHeader;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Transaction;
import com.swirlds.merkle.map.MerkleMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContractCallLocalAnswerTest {
    private final long fee = 1_234L;
    private final ContractID target = IdUtils.asContract("0.0.75231");
    private final ByteString result = ByteString.copyFrom("Searching for images".getBytes());

    private long gas = 123;
    private Transaction paymentTxn;

    @Mock private StateView view;
    @Mock private AccountStore accountStore;
    @Mock private EntityIdSource ids;
    @Mock private OptionValidator validator;
    @Mock private EntityAccess entityAccess;
    @Mock private GlobalDynamicProperties dynamicProperties;
    @Mock private CallLocalEvmTxProcessor evmTxProcessor;
    @Mock private MerkleMap<EntityNum, MerkleAccount> contracts;
    @Mock private NodeLocalProperties nodeLocalProperties;
    @Mock private AliasManager aliasManager;
    @Mock private StaticBlockMetaProvider blockMetaProvider;
    @Mock private BlockMetaSource blockMetaSource;

    private ContractCallLocalAnswer subject;

    @BeforeEach
    void setup() {
        subject =
                new ContractCallLocalAnswer(
                        ids,
                        aliasManager,
                        accountStore,
                        validator,
                        entityAccess,
                        dynamicProperties,
                        nodeLocalProperties,
                        () -> evmTxProcessor,
                        blockMetaProvider);
    }

    @Test
    void acceptsToken() throws Throwable {
        given(entityAccess.isTokenAccount(any())).willReturn(true);

        // given:
        final Query query = validQuery(COST_ANSWER, fee);
        given(dynamicProperties.maxGasPerSec()).willReturn(gas);

        // expect:
        assertEquals(OK, subject.checkValidity(query, view));
    }

    @Test
    void rejectsInvalidCid() throws Throwable {
        given(view.contracts()).willReturn(AccountStorageAdapter.fromInMemory(contracts));

        // given:
        final Query query = validQuery(COST_ANSWER, fee);
        given(dynamicProperties.maxGasPerSec()).willReturn(gas);

        // and:
        given(validator.queryableContractStatus(eq(EntityNum.fromContractId(target)), any()))
                .willReturn(CONTRACT_DELETED);

        // expect:
        assertEquals(CONTRACT_DELETED, subject.checkValidity(query, view));
    }

    @Test
    void rejectsNegativeGas() throws Throwable {
        gas = -1;

        // given:
        final Query query = validQuery(COST_ANSWER, fee);

        // expect:
        assertEquals(CONTRACT_NEGATIVE_GAS, subject.checkValidity(query, view));
    }

    @Test
    void rejectsGasLimitOverMaxGas() throws Throwable {

        // given:
        given(dynamicProperties.maxGasPerSec()).willReturn(gas - 1);
        final Query query = validQuery(COST_ANSWER, fee);

        // expect:
        assertEquals(MAX_GAS_LIMIT_EXCEEDED, subject.checkValidity(query, view));
    }

    @Test
    void noCopyPasteErrors() throws Throwable {
        // given:
        final Query query = validQuery(COST_ANSWER, fee);

        // when:
        final Response response = subject.responseGiven(query, view, INSUFFICIENT_TX_FEE, fee);

        // then:
        assertEquals(HederaFunctionality.ContractCallLocal, subject.canonicalFunction());
        assertEquals(paymentTxn, subject.extractPaymentFrom(query).get().getSignedTxnWrapper());
        assertTrue(subject.needsAnswerOnlyCost(query));
        assertFalse(subject.requiresNodePayment(query));
        assertEquals(INSUFFICIENT_TX_FEE, subject.extractValidityFrom(response));
    }

    @Test
    void getsInvalidResponse() throws Throwable {
        // setup:
        final Query query = validQuery(COST_ANSWER, fee);

        // when:
        final Response response = subject.responseGiven(query, view, FAIL_INVALID, fee);

        // then:
        assertTrue(response.hasContractCallLocal());
        assertEquals(
                FAIL_INVALID,
                response.getContractCallLocal().getHeader().getNodeTransactionPrecheckCode());
        assertEquals(COST_ANSWER, response.getContractCallLocal().getHeader().getResponseType());
        assertEquals(fee, response.getContractCallLocal().getHeader().getCost());
    }

    @Test
    void getsCostAnswerResponse() throws Throwable {
        // setup:
        final Query query = validQuery(COST_ANSWER, fee);

        // when:
        final Response response = subject.responseGiven(query, view, OK, fee);

        // then:
        assertTrue(response.hasContractCallLocal());
        final ContractCallLocalResponse opResponse = response.getContractCallLocal();
        assertEquals(OK, opResponse.getHeader().getNodeTransactionPrecheckCode());
        assertEquals(COST_ANSWER, opResponse.getHeader().getResponseType());
        assertEquals(fee, opResponse.getHeader().getCost());
    }

    @Test
    void failsOnAvailCtxWithNoCachedResponse() throws Throwable {
        // setup:
        final Query sensibleQuery = validQuery(ANSWER_ONLY, 5L);
        final Map<String, Object> queryCtx = new HashMap<>();

        final Response response = subject.responseGiven(sensibleQuery, view, OK, 0L, queryCtx);

        final var opResponse = response.getContractCallLocal();
        assertTrue(opResponse.hasHeader(), "Missing response header!");
        assertEquals(FAIL_INVALID, opResponse.getHeader().getNodeTransactionPrecheckCode());
    }

    @Test
    void usesCtxWhenAvail() throws Throwable {
        // setup:
        final Query sensibleQuery = validQuery(ANSWER_ONLY, 5L);
        final Map<String, Object> queryCtx = new HashMap<>();
        final var cachedResponse = response(CONTRACT_EXECUTION_EXCEPTION);
        queryCtx.put(ContractCallLocalAnswer.CONTRACT_CALL_LOCAL_CTX_KEY, cachedResponse);

        // when:
        final Response response = subject.responseGiven(sensibleQuery, view, OK, 0L, queryCtx);

        // then:
        final var opResponse = response.getContractCallLocal();
        assertTrue(opResponse.hasHeader(), "Missing response header!");
        assertEquals(
                CONTRACT_EXECUTION_EXCEPTION,
                opResponse.getHeader().getNodeTransactionPrecheckCode());
        assertEquals(result, opResponse.getFunctionResult().getContractCallResult());
        assertEquals(target, opResponse.getFunctionResult().getContractID());
        verify(accountStore, never()).loadAccount(any());
    }

    @Test
    void getsCallResponseWhenNoBlockMetaAvailable() throws Throwable {
        // setup:
        final Query sensibleQuery = validQuery(ANSWER_ONLY, 5L);

        final var transactionProcessingResult =
                TransactionProcessingResult.failed(
                        0,
                        0,
                        1,
                        Optional.empty(),
                        Optional.empty(),
                        new TreeMap<>(),
                        new ArrayList<>());

        final Response response = subject.responseGiven(sensibleQuery, view, OK, 0L);

        // then:
        final var opResponse = response.getContractCallLocal();
        assertTrue(opResponse.hasHeader(), "Missing response header");
        assertEquals(BUSY, opResponse.getHeader().getNodeTransactionPrecheckCode());
    }

    @Test
    void getsCallResponseWhenNoCtx() throws Throwable {
        // setup:
        final Query sensibleQuery = validQuery(ANSWER_ONLY, 5L);

        final var transactionProcessingResult =
                TransactionProcessingResult.failed(
                        0,
                        0,
                        1,
                        Optional.empty(),
                        Optional.empty(),
                        new TreeMap<>(),
                        new ArrayList<>());

        given(accountStore.loadAccount(any())).willReturn(new Account(Id.fromGrpcContract(target)));
        given(accountStore.loadContract(any()))
                .willReturn(new Account(Id.fromGrpcContract(target)));
        given(evmTxProcessor.execute(any(), any(), anyLong(), anyLong(), any()))
                .willReturn(transactionProcessingResult);
        given(blockMetaProvider.getSource()).willReturn(Optional.of(blockMetaSource));

        final Response response = subject.responseGiven(sensibleQuery, view, OK, 0L);

        // then:
        final var opResponse = response.getContractCallLocal();
        assertTrue(opResponse.hasHeader(), "Missing response header!");
        assertEquals(
                CONTRACT_EXECUTION_EXCEPTION,
                opResponse.getHeader().getNodeTransactionPrecheckCode());
        assertEquals(target, opResponse.getFunctionResult().getContractID());
    }

    @Test
    void translatesFailWhenNoCtx() throws Throwable {
        // setup:
        final Query sensibleQuery = validQuery(ANSWER_ONLY, 5L);
        given(blockMetaProvider.getSource()).willReturn(Optional.of(blockMetaSource));

        // when:
        final Response response = subject.responseGiven(sensibleQuery, view, OK, 0L);

        // then:
        final var opResponse = response.getContractCallLocal();
        assertTrue(opResponse.hasHeader(), "Missing response header!");
        assertEquals(FAIL_INVALID, opResponse.getHeader().getNodeTransactionPrecheckCode());
    }

    @Test
    void respectsMetaValidity() throws Throwable {
        // given:
        final Query sensibleQuery = validQuery(ANSWER_ONLY, 5L);

        // when:
        final Response response =
                subject.responseGiven(sensibleQuery, view, INVALID_TRANSACTION, 0L);

        // then:
        final var opResponse = response.getContractCallLocal();
        assertEquals(INVALID_TRANSACTION, opResponse.getHeader().getNodeTransactionPrecheckCode());
    }

    private Query validQuery(final ResponseType type, final long payment) throws Throwable {
        final var node = "0.0.3";
        final var payer = "0.0.12345";
        this.paymentTxn = payerSponsoredTransfer(payer, COMPLEX_KEY_ACCOUNT_KT, node, payment);

        final QueryHeader.Builder header =
                QueryHeader.newBuilder().setPayment(this.paymentTxn).setResponseType(type);
        final ContractCallLocalQuery.Builder op =
                ContractCallLocalQuery.newBuilder()
                        .setHeader(header)
                        .setContractID(target)
                        .setGas(gas);
        return Query.newBuilder().setContractCallLocal(op).build();
    }

    private ContractCallLocalResponse response(final ResponseCodeEnum status) {
        return ContractCallLocalResponse.newBuilder()
                .setHeader(ResponseHeader.newBuilder().setNodeTransactionPrecheckCode(status))
                .setFunctionResult(
                        ContractFunctionResult.newBuilder().setContractCallResult(result))
                .build();
    }
}
