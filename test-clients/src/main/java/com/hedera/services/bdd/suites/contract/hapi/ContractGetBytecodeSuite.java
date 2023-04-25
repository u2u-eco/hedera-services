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
package com.hedera.services.bdd.suites.contract.hapi;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractBytecode;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.getResourcePath;

import com.google.common.io.Files;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Assertions;

public class ContractGetBytecodeSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(ContractGetBytecodeSuite.class);
    private static final String NON_EXISTING_CONTRACT =
            HapiSpecSetup.getDefaultInstance().invalidContractName();

    public static void main(String... args) {
        new ContractGetBytecodeSuite().runSuiteSync();
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                getByteCodeWorks(),
                invalidContractFromCostAnswer(),
                invalidContractFromAnswerOnly());
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    private HapiSpec getByteCodeWorks() {
        final var contract = "EmptyConstructor";
        return HapiSpec.defaultHapiSpec("GetByteCodeWorks")
                .given(uploadInitCode(contract), contractCreate(contract))
                .when()
                .then(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var getBytecode =
                                            getContractBytecode(contract)
                                                    .saveResultTo("contractByteCode");
                                    allRunFor(spec, getBytecode);

                                    @SuppressWarnings("UnstableApiUsage")
                                    final var originalBytecode =
                                            Hex.decode(
                                                    Files.toByteArray(
                                                            new File(
                                                                    getResourcePath(
                                                                            contract, ".bin"))));
                                    final var actualBytecode =
                                            spec.registry().getBytes("contractByteCode");
                                    // The original bytecode is modified on deployment
                                    final var expectedBytecode =
                                            Arrays.copyOfRange(
                                                    originalBytecode, 29, originalBytecode.length);
                                    Assertions.assertArrayEquals(expectedBytecode, actualBytecode);
                                }));
    }

    private HapiSpec invalidContractFromCostAnswer() {
        return defaultHapiSpec("InvalidContractFromCostAnswer")
                .given()
                .when()
                .then(
                        getContractBytecode(NON_EXISTING_CONTRACT)
                                .hasCostAnswerPrecheck(ResponseCodeEnum.INVALID_CONTRACT_ID));
    }

    private HapiSpec invalidContractFromAnswerOnly() {
        return defaultHapiSpec("InvalidContractFromAnswerOnly")
                .given()
                .when()
                .then(
                        getContractBytecode(NON_EXISTING_CONTRACT)
                                .nodePayment(27_159_182L)
                                .hasAnswerOnlyPrecheck(ResponseCodeEnum.INVALID_CONTRACT_ID));
    }
}
