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

import static com.hedera.services.bdd.spec.HapiPropertySource.asContract;
import static com.hedera.services.bdd.spec.HapiPropertySource.asContractString;
import static com.hedera.services.bdd.spec.HapiPropertySource.asHexedSolidityAddress;
import static com.hedera.services.bdd.spec.HapiPropertySource.contractIdFromHexedMirrorAddress;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.changeFromSnapshot;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.inOrder;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.isLiteralResult;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.ContractInfoAsserts.contractWith;
import static com.hedera.services.bdd.spec.assertions.ContractLogAsserts.logWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.KeyFactory.KeyType.THRESHOLD;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractRecords;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenNftInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.asId;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.literalInitcodeFor;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.burnToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCallWithFunctionAbi;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCustomCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromAccountToAlias;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.assertionsHold;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.balanceSnapshot;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.createLargeFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyListNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.updateLargeFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.asToken;
import static com.hedera.services.bdd.suites.contract.Utils.captureChildCreate2MetaFor;
import static com.hedera.services.bdd.suites.contract.Utils.extractByteCode;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;
import static com.hedera.services.bdd.suites.contract.Utils.getABIForContract;
import static com.hedera.services.bdd.suites.utils.contracts.SimpleBytesResult.bigIntResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OBTAINER_SAME_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;
import static com.swirlds.common.utility.CommonUtils.unhex;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Address;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TupleType;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.spec.transactions.contract.HapiContractCreate;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import com.swirlds.common.utility.CommonUtils;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

public class ContractCallSuite extends HapiSuite {
    private static final Logger LOG = LogManager.getLogger(ContractCallSuite.class);

    private static final String ALICE = "Alice";

    public static final String LEDGER_AUTO_RENEW_PERIOD_MAX_DURATION =
            "ledger.autoRenewPeriod.maxDuration";
    public static final String DEFAULT_MAX_AUTO_RENEW_PERIOD =
            HapiSpecSetup.getDefaultNodeProps().get(LEDGER_AUTO_RENEW_PERIOD_MAX_DURATION);

    private static final long DEPOSIT_AMOUNT = 1000;
    private static final long GAS_TO_OFFER = 2_000_000L;

    public static final String PAY_RECEIVABLE_CONTRACT = "PayReceivable";
    public static final String SIMPLE_UPDATE_CONTRACT = "SimpleUpdate";
    public static final String TRANSFERRING_CONTRACT = "Transferring";
    private static final String SIMPLE_STORAGE_CONTRACT = "SimpleStorage";
    private static final String OWNER = "owner";
    private static final String INSERT = "insert";
    private static final String TOKEN_ISSUER = "tokenIssuer";
    public static final String DECIMALS = "decimals";
    private static final String BALANCE_OF = "balanceOf";
    private static final String ISSUER_TOKEN_BALANCE = "issuerTokenBalance";
    public static final String TRANSFER = "transfer";
    private static final String ALICE_TOKEN_BALANCE = "aliceTokenBalance";
    private static final String CAROL_TOKEN_BALANCE = "carolTokenBalance";
    private static final String BOB_TOKEN_BALANCE = "bobTokenBalance";
    private static final String PAYER = "payer";
    private static final String GET_CODE_SIZE = "getCodeSize";
    public static final String DEPOSIT = "deposit";
    private static final String PAY_TXN = "payTxn";
    private static final String BENEFICIARY = "beneficiary";
    private static final String RECEIVER = "receiver";
    private static final String GET_BALANCE = "getBalance";
    public static final String CONTRACTS_MAX_GAS_PER_SEC = "contracts.maxGasPerSec";
    private static final String TRANSFER_TXN = "transferTxn";
    public static final String ACCOUNT_INFO_AFTER_CALL = "accountInfoAfterCall";
    public static final String TRANSFER_TO_CALLER = "transferToCaller";
    private static final String CREATE_TRIVIAL = "CreateTrivial";
    private static final String TEST_APPROVER = "TestApprover";
    public static final String CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT =
            "contracts.maxRefundPercentOfGasLimit";
    private static final String FAIL_INSUFFICIENT_GAS = "failInsufficientGas";
    private static final String FAIL_INVALID_INITIAL_BALANCE = "failInvalidInitialBalance";
    private static final String SUCCESS_WITH_ZERO_INITIAL_BALANCE = "successWithZeroInitialBalance";
    private static final String KILL_ME = "killMe";
    private static final String CONTRACT_CALLER = "contractCaller";
    private static final String RECEIVABLE_SIG_REQ_ACCOUNT = "receivableSigReqAccount";
    private static final String RECEIVABLE_SIG_REQ_ACCOUNT_INFO = "receivableSigReqAccountInfo";
    private static final String TRANSFER_TO_ADDRESS = "transferToAddress";
    public static final String CALL_TX = "callTX";
    public static final String CALL_TX_REC = "callTXRec";
    private static final String ACCOUNT = "account";
    public static final String ACCOUNT_INFO = "accountInfo";
    public static final String CONTRACT_FROM = "contract_from";
    private static final String RECEIVER_INFO = "receiverInfo";
    private static final String SCINFO = "scinfo";
    private static final String NESTED_TRANSFER_CONTRACT = "NestedTransferContract";
    private static final String NESTED_TRANSFERRING_CONTRACT = "NestedTransferringContract";
    private static final String ACC_INFO = "accInfo";
    private static final String RECEIVER_1 = "receiver1";
    public static final String RECEIVER_2 = "receiver2";
    private static final String RECEIVER_3 = "receiver3";
    private static final String RECEIVER_1_INFO = "receiver1Info";
    private static final String RECEIVER_2_INFO = "receiver2Info";
    private static final String RECEIVER_3_INFO = "receiver3Info";

    public static void main(String... args) {
        new ContractCallSuite().runSuiteAsync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                consTimeManagementWorksWithRevertedInternalCreations(),
                payableSuccess(),
                depositSuccess(),
                depositDeleteSuccess(),
                associationAcknowledgedInApprovePrecompile(),
                multipleDepositSuccess(),
                payTestSelfDestructCall(),
                multipleSelfDestructsAreSafe(),
                smartContractInlineAssemblyCheck(),
                ocToken(),
                erc721TokenUriAndHtsNftInfoTreatNonUtf8BytesDifferently(),
                contractTransferToSigReqAccountWithKeySucceeds(),
                minChargeIsTXGasUsedByContractCall(),
                hscsEvm005TransferOfHBarsWorksBetweenContracts(),
                hscsEvm006ContractHBarTransferToAccount(),
                hscsEvm005TransfersWithSubLevelCallsBetweenContracts(),
                hscsEvm010MultiSignatureAccounts(),
                hscsEvm010ReceiverMustSignContractTx(),
                insufficientGas(),
                insufficientFee(),
                nonPayable(),
                invalidContract(),
                smartContractFailFirst(),
                contractTransferToSigReqAccountWithoutKeyFails(),
                callingDestructedContractReturnsStatusDeleted(),
                imapUserExercise(),
                sendHbarsToAddressesMultipleTimes(),
                sendHbarsToDifferentAddresses(),
                sendHbarsFromDifferentAddressessToAddress(),
                sendHbarsFromAndToDifferentAddressess(),
                transferNegativeAmountOfHbars(),
                transferZeroHbars(),
                sendHbarsToOuterContractFromDifferentAddresses(),
                sendHbarsToCallerFromDifferentAddresses(),
                bitcarbonTestStillPasses(),
                whitelistingAliasedContract(),
                cannotUseMirrorAddressOfAliasedContractInPrecompileMethod(),
                exchangeRatePrecompileWorks(),
                canMintAndTransferInSameContractOperation(),
                workingHoursDemo(),
                lpFarmSimulation(),
                nestedContractCannotOverSendValue(),
                depositMoreThanBalanceFailsGracefully());
    }

    private HapiSpec depositMoreThanBalanceFailsGracefully() {
        return defaultHapiSpec("Deposit More Than Balance Fails Gracefully")
                .given(
                        uploadInitCode(PAY_RECEIVABLE_CONTRACT),
                        cryptoCreate(ACCOUNT).balance(ONE_HBAR - 1))
                .when(contractCreate(PAY_RECEIVABLE_CONTRACT).adminKey(THRESHOLD))
                .then(
                        contractCall(PAY_RECEIVABLE_CONTRACT, DEPOSIT, BigInteger.valueOf(ONE_HBAR))
                                .via(PAY_TXN)
                                .payingWith(ACCOUNT)
                                .sending(ONE_HBAR)
                                .hasPrecheck(INSUFFICIENT_PAYER_BALANCE));
    }

    private HapiSpec nestedContractCannotOverSendValue() {
        return defaultHapiSpec("NestedContractCannotOverSendValue")
                .given(
                        cryptoCreate(ACCOUNT).balance(ONE_MILLION_HBARS),
                        cryptoCreate(RECEIVER).balance(10_000L),
                        uploadInitCode(NESTED_TRANSFERRING_CONTRACT, NESTED_TRANSFER_CONTRACT),
                        contractCustomCreate(NESTED_TRANSFER_CONTRACT, "1")
                                .balance(10_000L)
                                .payingWith(ACCOUNT),
                        contractCustomCreate(NESTED_TRANSFER_CONTRACT, "2")
                                .balance(10_000L)
                                .payingWith(ACCOUNT),
                        getAccountInfo(RECEIVER).savingSnapshot(RECEIVER_INFO))
                .when(
                        withOpContext(
                                (spec, log) -> {
                                    var receiverAddr =
                                            spec.registry()
                                                    .getAccountInfo(RECEIVER_INFO)
                                                    .getContractAccountID();

                                    allRunFor(
                                            spec,
                                            contractCreate(
                                                            NESTED_TRANSFERRING_CONTRACT,
                                                            asHeadlongAddress(
                                                                    getNestedContractAddress(
                                                                            NESTED_TRANSFER_CONTRACT
                                                                                    + "1",
                                                                            spec)),
                                                            asHeadlongAddress(
                                                                    getNestedContractAddress(
                                                                            NESTED_TRANSFER_CONTRACT
                                                                                    + "2",
                                                                            spec)))
                                                    .balance(10_000L)
                                                    .payingWith(ACCOUNT),
                                            contractCall(
                                                            NESTED_TRANSFERRING_CONTRACT,
                                                            "transferFromDifferentAddressesToAddress",
                                                            asHeadlongAddress(receiverAddr),
                                                            BigInteger.valueOf(40_000L))
                                                    .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                                    .payingWith(ACCOUNT)
                                                    .logged());
                                }))
                .then(
                        getAccountBalance(RECEIVER).hasTinyBars(10_000L),
                        sourcing(
                                () ->
                                        getContractInfo(NESTED_TRANSFER_CONTRACT + "1")
                                                .has(contractWith().balance(10_000L))),
                        sourcing(
                                () ->
                                        getContractInfo(NESTED_TRANSFER_CONTRACT + "2")
                                                .has(contractWith().balance(10_000L))));
    }

    private HapiSpec whitelistingAliasedContract() {
        final var creationTxn = "creationTxn";
        final var mirrorWhitelistCheckTxn = "mirrorWhitelistCheckTxn";
        final var evmWhitelistCheckTxn = "evmWhitelistCheckTxn";

        final var WHITELISTER = "Whitelister";
        final var CREATOR = "Creator";

        final AtomicReference<String> childMirror = new AtomicReference<>();
        final AtomicReference<String> childEip1014 = new AtomicReference<>();

        return defaultHapiSpec("whitelistingAliasedContract")
                .given(
                        sourcing(
                                () ->
                                        createLargeFile(
                                                DEFAULT_PAYER,
                                                WHITELISTER,
                                                literalInitcodeFor("Whitelister"))),
                        sourcing(
                                () ->
                                        createLargeFile(
                                                DEFAULT_PAYER,
                                                CREATOR,
                                                literalInitcodeFor("Creator"))),
                        withOpContext(
                                (spec, op) ->
                                        allRunFor(
                                                spec,
                                                contractCreate(WHITELISTER)
                                                        .payingWith(DEFAULT_PAYER)
                                                        .gas(GAS_TO_OFFER),
                                                contractCreate(CREATOR)
                                                        .payingWith(DEFAULT_PAYER)
                                                        .gas(GAS_TO_OFFER)
                                                        .via(creationTxn))))
                .when(
                        captureChildCreate2MetaFor(
                                1, 0, "setup", creationTxn, childMirror, childEip1014),
                        withOpContext(
                                (spec, op) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                WHITELISTER,
                                                                "addToWhitelist",
                                                                asHeadlongAddress(
                                                                        childEip1014.get()))
                                                        .payingWith(DEFAULT_PAYER),
                                                contractCallWithFunctionAbi(
                                                                asContractString(
                                                                        contractIdFromHexedMirrorAddress(
                                                                                childMirror.get())),
                                                                getABIFor(
                                                                        FUNCTION,
                                                                        "isWhitelisted",
                                                                        WHITELISTER),
                                                                asHeadlongAddress(
                                                                        getNestedContractAddress(
                                                                                WHITELISTER, spec)))
                                                        .payingWith(DEFAULT_PAYER)
                                                        .via(mirrorWhitelistCheckTxn),
                                                contractCall(
                                                                CREATOR,
                                                                "isWhitelisted",
                                                                asHeadlongAddress(
                                                                        getNestedContractAddress(
                                                                                WHITELISTER, spec)))
                                                        .payingWith(DEFAULT_PAYER)
                                                        .via(evmWhitelistCheckTxn))))
                .then(
                        getTxnRecord(mirrorWhitelistCheckTxn)
                                .hasPriority(
                                        recordWith()
                                                .contractCallResult(
                                                        resultWith()
                                                                .contractCallResult(
                                                                        bigIntResult(1))))
                                .logged(),
                        getTxnRecord(evmWhitelistCheckTxn)
                                .hasPriority(
                                        recordWith()
                                                .contractCallResult(
                                                        resultWith()
                                                                .contractCallResult(
                                                                        bigIntResult(1))))
                                .logged());
    }

    private HapiSpec cannotUseMirrorAddressOfAliasedContractInPrecompileMethod() {
        final var creationTxn = "creationTxn";
        final var ASSOCIATOR = "Associator";

        final AtomicReference<String> childMirror = new AtomicReference<>();
        final AtomicReference<String> childEip1014 = new AtomicReference<>();
        final AtomicReference<TokenID> tokenID = new AtomicReference<>();

        return defaultHapiSpec("cannotUseMirrorAddressOfAliasedContractInPrecompileMethod")
                .given(
                        cryptoCreate("Treasury"),
                        sourcing(
                                () ->
                                        createLargeFile(
                                                DEFAULT_PAYER,
                                                ASSOCIATOR,
                                                literalInitcodeFor("Associator"))),
                        withOpContext(
                                (spec, op) ->
                                        allRunFor(
                                                spec,
                                                contractCreate(ASSOCIATOR)
                                                        .payingWith(DEFAULT_PAYER)
                                                        .bytecode(ASSOCIATOR)
                                                        .gas(GAS_TO_OFFER)
                                                        .via(creationTxn))))
                .when(
                        withOpContext(
                                (spec, op) -> {
                                    allRunFor(
                                            spec,
                                            captureChildCreate2MetaFor(
                                                    1,
                                                    0,
                                                    "setup",
                                                    creationTxn,
                                                    childMirror,
                                                    childEip1014),
                                            tokenCreate("TokenA")
                                                    .initialSupply(100)
                                                    .treasury("Treasury")
                                                    .exposingCreatedIdTo(
                                                            id -> tokenID.set(asToken(id))));
                                    final var create2address = childEip1014.get();
                                    final var mirrorAddress = childMirror.get();
                                    allRunFor(
                                            spec,
                                            contractCall(
                                                            ASSOCIATOR,
                                                            "associate",
                                                            asHeadlongAddress(mirrorAddress),
                                                            asHeadlongAddress(
                                                                    asAddress(tokenID.get())))
                                                    .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                                    .gas(GAS_TO_OFFER)
                                                    .via("NOPE"),
                                            childRecordsCheck(
                                                    "NOPE",
                                                    CONTRACT_REVERT_EXECUTED,
                                                    recordWith().status(INVALID_ACCOUNT_ID)),
                                            contractCall(
                                                            ASSOCIATOR,
                                                            "associate",
                                                            asHeadlongAddress(create2address),
                                                            asHeadlongAddress(
                                                                    asAddress(tokenID.get())))
                                                    .gas(GAS_TO_OFFER));
                                }))
                .then();
    }

    @SuppressWarnings("java:S5669")
    private HapiSpec bitcarbonTestStillPasses() {
        final var addressBook = "AddressBook";
        final var jurisdictions = "Jurisdictions";
        final var minters = "Minters";
        final var addJurisTxn = "addJurisTxn";
        final var historicalAddress = "1234567890123456789012345678901234567890";
        final AtomicReference<byte[]> nyJurisCode = new AtomicReference<>();
        final AtomicReference<byte[]> defaultPayerMirror = new AtomicReference<>();
        final AtomicReference<String> addressBookMirror = new AtomicReference<>();
        final AtomicReference<String> jurisdictionMirror = new AtomicReference<>();

        return defaultHapiSpec("BitcarbonTestStillPasses")
                .given(
                        getAccountInfo(DEFAULT_CONTRACT_SENDER)
                                .savingSnapshot(DEFAULT_CONTRACT_SENDER),
                        withOpContext(
                                (spec, opLog) ->
                                        defaultPayerMirror.set(
                                                (unhex(
                                                        spec.registry()
                                                                .getAccountInfo(
                                                                        DEFAULT_CONTRACT_SENDER)
                                                                .getContractAccountID())))),
                        uploadInitCode(addressBook, jurisdictions),
                        contractCreate(addressBook)
                                .exposingNumTo(
                                        num ->
                                                addressBookMirror.set(
                                                        asHexedSolidityAddress(0, 0, num)))
                                .payingWith(DEFAULT_CONTRACT_SENDER),
                        contractCreate(jurisdictions)
                                .exposingNumTo(
                                        num ->
                                                jurisdictionMirror.set(
                                                        asHexedSolidityAddress(0, 0, num)))
                                .withExplicitParams(() -> EXPLICIT_JURISDICTION_CONS_PARAMS)
                                .payingWith(DEFAULT_CONTRACT_SENDER),
                        sourcing(
                                () ->
                                        createLargeFile(
                                                DEFAULT_CONTRACT_SENDER,
                                                minters,
                                                bookInterpolated(
                                                        literalInitcodeFor(minters).toByteArray(),
                                                        addressBookMirror.get()))),
                        contractCreate(minters)
                                .withExplicitParams(
                                        () ->
                                                String.format(
                                                        EXPLICIT_MINTER_CONS_PARAMS_TPL,
                                                        jurisdictionMirror.get()))
                                .payingWith(DEFAULT_CONTRACT_SENDER))
                .when(
                        contractCall(minters)
                                .withExplicitParams(
                                        () ->
                                                String.format(
                                                        EXPLICIT_MINTER_CONFIG_PARAMS_TPL,
                                                        jurisdictionMirror.get())),
                        contractCall(jurisdictions)
                                .withExplicitParams(() -> EXPLICIT_JURISDICTIONS_ADD_PARAMS)
                                .via(addJurisTxn)
                                .gas(1_000_000),
                        getTxnRecord(addJurisTxn)
                                .exposingFilteredCallResultVia(
                                        getABIForContract(jurisdictions),
                                        "JurisdictionAdded",
                                        data -> nyJurisCode.set((byte[]) data.get(0))),
                        sourcing(
                                () ->
                                        logIt(
                                                "NY juris code is "
                                                        + CommonUtils.hex(nyJurisCode.get()))))
                .then(
                        sourcing(
                                () ->
                                        contractCallLocal(
                                                        jurisdictions, "isValid", nyJurisCode.get())
                                                .has(
                                                        resultWith()
                                                                .resultThruAbi(
                                                                        getABIFor(
                                                                                FUNCTION,
                                                                                "isValid",
                                                                                jurisdictions),
                                                                        isLiteralResult(
                                                                                new Object[] {
                                                                                    Boolean.TRUE
                                                                                })))),
                        contractCallLocal(minters, "seven")
                                .has(
                                        resultWith()
                                                .resultThruAbi(
                                                        getABIFor(FUNCTION, "seven", minters),
                                                        isLiteralResult(
                                                                new Object[] {
                                                                    BigInteger.valueOf(7L)
                                                                }))),
                        sourcing(
                                () ->
                                        contractCallLocal(minters, OWNER)
                                                .has(
                                                        resultWith()
                                                                .resultThruAbi(
                                                                        getABIFor(
                                                                                FUNCTION, OWNER,
                                                                                minters),
                                                                        isLiteralResult(
                                                                                new Object[] {
                                                                                    asHeadlongAddress(
                                                                                            defaultPayerMirror
                                                                                                    .get())
                                                                                })))),
                        sourcing(
                                () ->
                                        contractCallLocal(jurisdictions, OWNER)
                                                .has(
                                                        resultWith()
                                                                .resultThruAbi(
                                                                        getABIFor(
                                                                                FUNCTION, OWNER,
                                                                                minters),
                                                                        isLiteralResult(
                                                                                new Object[] {
                                                                                    asHeadlongAddress(
                                                                                            defaultPayerMirror
                                                                                                    .get())
                                                                                })))),
                        sourcing(
                                () ->
                                        contractCall(
                                                        minters,
                                                        "add",
                                                        asHeadlongAddress(historicalAddress),
                                                        "Peter",
                                                        nyJurisCode.get())
                                                .gas(1_000_000)));
    }

    private HapiSpec workingHoursDemo() {
        final var gasToOffer = 4_000_000;
        final var contract = "WorkingHours";
        final var ticketToken = "ticketToken";
        final var adminKey = "admin";
        final var treasury = "treasury";
        final var newSupplyKey = "newSupplyKey";

        final var ticketTaking = "ticketTaking";
        final var ticketWorking = "ticketWorking";
        final var mint = "minting";
        final var burn = "burning";
        final var preMints =
                List.of(ByteString.copyFromUtf8("HELLO"), ByteString.copyFromUtf8("GOODBYE"));

        final AtomicLong ticketSerialNo = new AtomicLong();

        return defaultHapiSpec("WorkingHoursDemo")
                .given(
                        newKeyNamed(adminKey),
                        cryptoCreate(treasury),
                        // we need a new user, expiry to 1 Jan 2100 costs 11M gas for token
                        // associate
                        tokenCreate(ticketToken)
                                .treasury(treasury)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0L)
                                .supplyType(TokenSupplyType.INFINITE)
                                .adminKey(adminKey)
                                .supplyKey(adminKey),
                        mintToken(ticketToken, preMints).via(mint),
                        burnToken(ticketToken, List.of(1L)).via(burn),
                        uploadInitCode(contract))
                .when(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var registry = spec.registry();
                                    final var tokenId = registry.getTokenID(ticketToken);
                                    final var treasuryId = registry.getAccountID(treasury);
                                    final var creation =
                                            contractCreate(
                                                            contract,
                                                            asHeadlongAddress(asAddress(tokenId)),
                                                            asHeadlongAddress(
                                                                    asAddress(treasuryId)))
                                                    .gas(gasToOffer);
                                    allRunFor(spec, creation);
                                }),
                        newKeyNamed(newSupplyKey).shape(KeyShape.CONTRACT.signedWith(contract)),
                        tokenUpdate(ticketToken).supplyKey(newSupplyKey))
                .then(
                        /* Take a ticket */
                        contractCall(contract, "takeTicket")
                                .alsoSigningWithFullPrefix(DEFAULT_CONTRACT_SENDER, treasury)
                                .gas(4_000_000)
                                .via(ticketTaking)
                                .exposingResultTo(
                                        result -> {
                                            LOG.info("Explicit mint result is {}", result);
                                            ticketSerialNo.set(((Long) result[0]));
                                        }),
                        getTxnRecord(ticketTaking),
                        getAccountBalance(DEFAULT_CONTRACT_SENDER).hasTokenBalance(ticketToken, 1L),
                        /* Our ticket number is 3 (b/c of the two pre-mints), so we must call
                         * work twice before the contract will actually accept our ticket. */
                        sourcing(
                                () ->
                                        contractCall(contract, "workTicket", ticketSerialNo.get())
                                                .gas(2_000_000)
                                                .alsoSigningWithFullPrefix(
                                                        DEFAULT_CONTRACT_SENDER)),
                        getAccountBalance(DEFAULT_CONTRACT_SENDER).hasTokenBalance(ticketToken, 1L),
                        sourcing(
                                () ->
                                        contractCall(contract, "workTicket", ticketSerialNo.get())
                                                .gas(2_000_000)
                                                .alsoSigningWithFullPrefix(DEFAULT_CONTRACT_SENDER)
                                                .via(ticketWorking)),
                        getAccountBalance(DEFAULT_CONTRACT_SENDER).hasTokenBalance(ticketToken, 0L),
                        getTokenInfo(ticketToken).hasTotalSupply(1L),
                        /* Review the history */
                        getTxnRecord(ticketTaking).andAllChildRecords().logged(),
                        getTxnRecord(ticketWorking).andAllChildRecords().logged());
    }

    private HapiSpec canMintAndTransferInSameContractOperation() {
        final AtomicReference<String> tokenMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> aCivilianMirrorAddr = new AtomicReference<>();
        final var nfToken = "nfToken";
        final var multiKey = "multiKey";
        final var aCivilian = "aCivilian";
        final var treasuryContract = "SomeERC721Scenarios";
        final var mintAndTransferTxn = "mintAndTransferTxn";
        final var mintAndTransferAndBurnTxn = "mintAndTransferAndBurnTxn";

        return defaultHapiSpec("CanMintAndTransferInSameContractOperation")
                .given(
                        newKeyNamed(multiKey),
                        cryptoCreate(aCivilian)
                                .exposingCreatedIdTo(
                                        id -> aCivilianMirrorAddr.set(asHexedSolidityAddress(id))),
                        uploadInitCode(treasuryContract),
                        contractCreate(treasuryContract).adminKey(multiKey),
                        tokenCreate(nfToken)
                                .supplyKey(multiKey)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(treasuryContract)
                                .initialSupply(0)
                                .exposingCreatedIdTo(
                                        idLit ->
                                                tokenMirrorAddr.set(
                                                        asHexedSolidityAddress(
                                                                HapiPropertySource.asToken(
                                                                        idLit)))),
                        mintToken(
                                nfToken,
                                List.of(
                                        // 1
                                        ByteString.copyFromUtf8("A penny for"),
                                        // 2
                                        ByteString.copyFromUtf8("the Old Guy"))),
                        tokenAssociate(aCivilian, nfToken),
                        cryptoTransfer(
                                movingUnique(nfToken, 2L).between(treasuryContract, aCivilian)))
                .when(
                        sourcing(
                                () ->
                                        contractCall(
                                                        treasuryContract,
                                                        "nonSequiturMintAndTransfer",
                                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                                        asHeadlongAddress(
                                                                aCivilianMirrorAddr.get()))
                                                .via(mintAndTransferTxn)
                                                .gas(4_000_000)
                                                .alsoSigningWithFullPrefix(multiKey)))
                .then(
                        getTokenInfo(nfToken).hasTotalSupply(4L),
                        getTokenNftInfo(nfToken, 3L)
                                .hasSerialNum(3L)
                                .hasAccountID(aCivilian)
                                .hasMetadata(ByteString.copyFrom(new byte[] {(byte) 0xee})),
                        getTokenNftInfo(nfToken, 4L)
                                .hasSerialNum(4L)
                                .hasAccountID(aCivilian)
                                .hasMetadata(ByteString.copyFrom(new byte[] {(byte) 0xff})),
                        sourcing(
                                () ->
                                        contractCall(
                                                        treasuryContract,
                                                        "nonSequiturMintAndTransferAndBurn",
                                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                                        asHeadlongAddress(
                                                                aCivilianMirrorAddr.get()))
                                                .via(mintAndTransferAndBurnTxn)
                                                .gas(4_000_000)
                                                .alsoSigningWithFullPrefix(multiKey, aCivilian)));
    }

    private HapiSpec exchangeRatePrecompileWorks() {
        final var valueToTinycentCall = "recoverUsd";
        final var rateAware = "ExchangeRatePrecompile";
        // Must send $6.66 USD to access the gated method
        final var minPriceToAccessGatedMethod = 666L;
        final var minValueToAccessGatedMethodAtCurrentRate = new AtomicLong();

        return defaultHapiSpec("ExchangeRatePrecompileWorks")
                .given(
                        uploadInitCode(rateAware),
                        contractCreate(rateAware, BigInteger.valueOf(minPriceToAccessGatedMethod)),
                        withOpContext(
                                (spec, opLog) -> {
                                    final var rates = spec.ratesProvider().rates();
                                    minValueToAccessGatedMethodAtCurrentRate.set(
                                            minPriceToAccessGatedMethod
                                                    * TINY_PARTS_PER_WHOLE
                                                    * rates.getHbarEquiv()
                                                    / rates.getCentEquiv());
                                    LOG.info(
                                            "Requires {} tinybar of value to access the method",
                                            minValueToAccessGatedMethodAtCurrentRate::get);
                                }))
                .when(
                        sourcing(
                                () ->
                                        contractCall(rateAware, "gatedAccess")
                                                .sending(
                                                        minValueToAccessGatedMethodAtCurrentRate
                                                                        .get()
                                                                - 1)
                                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        sourcing(
                                () ->
                                        contractCall(rateAware, "gatedAccess")
                                                .sending(
                                                        minValueToAccessGatedMethodAtCurrentRate
                                                                .get())))
                .then(
                        sourcing(
                                () ->
                                        contractCall(rateAware, "approxUsdValue")
                                                .sending(
                                                        minValueToAccessGatedMethodAtCurrentRate
                                                                .get())
                                                .via(valueToTinycentCall)),
                        getTxnRecord(valueToTinycentCall)
                                .hasPriority(
                                        recordWith()
                                                .contractCallResult(
                                                        resultWith()
                                                                .resultViaFunctionName(
                                                                        "approxUsdValue",
                                                                        rateAware,
                                                                        isLiteralResult(
                                                                                new Object[] {
                                                                                    BigInteger
                                                                                            .valueOf(
                                                                                                    minPriceToAccessGatedMethod
                                                                                                            * TINY_PARTS_PER_WHOLE)
                                                                                }))))
                                .logged(),
                        sourcing(
                                () ->
                                        contractCall(rateAware, "invalidCall")
                                                .sending(
                                                        minValueToAccessGatedMethodAtCurrentRate
                                                                .get())
                                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)));
    }

    /**
     * This test characterizes a difference in behavior between the ERC721 {@code tokenURI()} and
     * HTS {@code getNonFungibleTokenInfo()} methods. The HTS method will leave non-UTF-8 bytes
     * as-is, while the ERC721 method will replace them with the Unicode replacement character.
     *
     * @return a spec characterizing this behavior
     */
    @SuppressWarnings("java:S5960")
    private HapiSpec erc721TokenUriAndHtsNftInfoTreatNonUtf8BytesDifferently() {
        final var contractAlternatives = "ErcAndHtsAlternatives";
        final AtomicReference<Address> nftAddr = new AtomicReference<>();
        final var viaErc721TokenURI = "erc721TokenURI";
        final var viaHtsNftInfo = "viaHtsNftInfo";
        // Valid UTF-8 bytes cannot include 0xff
        final var hexedNonUtf8Meta = "ff";

        return defaultHapiSpec("Erc721TokenUriAndHtsNftInfoSeeSameMetadata")
                .given(
                        uploadInitCode(contractAlternatives),
                        contractCreate(contractAlternatives),
                        tokenCreate("nft")
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .exposingAddressTo(nftAddr::set)
                                .initialSupply(0)
                                .supplyKey(DEFAULT_PAYER)
                                .treasury(DEFAULT_PAYER),
                        mintToken(
                                "nft",
                                List.of(ByteString.copyFrom(CommonUtils.unhex(hexedNonUtf8Meta)))))
                .when(
                        sourcing(
                                () ->
                                        contractCall(
                                                        contractAlternatives,
                                                        "canGetMetadataViaERC",
                                                        nftAddr.get(),
                                                        BigInteger.valueOf(1))
                                                .via(viaErc721TokenURI)),
                        sourcing(
                                () ->
                                        contractCall(
                                                        contractAlternatives,
                                                        "canGetMetadataViaHTS",
                                                        nftAddr.get(),
                                                        BigInteger.valueOf(1))
                                                .via(viaHtsNftInfo)
                                                .gas(1_000_000)))
                .then(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var getErcResult = getTxnRecord(viaErc721TokenURI);
                                    final var getHtsResult = getTxnRecord(viaHtsNftInfo);
                                    CustomSpecAssert.allRunFor(spec, getErcResult, getHtsResult);

                                    ABIType<Tuple> decoder = TypeFactory.create("(bytes)");

                                    final var htsResult =
                                            getHtsResult
                                                    .getResponseRecord()
                                                    .getContractCallResult()
                                                    .getContractCallResult();
                                    final var htsMetadata = decoder.decode(htsResult.toByteArray());
                                    // The HTS method leaves non-UTF-8 bytes as-is
                                    Assertions.assertEquals(
                                            hexedNonUtf8Meta, CommonUtils.hex(htsMetadata.get(0)));

                                    final var ercResult =
                                            getErcResult
                                                    .getResponseRecord()
                                                    .getContractCallResult()
                                                    .getContractCallResult();
                                    // But the ERC721 method returns the Unicode replacement
                                    // character
                                    final var ercMetadata = decoder.decode(ercResult.toByteArray());
                                    Assertions.assertEquals(
                                            "efbfbd", CommonUtils.hex(ercMetadata.get(0)));
                                }));
    }

    private HapiSpec imapUserExercise() {
        final var contract = "User";
        final var insert1To4 = "insert1To10";
        final var insert2To8 = "insert2To8";
        final var insert3To16 = "insert3To16";
        final var remove2 = "remove2";
        final var gasToOffer = 400_000;

        return defaultHapiSpec("ImapUserExercise")
                .given(uploadInitCode(contract), contractCreate(contract))
                .when()
                .then(
                        contractCall(contract, INSERT, BigInteger.ONE, BigInteger.valueOf(4))
                                .gas(gasToOffer)
                                .via(insert1To4),
                        contractCall(contract, INSERT, BigInteger.TWO, BigInteger.valueOf(8))
                                .gas(gasToOffer)
                                .via(insert2To8),
                        contractCall(
                                        contract,
                                        INSERT,
                                        BigInteger.valueOf(3),
                                        BigInteger.valueOf(16))
                                .gas(gasToOffer)
                                .via(insert3To16),
                        contractCall(contract, "remove", BigInteger.TWO)
                                .gas(gasToOffer)
                                .via(remove2));
    }

    // For this test we use refusingEthConversion() for the Eth Call isomer,
    // since we should modify the expected balances and change the test itself in order to pass with
    // Eth Calls
    HapiSpec ocToken() {
        final var contract = "OcToken";

        return defaultHapiSpec("ocToken")
                .given(
                        cryptoCreate(TOKEN_ISSUER).balance(1_000_000_000_000L),
                        cryptoCreate(ALICE).balance(10_000_000_000L).payingWith(TOKEN_ISSUER),
                        cryptoCreate("Bob").balance(10_000_000_000L).payingWith(TOKEN_ISSUER),
                        cryptoCreate("Carol").balance(10_000_000_000L).payingWith(TOKEN_ISSUER),
                        cryptoCreate("Dave").balance(10_000_000_000L).payingWith(TOKEN_ISSUER),
                        getAccountInfo(TOKEN_ISSUER).savingSnapshot("tokenIssuerAcctInfo"),
                        getAccountInfo(ALICE).savingSnapshot("AliceAcctInfo"),
                        getAccountInfo("Bob").savingSnapshot("BobAcctInfo"),
                        getAccountInfo("Carol").savingSnapshot("CarolAcctInfo"),
                        getAccountInfo("Dave").savingSnapshot("DaveAcctInfo"),
                        uploadInitCode(contract),
                        contractCreate(
                                        contract,
                                        BigInteger.valueOf(1_000_000L),
                                        "OpenCrowd Token",
                                        "OCT")
                                .gas(250_000L)
                                .payingWith(TOKEN_ISSUER)
                                .via("tokenCreateTxn")
                                .logged())
                .when(
                        assertionsHold(
                                (spec, ctxLog) -> {
                                    final var issuerEthAddress =
                                            spec.registry()
                                                    .getAccountInfo("tokenIssuerAcctInfo")
                                                    .getContractAccountID();
                                    final var aliceEthAddress =
                                            spec.registry()
                                                    .getAccountInfo("AliceAcctInfo")
                                                    .getContractAccountID();
                                    final var bobEthAddress =
                                            spec.registry()
                                                    .getAccountInfo("BobAcctInfo")
                                                    .getContractAccountID();
                                    final var carolEthAddress =
                                            spec.registry()
                                                    .getAccountInfo("CarolAcctInfo")
                                                    .getContractAccountID();
                                    final var daveEthAddress =
                                            spec.registry()
                                                    .getAccountInfo("DaveAcctInfo")
                                                    .getContractAccountID();

                                    final var subop1 =
                                            getContractInfo(contract)
                                                    .nodePayment(10L)
                                                    .saveToRegistry("tokenContract");

                                    final var subop3 =
                                            contractCallLocal(contract, DECIMALS)
                                                    .saveResultTo(DECIMALS)
                                                    .payingWith(TOKEN_ISSUER);

                                    // Note: This contract call will cause a INSUFFICIENT_TX_FEE
                                    // error, not sure why.
                                    final var subop4 =
                                            contractCallLocal(contract, "symbol")
                                                    .saveResultTo("token_symbol")
                                                    .payingWith(TOKEN_ISSUER)
                                                    .hasAnswerOnlyPrecheckFrom(
                                                            OK, INSUFFICIENT_TX_FEE);

                                    final var subop5 =
                                            contractCallLocal(
                                                            contract,
                                                            BALANCE_OF,
                                                            asHeadlongAddress(issuerEthAddress))
                                                    .gas(250_000L)
                                                    .saveResultTo(ISSUER_TOKEN_BALANCE);

                                    allRunFor(spec, subop1, subop3, subop4, subop5);

                                    final var funcSymbol =
                                            Function.fromJson(
                                                    getABIFor(FUNCTION, "symbol", contract));

                                    final var symbol =
                                            getValueFromRegistry(spec, "token_symbol", funcSymbol);

                                    ctxLog.info("symbol: [{}]", symbol);

                                    Assertions.assertEquals(
                                            "",
                                            symbol,
                                            "TokenIssuer's symbol should be fixed value"); // should
                                    // be
                                    // "OCT"
                                    // as
                                    // expected
                                    final var funcDecimals =
                                            Function.fromJson(
                                                    getABIFor(FUNCTION, DECIMALS, contract));

                                    final Integer decimals =
                                            getValueFromRegistry(spec, DECIMALS, funcDecimals);

                                    ctxLog.info("decimals {}", decimals);
                                    Assertions.assertEquals(
                                            3,
                                            decimals,
                                            "TokenIssuer's decimals should be fixed value");

                                    final long tokenMultiplier = (long) Math.pow(10, decimals);

                                    final var function =
                                            Function.fromJson(
                                                    getABIFor(FUNCTION, BALANCE_OF, contract));

                                    long issuerBalance =
                                            ((BigInteger)
                                                            getValueFromRegistry(
                                                                    spec,
                                                                    ISSUER_TOKEN_BALANCE,
                                                                    function))
                                                    .longValue();

                                    ctxLog.info(
                                            "initial balance of Issuer {}",
                                            issuerBalance / tokenMultiplier);
                                    Assertions.assertEquals(
                                            1_000_000,
                                            issuerBalance / tokenMultiplier,
                                            "TokenIssuer's initial token balance should be"
                                                    + " 1_000_000");

                                    //  Do token transfers
                                    final var subop6 =
                                            contractCall(
                                                            contract,
                                                            TRANSFER,
                                                            asHeadlongAddress(aliceEthAddress),
                                                            BigInteger.valueOf(
                                                                    1000 * tokenMultiplier))
                                                    .gas(250_000L)
                                                    .payingWith(TOKEN_ISSUER)
                                                    .refusingEthConversion();

                                    final var subop7 =
                                            contractCall(
                                                            contract,
                                                            TRANSFER,
                                                            asHeadlongAddress(bobEthAddress),
                                                            BigInteger.valueOf(
                                                                    2000 * tokenMultiplier))
                                                    .gas(250_000L)
                                                    .payingWith(TOKEN_ISSUER)
                                                    .refusingEthConversion();

                                    final var subop8 =
                                            contractCall(
                                                            contract,
                                                            TRANSFER,
                                                            asHeadlongAddress(carolEthAddress),
                                                            BigInteger.valueOf(
                                                                    500 * tokenMultiplier))
                                                    .gas(250_000L)
                                                    .payingWith("Bob")
                                                    .refusingEthConversion();

                                    final var subop9 =
                                            contractCallLocal(
                                                            contract,
                                                            BALANCE_OF,
                                                            asHeadlongAddress(aliceEthAddress))
                                                    .gas(250_000L)
                                                    .saveResultTo(ALICE_TOKEN_BALANCE);

                                    final var subop10 =
                                            contractCallLocal(
                                                            contract,
                                                            BALANCE_OF,
                                                            asHeadlongAddress(carolEthAddress))
                                                    .gas(250_000L)
                                                    .saveResultTo(CAROL_TOKEN_BALANCE);

                                    final var subop11 =
                                            contractCallLocal(
                                                            contract,
                                                            BALANCE_OF,
                                                            asHeadlongAddress(bobEthAddress))
                                                    .gas(250_000L)
                                                    .saveResultTo(BOB_TOKEN_BALANCE);

                                    allRunFor(
                                            spec, subop6, subop7, subop8, subop9, subop10, subop11);

                                    var aliceBalance =
                                            ((BigInteger)
                                                            getValueFromRegistry(
                                                                    spec,
                                                                    ALICE_TOKEN_BALANCE,
                                                                    function))
                                                    .longValue();
                                    var bobBalance =
                                            ((BigInteger)
                                                            getValueFromRegistry(
                                                                    spec,
                                                                    BOB_TOKEN_BALANCE,
                                                                    function))
                                                    .longValue();
                                    var carolBalance =
                                            ((BigInteger)
                                                            getValueFromRegistry(
                                                                    spec,
                                                                    CAROL_TOKEN_BALANCE,
                                                                    function))
                                                    .longValue();

                                    ctxLog.info("aliceBalance  {}", aliceBalance / tokenMultiplier);
                                    ctxLog.info("bobBalance  {}", bobBalance / tokenMultiplier);
                                    ctxLog.info("carolBalance  {}", carolBalance / tokenMultiplier);

                                    Assertions.assertEquals(
                                            1000,
                                            aliceBalance / tokenMultiplier,
                                            "Alice's token balance should be 1_000");

                                    final var subop12 =
                                            contractCall(
                                                            contract,
                                                            "approve",
                                                            asHeadlongAddress(daveEthAddress),
                                                            BigInteger.valueOf(
                                                                    200 * tokenMultiplier))
                                                    .gas(250_000L)
                                                    .payingWith(ALICE)
                                                    .refusingEthConversion();

                                    final var subop13 =
                                            contractCall(
                                                            contract,
                                                            "transferFrom",
                                                            asHeadlongAddress(aliceEthAddress),
                                                            asHeadlongAddress(bobEthAddress),
                                                            BigInteger.valueOf(
                                                                    100 * tokenMultiplier))
                                                    .gas(250_000L)
                                                    .payingWith("Dave")
                                                    .refusingEthConversion();

                                    final var subop14 =
                                            contractCallLocal(
                                                            contract,
                                                            BALANCE_OF,
                                                            asHeadlongAddress(aliceEthAddress))
                                                    .gas(250_000L)
                                                    .saveResultTo(ALICE_TOKEN_BALANCE);

                                    final var subop15 =
                                            contractCallLocal(
                                                            contract,
                                                            BALANCE_OF,
                                                            asHeadlongAddress(bobEthAddress))
                                                    .gas(250_000L)
                                                    .saveResultTo(BOB_TOKEN_BALANCE);

                                    final var subop16 =
                                            contractCallLocal(
                                                            contract,
                                                            BALANCE_OF,
                                                            asHeadlongAddress(carolEthAddress))
                                                    .gas(250_000L)
                                                    .saveResultTo(CAROL_TOKEN_BALANCE);

                                    final var subop17 =
                                            contractCallLocal(
                                                            contract,
                                                            BALANCE_OF,
                                                            asHeadlongAddress(daveEthAddress))
                                                    .gas(250_000L)
                                                    .saveResultTo("daveTokenBalance");

                                    final var subop18 =
                                            contractCallLocal(
                                                            contract,
                                                            BALANCE_OF,
                                                            asHeadlongAddress(issuerEthAddress))
                                                    .gas(250_000L)
                                                    .saveResultTo(ISSUER_TOKEN_BALANCE);

                                    allRunFor(
                                            spec, subop12, subop13, subop14, subop15, subop16,
                                            subop17, subop18);

                                    final var daveBalance =
                                            ((BigInteger)
                                                            getValueFromRegistry(
                                                                    spec,
                                                                    "daveTokenBalance",
                                                                    function))
                                                    .longValue();
                                    aliceBalance =
                                            ((BigInteger)
                                                            getValueFromRegistry(
                                                                    spec,
                                                                    ALICE_TOKEN_BALANCE,
                                                                    function))
                                                    .longValue();
                                    bobBalance =
                                            ((BigInteger)
                                                            getValueFromRegistry(
                                                                    spec,
                                                                    BOB_TOKEN_BALANCE,
                                                                    function))
                                                    .longValue();
                                    carolBalance =
                                            ((BigInteger)
                                                            getValueFromRegistry(
                                                                    spec,
                                                                    CAROL_TOKEN_BALANCE,
                                                                    function))
                                                    .longValue();
                                    issuerBalance =
                                            ((BigInteger)
                                                            getValueFromRegistry(
                                                                    spec,
                                                                    ISSUER_TOKEN_BALANCE,
                                                                    function))
                                                    .longValue();

                                    ctxLog.info(
                                            "aliceBalance at end {}",
                                            aliceBalance / tokenMultiplier);
                                    ctxLog.info(
                                            "bobBalance at end {}", bobBalance / tokenMultiplier);
                                    ctxLog.info(
                                            "carolBalance at end {}",
                                            carolBalance / tokenMultiplier);
                                    ctxLog.info(
                                            "daveBalance at end {}", daveBalance / tokenMultiplier);
                                    ctxLog.info(
                                            "issuerBalance at end {}",
                                            issuerBalance / tokenMultiplier);

                                    Assertions.assertEquals(
                                            997000,
                                            issuerBalance / tokenMultiplier,
                                            "TokenIssuer's final balance should be 997000");

                                    Assertions.assertEquals(
                                            900,
                                            aliceBalance / tokenMultiplier,
                                            "Alice's final balance should be 900");
                                    Assertions.assertEquals(
                                            1600,
                                            bobBalance / tokenMultiplier,
                                            "Bob's final balance should be 1600");
                                    Assertions.assertEquals(
                                            500,
                                            carolBalance / tokenMultiplier,
                                            "Carol's final balance should be 500");
                                    Assertions.assertEquals(
                                            0,
                                            daveBalance / tokenMultiplier,
                                            "Dave's final balance should be 0");
                                }))
                .then(
                        getContractRecords(contract).hasCostAnswerPrecheck(NOT_SUPPORTED),
                        getContractRecords(contract)
                                .nodePayment(100L)
                                .hasAnswerOnlyPrecheck(NOT_SUPPORTED));
    }

    private <T> T getValueFromRegistry(HapiSpec spec, String from, Function function) {
        byte[] value = spec.registry().getBytes(from);

        T decodedReturnedValue;
        if (function.getOutputs().equals(TupleType.parse("(string)"))) {
            decodedReturnedValue = (T) "";
        } else {
            decodedReturnedValue = (T) new byte[0];
        }

        if (value.length > 0) {
            Tuple retResults = function.decodeReturn(value);
            decodedReturnedValue = (T) retResults.get(0);
        }
        return decodedReturnedValue;
    }

    HapiSpec smartContractInlineAssemblyCheck() {
        final var inlineTestContract = "InlineTest";

        return defaultHapiSpec("smartContractInlineAssemblyCheck")
                .given(
                        cryptoCreate(PAYER).balance(10_000_000_000_000L),
                        uploadInitCode(SIMPLE_STORAGE_CONTRACT, inlineTestContract))
                .when(contractCreate(SIMPLE_STORAGE_CONTRACT), contractCreate(inlineTestContract))
                .then(
                        assertionsHold(
                                (spec, ctxLog) -> {
                                    final var subop1 =
                                            getContractInfo(SIMPLE_STORAGE_CONTRACT)
                                                    .nodePayment(10L)
                                                    .saveToRegistry("simpleStorageKey");

                                    final var subop2 =
                                            getAccountInfo(PAYER)
                                                    .savingSnapshot("payerAccountInfo");
                                    allRunFor(spec, subop1, subop2);

                                    final var simpleStorageContractInfo =
                                            spec.registry().getContractInfo("simpleStorageKey");
                                    final var contractAddress =
                                            simpleStorageContractInfo.getContractAccountID();

                                    final var subop3 =
                                            contractCallLocal(
                                                            inlineTestContract,
                                                            GET_CODE_SIZE,
                                                            asHeadlongAddress(contractAddress))
                                                    .saveResultTo(
                                                            "simpleStorageContractCodeSizeBytes")
                                                    .gas(300_000L);

                                    allRunFor(spec, subop3);

                                    var result =
                                            spec.registry()
                                                    .getBytes("simpleStorageContractCodeSizeBytes");

                                    final var funcJson =
                                            getABIFor(FUNCTION, GET_CODE_SIZE, inlineTestContract)
                                                    .replace("'", "\"");
                                    final var function = Function.fromJson(funcJson);

                                    var codeSize = 0;
                                    if (result != null && result.length > 0) {
                                        final var retResults = function.decodeReturn(result);
                                        if (retResults != null && retResults.size() > 0) {
                                            final var retBi = (BigInteger) retResults.get(0);
                                            codeSize = retBi.intValue();
                                        }
                                    }

                                    ctxLog.info("Contract code size {}", codeSize);
                                    Assertions.assertNotEquals(
                                            0,
                                            codeSize,
                                            "Real smart contract code size should be greater than"
                                                    + " 0");

                                    final var payerAccountInfo =
                                            spec.registry().getAccountInfo("payerAccountInfo");
                                    final var acctAddress = payerAccountInfo.getContractAccountID();

                                    final var subop4 =
                                            contractCallLocal(
                                                            inlineTestContract,
                                                            GET_CODE_SIZE,
                                                            asHeadlongAddress(acctAddress))
                                                    .saveResultTo("fakeCodeSizeBytes")
                                                    .gas(300_000L);

                                    allRunFor(spec, subop4);
                                    result = spec.registry().getBytes("fakeCodeSizeBytes");

                                    codeSize = 0;
                                    if (result != null && result.length > 0) {
                                        final var retResults = function.decodeReturn(result);
                                        if (retResults != null && retResults.size() > 0) {
                                            final var retBi = (BigInteger) retResults.get(0);
                                            codeSize = retBi.intValue();
                                        }
                                    }

                                    ctxLog.info("Fake contract code size {}", codeSize);
                                    Assertions.assertEquals(
                                            0, codeSize, "Fake contract code size should be 0");
                                }));
    }

    private HapiSpec multipleSelfDestructsAreSafe() {
        final var contract = "Fuse";
        return defaultHapiSpec("MultipleSelfDestructsAreSafe")
                .given(uploadInitCode(contract), contractCreate(contract).gas(300_000))
                .when(contractCall(contract, "light").via("lightTxn").scrambleTxnBody(tx -> tx))
                .then(getTxnRecord("lightTxn").logged());
    }

    HapiSpec depositSuccess() {
        return defaultHapiSpec("DepositSuccess")
                .given(
                        uploadInitCode(PAY_RECEIVABLE_CONTRACT),
                        contractCreate(PAY_RECEIVABLE_CONTRACT).adminKey(THRESHOLD))
                .when(
                        contractCall(
                                        PAY_RECEIVABLE_CONTRACT,
                                        DEPOSIT,
                                        BigInteger.valueOf(DEPOSIT_AMOUNT))
                                .via(PAY_TXN)
                                .sending(DEPOSIT_AMOUNT))
                .then(
                        getTxnRecord(PAY_TXN)
                                .hasPriority(
                                        recordWith()
                                                .contractCallResult(resultWith().logs(inOrder()))));
    }

    HapiSpec multipleDepositSuccess() {
        return defaultHapiSpec("MultipleDepositSuccess")
                .given(
                        uploadInitCode(PAY_RECEIVABLE_CONTRACT),
                        contractCreate(PAY_RECEIVABLE_CONTRACT).adminKey(THRESHOLD))
                .when()
                .then(
                        withOpContext(
                                (spec, opLog) -> {
                                    for (int i = 0; i < 10; i++) {
                                        final var subOp1 =
                                                balanceSnapshot(
                                                        "payerBefore", PAY_RECEIVABLE_CONTRACT);
                                        final var subOp2 =
                                                contractCall(
                                                                PAY_RECEIVABLE_CONTRACT,
                                                                DEPOSIT,
                                                                BigInteger.valueOf(DEPOSIT_AMOUNT))
                                                        .via(PAY_TXN)
                                                        .sending(DEPOSIT_AMOUNT);
                                        final var subOp3 =
                                                getAccountBalance(PAY_RECEIVABLE_CONTRACT)
                                                        .hasTinyBars(
                                                                changeFromSnapshot(
                                                                        "payerBefore",
                                                                        +DEPOSIT_AMOUNT));
                                        allRunFor(spec, subOp1, subOp2, subOp3);
                                    }
                                }));
    }

    HapiSpec depositDeleteSuccess() {
        final var initBalance = 7890L;
        return defaultHapiSpec("DepositDeleteSuccess")
                .given(
                        cryptoCreate(BENEFICIARY).balance(initBalance),
                        uploadInitCode(PAY_RECEIVABLE_CONTRACT),
                        contractCreate(PAY_RECEIVABLE_CONTRACT).adminKey(THRESHOLD))
                .when(
                        contractCall(
                                        PAY_RECEIVABLE_CONTRACT,
                                        DEPOSIT,
                                        BigInteger.valueOf(DEPOSIT_AMOUNT))
                                .via(PAY_TXN)
                                .sending(DEPOSIT_AMOUNT))
                .then(
                        contractDelete(PAY_RECEIVABLE_CONTRACT).transferAccount(BENEFICIARY),
                        getAccountBalance(BENEFICIARY).hasTinyBars(initBalance + DEPOSIT_AMOUNT));
    }

    HapiSpec associationAcknowledgedInApprovePrecompile() {
        final var token = "TOKEN";
        final var spender = "SPENDER";
        final AtomicReference<Address> tokenAddress = new AtomicReference<>();
        final AtomicReference<Address> spenderAddress = new AtomicReference<>();
        return defaultHapiSpec("AssociationAcknowledgedInApprovePrecompile")
                .given(
                        cryptoCreate(spender)
                                .balance(123 * ONE_HUNDRED_HBARS)
                                .keyShape(SigControl.SECP256K1_ON)
                                .exposingEvmAddressTo(spenderAddress::set),
                        tokenCreate(token)
                                .initialSupply(1000)
                                .treasury(spender)
                                .exposingAddressTo(tokenAddress::set))
                .when(
                        uploadInitCode(TEST_APPROVER),
                        sourcing(
                                () ->
                                        contractCreate(
                                                        TEST_APPROVER,
                                                        tokenAddress.get(),
                                                        spenderAddress.get())
                                                .gas(5_000_000)
                                                .payingWith(spender)
                                                .hasKnownStatus(SUCCESS)))
                .then();
    }

    HapiSpec payableSuccess() {
        return defaultHapiSpec("PayableSuccess")
                .given(
                        uploadInitCode(PAY_RECEIVABLE_CONTRACT),
                        contractCreate(PAY_RECEIVABLE_CONTRACT).adminKey(THRESHOLD).gas(1_000_000))
                .when(contractCall(PAY_RECEIVABLE_CONTRACT).via(PAY_TXN).sending(DEPOSIT_AMOUNT))
                .then(
                        getTxnRecord(PAY_TXN)
                                .hasPriority(
                                        recordWith()
                                                .contractCallResult(
                                                        resultWith()
                                                                .logs(
                                                                        inOrder(
                                                                                logWith()
                                                                                        .longAtBytes(
                                                                                                DEPOSIT_AMOUNT,
                                                                                                24))))));
    }

    HapiSpec callingDestructedContractReturnsStatusDeleted() {
        return defaultHapiSpec("CallingDestructedContractReturnsStatusDeleted")
                .given(uploadInitCode(SIMPLE_UPDATE_CONTRACT))
                .when(
                        contractCreate(SIMPLE_UPDATE_CONTRACT).gas(300_000L),
                        contractCall(
                                        SIMPLE_UPDATE_CONTRACT,
                                        "set",
                                        BigInteger.valueOf(5),
                                        BigInteger.valueOf(42))
                                .gas(300_000L),
                        contractCall(
                                        SIMPLE_UPDATE_CONTRACT,
                                        "del",
                                        asHeadlongAddress(
                                                "0x0000000000000000000000000000000000000002"))
                                .gas(1_000_000L))
                .then(
                        contractCall(
                                        SIMPLE_UPDATE_CONTRACT,
                                        "set",
                                        BigInteger.valueOf(15),
                                        BigInteger.valueOf(434))
                                .gas(350_000L)
                                .hasKnownStatus(CONTRACT_DELETED));
    }

    HapiSpec insufficientGas() {
        return defaultHapiSpec("InsufficientGas")
                .given(
                        uploadInitCode(SIMPLE_STORAGE_CONTRACT),
                        contractCreate(SIMPLE_STORAGE_CONTRACT).adminKey(THRESHOLD),
                        getContractInfo(SIMPLE_STORAGE_CONTRACT)
                                .saveToRegistry("simpleStorageInfo"))
                .when()
                .then(
                        contractCall(SIMPLE_STORAGE_CONTRACT, "get")
                                .via("simpleStorageTxn")
                                .gas(0L)
                                .hasKnownStatus(INSUFFICIENT_GAS),
                        getTxnRecord("simpleStorageTxn").logged());
    }

    HapiSpec insufficientFee() {
        final var contract = CREATE_TRIVIAL;

        return defaultHapiSpec("InsufficientFee")
                .given(
                        cryptoCreate("accountToPay"),
                        uploadInitCode(contract),
                        contractCreate(contract))
                .when()
                .then(
                        contractCall(contract, "create")
                                .fee(0L)
                                .payingWith("accountToPay")
                                .hasPrecheck(INSUFFICIENT_TX_FEE));
    }

    HapiSpec nonPayable() {
        final var contract = CREATE_TRIVIAL;

        return defaultHapiSpec("NonPayable")
                .given(uploadInitCode(contract), contractCreate(contract))
                .when(
                        contractCall(contract, "create")
                                .via("callTxn")
                                .sending(DEPOSIT_AMOUNT)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED))
                .then(
                        getTxnRecord("callTxn")
                                .hasPriority(
                                        recordWith()
                                                .contractCallResult(resultWith().logs(inOrder()))));
    }

    HapiSpec invalidContract() {
        final var function = getABIFor(FUNCTION, "getIndirect", CREATE_TRIVIAL);

        return defaultHapiSpec("InvalidContract")
                .given(
                        withOpContext(
                                (spec, ctxLog) ->
                                        spec.registry()
                                                .saveContractId("invalid", asContract("1.1.1"))))
                .when()
                .then(
                        contractCallWithFunctionAbi("invalid", function)
                                .hasKnownStatus(INVALID_CONTRACT_ID));
    }

    HapiSpec smartContractFailFirst() {
        final var civilian = "civilian";
        return defaultHapiSpec("smartContractFailFirst")
                .given(
                        uploadInitCode(SIMPLE_STORAGE_CONTRACT),
                        cryptoCreate(civilian).balance(ONE_MILLION_HBARS).payingWith(GENESIS))
                .when(
                        withOpContext(
                                (spec, ignore) -> {
                                    final var subop1 = balanceSnapshot("balanceBefore0", civilian);
                                    final var subop2 =
                                            contractCreate(SIMPLE_STORAGE_CONTRACT)
                                                    .balance(0)
                                                    .payingWith(civilian)
                                                    .gas(1)
                                                    .hasKnownStatus(INSUFFICIENT_GAS)
                                                    .via(FAIL_INSUFFICIENT_GAS);
                                    final var subop3 = getTxnRecord(FAIL_INSUFFICIENT_GAS);
                                    allRunFor(spec, subop1, subop2, subop3);
                                    final var delta =
                                            subop3.getResponseRecord().getTransactionFee();
                                    final var subop4 =
                                            getAccountBalance(civilian)
                                                    .hasTinyBars(
                                                            changeFromSnapshot(
                                                                    "balanceBefore0", -delta));
                                    allRunFor(spec, subop4);
                                }),
                        withOpContext(
                                (spec, ignore) -> {
                                    final var subop1 = balanceSnapshot("balanceBefore1", civilian);
                                    final var subop2 =
                                            contractCreate(SIMPLE_STORAGE_CONTRACT)
                                                    .balance(100_000_000_000L)
                                                    .payingWith(civilian)
                                                    .gas(250_000L)
                                                    .via(FAIL_INVALID_INITIAL_BALANCE)
                                                    .hasKnownStatus(CONTRACT_REVERT_EXECUTED);
                                    final var subop3 = getTxnRecord(FAIL_INVALID_INITIAL_BALANCE);
                                    allRunFor(spec, subop1, subop2, subop3);
                                    final var delta =
                                            subop3.getResponseRecord().getTransactionFee();
                                    final var subop4 =
                                            getAccountBalance(civilian)
                                                    .hasTinyBars(
                                                            changeFromSnapshot(
                                                                    "balanceBefore1", -delta));
                                    allRunFor(spec, subop4);
                                }),
                        withOpContext(
                                (spec, ignore) -> {
                                    final var subop1 = balanceSnapshot("balanceBefore2", civilian);
                                    final var subop2 =
                                            contractCreate(SIMPLE_STORAGE_CONTRACT)
                                                    .balance(0L)
                                                    .payingWith(civilian)
                                                    .gas(250_000L)
                                                    .hasKnownStatus(SUCCESS)
                                                    .via(SUCCESS_WITH_ZERO_INITIAL_BALANCE);
                                    final var subop3 =
                                            getTxnRecord(SUCCESS_WITH_ZERO_INITIAL_BALANCE);
                                    allRunFor(spec, subop1, subop2, subop3);
                                    final var delta =
                                            subop3.getResponseRecord().getTransactionFee();
                                    final var subop4 =
                                            getAccountBalance(civilian)
                                                    .hasTinyBars(
                                                            changeFromSnapshot(
                                                                    "balanceBefore2", -delta));
                                    allRunFor(spec, subop4);
                                }),
                        withOpContext(
                                (spec, ignore) -> {
                                    final var subop1 = balanceSnapshot("balanceBefore3", civilian);
                                    final var subop2 =
                                            contractCall(
                                                            SIMPLE_STORAGE_CONTRACT,
                                                            "set",
                                                            BigInteger.valueOf(999_999L))
                                                    .payingWith(civilian)
                                                    .gas(300_000L)
                                                    .hasKnownStatus(SUCCESS)
                                                    // ContractCall and EthereumTransaction gas fees
                                                    // differ
                                                    .refusingEthConversion()
                                                    .via("setValue");
                                    final var subop3 = getTxnRecord("setValue");
                                    allRunFor(spec, subop1, subop2, subop3);
                                    final var delta =
                                            subop3.getResponseRecord().getTransactionFee();
                                    final var subop4 =
                                            getAccountBalance(civilian)
                                                    .hasTinyBars(
                                                            changeFromSnapshot(
                                                                    "balanceBefore3", -delta));
                                    allRunFor(spec, subop4);
                                }),
                        withOpContext(
                                (spec, ignore) -> {
                                    final var subop1 = balanceSnapshot("balanceBefore4", civilian);
                                    final var subop2 =
                                            contractCall(SIMPLE_STORAGE_CONTRACT, "get")
                                                    .payingWith(civilian)
                                                    .gas(300_000L)
                                                    .hasKnownStatus(SUCCESS)
                                                    // ContractCall and EthereumTransaction gas fees
                                                    // differ
                                                    .refusingEthConversion()
                                                    .via("getValue");
                                    final var subop3 = getTxnRecord("getValue");
                                    allRunFor(spec, subop1, subop2, subop3);
                                    final var delta =
                                            subop3.getResponseRecord().getTransactionFee();

                                    final var subop4 =
                                            getAccountBalance(civilian)
                                                    .hasTinyBars(
                                                            changeFromSnapshot(
                                                                    "balanceBefore4", -delta));
                                    allRunFor(spec, subop4);
                                }))
                .then(
                        getTxnRecord(FAIL_INSUFFICIENT_GAS),
                        getTxnRecord(SUCCESS_WITH_ZERO_INITIAL_BALANCE),
                        getTxnRecord(FAIL_INVALID_INITIAL_BALANCE));
    }

    HapiSpec payTestSelfDestructCall() {
        final var contract = "PayTestSelfDestruct";

        return defaultHapiSpec("payTestSelfDestructCall")
                .given(
                        cryptoCreate(PAYER).balance(1_000_000_000_000L).logged(),
                        cryptoCreate(RECEIVER).balance(1_000L),
                        uploadInitCode(contract),
                        contractCreate(contract))
                .when(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var subop1 =
                                            contractCall(
                                                            contract,
                                                            DEPOSIT,
                                                            BigInteger.valueOf(1_000L))
                                                    .payingWith(PAYER)
                                                    .gas(300_000L)
                                                    .via(DEPOSIT)
                                                    .sending(1_000L);

                                    final var subop2 =
                                            contractCall(contract, GET_BALANCE)
                                                    .payingWith(PAYER)
                                                    .gas(300_000L)
                                                    .via(GET_BALANCE);

                                    final var contractAccountId = asId(contract, spec);
                                    final var subop3 =
                                            contractCall(
                                                            contract,
                                                            KILL_ME,
                                                            asHeadlongAddress(
                                                                    asAddress(contractAccountId)))
                                                    .payingWith(PAYER)
                                                    .gas(300_000L)
                                                    .hasKnownStatus(OBTAINER_SAME_CONTRACT_ID);

                                    final var subop4 =
                                            contractCall(
                                                            contract,
                                                            KILL_ME,
                                                            asHeadlongAddress(new byte[20]))
                                                    .payingWith(PAYER)
                                                    .gas(300_000L)
                                                    .hasKnownStatus(INVALID_SOLIDITY_ADDRESS);

                                    final var receiverAccountId = asId(RECEIVER, spec);
                                    final var subop5 =
                                            contractCall(
                                                            contract,
                                                            KILL_ME,
                                                            asHeadlongAddress(
                                                                    asAddress(receiverAccountId)))
                                                    .payingWith(PAYER)
                                                    .gas(300_000L)
                                                    .via("selfDestruct")
                                                    .hasKnownStatus(SUCCESS);

                                    allRunFor(spec, subop1, subop2, subop3, subop4, subop5);
                                }))
                .then(
                        getTxnRecord(DEPOSIT),
                        getTxnRecord(GET_BALANCE)
                                .hasPriority(
                                        recordWith()
                                                .contractCallResult(
                                                        resultWith()
                                                                .resultViaFunctionName(
                                                                        GET_BALANCE,
                                                                        contract,
                                                                        isLiteralResult(
                                                                                new Object[] {
                                                                                    BigInteger
                                                                                            .valueOf(
                                                                                                    1_000L)
                                                                                })))),
                        getAccountBalance(RECEIVER).hasTinyBars(2_000L));
    }

    private HapiSpec contractTransferToSigReqAccountWithKeySucceeds() {
        return defaultHapiSpec("ContractTransferToSigReqAccountWithKeySucceeds")
                .given(
                        cryptoCreate(CONTRACT_CALLER).balance(1_000_000_000_000L),
                        cryptoCreate(RECEIVABLE_SIG_REQ_ACCOUNT)
                                .balance(1_000_000_000_000L)
                                .receiverSigRequired(true),
                        getAccountInfo(CONTRACT_CALLER).savingSnapshot("contractCallerInfo"),
                        getAccountInfo(RECEIVABLE_SIG_REQ_ACCOUNT)
                                .savingSnapshot(RECEIVABLE_SIG_REQ_ACCOUNT_INFO),
                        uploadInitCode(TRANSFERRING_CONTRACT))
                .when(contractCreate(TRANSFERRING_CONTRACT).gas(300_000L).balance(5000L))
                .then(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var accountAddress =
                                            spec.registry()
                                                    .getAccountInfo(RECEIVABLE_SIG_REQ_ACCOUNT_INFO)
                                                    .getContractAccountID();
                                    final var receivableAccountKey =
                                            spec.registry()
                                                    .getAccountInfo(RECEIVABLE_SIG_REQ_ACCOUNT_INFO)
                                                    .getKey();
                                    final var contractCallerKey =
                                            spec.registry()
                                                    .getAccountInfo("contractCallerInfo")
                                                    .getKey();
                                    spec.registry().saveKey("receivableKey", receivableAccountKey);
                                    spec.registry().saveKey("contractCallerKey", contractCallerKey);
                                    /* if any of the keys are missing, INVALID_SIGNATURE is returned */
                                    final var call =
                                            contractCall(
                                                            TRANSFERRING_CONTRACT,
                                                            TRANSFER_TO_ADDRESS,
                                                            asHeadlongAddress(accountAddress),
                                                            BigInteger.ONE)
                                                    .payingWith(CONTRACT_CALLER)
                                                    .gas(300_000)
                                                    .alsoSigningWithFullPrefix("receivableKey");
                                    /* calling with the receivableSigReqAccount should pass without adding keys */
                                    final var callWithReceivable =
                                            contractCall(
                                                            TRANSFERRING_CONTRACT,
                                                            TRANSFER_TO_ADDRESS,
                                                            asHeadlongAddress(accountAddress),
                                                            BigInteger.ONE)
                                                    .payingWith(RECEIVABLE_SIG_REQ_ACCOUNT)
                                                    .gas(300_000)
                                                    .hasKnownStatus(SUCCESS);
                                    allRunFor(spec, call, callWithReceivable);
                                }));
    }

    private HapiSpec contractTransferToSigReqAccountWithoutKeyFails() {
        return defaultHapiSpec("ContractTransferToSigReqAccountWithoutKeyFails")
                .given(
                        cryptoCreate(RECEIVABLE_SIG_REQ_ACCOUNT)
                                .balance(1_000_000_000_000L)
                                .receiverSigRequired(true),
                        getAccountInfo(RECEIVABLE_SIG_REQ_ACCOUNT)
                                .savingSnapshot(RECEIVABLE_SIG_REQ_ACCOUNT_INFO),
                        uploadInitCode(TRANSFERRING_CONTRACT))
                .when(contractCreate(TRANSFERRING_CONTRACT).gas(300_000L).balance(5000L))
                .then(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var accountAddress =
                                            spec.registry()
                                                    .getAccountInfo(RECEIVABLE_SIG_REQ_ACCOUNT_INFO)
                                                    .getContractAccountID();
                                    final var call =
                                            contractCall(
                                                            TRANSFERRING_CONTRACT,
                                                            TRANSFER_TO_ADDRESS,
                                                            asHeadlongAddress(accountAddress),
                                                            BigInteger.ONE)
                                                    .gas(300_000)
                                                    .hasKnownStatus(INVALID_SIGNATURE);
                                    allRunFor(spec, call);
                                }));
    }

    private HapiSpec minChargeIsTXGasUsedByContractCall() {
        return defaultHapiSpec("MinChargeIsTXGasUsedByContractCall")
                .given(uploadInitCode(SIMPLE_UPDATE_CONTRACT))
                .when(
                        contractCreate(SIMPLE_UPDATE_CONTRACT).gas(300_000L),
                        contractCall(
                                        SIMPLE_UPDATE_CONTRACT,
                                        "set",
                                        BigInteger.valueOf(5),
                                        BigInteger.valueOf(42))
                                .gas(300_000L)
                                .via(CALL_TX))
                .then(
                        withOpContext(
                                (spec, ignore) -> {
                                    final var subop01 =
                                            getTxnRecord(CALL_TX)
                                                    .saveTxnRecordToRegistry(CALL_TX_REC);
                                    allRunFor(spec, subop01);

                                    final var gasUsed =
                                            spec.registry()
                                                    .getTransactionRecord(CALL_TX_REC)
                                                    .getContractCallResult()
                                                    .getGasUsed();
                                    Assertions.assertTrue(gasUsed > 0L);
                                }));
    }

    private HapiSpec hscsEvm006ContractHBarTransferToAccount() {
        return defaultHapiSpec("HSCS_EVM_006_ContractHBarTransferToAccount")
                .given(
                        cryptoCreate(ACCOUNT).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER).balance(10_000L),
                        uploadInitCode(TRANSFERRING_CONTRACT),
                        contractCreate(TRANSFERRING_CONTRACT).balance(10_000L).payingWith(ACCOUNT),
                        getContractInfo(TRANSFERRING_CONTRACT).saveToRegistry(CONTRACT_FROM),
                        getAccountInfo(ACCOUNT).savingSnapshot(ACCOUNT_INFO),
                        getAccountInfo(RECEIVER).savingSnapshot(RECEIVER_INFO))
                .when(
                        withOpContext(
                                (spec, log) -> {
                                    final var receiverAddr =
                                            spec.registry()
                                                    .getAccountInfo(RECEIVER_INFO)
                                                    .getContractAccountID();
                                    final var transferCall =
                                            contractCall(
                                                            TRANSFERRING_CONTRACT,
                                                            TRANSFER_TO_ADDRESS,
                                                            asHeadlongAddress(receiverAddr),
                                                            BigInteger.valueOf(10))
                                                    .payingWith(ACCOUNT)
                                                    .logged();
                                    allRunFor(spec, transferCall);
                                }))
                .then(getAccountBalance(RECEIVER).hasTinyBars(10_000L + 10));
    }

    private HapiSpec hscsEvm005TransfersWithSubLevelCallsBetweenContracts() {
        final var topLevelContract = "TopLevelTransferring";
        final var subLevelContract = "SubLevelTransferring";
        final var INITIAL_CONTRACT_BALANCE = 100;

        return defaultHapiSpec("HSCS_EVM_005_TransfersWithSubLevelCallsBetweenContracts")
                .given(
                        cryptoCreate(ACCOUNT).balance(ONE_HUNDRED_HBARS),
                        uploadInitCode(topLevelContract, subLevelContract))
                .when(
                        contractCreate(topLevelContract)
                                .payingWith(ACCOUNT)
                                .balance(INITIAL_CONTRACT_BALANCE),
                        contractCreate(subLevelContract)
                                .payingWith(ACCOUNT)
                                .balance(INITIAL_CONTRACT_BALANCE))
                .then(
                        contractCall(topLevelContract).sending(10).payingWith(ACCOUNT),
                        getAccountBalance(topLevelContract)
                                .hasTinyBars(INITIAL_CONTRACT_BALANCE + 10L),
                        contractCall(topLevelContract, "topLevelTransferCall")
                                .sending(10)
                                .payingWith(ACCOUNT),
                        getAccountBalance(topLevelContract)
                                .hasTinyBars(INITIAL_CONTRACT_BALANCE + 20L),
                        contractCall(topLevelContract, "topLevelNonPayableCall")
                                .sending(10)
                                .payingWith(ACCOUNT)
                                .hasKnownStatus(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED),
                        getAccountBalance(topLevelContract)
                                .hasTinyBars(INITIAL_CONTRACT_BALANCE + 20L),
                        getContractInfo(topLevelContract).saveToRegistry("tcinfo"),
                        getContractInfo(subLevelContract).saveToRegistry(SCINFO),

                        /* sub-level non-payable contract call */
                        assertionsHold(
                                (spec, log) -> {
                                    final var subLevelSolidityAddr =
                                            spec.registry()
                                                    .getContractInfo(SCINFO)
                                                    .getContractAccountID();
                                    final var cc =
                                            contractCall(
                                                            subLevelContract,
                                                            "subLevelNonPayableCall",
                                                            asHeadlongAddress(subLevelSolidityAddr),
                                                            BigInteger.valueOf(20L))
                                                    .hasKnownStatus(
                                                            ResponseCodeEnum
                                                                    .CONTRACT_REVERT_EXECUTED);
                                    allRunFor(spec, cc);
                                }),
                        getAccountBalance(topLevelContract)
                                .hasTinyBars(20L + INITIAL_CONTRACT_BALANCE),
                        getAccountBalance(subLevelContract).hasTinyBars(INITIAL_CONTRACT_BALANCE),

                        /* sub-level payable contract call */
                        assertionsHold(
                                (spec, log) -> {
                                    final var subLevelSolidityAddr =
                                            spec.registry()
                                                    .getContractInfo(SCINFO)
                                                    .getContractAccountID();
                                    final var cc =
                                            contractCall(
                                                    topLevelContract,
                                                    "subLevelPayableCall",
                                                    asHeadlongAddress(subLevelSolidityAddr),
                                                    BigInteger.valueOf(20L));
                                    allRunFor(spec, cc);
                                }),
                        getAccountBalance(topLevelContract).hasTinyBars(INITIAL_CONTRACT_BALANCE),
                        getAccountBalance(subLevelContract)
                                .hasTinyBars(20L + INITIAL_CONTRACT_BALANCE));
    }

    private HapiSpec hscsEvm005TransferOfHBarsWorksBetweenContracts() {
        final var to = "To";

        return defaultHapiSpec("HSCS_EVM_005_TransferOfHBarsWorksBetweenContracts")
                .given(
                        cryptoCreate(ACCOUNT).balance(ONE_HUNDRED_HBARS),
                        uploadInitCode(TRANSFERRING_CONTRACT),
                        contractCreate(TRANSFERRING_CONTRACT).balance(10_000L).payingWith(ACCOUNT),
                        contractCustomCreate(TRANSFERRING_CONTRACT, to)
                                .balance(10_000L)
                                .payingWith(ACCOUNT),
                        getContractInfo(TRANSFERRING_CONTRACT).saveToRegistry(CONTRACT_FROM),
                        getContractInfo(TRANSFERRING_CONTRACT + to).saveToRegistry("contract_to"),
                        getAccountInfo(ACCOUNT).savingSnapshot(ACCOUNT_INFO))
                .when(
                        withOpContext(
                                (spec, log) -> {
                                    var cto =
                                            spec.registry()
                                                    .getContractInfo(TRANSFERRING_CONTRACT + to)
                                                    .getContractAccountID();
                                    var transferCall =
                                            contractCall(
                                                            TRANSFERRING_CONTRACT,
                                                            TRANSFER_TO_ADDRESS,
                                                            asHeadlongAddress(cto),
                                                            BigInteger.valueOf(10))
                                                    .payingWith(ACCOUNT)
                                                    .logged();
                                    allRunFor(spec, transferCall);
                                }))
                .then(
                        getAccountBalance(TRANSFERRING_CONTRACT).hasTinyBars(10_000 - 10L),
                        getAccountBalance(TRANSFERRING_CONTRACT + to).hasTinyBars(10_000 + 10L));
    }

    private HapiSpec hscsEvm010ReceiverMustSignContractTx() {
        final var ACC = "acc";
        final var RECEIVER_KEY = "receiverKey";
        return defaultHapiSpec("HSCS_EVM_010_ReceiverMustSignContractTx")
                .given(
                        newKeyNamed(RECEIVER_KEY),
                        cryptoCreate(ACC)
                                .balance(5 * ONE_HUNDRED_HBARS)
                                .receiverSigRequired(true)
                                .key(RECEIVER_KEY))
                .when(
                        getAccountInfo(ACC).savingSnapshot(ACC_INFO),
                        uploadInitCode(TRANSFERRING_CONTRACT),
                        contractCreate(TRANSFERRING_CONTRACT)
                                .payingWith(ACC)
                                .balance(ONE_HUNDRED_HBARS))
                .then(
                        withOpContext(
                                (spec, log) -> {
                                    final var acc =
                                            spec.registry()
                                                    .getAccountInfo(ACC_INFO)
                                                    .getContractAccountID();
                                    final var withoutReceiverSignature =
                                            contractCall(
                                                            TRANSFERRING_CONTRACT,
                                                            TRANSFER_TO_ADDRESS,
                                                            asHeadlongAddress(acc),
                                                            BigInteger.valueOf(
                                                                    ONE_HUNDRED_HBARS / 2))
                                                    .hasKnownStatus(INVALID_SIGNATURE);
                                    allRunFor(spec, withoutReceiverSignature);

                                    final var withSignature =
                                            contractCall(
                                                            TRANSFERRING_CONTRACT,
                                                            TRANSFER_TO_ADDRESS,
                                                            asHeadlongAddress(acc),
                                                            BigInteger.valueOf(
                                                                    ONE_HUNDRED_HBARS / 2))
                                                    .payingWith(ACC)
                                                    .signedBy(RECEIVER_KEY)
                                                    .hasKnownStatus(SUCCESS);
                                    allRunFor(spec, withSignature);
                                }));
    }

    private HapiSpec hscsEvm010MultiSignatureAccounts() {
        final var ACC = "acc";
        final var PAYER_KEY = "pkey";
        final var OTHER_KEY = "okey";
        final var KEY_LIST = "klist";
        return defaultHapiSpec("HSCS_EVM_010_MultiSignatureAccounts")
                .given(
                        newKeyNamed(PAYER_KEY),
                        newKeyNamed(OTHER_KEY),
                        newKeyListNamed(KEY_LIST, List.of(PAYER_KEY, OTHER_KEY)),
                        cryptoCreate(ACC)
                                .balance(ONE_HUNDRED_HBARS)
                                .key(KEY_LIST)
                                .keyType(THRESHOLD))
                .when(
                        uploadInitCode(TRANSFERRING_CONTRACT),
                        getAccountInfo(ACC).savingSnapshot(ACC_INFO),
                        contractCreate(TRANSFERRING_CONTRACT)
                                .payingWith(ACC)
                                .signedBy(PAYER_KEY)
                                .adminKey(KEY_LIST)
                                .hasPrecheck(INVALID_SIGNATURE),
                        contractCreate(TRANSFERRING_CONTRACT)
                                .payingWith(ACC)
                                .signedBy(PAYER_KEY, OTHER_KEY)
                                .balance(10)
                                .adminKey(KEY_LIST))
                .then(
                        withOpContext(
                                (spec, log) -> {
                                    final var acc =
                                            spec.registry()
                                                    .getAccountInfo(ACC_INFO)
                                                    .getContractAccountID();
                                    final var assertionWithOnlyOneKey =
                                            contractCall(
                                                            TRANSFERRING_CONTRACT,
                                                            TRANSFER_TO_ADDRESS,
                                                            asHeadlongAddress(acc),
                                                            BigInteger.valueOf(10L))
                                                    .payingWith(ACC)
                                                    .signedBy(PAYER_KEY)
                                                    .hasPrecheck(INVALID_SIGNATURE)
                                                    .refusingEthConversion();
                                    allRunFor(spec, assertionWithOnlyOneKey);

                                    final var assertionWithBothKeys =
                                            contractCall(
                                                            TRANSFERRING_CONTRACT,
                                                            TRANSFER_TO_ADDRESS,
                                                            asHeadlongAddress(acc),
                                                            BigInteger.valueOf(10L))
                                                    .payingWith(ACC)
                                                    .signedBy(PAYER_KEY, OTHER_KEY)
                                                    .hasKnownStatus(SUCCESS)
                                                    .refusingEthConversion();
                                    allRunFor(spec, assertionWithBothKeys);
                                }));
    }

    private HapiSpec sendHbarsToAddressesMultipleTimes() {
        return defaultHapiSpec("sendHbarsToAddressesMultipleTimes")
                .given(
                        cryptoCreate(ACCOUNT).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER).balance(10_000L),
                        uploadInitCode(TRANSFERRING_CONTRACT),
                        contractCreate(TRANSFERRING_CONTRACT).balance(10_000L).payingWith(ACCOUNT),
                        getAccountInfo(RECEIVER).savingSnapshot(RECEIVER_INFO))
                .when(
                        withOpContext(
                                (spec, log) -> {
                                    var receiverAddr =
                                            spec.registry()
                                                    .getAccountInfo(RECEIVER_INFO)
                                                    .getContractAccountID();
                                    var transferCall =
                                            contractCall(
                                                            TRANSFERRING_CONTRACT,
                                                            "transferToAddressMultipleTimes",
                                                            asHeadlongAddress(receiverAddr),
                                                            BigInteger.valueOf(64))
                                                    .payingWith(ACCOUNT)
                                                    .logged();
                                    allRunFor(spec, transferCall);
                                }))
                .then(
                        getAccountBalance(RECEIVER).hasTinyBars(10_000L + 127L),
                        sourcing(
                                () ->
                                        getContractInfo(TRANSFERRING_CONTRACT)
                                                .has(contractWith().balance(10_000L - 127L))));
    }

    private HapiSpec sendHbarsToDifferentAddresses() {
        return defaultHapiSpec("sendHbarsToDifferentAddresses")
                .given(
                        cryptoCreate(ACCOUNT).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER_1).balance(10_000L),
                        cryptoCreate(RECEIVER_2).balance(10_000L),
                        cryptoCreate(RECEIVER_3).balance(10_000L),
                        uploadInitCode(TRANSFERRING_CONTRACT),
                        contractCreate(TRANSFERRING_CONTRACT).balance(10_000L).payingWith(ACCOUNT),
                        getAccountInfo(RECEIVER_1).savingSnapshot(RECEIVER_1_INFO),
                        getAccountInfo(RECEIVER_2).savingSnapshot(RECEIVER_2_INFO),
                        getAccountInfo(RECEIVER_3).savingSnapshot(RECEIVER_3_INFO))
                .when(
                        withOpContext(
                                (spec, log) -> {
                                    var receiver1Addr =
                                            spec.registry()
                                                    .getAccountInfo(RECEIVER_1_INFO)
                                                    .getContractAccountID();
                                    var receiver2Addr =
                                            spec.registry()
                                                    .getAccountInfo(RECEIVER_2_INFO)
                                                    .getContractAccountID();
                                    var receiver3Addr =
                                            spec.registry()
                                                    .getAccountInfo(RECEIVER_3_INFO)
                                                    .getContractAccountID();

                                    var transferCall =
                                            contractCall(
                                                            TRANSFERRING_CONTRACT,
                                                            "transferToDifferentAddresses",
                                                            asHeadlongAddress(receiver1Addr),
                                                            asHeadlongAddress(receiver2Addr),
                                                            asHeadlongAddress(receiver3Addr),
                                                            BigInteger.valueOf(20))
                                                    .payingWith(ACCOUNT)
                                                    .logged();
                                    allRunFor(spec, transferCall);
                                }))
                .then(
                        getAccountBalance(RECEIVER_1).hasTinyBars(10_000L + 20L),
                        getAccountBalance(RECEIVER_2).hasTinyBars(10_000L + 10L),
                        getAccountBalance(RECEIVER_3).hasTinyBars(10_000L + 5L),
                        sourcing(
                                () ->
                                        getContractInfo(TRANSFERRING_CONTRACT)
                                                .has(contractWith().balance(10_000L - 35L))));
    }

    private HapiSpec sendHbarsFromDifferentAddressessToAddress() {
        return defaultHapiSpec("sendHbarsFromDifferentAddressessToAddress")
                .given(
                        cryptoCreate(ACCOUNT).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER).balance(10_000L),
                        uploadInitCode(NESTED_TRANSFERRING_CONTRACT, NESTED_TRANSFER_CONTRACT),
                        contractCustomCreate(NESTED_TRANSFER_CONTRACT, "1")
                                .balance(10_000L)
                                .payingWith(ACCOUNT),
                        contractCustomCreate(NESTED_TRANSFER_CONTRACT, "2")
                                .balance(10_000L)
                                .payingWith(ACCOUNT),
                        getAccountInfo(RECEIVER).savingSnapshot(RECEIVER_INFO))
                .when(
                        withOpContext(
                                (spec, log) -> {
                                    var receiverAddr =
                                            spec.registry()
                                                    .getAccountInfo(RECEIVER_INFO)
                                                    .getContractAccountID();

                                    allRunFor(
                                            spec,
                                            contractCreate(
                                                            NESTED_TRANSFERRING_CONTRACT,
                                                            asHeadlongAddress(
                                                                    getNestedContractAddress(
                                                                            NESTED_TRANSFER_CONTRACT
                                                                                    + "1",
                                                                            spec)),
                                                            asHeadlongAddress(
                                                                    getNestedContractAddress(
                                                                            NESTED_TRANSFER_CONTRACT
                                                                                    + "2",
                                                                            spec)))
                                                    .balance(10_000L)
                                                    .payingWith(ACCOUNT),
                                            contractCall(
                                                            NESTED_TRANSFERRING_CONTRACT,
                                                            "transferFromDifferentAddressesToAddress",
                                                            asHeadlongAddress(receiverAddr),
                                                            BigInteger.valueOf(40L))
                                                    .payingWith(ACCOUNT)
                                                    .logged());
                                }))
                .then(
                        getAccountBalance(RECEIVER).hasTinyBars(10_000L + 80L),
                        sourcing(
                                () ->
                                        getContractInfo(NESTED_TRANSFER_CONTRACT + "1")
                                                .has(contractWith().balance(10_000L - 20L))),
                        sourcing(
                                () ->
                                        getContractInfo(NESTED_TRANSFER_CONTRACT + "2")
                                                .has(contractWith().balance(10_000L - 20L))));
    }

    private HapiSpec sendHbarsToOuterContractFromDifferentAddresses() {
        return defaultHapiSpec("sendHbarsToOuterContractFromDifferentAddresses")
                .given(
                        cryptoCreate(ACCOUNT).balance(ONE_HUNDRED_HBARS),
                        uploadInitCode(NESTED_TRANSFERRING_CONTRACT, NESTED_TRANSFER_CONTRACT),
                        contractCustomCreate(NESTED_TRANSFER_CONTRACT, "1")
                                .balance(10_000L)
                                .payingWith(ACCOUNT),
                        contractCustomCreate(NESTED_TRANSFER_CONTRACT, "2")
                                .balance(10_000L)
                                .payingWith(ACCOUNT))
                .when(
                        withOpContext(
                                (spec, log) ->
                                        allRunFor(
                                                spec,
                                                contractCreate(
                                                                NESTED_TRANSFERRING_CONTRACT,
                                                                asHeadlongAddress(
                                                                        getNestedContractAddress(
                                                                                NESTED_TRANSFER_CONTRACT
                                                                                        + "1",
                                                                                spec)),
                                                                asHeadlongAddress(
                                                                        getNestedContractAddress(
                                                                                NESTED_TRANSFER_CONTRACT
                                                                                        + "2",
                                                                                spec)))
                                                        .balance(10_000L)
                                                        .payingWith(ACCOUNT),
                                                contractCall(
                                                                NESTED_TRANSFERRING_CONTRACT,
                                                                "transferToContractFromDifferentAddresses",
                                                                BigInteger.valueOf(50L))
                                                        .payingWith(ACCOUNT)
                                                        .logged())))
                .then(
                        sourcing(
                                () ->
                                        getContractInfo(NESTED_TRANSFERRING_CONTRACT)
                                                .has(contractWith().balance(10_000L + 100L))),
                        sourcing(
                                () ->
                                        getContractInfo(NESTED_TRANSFER_CONTRACT + "1")
                                                .has(contractWith().balance(10_000L - 50L))),
                        sourcing(
                                () ->
                                        getContractInfo(NESTED_TRANSFER_CONTRACT + "2")
                                                .has(contractWith().balance(10_000L - 50L))));
    }

    private HapiSpec sendHbarsToCallerFromDifferentAddresses() {
        return defaultHapiSpec("sendHbarsToCallerFromDifferentAddresses")
                .given(
                        withOpContext(
                                (spec, log) -> {
                                    if (!spec.isUsingEthCalls()) {
                                        spec.registry()
                                                .saveAccountId(
                                                        DEFAULT_CONTRACT_RECEIVER,
                                                        spec.setup().strongControlAccount());
                                    }
                                    final var keyCreation =
                                            newKeyNamed(SECP_256K1_SOURCE_KEY)
                                                    .shape(SECP_256K1_SHAPE);
                                    final var transfer1 =
                                            cryptoTransfer(
                                                            tinyBarsFromAccountToAlias(
                                                                    GENESIS,
                                                                    SECP_256K1_SOURCE_KEY,
                                                                    ONE_HUNDRED_HBARS))
                                                    .via("autoAccount");

                                    final var nestedTransferringUpload =
                                            uploadInitCode(
                                                    NESTED_TRANSFERRING_CONTRACT,
                                                    NESTED_TRANSFER_CONTRACT);
                                    final var createFirstNestedContract =
                                            contractCustomCreate(NESTED_TRANSFER_CONTRACT, "1")
                                                    .balance(10_000L);
                                    final var createSecondNestedContract =
                                            contractCustomCreate(NESTED_TRANSFER_CONTRACT, "2")
                                                    .balance(10_000L);
                                    final var transfer2 =
                                            cryptoTransfer(
                                                    TokenMovement.movingHbar(10_000_000L)
                                                            .between(
                                                                    GENESIS,
                                                                    DEFAULT_CONTRACT_RECEIVER));
                                    final var saveSnapshot =
                                            getAccountInfo(DEFAULT_CONTRACT_RECEIVER)
                                                    .savingSnapshot(ACCOUNT_INFO)
                                                    .payingWith(GENESIS);
                                    allRunFor(
                                            spec,
                                            keyCreation,
                                            transfer1,
                                            nestedTransferringUpload,
                                            createFirstNestedContract,
                                            createSecondNestedContract,
                                            transfer2,
                                            saveSnapshot);
                                }))
                .when(
                        withOpContext(
                                (spec, log) ->
                                        allRunFor(
                                                spec,
                                                contractCreate(
                                                                NESTED_TRANSFERRING_CONTRACT,
                                                                asHeadlongAddress(
                                                                        getNestedContractAddress(
                                                                                NESTED_TRANSFER_CONTRACT
                                                                                        + "1",
                                                                                spec)),
                                                                asHeadlongAddress(
                                                                        getNestedContractAddress(
                                                                                NESTED_TRANSFER_CONTRACT
                                                                                        + "2",
                                                                                spec)))
                                                        .balance(10_000L)
                                                        .payingWith(GENESIS),
                                                contractCall(
                                                                NESTED_TRANSFERRING_CONTRACT,
                                                                "transferToCallerFromDifferentAddresses",
                                                                BigInteger.valueOf(100L))
                                                        .payingWith(DEFAULT_CONTRACT_RECEIVER)
                                                        .signingWith(SECP_256K1_RECEIVER_SOURCE_KEY)
                                                        .via(TRANSFER_TXN)
                                                        .logged(),
                                                getTxnRecord(TRANSFER_TXN)
                                                        .saveTxnRecordToRegistry("txn")
                                                        .payingWith(GENESIS),
                                                getAccountInfo(DEFAULT_CONTRACT_RECEIVER)
                                                        .savingSnapshot(ACCOUNT_INFO_AFTER_CALL)
                                                        .payingWith(GENESIS))))
                .then(
                        assertionsHold(
                                (spec, opLog) -> {
                                    final var fee =
                                            spec.registry()
                                                    .getTransactionRecord("txn")
                                                    .getTransactionFee();
                                    final var accountBalanceBeforeCall =
                                            spec.registry()
                                                    .getAccountInfo(ACCOUNT_INFO)
                                                    .getBalance();
                                    final var accountBalanceAfterCall =
                                            spec.registry()
                                                    .getAccountInfo(ACCOUNT_INFO_AFTER_CALL)
                                                    .getBalance();

                                    Assertions.assertEquals(
                                            accountBalanceAfterCall,
                                            accountBalanceBeforeCall - fee + 200L);
                                }),
                        sourcing(
                                () ->
                                        getContractInfo(NESTED_TRANSFERRING_CONTRACT)
                                                .has(contractWith().balance(10_000L - 200L))),
                        sourcing(
                                () ->
                                        getContractInfo(NESTED_TRANSFER_CONTRACT + "1")
                                                .has(contractWith().balance(10_000L))),
                        sourcing(
                                () ->
                                        getContractInfo(NESTED_TRANSFER_CONTRACT + "2")
                                                .has(contractWith().balance(10_000L))));
    }

    private HapiSpec sendHbarsFromAndToDifferentAddressess() {
        return defaultHapiSpec("sendHbarsFromAndToDifferentAddressess")
                .given(
                        cryptoCreate(ACCOUNT).balance(200 * ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER_1).balance(10_000L),
                        cryptoCreate(RECEIVER_2).balance(10_000L),
                        cryptoCreate(RECEIVER_3).balance(10_000L),
                        uploadInitCode(NESTED_TRANSFERRING_CONTRACT, NESTED_TRANSFER_CONTRACT),
                        contractCustomCreate(NESTED_TRANSFER_CONTRACT, "1")
                                .balance(10_000L)
                                .payingWith(ACCOUNT),
                        contractCustomCreate(NESTED_TRANSFER_CONTRACT, "2")
                                .balance(10_000L)
                                .payingWith(ACCOUNT),
                        getAccountInfo(RECEIVER_1).savingSnapshot(RECEIVER_1_INFO),
                        getAccountInfo(RECEIVER_2).savingSnapshot(RECEIVER_2_INFO),
                        getAccountInfo(RECEIVER_3).savingSnapshot(RECEIVER_3_INFO))
                .when(
                        withOpContext(
                                (spec, log) -> {
                                    var receiver1Addr =
                                            spec.registry()
                                                    .getAccountInfo(RECEIVER_1_INFO)
                                                    .getContractAccountID();
                                    var receiver2Addr =
                                            spec.registry()
                                                    .getAccountInfo(RECEIVER_2_INFO)
                                                    .getContractAccountID();
                                    var receiver3Addr =
                                            spec.registry()
                                                    .getAccountInfo(RECEIVER_3_INFO)
                                                    .getContractAccountID();

                                    allRunFor(
                                            spec,
                                            contractCreate(
                                                            NESTED_TRANSFERRING_CONTRACT,
                                                            asHeadlongAddress(
                                                                    getNestedContractAddress(
                                                                            NESTED_TRANSFER_CONTRACT
                                                                                    + "1",
                                                                            spec)),
                                                            asHeadlongAddress(
                                                                    getNestedContractAddress(
                                                                            NESTED_TRANSFER_CONTRACT
                                                                                    + "2",
                                                                            spec)))
                                                    .balance(10_000L)
                                                    .payingWith(ACCOUNT),
                                            contractCall(
                                                            NESTED_TRANSFERRING_CONTRACT,
                                                            "transferFromAndToDifferentAddresses",
                                                            asHeadlongAddress(receiver1Addr),
                                                            asHeadlongAddress(receiver2Addr),
                                                            asHeadlongAddress(receiver3Addr),
                                                            BigInteger.valueOf(40))
                                                    .payingWith(ACCOUNT)
                                                    .gas(1_000_000L)
                                                    .logged());
                                }))
                .then(
                        getAccountBalance(RECEIVER_1).hasTinyBars(10_000 + 80L),
                        getAccountBalance(RECEIVER_2).hasTinyBars(10_000 + 80L),
                        getAccountBalance(RECEIVER_3).hasTinyBars(10_000 + 80L),
                        sourcing(
                                () ->
                                        getContractInfo(NESTED_TRANSFER_CONTRACT + "1")
                                                .has(contractWith().balance(10_000 - 60L))),
                        sourcing(
                                () ->
                                        getContractInfo(NESTED_TRANSFER_CONTRACT + "2")
                                                .has(contractWith().balance(10_000 - 60L))));
    }

    private HapiSpec transferNegativeAmountOfHbars() {
        return defaultHapiSpec("transferNegativeAmountOfHbarsFails")
                .given(
                        cryptoCreate(ACCOUNT).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER).balance(10_000L),
                        uploadInitCode(TRANSFERRING_CONTRACT),
                        contractCreate(TRANSFERRING_CONTRACT).balance(10_000L).payingWith(ACCOUNT),
                        getAccountInfo(RECEIVER).savingSnapshot(RECEIVER_INFO))
                .when(
                        withOpContext(
                                (spec, log) -> {
                                    var receiverAddr =
                                            spec.registry()
                                                    .getAccountInfo(RECEIVER_INFO)
                                                    .getContractAccountID();
                                    var transferCall =
                                            contractCall(
                                                            TRANSFERRING_CONTRACT,
                                                            "transferToAddressNegativeAmount",
                                                            asHeadlongAddress(receiverAddr),
                                                            BigInteger.valueOf(10))
                                                    .payingWith(ACCOUNT)
                                                    .hasKnownStatus(CONTRACT_REVERT_EXECUTED);
                                    var transferCallZeroHbars =
                                            contractCall(
                                                            TRANSFERRING_CONTRACT,
                                                            "transferToAddressNegativeAmount",
                                                            asHeadlongAddress(receiverAddr),
                                                            BigInteger.ZERO)
                                                    .payingWith(ACCOUNT)
                                                    .hasKnownStatus(SUCCESS);

                                    allRunFor(spec, transferCall, transferCallZeroHbars);
                                }))
                .then(
                        getAccountBalance(RECEIVER).hasTinyBars(10_000L),
                        sourcing(
                                () ->
                                        getContractInfo(TRANSFERRING_CONTRACT)
                                                .has(contractWith().balance(10_000L))));
    }

    private HapiSpec transferZeroHbars() {
        return defaultHapiSpec("transferZeroHbars")
                .given(
                        cryptoCreate(ACCOUNT).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER).balance(10_000L),
                        uploadInitCode(TRANSFERRING_CONTRACT),
                        contractCreate(TRANSFERRING_CONTRACT).balance(10_000L),
                        getAccountInfo(RECEIVER).savingSnapshot(RECEIVER_INFO))
                .when(
                        withOpContext(
                                (spec, log) -> {
                                    var receiverAddr =
                                            spec.registry()
                                                    .getAccountInfo(RECEIVER_INFO)
                                                    .getContractAccountID();

                                    var transferCall =
                                            contractCall(
                                                            TRANSFERRING_CONTRACT,
                                                            TRANSFER_TO_ADDRESS,
                                                            asHeadlongAddress(receiverAddr),
                                                            BigInteger.ZERO)
                                                    .payingWith(ACCOUNT)
                                                    .via(TRANSFER_TXN)
                                                    .logged();

                                    var saveContractInfo =
                                            getContractInfo(TRANSFERRING_CONTRACT)
                                                    .saveToRegistry(CONTRACT_FROM);

                                    allRunFor(spec, transferCall, saveContractInfo);
                                }))
                .then(
                        assertionsHold(
                                (spec, opLog) -> {
                                    final var contractBalanceAfterCall =
                                            spec.registry()
                                                    .getContractInfo(CONTRACT_FROM)
                                                    .getBalance();

                                    Assertions.assertEquals(contractBalanceAfterCall, 10_000L);
                                }),
                        getAccountBalance(RECEIVER).hasTinyBars(10_000L));
    }

    private HapiSpec lpFarmSimulation() {
        final var adminKey = "adminKey";
        final var gasToOffer = 4_000_000;
        final var farmInitcodeLoc = "src/main/resource/contract/bytecodes/farmInitcode.bin";
        final var consAbi =
                "{ \"inputs\": [ { \"internalType\": \"address\", \"name\": \"_devaddr\", \"type\":"
                    + " \"address\" }, { \"internalType\": \"address\", \"name\": \"_rentPayer\","
                    + " \"type\": \"address\" },     { \"internalType\": \"uint256\", \"name\":"
                    + " \"_saucePerSecond\", \"type\": \"uint256\" }, { \"internalType\":"
                    + " \"uint256\", \"name\": \"_hbarPerSecond\", \"type\": \"uint256\" }, {"
                    + " \"internalType\": \"uint256\", \"name\": \"_maxSauceSupply\", \"type\":"
                    + " \"uint256\" }, { \"internalType\": \"uint256\", \"name\":"
                    + " \"_depositFeeTinyCents\", \"type\": \"uint256\" } ], \"stateMutability\":"
                    + " \"nonpayable\", \"type\": \"constructor\" }";
        final var addPoolAbi =
                "{ \"inputs\": [ { \"internalType\": \"uint256\", \"name\": \"_allocPoint\","
                        + " \"type\": \"uint256\" }, { \"internalType\": \"address\", \"name\":"
                        + " \"_lpToken\", \"type\": \"address\" }       ], \"name\": \"add\","
                        + " \"outputs\": [], \"stateMutability\": \"nonpayable\", \"type\":"
                        + " \"function\" }";
        final var depositAbi =
                "{ \"inputs\": [ { \"internalType\": \"uint256\", \"name\": \"_pid\", \"type\":"
                        + " \"uint256\" }, { \"internalType\": \"uint256\", \"name\": \"_amount\","
                        + " \"type\": \"uint256\" } ], \"name\": \"deposit\", \"outputs\": [],"
                        + " \"stateMutability\": \"payable\", \"type\": \"function\" }";
        final var withdrawAbi =
                "{ \"inputs\": [ { \"internalType\": \"uint256\", \"name\": \"_pid\", \"type\":"
                        + " \"uint256\" }, { \"internalType\": \"uint256\", \"name\": \"_amount\","
                        + " \"type\": \"uint256\" } ], \"name\": \"withdraw\", \"outputs\": [],"
                        + " \"stateMutability\": \"nonpayable\", \"type\": \"function\" }";
        final var setSauceAbi =
                "{ \"inputs\": [ { \"internalType\": \"address\", \"name\": \"_sauce\", \"type\":"
                        + " \"address\" } ], \"name\": \"setSauceAddress\", \"outputs\": [],"
                        + " \"stateMutability\": \"nonpayable\", \"type\": \"function\" }";
        final var transferAbi =
                "{ \"inputs\": [ { \"internalType\": \"address\", \"name\": \"newOwner\", \"type\":"
                        + " \"address\" } ], \"name\": \"transferOwnership\", \"outputs\": [],"
                        + " \"stateMutability\": \"nonpayable\", \"type\": \"function\" }";
        final var initcode = "farmInitcode";
        final var farm = "farm";
        final var dev = "dev";
        final var lp = "lp";
        final var sauce = "sauce";
        final var rentPayer = "rentPayer";
        final AtomicReference<String> devAddr = new AtomicReference<>();
        final AtomicReference<String> ownerAddr = new AtomicReference<>();
        final AtomicReference<String> sauceAddr = new AtomicReference<>();
        final AtomicReference<String> lpTokenAddr = new AtomicReference<>();
        final AtomicReference<String> rentPayerAddr = new AtomicReference<>();

        return defaultHapiSpec("FarmSimulation")
                .given(
                        newKeyNamed(adminKey),
                        fileCreate(initcode),
                        cryptoCreate(OWNER)
                                .balance(ONE_MILLION_HBARS)
                                .exposingCreatedIdTo(
                                        id -> ownerAddr.set(asHexedSolidityAddress(id))),
                        cryptoCreate(dev)
                                .balance(ONE_MILLION_HBARS)
                                .exposingCreatedIdTo(id -> devAddr.set(asHexedSolidityAddress(id))),
                        cryptoCreate(rentPayer)
                                .balance(ONE_MILLION_HBARS)
                                .exposingCreatedIdTo(
                                        id -> rentPayerAddr.set(asHexedSolidityAddress(id))),
                        updateLargeFile(GENESIS, initcode, extractByteCode(farmInitcodeLoc)),
                        sourcing(
                                () ->
                                        new HapiContractCreate(
                                                        farm,
                                                        consAbi,
                                                        asHeadlongAddress(devAddr.get()),
                                                        asHeadlongAddress(rentPayerAddr.get()),
                                                        BigInteger.valueOf(4804540L),
                                                        BigInteger.valueOf(10000L),
                                                        BigInteger.valueOf(1000000000000000L),
                                                        BigInteger.valueOf(2500000000L))
                                                .bytecode(initcode)),
                        tokenCreate(sauce)
                                .supplyType(TokenSupplyType.FINITE)
                                .initialSupply(300_000_000)
                                .maxSupply(1_000_000_000)
                                .treasury(farm)
                                .adminKey(adminKey)
                                .supplyKey(adminKey)
                                .exposingCreatedIdTo(
                                        idLit ->
                                                sauceAddr.set(
                                                        asHexedSolidityAddress(
                                                                HapiPropertySource.asToken(
                                                                        idLit)))),
                        tokenCreate(lp)
                                .treasury(dev)
                                .initialSupply(1_000_000_000)
                                .exposingCreatedIdTo(
                                        idLit ->
                                                lpTokenAddr.set(
                                                        asHexedSolidityAddress(
                                                                HapiPropertySource.asToken(
                                                                        idLit)))),
                        tokenAssociate(dev, sauce),
                        sourcing(
                                () ->
                                        contractCallWithFunctionAbi(
                                                        farm,
                                                        setSauceAbi,
                                                        asHeadlongAddress(sauceAddr.get()))
                                                .gas(gasToOffer)),
                        sourcing(
                                () ->
                                        contractCallWithFunctionAbi(
                                                        farm,
                                                        transferAbi,
                                                        asHeadlongAddress(ownerAddr.get()))
                                                .gas(gasToOffer)))
                .when(
                        sourcing(
                                () ->
                                        contractCallWithFunctionAbi(
                                                        farm,
                                                        addPoolAbi,
                                                        BigInteger.valueOf(2392L),
                                                        asHeadlongAddress(lpTokenAddr.get()))
                                                .via("add")
                                                .payingWith(OWNER)
                                                .gas(gasToOffer)),
                        newKeyNamed("contractControl").shape(KeyShape.CONTRACT.signedWith(farm)),
                        tokenUpdate(sauce).supplyKey("contractControl"),
                        sourcing(
                                () ->
                                        contractCallWithFunctionAbi(
                                                        farm,
                                                        depositAbi,
                                                        BigInteger.ZERO,
                                                        BigInteger.valueOf(100_000))
                                                .sending(ONE_HUNDRED_HBARS)
                                                .payingWith(dev)
                                                .gas(gasToOffer)),
                        sleepFor(1000),
                        sourcing(
                                () ->
                                        contractCallWithFunctionAbi(
                                                        farm,
                                                        depositAbi,
                                                        BigInteger.ZERO,
                                                        BigInteger.valueOf(100_000))
                                                .sending(ONE_HUNDRED_HBARS)
                                                .payingWith(dev)
                                                .gas(gasToOffer)
                                                .via("second")),
                        getTxnRecord("second").andAllChildRecords().logged())
                .then(
                        sourcing(
                                () ->
                                        contractCallWithFunctionAbi(
                                                        farm,
                                                        withdrawAbi,
                                                        BigInteger.ZERO,
                                                        BigInteger.valueOf(200_000))
                                                .payingWith(dev)
                                                .gas(gasToOffer)));
    }

    private HapiSpec consTimeManagementWorksWithRevertedInternalCreations() {
        final var contract = "ConsTimeRepro";
        final var failingCall = "FailingCall";
        final AtomicReference<Timestamp> parentConsTime = new AtomicReference<>();
        return defaultHapiSpec("ConsTimeManagementWorksWithRevertedInternalCreations")
                .given(uploadInitCode(contract), contractCreate(contract))
                .when(
                        contractCall(
                                        contract,
                                        "createChildThenFailToAssociate",
                                        asHeadlongAddress(new byte[20]),
                                        asHeadlongAddress(new byte[20]))
                                .via(failingCall)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED))
                .then(
                        getTxnRecord(failingCall)
                                .exposingTo(
                                        failureRecord ->
                                                parentConsTime.set(
                                                        failureRecord.getConsensusTimestamp())),
                        sourcing(
                                () ->
                                        childRecordsCheck(
                                                failingCall,
                                                CONTRACT_REVERT_EXECUTED,
                                                recordWith()
                                                        .status(INSUFFICIENT_GAS)
                                                        .consensusTimeImpliedByNonce(
                                                                parentConsTime.get(), 1))));
    }

    private String getNestedContractAddress(final String contract, final HapiSpec spec) {
        return HapiPropertySource.asHexedSolidityAddress(spec.registry().getContractId(contract));
    }

    @Override
    protected Logger getResultsLogger() {
        return LOG;
    }

    private ByteString bookInterpolated(
            final byte[] jurisdictionInitcode, final String addressBookMirror) {
        return ByteString.copyFrom(
                new String(jurisdictionInitcode)
                        .replaceAll("_+AddressBook.sol:AddressBook_+", addressBookMirror)
                        .getBytes());
    }

    private static final String EXPLICIT_JURISDICTION_CONS_PARAMS =
            "45fd06740000000000000000000000001234567890123456789012345678901234567890";
    private static final String EXPLICIT_MINTER_CONS_PARAMS_TPL =
            "1c26cc85%s0000000000000000000000001234567890123456789012345678901234567890";
    private static final String EXPLICIT_MINTER_CONFIG_PARAMS_TPL =
            "da71addf000000000000000000000000%s";
    private static final String EXPLICIT_JURISDICTIONS_ADD_PARAMS =
            "218c66ea0000000000000000000000000000000000000000000000000000000000000080000000000000000000000000"
                + "0000000000000000000000000000000000000339000000000000000000000000123456789012345678901234"
                + "5678901234567890000000000000000000000000123456789012345678901234567890123456789000000000"
                + "000000000000000000000000000000000000000000000000000000026e790000000000000000000000000000"
                + "00000000000000000000000000000000";
}
