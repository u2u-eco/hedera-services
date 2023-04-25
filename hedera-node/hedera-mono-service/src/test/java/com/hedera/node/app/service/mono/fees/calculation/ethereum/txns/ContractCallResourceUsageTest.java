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
package com.hedera.node.app.service.mono.fees.calculation.ethereum.txns;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

import com.hedera.node.app.hapi.utils.fee.SigValueObj;
import com.hedera.node.app.hapi.utils.fee.SmartContractFeeBuilder;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EthereumTransactionResourceUsageTest {
    private SigValueObj sigUsage;
    private SmartContractFeeBuilder usageEstimator;
    private EthereumTransactionResourceUsage subject;

    private TransactionBody nonContractCallTxn;
    private TransactionBody ethTxn;

    @BeforeEach
    void setup() throws Throwable {
        ethTxn = mock(TransactionBody.class);
        given(ethTxn.hasEthereumTransaction()).willReturn(true);

        nonContractCallTxn = mock(TransactionBody.class);
        given(nonContractCallTxn.hasContractCall()).willReturn(false);

        sigUsage = mock(SigValueObj.class);
        usageEstimator = mock(SmartContractFeeBuilder.class);

        subject = new EthereumTransactionResourceUsage(usageEstimator);
    }

    @Test
    void recognizesApplicability() {
        // expect:
        assertTrue(subject.applicableTo(ethTxn));
        assertFalse(subject.applicableTo(nonContractCallTxn));
    }

    @Test
    void delegatesToCorrectEstimate() throws Exception {
        // when:
        subject.usageGiven(ethTxn, sigUsage, null);

        // then:
        verify(usageEstimator).getEthereumTransactionFeeMatrices(ethTxn, sigUsage);
    }
}
