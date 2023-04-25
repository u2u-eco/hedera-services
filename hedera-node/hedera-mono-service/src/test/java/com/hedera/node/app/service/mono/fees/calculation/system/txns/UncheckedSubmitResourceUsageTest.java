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
package com.hedera.node.app.service.mono.fees.calculation.system.txns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.hapi.utils.exception.InvalidTxBodyException;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UncheckedSubmitResourceUsageTest {
    private UncheckedSubmitResourceUsage subject;

    @BeforeEach
    void setup() {
        subject = new UncheckedSubmitResourceUsage();
    }

    @Test
    void recognizesApplicability() {
        final var txn = mock(TransactionBody.class);
        given(txn.hasUncheckedSubmit()).willReturn(true, false);

        assertTrue(subject.applicableTo(txn));
        assertFalse(subject.applicableTo(txn));
        verify(txn, times(2)).hasUncheckedSubmit();
    }

    @Test
    void delegatesToCorrectEstimate() throws InvalidTxBodyException {
        assertEquals(FeeData.getDefaultInstance(), subject.usageGiven(null, null, null));
    }
}
