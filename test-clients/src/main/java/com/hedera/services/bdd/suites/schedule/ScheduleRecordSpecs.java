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
package com.hedera.services.bdd.suites.schedule;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.assertions.TransferListAsserts.exactParticipants;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getScheduleInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTopicInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleSign;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.submitMessageTo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingAllOf;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.usableTxnIdNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsdWithin;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.schedule.ScheduleLongTermExecutionSpecs.withAndWithoutLongTermEnabled;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_ID_FIELD_NOT_ALLOWED;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ScheduleRecordSpecs extends HapiSuite {
    private static final Logger log = LogManager.getLogger(ScheduleRecordSpecs.class);

    public static void main(String... args) {
        new ScheduleRecordSpecs().runSuiteAsync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return withAndWithoutLongTermEnabled(
                () ->
                        List.of(
                                executionTimeIsAvailable(),
                                deletionTimeIsAvailable(),
                                allRecordsAreQueryable(),
                                schedulingTxnIdFieldsNotAllowed(),
                                canonicalScheduleOpsHaveExpectedUsdFees(),
                                canScheduleChunkedMessages(),
                                noFeesChargedIfTriggeredPayerIsInsolvent(),
                                noFeesChargedIfTriggeredPayerIsUnwilling()));
    }

    HapiSpec canonicalScheduleOpsHaveExpectedUsdFees() {
        return defaultHapiSpec("CanonicalScheduleOpsHaveExpectedUsdFees")
                .given(
                        overriding("scheduling.whitelist", "CryptoTransfer,ContractCall"),
                        uploadInitCode("SimpleUpdate"),
                        cryptoCreate("otherPayer"),
                        cryptoCreate("payingSender"),
                        cryptoCreate("receiver").receiverSigRequired(true),
                        contractCreate("SimpleUpdate").gas(300_000L))
                .when(
                        scheduleCreate(
                                        "canonical",
                                        cryptoTransfer(
                                                        tinyBarsFromTo(
                                                                "payingSender", "receiver", 1L))
                                                .memo("")
                                                .fee(ONE_HBAR))
                                .payingWith("otherPayer")
                                .via("canonicalCreation")
                                .alsoSigningWith("payingSender")
                                .adminKey("otherPayer"),
                        scheduleSign("canonical")
                                .via("canonicalSigning")
                                .payingWith("payingSender")
                                .alsoSigningWith("receiver"),
                        scheduleCreate(
                                        "tbd",
                                        cryptoTransfer(
                                                        tinyBarsFromTo(
                                                                "payingSender", "receiver", 1L))
                                                .memo("")
                                                .fee(ONE_HBAR))
                                .payingWith("payingSender")
                                .adminKey("payingSender"),
                        scheduleDelete("tbd").via("canonicalDeletion").payingWith("payingSender"),
                        scheduleCreate(
                                        "contractCall",
                                        contractCall(
                                                        "SimpleUpdate",
                                                        "set",
                                                        BigInteger.valueOf(5),
                                                        BigInteger.valueOf(42))
                                                .gas(10_000L)
                                                .memo("")
                                                .fee(ONE_HBAR))
                                .payingWith("otherPayer")
                                .via("canonicalContractCall")
                                .adminKey("otherPayer"))
                .then(
                        overriding(
                                "scheduling.whitelist",
                                HapiSpecSetup.getDefaultNodeProps().get("scheduling.whitelist")),
                        validateChargedUsdWithin("canonicalCreation", 0.01, 3.0),
                        validateChargedUsdWithin("canonicalSigning", 0.001, 3.0),
                        validateChargedUsdWithin("canonicalDeletion", 0.001, 3.0),
                        validateChargedUsdWithin("canonicalContractCall", 0.1, 3.0));
    }

    public HapiSpec noFeesChargedIfTriggeredPayerIsUnwilling() {
        return defaultHapiSpec("NoFeesChargedIfTriggeredPayerIsUnwilling")
                .given(cryptoCreate("unwillingPayer"))
                .when(
                        scheduleCreate(
                                        "schedule",
                                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1)).fee(1L))
                                .alsoSigningWith(GENESIS, "unwillingPayer")
                                .via("simpleXferSchedule")
                                // prevent multiple runs of this test causing duplicates
                                .withEntityMemo("" + new SecureRandom().nextLong())
                                .designatingPayer("unwillingPayer")
                                .savingExpectedScheduledTxnId())
                .then(
                        getTxnRecord("simpleXferSchedule")
                                .scheduledBy("schedule")
                                .hasPriority(
                                        recordWith()
                                                .transfers(
                                                        exactParticipants(
                                                                ignore -> Collections.emptyList()))
                                                .status(INSUFFICIENT_TX_FEE)));
    }

    public HapiSpec noFeesChargedIfTriggeredPayerIsInsolvent() {
        return defaultHapiSpec("NoFeesChargedIfTriggeredPayerIsInsolvent")
                .given(cryptoCreate("insolventPayer").balance(0L))
                .when(
                        scheduleCreate(
                                        "schedule",
                                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1)))
                                .alsoSigningWith(GENESIS, "insolventPayer")
                                .via("simpleXferSchedule")
                                // prevent multiple runs of this test causing duplicates
                                .withEntityMemo("" + new SecureRandom().nextLong())
                                .designatingPayer("insolventPayer")
                                .savingExpectedScheduledTxnId())
                .then(
                        getTxnRecord("simpleXferSchedule")
                                .scheduledBy("schedule")
                                .hasPriority(
                                        recordWith()
                                                .transfers(
                                                        exactParticipants(
                                                                ignore -> Collections.emptyList()))
                                                .status(INSUFFICIENT_PAYER_BALANCE)));
    }

    public HapiSpec canScheduleChunkedMessages() {
        String ofGeneralInterest = "Scotch";
        AtomicReference<TransactionID> initialTxnId = new AtomicReference<>();

        return defaultHapiSpec("CanScheduleChunkedMessages")
                .given(
                        overridingAllOf(
                                Map.of(
                                        "staking.fees.nodeRewardPercentage", "10",
                                        "staking.fees.stakingRewardPercentage", "10")),
                        cryptoCreate("payingSender").balance(ONE_HUNDRED_HBARS),
                        createTopic(ofGeneralInterest))
                .when(
                        withOpContext(
                                (spec, opLog) -> {
                                    var subOp = usableTxnIdNamed("begin").payerId("payingSender");
                                    allRunFor(spec, subOp);
                                    initialTxnId.set(spec.registry().getTxnId("begin"));
                                }),
                        sourcing(
                                () ->
                                        scheduleCreate(
                                                        "firstChunk",
                                                        submitMessageTo(ofGeneralInterest)
                                                                .chunkInfo(
                                                                        3,
                                                                        1,
                                                                        scheduledVersionOf(
                                                                                initialTxnId
                                                                                        .get())))
                                                .txnId("begin")
                                                .logged()
                                                .signedBy("payingSender")),
                        getTxnRecord("begin")
                                .hasPriority(
                                        recordWith()
                                                .status(SUCCESS)
                                                .transfers(
                                                        exactParticipants(
                                                                spec ->
                                                                        List.of(
                                                                                spec.setup()
                                                                                        .defaultNode(),
                                                                                spec.setup()
                                                                                        .fundingAccount(),
                                                                                spec.setup()
                                                                                        .stakingRewardAccount(),
                                                                                spec.setup()
                                                                                        .nodeRewardAccount(),
                                                                                spec.registry()
                                                                                        .getAccountID(
                                                                                                "payingSender")))))
                                .assertingOnlyPriority()
                                .logged(),
                        getTxnRecord("begin")
                                .scheduled()
                                .hasPriority(
                                        recordWith()
                                                .status(SUCCESS)
                                                .transfers(
                                                        exactParticipants(
                                                                spec ->
                                                                        List.of(
                                                                                spec.setup()
                                                                                        .fundingAccount(),
                                                                                spec.setup()
                                                                                        .stakingRewardAccount(),
                                                                                spec.setup()
                                                                                        .nodeRewardAccount(),
                                                                                spec.registry()
                                                                                        .getAccountID(
                                                                                                "payingSender")))))
                                .logged())
                .then(
                        scheduleCreate(
                                        "secondChunk",
                                        submitMessageTo(ofGeneralInterest)
                                                .chunkInfo(3, 2, "payingSender"))
                                .via("end")
                                .logged()
                                .payingWith("payingSender"),
                        getTxnRecord("end")
                                .scheduled()
                                .hasPriority(
                                        recordWith()
                                                .status(SUCCESS)
                                                .transfers(
                                                        exactParticipants(
                                                                spec ->
                                                                        List.of(
                                                                                spec.setup()
                                                                                        .fundingAccount(),
                                                                                spec.setup()
                                                                                        .stakingRewardAccount(),
                                                                                spec.setup()
                                                                                        .nodeRewardAccount(),
                                                                                spec.registry()
                                                                                        .getAccountID(
                                                                                                "payingSender")))))
                                .logged(),
                        getTopicInfo(ofGeneralInterest).logged().hasSeqNo(2L),
                        overridingAllOf(
                                Map.of(
                                        "staking.fees.nodeRewardPercentage",
                                                HapiSpecSetup.getDefaultNodeProps()
                                                        .get("staking.fees.nodeRewardPercentage"),
                                        "staking.fees.stakingRewardPercentage",
                                                HapiSpecSetup.getDefaultNodeProps()
                                                        .get(
                                                                "staking.fees.stakingRewardPercentage"))));
    }

    static TransactionID scheduledVersionOf(TransactionID txnId) {
        return txnId.toBuilder().setScheduled(true).build();
    }

    public HapiSpec schedulingTxnIdFieldsNotAllowed() {
        return defaultHapiSpec("SchedulingTxnIdFieldsNotAllowed")
                .given(usableTxnIdNamed("withScheduled").settingScheduledInappropriately())
                .when()
                .then(
                        cryptoCreate("nope")
                                .txnId("withScheduled")
                                .hasPrecheck(TRANSACTION_ID_FIELD_NOT_ALLOWED));
    }

    public HapiSpec executionTimeIsAvailable() {
        return defaultHapiSpec("ExecutionTimeIsAvailable")
                .given(
                        cryptoCreate("payer"),
                        cryptoCreate("receiver").receiverSigRequired(true).balance(0L))
                .when(
                        scheduleCreate(
                                        "tb",
                                        cryptoTransfer(tinyBarsFromTo("payer", "receiver", 1))
                                                .fee(ONE_HBAR))
                                .savingExpectedScheduledTxnId()
                                .payingWith("payer")
                                .via("creation"),
                        scheduleSign("tb").via("trigger").alsoSigningWith("receiver"))
                .then(getScheduleInfo("tb").logged().wasExecutedBy("trigger"));
    }

    public HapiSpec deletionTimeIsAvailable() {
        return defaultHapiSpec("DeletionTimeIsAvailable")
                .given(
                        newKeyNamed("admin"),
                        cryptoCreate("payer"),
                        cryptoCreate("receiver").receiverSigRequired(true).balance(0L))
                .when(
                        scheduleCreate(
                                        "ntb",
                                        cryptoTransfer(tinyBarsFromTo("payer", "receiver", 1))
                                                .fee(ONE_HBAR))
                                .payingWith("payer")
                                .adminKey("admin")
                                .via("creation"),
                        scheduleDelete("ntb").via("deletion").signedBy(DEFAULT_PAYER, "admin"))
                .then(getScheduleInfo("ntb").wasDeletedAtConsensusTimeOf("deletion"));
    }

    public HapiSpec allRecordsAreQueryable() {
        return defaultHapiSpec("AllRecordsAreQueryable")
                .given(
                        cryptoCreate("payer"),
                        cryptoCreate("receiver").receiverSigRequired(true).balance(0L))
                .when(
                        scheduleCreate(
                                        "twoSigXfer",
                                        cryptoTransfer(tinyBarsFromTo("payer", "receiver", 1))
                                                .fee(ONE_HBAR))
                                .logged()
                                .savingExpectedScheduledTxnId()
                                .payingWith("payer")
                                .via("creation"),
                        getAccountBalance("receiver").hasTinyBars(0L))
                .then(
                        scheduleSign("twoSigXfer").via("trigger").alsoSigningWith("receiver"),
                        getAccountBalance("receiver").hasTinyBars(1L),
                        getTxnRecord("trigger"),
                        getTxnRecord("creation"),
                        getTxnRecord("creation").scheduled(),
                        getTxnRecord("creation").scheduledBy("twoSigXfer"));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
