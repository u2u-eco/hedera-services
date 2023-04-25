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
package com.hedera.node.app.service.mono.fees;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusCreateTopic;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAssociateToAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;
import static java.time.Instant.ofEpochSecond;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.hapi.utils.throttles.DeterministicThrottle;
import com.hedera.node.app.service.mono.config.MockGlobalDynamicProps;
import com.hedera.node.app.service.mono.fees.calculation.EntityScaleFactors;
import com.hedera.node.app.service.mono.fees.congestion.TxnRateFeeMultiplierSource;
import com.hedera.node.app.service.mono.state.validation.UsageLimits;
import com.hedera.node.app.service.mono.throttling.FunctionalityThrottling;
import com.hedera.node.app.service.mono.utils.accessors.TxnAccessor;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, LogCaptureExtension.class})
class TxnRateFeeMultiplierSourceTest {
    private static final int ROUNDED_PERCENT_UTIL = 33;
    private static final long EXPECTED_MULTIPLIER = 7L;
    private static final EntityScaleFactors ENTITY_SCALE_FACTORS =
            EntityScaleFactors.from("DEFAULT(32,7:1)");
    private final Instant[] congestionStarts =
            new Instant[] {
                ofEpochSecond(1L), ofEpochSecond(2L), ofEpochSecond(3L),
            };
    private final Instant consensusNow = congestionStarts[2].plusSeconds(1L);

    @Mock private FunctionalityThrottling throttling;
    @Mock private TxnAccessor accessor;
    @Mock private UsageLimits usageLimits;

    private MockGlobalDynamicProps mockProps;

    @LoggingTarget private LogCaptor logCaptor;
    @LoggingSubject private TxnRateFeeMultiplierSource subject;

    @BeforeEach
    void setUp() {
        mockProps = new MockGlobalDynamicProps();
        subject = new TxnRateFeeMultiplierSource(mockProps, throttling, usageLimits);
    }

    @Test
    void makesDefensiveCopyOfCongestionStarts() {
        final var someInstants =
                new Instant[] {
                    Instant.ofEpochSecond(1_234_567L), Instant.ofEpochSecond(2_234_567L),
                };
        subject.resetCongestionLevelStarts(someInstants);

        final var equalNotSameInstants = subject.congestionLevelStarts();

        assertEquals(List.of(someInstants), List.of(equalNotSameInstants));
        assertNotSame(someInstants, equalNotSameInstants);
    }

    @Test
    void updatesCongestionStarts() {
        subject.resetCongestionLevelStarts(congestionStarts);

        Assertions.assertEquals(
                List.of(congestionStarts), List.of(subject.congestionLevelStarts()));
    }

    /* MockGlobalDynamicProps has 2 secs for minCongestionPeriod */
    @ParameterizedTest
    @CsvSource({
        "9, 1, 2, 3, 1, 10, 1, 10, 1, -1, -1, -1",
        "9, -1, -1, -1, 9, 10, 1, 10, 1, 9, -1, -1",
        "9, -1, -1, -1, 1, 10, 9, 10, 1, 9, -1, -1",
        "9, 3, 3, 3, 89, 100, 1, 10, 1, -1, -1, -1",
        "9, 1, -1, -1, 1, 10, 89, 100, 1, -1, -1, -1",
        "9, 1, 2, -1, 2, 2, 1, 10, 25, 1, 2, 9",
        "9, 3, 3, 3, 0, 2, 950, 1000, 25, 3, 3, -1",
        "9, -1, -1, -1, 9999, 10000, 950, 1000, 1, 9, 9, 9",
        "9, -1, -1, -1, 0, 1, 0, 390000, 1, -1, -1, -1",
        "4, -1, -1, -1, 100, 100, 1000, 1000, 1, 4, 4, 4",
        "5, 3, -1, -1, 100, 100, 1000, 1000, 10, 3, 5, 5",
        "5, 1, 3, 4, 100, 100, 1000, 1000, 25, 1, 3, 4",
    })
    void usesExpectedMultiplier(
            final long consensusSec,
            final long old10XLevelStart,
            final long old25XLevelStart,
            final long old100XLevelStart,
            final int firstUsed,
            final int firstTps,
            final int secondUsed,
            final int secondTps,
            final long expectedMultiplier,
            final long new10XLevelStart,
            final long new25XLevelStart,
            final long new100XLevelStart) {

        given(accessor.getFunction()).willReturn(CryptoTransfer);
        final var aThrottle = DeterministicThrottle.withTps(firstTps);
        final var bThrottle = DeterministicThrottle.withTps(secondTps);
        aThrottle.allow(firstUsed, Instant.now());
        bThrottle.allow(secondUsed, Instant.now());
        given(throttling.activeThrottlesFor(CryptoTransfer))
                .willReturn(List.of(aThrottle, bThrottle));

        subject.resetExpectations();
        subject.resetCongestionLevelStarts(
                instants(old10XLevelStart, old25XLevelStart, old100XLevelStart));
        subject.updateMultiplier(accessor, Instant.ofEpochSecond(consensusSec));
        final long actualMultiplier = subject.currentMultiplier(accessor);
        final var starts = subject.congestionLevelStarts();

        assertEquals(expectedMultiplier, actualMultiplier);
        assertNullOrEquals(starts[0], new10XLevelStart);
        assertNullOrEquals(starts[1], new25XLevelStart);
        assertNullOrEquals(starts[2], new100XLevelStart);
    }

    private void assertNullOrEquals(final Instant instant, final long expected) {
        if (expected == -1) {
            assertNull(instant);
        } else {
            assertEquals(expected, instant.getEpochSecond());
        }
    }

    @Test
    void usesAccountMultiplierAsExpected() {
        givenCreateSetup(CryptoCreate);
        given(usageLimits.roundedAccountPercentUtil()).willReturn(ROUNDED_PERCENT_UTIL);

        final var actual = subject.currentMultiplier(accessor);

        assertEquals(EXPECTED_MULTIPLIER, actual);
    }

    @Test
    void usesContractMultiplierAsExpected() {
        givenCreateSetup(ContractCreate);
        given(usageLimits.roundedContractPercentUtil()).willReturn(ROUNDED_PERCENT_UTIL);

        final var actual = subject.currentMultiplier(accessor);

        assertEquals(EXPECTED_MULTIPLIER, actual);
    }

    @Test
    void usesFileMultiplierAsExpected() {
        givenCreateSetup(FileCreate);
        given(usageLimits.roundedFilePercentUtil()).willReturn(ROUNDED_PERCENT_UTIL);

        final var actual = subject.currentMultiplier(accessor);

        assertEquals(EXPECTED_MULTIPLIER, actual);
    }

    @Test
    void usesNftMintAsExpected() {
        givenCreateSetup(TokenMint);
        given(accessor.mintsWithMetadata()).willReturn(true);
        given(usageLimits.roundedNftPercentUtil()).willReturn(ROUNDED_PERCENT_UTIL);

        final var actual = subject.currentMultiplier(accessor);

        assertEquals(EXPECTED_MULTIPLIER, actual);
    }

    @Test
    void usesFungibleMintAsExpected() {
        givenCreateSetup(TokenMint);

        final var actual = subject.currentMultiplier(accessor);

        assertEquals(1L, actual);
    }

    @Test
    void usesScheduleAsExpected() {
        givenCreateSetup(ScheduleCreate);
        given(usageLimits.roundedSchedulePercentUtil()).willReturn(ROUNDED_PERCENT_UTIL);

        final var actual = subject.currentMultiplier(accessor);

        assertEquals(EXPECTED_MULTIPLIER, actual);
    }

    @Test
    void usesTokenAsExpected() {
        givenCreateSetup(TokenCreate);
        given(usageLimits.roundedTokenPercentUtil()).willReturn(ROUNDED_PERCENT_UTIL);

        final var actual = subject.currentMultiplier(accessor);

        assertEquals(EXPECTED_MULTIPLIER, actual);
    }

    @Test
    void usesTokenAssociateAsExpected() {
        givenCreateSetup(TokenAssociateToAccount);
        given(usageLimits.roundedTokenRelPercentUtil()).willReturn(ROUNDED_PERCENT_UTIL);

        final var actual = subject.currentMultiplier(accessor);

        assertEquals(EXPECTED_MULTIPLIER, actual);
    }

    @Test
    void usesTopicAsExpected() {
        givenCreateSetup(ConsensusCreateTopic);
        given(usageLimits.roundedTopicPercentUtil()).willReturn(ROUNDED_PERCENT_UTIL);

        final var actual = subject.currentMultiplier(accessor);

        assertEquals(EXPECTED_MULTIPLIER, actual);
    }

    private void givenCreateSetup(final HederaFunctionality function) {
        final var aThrottle = DeterministicThrottle.withTps(100);
        given(throttling.activeThrottlesFor(CryptoTransfer)).willReturn(List.of(aThrottle));
        given(accessor.getFunction()).willReturn(function);
        mockProps.setScaleFactors(ENTITY_SCALE_FACTORS);

        subject.resetExpectations();
        subject.updateMultiplier(accessor, consensusNow);
    }

    @Test
    void adaptsToChangedProperties() {
        final var aThrottle = DeterministicThrottle.withTps(100);
        aThrottle.allow(96, Instant.now());
        given(throttling.activeThrottlesFor(CryptoTransfer)).willReturn(List.of(aThrottle));
        given(accessor.getFunction()).willReturn(CryptoTransfer);

        subject.resetExpectations();
        subject.resetCongestionLevelStarts(instants(1L, 1L, 1L));
        subject.updateMultiplier(accessor, consensusNow);

        assertEquals(25, subject.currentMultiplier(accessor));

        mockProps.useDifferentMultipliers();
        subject.updateMultiplier(accessor, consensusNow);

        assertEquals(26, subject.currentMultiplier(accessor));
    }

    @Test
    void doesntThrowOnMissingThrottles() {
        given(accessor.getFunction()).willReturn(CryptoTransfer);
        given(throttling.activeThrottlesFor(CryptoTransfer)).willReturn(Collections.emptyList());

        assertDoesNotThrow(subject::resetExpectations);
        assertEquals(1L, subject.currentMultiplier(accessor));
    }

    @Test
    void logsCongestionPricingStart() {
        final var desired = "Congestion pricing beginning w/ 10x multiplier";

        subject.getDelegate().logMultiplierChange(1L, 10L);

        assertThat(logCaptor.infoLogs(), contains(desired));
    }

    @Test
    void logsCongestionPricingIncrease() {
        final var desired = "Congestion pricing continuing, reached 100x multiplier";

        subject.getDelegate().logMultiplierChange(10L, 100L);

        assertThat(logCaptor.infoLogs(), contains(desired));
    }

    @Test
    void logsCongestionPricingEnd() {
        final var desired = "Congestion pricing ended";

        subject.getDelegate().logMultiplierChange(10L, 1L);

        assertThat(logCaptor.infoLogs(), contains(desired));
    }

    @Test
    void silentOnCongestionPricingDrop() {
        subject.getDelegate().logMultiplierChange(100L, 10L);

        assertTrue(logCaptor.infoLogs().isEmpty());
    }

    @Test
    void toStringIndicatesUnavailableConfig() {
        final var desired =
                "The new cutoffs for CryptoTransfer throughput congestion pricing are :  <N/A>";

        subject.getDelegate().logReadableCutoffs();

        assertThat(logCaptor.infoLogs(), contains(desired));
    }

    @Test
    void toStringHasExpectedCutoffsMsg() {
        final var desired =
                "The new cutoffs for CryptoTransfer throughput congestion pricing are : \n"
                        + "  (A) When logical TPS exceeds:\n"
                        + "    900.00 TPS, multiplier is 10x\n"
                        + "    950.00 TPS, multiplier is 25x\n"
                        + "    990.00 TPS, multiplier is 100x\n"
                        + "  (B) When logical TPS exceeds:\n"
                        + "    9.00 TPS, multiplier is 10x\n"
                        + "    9.50 TPS, multiplier is 25x\n"
                        + "    9.90 TPS, multiplier is 100x";
        final var aThrottle = DeterministicThrottle.withTpsNamed(1000, "A");
        final var bThrottle = DeterministicThrottle.withTpsNamed(10, "B");
        given(throttling.activeThrottlesFor(CryptoTransfer))
                .willReturn(List.of(aThrottle, bThrottle));

        subject.resetExpectations();

        assertThat(logCaptor.infoLogs(), contains(desired));
    }

    @Test
    void disabledWhenCongestionExempt() {
        final var aThrottle = DeterministicThrottle.withTps(100);
        aThrottle.allow(96, Instant.now());
        given(throttling.activeThrottlesFor(CryptoTransfer)).willReturn(List.of(aThrottle));
        given(accessor.getFunction()).willReturn(CryptoTransfer);

        subject.resetExpectations();
        subject.resetCongestionLevelStarts(instants(1L, 1L, 1L));
        subject.updateMultiplier(accessor, consensusNow);

        assertEquals(25, subject.currentMultiplier(accessor));

        given(accessor.congestionExempt()).willReturn(true);

        assertEquals(1L, subject.currentMultiplier(accessor));

        subject.updateMultiplier(accessor, consensusNow);

        given(accessor.congestionExempt()).willReturn(false);

        assertEquals(25, subject.currentMultiplier(accessor));
    }

    private Instant[] instants(final long a, final long b, final long c) {
        final var ans = new Instant[3];
        ans[0] = (a == -1) ? null : Instant.ofEpochSecond(a);
        ans[1] = (b == -1) ? null : Instant.ofEpochSecond(b);
        ans[2] = (c == -1) ? null : Instant.ofEpochSecond(c);
        return ans;
    }
}
