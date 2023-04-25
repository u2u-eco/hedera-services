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
package com.hedera.services.bdd.suites.issues;

import static com.hedera.services.bdd.spec.HapiPropertySource.asAccount;
import static com.hedera.services.bdd.spec.HapiPropertySource.asContract;
import static com.hedera.services.bdd.spec.HapiPropertySource.asFile;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileAppend;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.takeBalanceSnapshots;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateTransferListForBalances;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.keys.KeyFactory;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Issue1765Suite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(Issue1765Suite.class);

    public static void main(String... args) {
        new Issue1765Suite().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                //				recordOfInvalidFileAppendSanityChecks(),
                //				recordOfInvalidAccountUpdateSanityChecks(),
                //				recordOfInvalidAccountTransferSanityChecks()
                //				recordOfInvalidFileUpdateSanityChecks()
                //				recordOfInvalidContractUpdateSanityChecks()
                get950Balance());
    }

    public static HapiSpec get950Balance() {
        return defaultHapiSpec("Get950Balance")
                .given()
                .when()
                .then(getAccountBalance("0.0.950").logged());
    }

    public static HapiSpec recordOfInvalidAccountTransferSanityChecks() {
        final String INVALID_ACCOUNT = "imaginary";

        return defaultHapiSpec("RecordOfInvalidAccountTransferSanityChecks")
                .given(
                        flattened(
                                withOpContext(
                                        (spec, ctxLog) -> {
                                            spec.registry()
                                                    .saveAccountId(
                                                            INVALID_ACCOUNT, asAccount("1.1.1"));
                                        }),
                                takeBalanceSnapshots(FUNDING, GENESIS, NODE)))
                .when(cryptoTransfer(tinyBarsFromTo(GENESIS, INVALID_ACCOUNT, 1L)))
                .then();
    }

    public static HapiSpec recordOfInvalidAccountUpdateSanityChecks() {
        final String INVALID_ACCOUNT = "imaginary";

        return defaultHapiSpec("RecordOfInvalidAccountSanityChecks")
                .given(
                        flattened(
                                withOpContext(
                                        (spec, ctxLog) -> {
                                            spec.registry()
                                                    .saveAccountId(
                                                            INVALID_ACCOUNT, asAccount("1.1.1"));
                                        }),
                                newKeyNamed(INVALID_ACCOUNT),
                                newKeyNamed("irrelevant"),
                                takeBalanceSnapshots(FUNDING, GENESIS, NODE)))
                .when(cryptoUpdate(INVALID_ACCOUNT).key("irrelevant"))
                .then();
    }

    public static HapiSpec recordOfInvalidContractUpdateSanityChecks() {
        final long ADEQUATE_FEE = 100_000_000L;
        final String INVALID_CONTRACT = "imaginary";
        final String THE_MEMO_IS = "Turning and turning in the widening gyre";

        return defaultHapiSpec("RecordOfInvalidContractUpdateSanityChecks")
                .given(
                        flattened(
                                withOpContext(
                                        (spec, ctxLog) -> {
                                            spec.registry()
                                                    .saveContractId(
                                                            INVALID_CONTRACT, asContract("1.1.1"));
                                        }),
                                newKeyNamed(INVALID_CONTRACT),
                                takeBalanceSnapshots(FUNDING, GENESIS, NODE)))
                .when(
                        contractUpdate(INVALID_CONTRACT)
                                .memo(THE_MEMO_IS)
                                .fee(ADEQUATE_FEE)
                                .via("invalidUpdateTxn")
                                .hasKnownStatus(ResponseCodeEnum.INVALID_CONTRACT_ID))
                .then(
                        validateTransferListForBalances(
                                "invalidUpdateTxn", List.of(FUNDING, GENESIS, NODE)),
                        getTxnRecord("invalidUpdateTxn")
                                .hasPriority(recordWith().memo(THE_MEMO_IS)));
    }

    public static HapiSpec recordOfInvalidFileUpdateSanityChecks() {
        final long ADEQUATE_FEE = 100_000_000L;
        final String INVALID_FILE = "imaginary";
        final String THE_MEMO_IS = "Turning and turning in the widening gyre";

        return defaultHapiSpec("RecordOfInvalidFileUpdateSanityChecks")
                .given(
                        flattened(
                                withOpContext(
                                        (spec, ctxLog) -> {
                                            spec.registry()
                                                    .saveFileId(INVALID_FILE, asFile("0.0.0"));
                                        }),
                                newKeyNamed(INVALID_FILE).type(KeyFactory.KeyType.LIST),
                                takeBalanceSnapshots(FUNDING, GENESIS, NODE)))
                .when(
                        fileUpdate(INVALID_FILE)
                                .memo(THE_MEMO_IS)
                                .fee(ADEQUATE_FEE)
                                .via("invalidUpdateTxn")
                                .hasKnownStatus(ResponseCodeEnum.INVALID_FILE_ID))
                .then(
                        validateTransferListForBalances(
                                "invalidUpdateTxn", List.of(FUNDING, GENESIS, NODE)),
                        getTxnRecord("invalidUpdateTxn")
                                .hasPriority(recordWith().memo(THE_MEMO_IS)));
    }

    public static HapiSpec recordOfInvalidFileAppendSanityChecks() {
        final long ADEQUATE_FEE = 100_000_000L;
        final String INVALID_FILE = "imaginary";
        final String THE_MEMO_IS = "Turning and turning in the widening gyre";

        return defaultHapiSpec("RecordOfInvalidFileAppendSanityChecks")
                .given(
                        flattened(
                                withOpContext(
                                        (spec, ctxLog) -> {
                                            spec.registry()
                                                    .saveFileId(INVALID_FILE, asFile("0.0.0"));
                                        }),
                                newKeyNamed(INVALID_FILE).type(KeyFactory.KeyType.LIST),
                                takeBalanceSnapshots(FUNDING, GENESIS, NODE)))
                .when(
                        fileAppend(INVALID_FILE)
                                .memo(THE_MEMO_IS)
                                .content("Some more content.")
                                .fee(ADEQUATE_FEE)
                                .via("invalidAppendTxn")
                                .hasKnownStatus(ResponseCodeEnum.INVALID_FILE_ID))
                .then(
                        validateTransferListForBalances(
                                "invalidAppendTxn", List.of(FUNDING, GENESIS, NODE)),
                        getTxnRecord("invalidAppendTxn")
                                .hasPriority(recordWith().memo(THE_MEMO_IS)));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
