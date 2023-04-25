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
package com.hedera.node.app.service.mono.fees.calculation.token.txns;

import static com.hedera.node.app.hapi.fees.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.Mockito.mockStatic;

import com.hedera.node.app.hapi.fees.usage.EstimatorFactory;
import com.hedera.node.app.hapi.fees.usage.SigUsage;
import com.hedera.node.app.hapi.fees.usage.TxnUsageEstimator;
import com.hedera.node.app.hapi.fees.usage.token.TokenDissociateUsage;
import com.hedera.node.app.hapi.utils.fee.SigValueObj;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.migration.AccountStorageAdapter;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TokenDissociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TokenDissociateResourceUsageTest {
    private TokenDissociateResourceUsage subject;

    AccountID target = IdUtils.asAccount("1.2.3");
    MerkleAccount account;
    MerkleMap<EntityNum, MerkleAccount> accounts;

    private TransactionBody nonTokenDissociateTxn;
    private TransactionBody tokenDissociateTxn;

    StateView view;
    int numSigs = 10, sigsSize = 100, numPayerKeys = 3;
    SigValueObj obj = new SigValueObj(numSigs, numPayerKeys, sigsSize);
    SigUsage sigUsage = new SigUsage(numSigs, sigsSize, numPayerKeys);
    FeeData expected;

    TokenDissociateUsage usage;

    long expiry = 1_234_567L;
    TokenID firstToken = IdUtils.asToken("0.0.123");
    TokenID secondToken = IdUtils.asToken("0.0.124");
    TxnUsageEstimator txnUsageEstimator;

    @BeforeEach
    void setup() throws Throwable {
        expected = mock(FeeData.class);
        account = mock(MerkleAccount.class);
        given(account.getExpiry()).willReturn(expiry);
        accounts = mock(MerkleMap.class);
        given(accounts.get(EntityNum.fromAccountId(target))).willReturn(account);
        view = mock(StateView.class);
        given(view.accounts()).willReturn(AccountStorageAdapter.fromInMemory(accounts));

        tokenDissociateTxn = mock(TransactionBody.class);
        given(tokenDissociateTxn.hasTokenDissociate()).willReturn(true);
        given(tokenDissociateTxn.getTokenDissociate())
                .willReturn(
                        TokenDissociateTransactionBody.newBuilder()
                                .setAccount(IdUtils.asAccount("1.2.3"))
                                .addTokens(firstToken)
                                .addTokens(secondToken)
                                .build());

        nonTokenDissociateTxn = mock(TransactionBody.class);
        given(nonTokenDissociateTxn.hasTokenAssociate()).willReturn(false);

        usage = mock(TokenDissociateUsage.class);
        given(usage.get()).willReturn(expected);

        txnUsageEstimator = mock(TxnUsageEstimator.class);
        final EstimatorFactory estimatorFactory = mock(EstimatorFactory.class);
        given(estimatorFactory.get(sigUsage, tokenDissociateTxn, ESTIMATOR_UTILS))
                .willReturn(txnUsageEstimator);
        subject = new TokenDissociateResourceUsage(estimatorFactory);
    }

    @Test
    void recognizesApplicability() {
        // expect:
        assertTrue(subject.applicableTo(tokenDissociateTxn));
        assertFalse(subject.applicableTo(nonTokenDissociateTxn));
    }

    @Test
    void delegatesToCorrectEstimate() throws Exception {
        final var mockStatic = mockStatic(TokenDissociateUsage.class);
        mockStatic
                .when(() -> TokenDissociateUsage.newEstimate(tokenDissociateTxn, txnUsageEstimator))
                .thenReturn(usage);

        // expect:
        assertEquals(expected, subject.usageGiven(tokenDissociateTxn, obj, view));

        mockStatic.close();
    }

    @Test
    void returnsDefaultIfInfoMissing() throws Exception {
        final var mockStatic = mockStatic(TokenDissociateUsage.class);
        mockStatic
                .when(() -> TokenDissociateUsage.newEstimate(tokenDissociateTxn, txnUsageEstimator))
                .thenReturn(usage);

        given(accounts.get(EntityNum.fromAccountId(target))).willReturn(null);

        // expect:
        assertEquals(
                FeeData.getDefaultInstance(), subject.usageGiven(tokenDissociateTxn, obj, view));

        mockStatic.close();
    }
}
