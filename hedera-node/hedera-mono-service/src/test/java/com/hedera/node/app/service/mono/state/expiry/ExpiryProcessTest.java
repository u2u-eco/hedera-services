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
package com.hedera.node.app.service.mono.state.expiry;

import static com.hedera.node.app.service.mono.state.expiry.classification.ClassificationResult.*;
import static com.hedera.node.app.service.mono.state.tasks.SystemTaskResult.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.hedera.node.app.service.mono.config.MockGlobalDynamicProps;
import com.hedera.node.app.service.mono.context.SideEffectsTracker;
import com.hedera.node.app.service.mono.fees.FeeCalculator;
import com.hedera.node.app.service.mono.fees.calculation.RenewAssessment;
import com.hedera.node.app.service.mono.fees.charging.FeeDistribution;
import com.hedera.node.app.service.mono.fees.charging.NonHapiFeeCharging;
import com.hedera.node.app.service.mono.ledger.TransactionalLedger;
import com.hedera.node.app.service.mono.ledger.properties.AccountProperty;
import com.hedera.node.app.service.mono.records.ConsensusTimeTracker;
import com.hedera.node.app.service.mono.state.expiry.classification.ClassificationWork;
import com.hedera.node.app.service.mono.state.expiry.removal.AccountGC;
import com.hedera.node.app.service.mono.state.expiry.removal.ContractGC;
import com.hedera.node.app.service.mono.state.expiry.removal.CryptoGcOutcome;
import com.hedera.node.app.service.mono.state.expiry.removal.FungibleTreasuryReturns;
import com.hedera.node.app.service.mono.state.expiry.removal.NonFungibleTreasuryReturns;
import com.hedera.node.app.service.mono.state.expiry.removal.RemovalHelper;
import com.hedera.node.app.service.mono.state.expiry.removal.RemovalWork;
import com.hedera.node.app.service.mono.state.expiry.renewal.RenewalHelper;
import com.hedera.node.app.service.mono.state.expiry.renewal.RenewalWork;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.stats.ExpiryStats;
import com.hedera.node.app.service.mono.throttling.ExpiryThrottle;
import com.hedera.node.app.service.mono.throttling.MapAccessType;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExpiryProcessTest {
    private final Instant now = Instant.ofEpochSecond(1_234_567L);
    private final long requestedRenewalPeriod = 3601L;
    private final long nonZeroBalance = 2L;
    private final long fee = 1L;
    private final long actualRenewalPeriod = 3600L;
    private final long nonExpiredAccountNum = 1002L;
    private final long fundedExpiredContractNum = 1004L;
    private final MerkleAccount mockAccount =
            MerkleAccountFactory.newAccount()
                    .autoRenewPeriod(requestedRenewalPeriod)
                    .balance(nonZeroBalance)
                    .expirationTime(now.getEpochSecond() - 1)
                    .get();
    private final MerkleAccount mockContract =
            MerkleAccountFactory.newContract()
                    .autoRenewPeriod(requestedRenewalPeriod)
                    .balance(nonZeroBalance)
                    .expirationTime(now.getEpochSecond() - 1)
                    .get();
    private final CryptoGcOutcome finishedReturns =
            new CryptoGcOutcome(
                    FungibleTreasuryReturns.FINISHED_NOOP_FUNGIBLE_RETURNS,
                    NonFungibleTreasuryReturns.FINISHED_NOOP_NON_FUNGIBLE_RETURNS,
                    true);

    private final CryptoGcOutcome partiallyFinishedReturns =
            new CryptoGcOutcome(
                    FungibleTreasuryReturns.UNFINISHED_NOOP_FUNGIBLE_RETURNS,
                    new NonFungibleTreasuryReturns(
                            List.of(EntityId.fromIdentityCode(1234)), List.of(), false),
                    false);

    @Mock private FeeCalculator fees;
    @Mock private ClassificationWork classifier;
    @Mock private AccountGC accountGC;
    @Mock private ContractGC contractGC;
    @Mock private ExpiryRecordsHelper recordsHelper;
    @Mock private ExpiryThrottle expiryThrottle;
    @Mock private ExpiryStats expiryStats;
    @Mock private TransactionalLedger<AccountID, AccountProperty, HederaAccount> accountsLedger;
    @Mock private FeeDistribution feeDistribution;
    @Mock private SideEffectsTracker sideEffectsTracker;
    @Mock private ConsensusTimeTracker consensusTimeTracker;
    private MockGlobalDynamicProps dynamicProperties = new MockGlobalDynamicProps();
    private RenewalWork renewalWork;
    private RemovalWork removalWork;
    private NonHapiFeeCharging nonHapiFeeCharging;

    private ExpiryProcess subject;

    @BeforeEach
    void setUp() {
        setUpPreRequisites();
        subject = new ExpiryProcess(classifier, renewalWork, removalWork, consensusTimeTracker);
        given(consensusTimeTracker.hasMoreStandaloneRecordTime()).willReturn(true);
    }

    private void setUpPreRequisites() {
        nonHapiFeeCharging = new NonHapiFeeCharging(feeDistribution);
        renewalWork =
                new RenewalHelper(
                        expiryStats,
                        expiryThrottle,
                        classifier,
                        dynamicProperties,
                        fees,
                        recordsHelper,
                        nonHapiFeeCharging,
                        accountsLedger,
                        sideEffectsTracker);
        removalWork =
                new RemovalHelper(
                        expiryStats,
                        classifier,
                        dynamicProperties,
                        contractGC,
                        accountGC,
                        recordsHelper,
                        expiryThrottle);
    }

    @Test
    void needsNewContextWithNoStandaloneTime() {
        given(consensusTimeTracker.hasMoreStandaloneRecordTime()).willReturn(false);

        var result = subject.process(nonExpiredAccountNum, now);

        assertEquals(NEEDS_DIFFERENT_CONTEXT, result);
    }

    @Test
    void doesNothingOnNonExpiredAccount() {
        given(classifier.classify(EntityNum.fromLong(nonExpiredAccountNum), now)).willReturn(OTHER);

        var result = subject.process(nonExpiredAccountNum, now);

        assertEquals(NOTHING_TO_DO, result);
        verifyNoMoreInteractions(classifier);
    }

    @Test
    void noCapacityNow() {
        given(classifier.classify(EntityNum.fromLong(nonExpiredAccountNum), now))
                .willReturn(COME_BACK_LATER);

        var result = subject.process(nonExpiredAccountNum, now);

        assertEquals(NO_CAPACITY_LEFT, result);
        verifyNoMoreInteractions(classifier);
    }

    @Test
    void onlyWarnsIfNotInCycle() {
        given(classifier.classify(EntityNum.fromLong(nonExpiredAccountNum), now))
                .willReturn(COME_BACK_LATER);

        assertDoesNotThrow(() -> subject.process(nonExpiredAccountNum, now));
    }

    @Test
    void doesNothingForTreasuryWithTokenStillLive() {
        given(classifier.classify(EntityNum.fromLong(nonExpiredAccountNum), now))
                .willReturn(DETACHED_TREASURY_GRACE_PERIOD_OVER_BEFORE_TOKEN);

        final var result = subject.process(nonExpiredAccountNum, now);

        assertEquals(NOTHING_TO_DO, result);
        verifyNoMoreInteractions(classifier);
    }

    @Test
    void ignoresExpiredBrokeAccountIfNotTargetType() {
        dynamicProperties.disableContractAutoRenew();
        long brokeExpiredAccountNum = 1003L;
        final var expiredNum = EntityNum.fromLong(brokeExpiredAccountNum);

        given(classifier.classify(expiredNum, now)).willReturn(DETACHED_CONTRACT_GRACE_PERIOD_OVER);

        final var result = subject.process(brokeExpiredAccountNum, now);

        assertEquals(NOTHING_TO_DO, result);
    }

    @Test
    void ignoresExpiredBrokeContractIfNotTargetType() {
        long brokeExpiredAccountNum = 1003L;
        final var expiredNum = EntityNum.fromLong(brokeExpiredAccountNum);
        given(classifier.classify(expiredNum, now)).willReturn(EXPIRED_CONTRACT_READY_TO_RENEW);

        dynamicProperties.disableAutoRenew();
        dynamicProperties.disableContractAutoRenew();

        final var result = subject.process(brokeExpiredAccountNum, now);

        assertEquals(NOTHING_TO_DO, result);
    }

    @Test
    void removesExpiredBrokeAccount() {
        final var treasuryReturns =
                new CryptoGcOutcome(
                        FungibleTreasuryReturns.FINISHED_NOOP_FUNGIBLE_RETURNS,
                        NonFungibleTreasuryReturns.FINISHED_NOOP_NON_FUNGIBLE_RETURNS,
                        true);

        long brokeExpiredAccountNum = 1003L;
        final var expiredNum = EntityNum.fromLong(brokeExpiredAccountNum);
        given(classifier.classify(expiredNum, now)).willReturn(DETACHED_ACCOUNT_GRACE_PERIOD_OVER);
        given(classifier.getLastClassified()).willReturn(mockAccount);
        given(accountGC.expireBestEffort(expiredNum, mockAccount)).willReturn(treasuryReturns);
        dynamicProperties.enableAutoRenew();

        final var result = subject.process(brokeExpiredAccountNum, now);

        assertEquals(DONE, result);

        verify(accountGC).expireBestEffort(expiredNum, mockAccount);
        verify(recordsHelper).streamCryptoRemovalStep(false, expiredNum, treasuryReturns);
    }

    @Test
    void canMarkContractDetached() {
        dynamicProperties.enableContractAutoRenew();

        long detachedContractNum = 1003L;
        final var detachedNum = EntityNum.fromLong(detachedContractNum);
        given(classifier.classify(detachedNum, now)).willReturn(DETACHED_CONTRACT);
        given(expiryThrottle.allowOne(MapAccessType.ACCOUNTS_GET_FOR_MODIFY)).willReturn(true);

        final var result = subject.process(detachedContractNum, now);

        assertEquals(DONE, result);
        verify(accountGC).markDetached(detachedNum);
    }

    @Test
    void canMarkAccountDetached() {
        dynamicProperties.enableAutoRenew();

        long detachedAccountNum = 1003L;
        final var detachedNum = EntityNum.fromLong(detachedAccountNum);
        given(classifier.classify(detachedNum, now)).willReturn(DETACHED_ACCOUNT);
        given(expiryThrottle.allowOne(MapAccessType.ACCOUNTS_GET_FOR_MODIFY)).willReturn(true);

        final var result = subject.process(detachedAccountNum, now);

        assertEquals(DONE, result);
        verify(accountGC).markDetached(detachedNum);
    }

    @Test
    void removesExpiredBrokeContractImmediatelyIfStoragePurged() {
        dynamicProperties.enableContractAutoRenew();

        long brokeExpiredContractNum = 1003L;
        final var expiredNum = EntityNum.fromLong(brokeExpiredContractNum);
        given(classifier.classify(expiredNum, now)).willReturn(DETACHED_CONTRACT_GRACE_PERIOD_OVER);
        given(classifier.getLastClassified()).willReturn(mockContract);
        given(contractGC.expireBestEffort(expiredNum, mockContract)).willReturn(true);
        given(accountGC.expireBestEffort(expiredNum, mockContract)).willReturn(finishedReturns);

        final var result = subject.process(brokeExpiredContractNum, now);

        assertEquals(DONE, result);
        verify(accountGC).expireBestEffort(expiredNum, mockContract);
        verify(recordsHelper).streamCryptoRemovalStep(true, expiredNum, finishedReturns);
    }

    @Test
    void doesntExpireBrokeContractUntilStoragePurged() {
        long brokeExpiredContractNum = 1003L;
        final var expiredNum = EntityNum.fromLong(brokeExpiredContractNum);
        given(classifier.classify(expiredNum, now)).willReturn(DETACHED_CONTRACT_GRACE_PERIOD_OVER);
        given(classifier.getLastClassified()).willReturn(mockContract);
        dynamicProperties.enableContractAutoRenew();

        final var result = subject.process(brokeExpiredContractNum, now);

        assertEquals(NO_CAPACITY_LEFT, result);
        verifyNoMoreInteractions(accountGC, recordsHelper);
    }

    @Test
    void alertsIfNotAllExpirationWorkCanBeDone() {
        long brokeExpiredAccountNum = 1003L;
        final var expiredNum = EntityNum.fromLong(brokeExpiredAccountNum);
        given(classifier.classify(expiredNum, now)).willReturn(DETACHED_ACCOUNT_GRACE_PERIOD_OVER);
        given(classifier.getLastClassified()).willReturn(mockAccount);
        given(accountGC.expireBestEffort(expiredNum, mockAccount))
                .willReturn(partiallyFinishedReturns);
        dynamicProperties.enableAutoRenew();

        final var result = subject.process(brokeExpiredAccountNum, now);

        assertEquals(NO_CAPACITY_LEFT, result);
        verify(accountGC).expireBestEffort(expiredNum, mockAccount);
        verify(recordsHelper).streamCryptoRemovalStep(false, expiredNum, partiallyFinishedReturns);
    }

    @Test
    void renewsAccountAtExpectedFee() {
        // setup:
        long fundedExpiredAccountNum = 1004L;
        var key = EntityNum.fromLong(fundedExpiredAccountNum);
        mockAccount.setKey(key);

        given(classifier.classify(EntityNum.fromLong(fundedExpiredAccountNum), now))
                .willReturn(EXPIRED_ACCOUNT_READY_TO_RENEW);
        given(classifier.getLastClassified()).willReturn(mockAccount);
        given(classifier.getLastClassifiedNum()).willReturn(key);

        given(classifier.getPayerForLastClassified()).willReturn(mockAccount);
        given(classifier.getPayerNumForLastClassified()).willReturn(key);
        given(expiryThrottle.allow(anyList())).willReturn(true);

        given(
                        accountsLedger.get(
                                EntityNum.fromLong(fundedExpiredAccountNum).toGrpcAccountId(),
                                AccountProperty.BALANCE))
                .willReturn(nonZeroBalance);

        given(fees.assessCryptoAutoRenewal(mockAccount, requestedRenewalPeriod, now, mockAccount))
                .willReturn(new RenewAssessment(fee, actualRenewalPeriod));

        final var result = subject.process(fundedExpiredAccountNum, now);

        assertEquals(DONE, result);

        verify(feeDistribution).distributeChargedFee(anyLong(), eq(accountsLedger));
        verify(recordsHelper)
                .streamCryptoRenewal(key, fee, now.getEpochSecond() + actualRenewalPeriod, false);
    }

    @Test
    void renewsContractAtExpectedFee() {
        // setup: 1241766
        var key = EntityNum.fromLong(fundedExpiredContractNum);
        mockContract.setKey(key);

        given(expiryThrottle.allow(any())).willReturn(true);
        given(classifier.classify(EntityNum.fromLong(fundedExpiredContractNum), now))
                .willReturn(EXPIRED_CONTRACT_READY_TO_RENEW);
        given(classifier.getLastClassified()).willReturn(mockContract);
        given(classifier.getLastClassifiedNum())
                .willReturn(EntityNum.fromLong(fundedExpiredContractNum));
        given(fees.assessCryptoAutoRenewal(mockContract, requestedRenewalPeriod, now, mockContract))
                .willReturn(new RenewAssessment(fee, actualRenewalPeriod));

        given(
                        accountsLedger.get(
                                EntityNum.fromLong(fundedExpiredContractNum).toGrpcAccountId(),
                                AccountProperty.BALANCE))
                .willReturn(nonZeroBalance);

        given(classifier.getPayerNumForLastClassified())
                .willReturn(EntityNum.fromLong(fundedExpiredContractNum));
        given(classifier.getPayerForLastClassified()).willReturn(mockContract);

        final var result = subject.process(fundedExpiredContractNum, now);

        assertEquals(DONE, result);

        verify(feeDistribution).distributeChargedFee(anyLong(), eq(accountsLedger));
        verify(recordsHelper)
                .streamCryptoRenewal(key, fee, now.getEpochSecond() + actualRenewalPeriod, true);
    }

    @Test
    void skipsAccountRenewalIfNotEnabled() {
        // setup:
        long fundedExpiredAccountNum = 1004L;

        given(classifier.classify(EntityNum.fromLong(fundedExpiredAccountNum), now))
                .willReturn(EXPIRED_ACCOUNT_READY_TO_RENEW);
        dynamicProperties.disableAutoRenew();
        dynamicProperties.enableContractAutoRenew();

        final var result = subject.process(fundedExpiredAccountNum, now);

        assertEquals(NOTHING_TO_DO, result);
    }
}
