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
package com.hedera.services.bdd.suites.misc;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runWithProvider;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FileQueriesStressTests extends HapiSuite {
    private static final Logger log = LogManager.getLogger(FileQueriesStressTests.class);

    private AtomicLong duration = new AtomicLong(30);
    private AtomicReference<TimeUnit> unit = new AtomicReference<>(SECONDS);
    private AtomicInteger maxOpsPerSec = new AtomicInteger(100);

    public static void main(String... args) {
        new FileQueriesStressTests().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                new HapiSpec[] {
                    getFileInfoStress(), getFileContentsStress(),
                });
    }

    private HapiSpec getFileContentsStress() {
        return defaultHapiSpec("getFileContentsStress")
                .given()
                .when()
                .then(
                        withOpContext((spec, opLog) -> configureFromCi(spec)),
                        runWithProvider(getFileContentsFactory())
                                .lasting(duration::get, unit::get)
                                .maxOpsPerSec(maxOpsPerSec::get));
    }

    private HapiSpec getFileInfoStress() {
        return defaultHapiSpec("getFileInfoStress")
                .given()
                .when()
                .then(
                        withOpContext((spec, opLog) -> configureFromCi(spec)),
                        runWithProvider(getFileInfoFactory())
                                .lasting(duration::get, unit::get)
                                .maxOpsPerSec(maxOpsPerSec::get));
    }

    private Function<HapiSpec, OpProvider> getFileContentsFactory() {
        byte[] contents = "You won't believe this!".getBytes();

        return spec ->
                new OpProvider() {
                    @Override
                    public List<HapiSpecOperation> suggestedInitializers() {
                        return List.of(fileCreate("something").contents(contents));
                    }

                    @Override
                    public Optional<HapiSpecOperation> get() {
                        return Optional.of(
                                getFileContents("something")
                                        .hasContents(ignore -> contents)
                                        .noLogging());
                    }
                };
    }

    private Function<HapiSpec, OpProvider> getFileInfoFactory() {
        return spec ->
                new OpProvider() {
                    @Override
                    public List<HapiSpecOperation> suggestedInitializers() {
                        return List.of(fileCreate("something").contents("You won't believe this!"));
                    }

                    @Override
                    public Optional<HapiSpecOperation> get() {
                        return Optional.of(getFileInfo("something").noLogging());
                    }
                };
    }

    private void configureFromCi(HapiSpec spec) {
        HapiPropertySource ciProps = spec.setup().ciPropertiesMap();
        configure("duration", duration::set, ciProps, ciProps::getLong);
        configure("unit", unit::set, ciProps, ciProps::getTimeUnit);
        configure("maxOpsPerSec", maxOpsPerSec::set, ciProps, ciProps::getInteger);
    }

    private <T> void configure(
            String name,
            Consumer<T> configurer,
            HapiPropertySource ciProps,
            Function<String, T> getter) {
        if (ciProps.has(name)) {
            configurer.accept(getter.apply(name));
        }
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
