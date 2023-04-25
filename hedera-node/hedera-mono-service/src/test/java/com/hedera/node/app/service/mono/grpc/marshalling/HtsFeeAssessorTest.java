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
package com.hedera.node.app.service.mono.grpc.marshalling;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.mono.ledger.BalanceChange;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.FcCustomFee;
import com.hedera.node.app.service.mono.store.models.Id;
import com.hederahashgraph.api.proto.java.AccountID;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HtsFeeAssessorTest {
    private final List<AssessedCustomFeeWrapper> accumulator = new ArrayList<>();

    @Mock private BalanceChange collectorChange;
    @Mock private BalanceChangeManager balanceChangeManager;

    private final HtsFeeAssessor subject = new HtsFeeAssessor();

    @Test
    void updatesExistingChangesIfPresent() {
        // setup:
        final var expectedAssess =
                new AssessedCustomFeeWrapper(
                        htsFeeCollector, feeDenom, amountOfHtsFee, effPayerNums);
        // and:
        final var expectedPayerChange =
                BalanceChange.tokenCustomFeeAdjust(payer, denom, -amountOfHtsFee);
        expectedPayerChange.setCodeForInsufficientBalance(
                INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE);

        given(balanceChangeManager.changeFor(feeCollector, denom)).willReturn(collectorChange);

        // when:
        subject.assess(payer, nonDenomFeeMeta, htsFee, balanceChangeManager, accumulator, false);

        // then:
        verify(collectorChange).aggregateUnits(+amountOfHtsFee);
        verify(balanceChangeManager).includeChange(expectedPayerChange);
        // and:
        assertEquals(1, accumulator.size());
        assertEquals(expectedAssess, accumulator.get(0));
    }

    @Test
    void addsNewChangesIfNotPresent() {
        // setup:
        final var expectedAssess =
                new AssessedCustomFeeWrapper(
                        htsFeeCollector, feeDenom, amountOfHtsFee, effPayerNums);

        // given:
        final var expectedPayerChange =
                BalanceChange.tokenCustomFeeAdjust(payer, denom, -amountOfHtsFee);
        expectedPayerChange.setCodeForInsufficientBalance(
                INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE);

        // when:
        subject.assess(payer, nonDenomFeeMeta, htsFee, balanceChangeManager, accumulator, false);

        // then:
        verify(balanceChangeManager).includeChange(expectedPayerChange);
        verify(balanceChangeManager)
                .includeChange(
                        BalanceChange.tokenCustomFeeAdjust(feeCollector, denom, +amountOfHtsFee));
        // and:
        assertEquals(1, accumulator.size());
        assertEquals(expectedAssess, accumulator.get(0));
    }

    @Test
    void addsNewChangesWithFallbackFeeIfNotPresent() {
        // setup:
        final var expectedAssess =
                new AssessedCustomFeeWrapper(
                        htsFeeCollector, feeDenom, amountOfHtsFee, effPayerNums);

        // given:
        final var expectedPayerChange =
                BalanceChange.tokenCustomFeeAdjust(payer, denom, -amountOfHtsFee);
        expectedPayerChange.setIncludesFallbackFee();
        expectedPayerChange.setCodeForInsufficientBalance(
                INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE);

        // when:
        subject.assess(payer, nonDenomFeeMeta, htsFee, balanceChangeManager, accumulator, true);

        // then:
        verify(balanceChangeManager).includeChange(expectedPayerChange);
        verify(balanceChangeManager)
                .includeChange(
                        BalanceChange.tokenCustomFeeAdjust(feeCollector, denom, +amountOfHtsFee));
        // and:
        assertEquals(1, accumulator.size());
        assertEquals(expectedAssess, accumulator.get(0));
    }

    @Test
    void addsExemptNewChangesForSelfDenominatedFee() {
        // setup:
        final var expectedAssess =
                new AssessedCustomFeeWrapper(
                        htsFeeCollector, feeDenom, amountOfHtsFee, effPayerNums);

        // given:
        final var expectedPayerChange =
                BalanceChange.tokenCustomFeeAdjust(payer, denom, -amountOfHtsFee);
        expectedPayerChange.setExemptFromCustomFees(true);
        expectedPayerChange.setCodeForInsufficientBalance(
                INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE);

        // when:
        subject.assess(payer, denomFeeMeta, htsFee, balanceChangeManager, accumulator, false);

        // then:
        verify(balanceChangeManager).includeChange(expectedPayerChange);
        verify(balanceChangeManager)
                .includeChange(
                        BalanceChange.tokenCustomFeeAdjust(feeCollector, denom, +amountOfHtsFee));
        // and:
        assertEquals(1, accumulator.size());
        assertEquals(expectedAssess, accumulator.get(0));
    }

    private final long amountOfHtsFee = 100_000L;
    private final Id payer = new Id(0, 0, 2);
    private final Id denom = new Id(6, 6, 6);
    private final Id treasury = new Id(7, 7, 7);
    private final Id nonSelfDenominatedChargingToken = new Id(7, 7, 7);
    private final Id feeCollector = new Id(1, 2, 3);
    private final EntityId feeDenom = new EntityId(6, 6, 6);
    private final EntityId htsFeeCollector = feeCollector.asEntityId();
    private final FcCustomFee htsFee =
            FcCustomFee.fixedFee(amountOfHtsFee, feeDenom, htsFeeCollector, false);
    private final CustomFeeMeta denomFeeMeta = new CustomFeeMeta(denom, treasury, List.of());
    private final CustomFeeMeta nonDenomFeeMeta =
            new CustomFeeMeta(nonSelfDenominatedChargingToken, treasury, List.of());
    private final AccountID[] effPayerNums =
            new AccountID[] {AccountID.newBuilder().setAccountNum(2L).build()};
}
