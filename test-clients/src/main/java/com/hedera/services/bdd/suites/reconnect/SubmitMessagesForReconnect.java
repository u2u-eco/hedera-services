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
package com.hedera.services.bdd.suites.reconnect;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.randomUtf8Bytes;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.submitMessageTo;
import static com.hedera.services.bdd.spec.utilops.LoadTest.defaultLoadTest;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hedera.services.bdd.suites.perf.PerfTestLoadSettings;
import java.util.List;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SubmitMessagesForReconnect extends HapiSuite {
    private static final Logger log = LogManager.getLogger(SubmitMessagesForReconnect.class);

    public static void main(String... args) {
        new SubmitMessagesForReconnect().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(runSubmitMessages());
    }

    private static HapiSpecOperation submitToTestTopic(PerfTestLoadSettings settings) {
        String topicToSubmit = String.format("0.0.%d", settings.getTestTopicId());
        return submitMessageTo(topicToSubmit)
                .message(randomUtf8Bytes(100))
                .noLogging()
                .fee(ONE_HBAR)
                .hasRetryPrecheckFrom(BUSY, PLATFORM_TRANSACTION_NOT_CREATED)
                .deferStatusResolution();
    }

    private HapiSpec runSubmitMessages() {
        PerfTestLoadSettings settings = new PerfTestLoadSettings();

        Supplier<HapiSpecOperation[]> submitBurst =
                () -> new HapiSpecOperation[] {submitToTestTopic(settings)};

        return defaultHapiSpec("RunSubmitMessages")
                .given(
                        withOpContext(
                                (spec, ignore) -> settings.setFrom(spec.setup().ciPropertiesMap())),
                        logIt(ignore -> settings.toString()))
                .when()
                .then(defaultLoadTest(submitBurst, settings));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
