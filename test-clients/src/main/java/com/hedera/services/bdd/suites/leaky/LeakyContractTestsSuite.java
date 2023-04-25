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
package com.hedera.services.bdd.suites.leaky;

import static com.google.protobuf.ByteString.copyFromUtf8;
import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;
import static com.hedera.services.bdd.spec.HapiPropertySource.asHexedSolidityAddress;
import static com.hedera.services.bdd.spec.HapiPropertySource.asSolidityAddress;
import static com.hedera.services.bdd.spec.HapiPropertySource.asTokenString;
import static com.hedera.services.bdd.spec.HapiSpec.*;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.inOrder;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.ContractInfoAsserts.contractWith;
import static com.hedera.services.bdd.spec.assertions.ContractLogAsserts.logWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.KeyFactory.KeyType.THRESHOLD;
import static com.hedera.services.bdd.spec.keys.KeyShape.CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.DELEGATE_CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.ED25519;
import static com.hedera.services.bdd.spec.keys.KeyShape.SECP256K1;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.SigControl.ED25519_ON;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.keys.SigControl.SECP256K1_ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAliasedAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getReceipt;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCallWithFunctionAbi;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCustomCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoApproveAllowance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.ethereumContractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uncheckedSubmit;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadSingleInitCode;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddressArray;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeTests.fixedHbarFeeInSchedule;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeTests.fixedHtsFeeInSchedule;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeTests.fractionalFeeInSchedule;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeTests.royaltyFeeWithFallbackInHbarsInSchedule;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeTests.royaltyFeeWithFallbackInTokenInSchedule;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeTests.royaltyFeeWithoutFallbackInSchedule;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.accountAmount;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.accountAmountAlias;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.addressedAccountAmount;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.assertionsHold;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.emptyChildRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingThree;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingTwo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.resetToDefault;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.tokenTransferList;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.tokenTransferLists;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.usableTxnIdNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.asHexedAddress;
import static com.hedera.services.bdd.suites.contract.Utils.asToken;
import static com.hedera.services.bdd.suites.contract.Utils.eventSignatureOf;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;
import static com.hedera.services.bdd.suites.contract.Utils.parsedToByteString;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.ACCOUNT_INFO;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.ACCOUNT_INFO_AFTER_CALL;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.CALL_TX;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.CALL_TX_REC;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.CONTRACTS_MAX_GAS_PER_SEC;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.CONTRACT_FROM;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.DECIMALS;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.DEFAULT_MAX_AUTO_RENEW_PERIOD;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.DEPOSIT;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.LEDGER_AUTO_RENEW_PERIOD_MAX_DURATION;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.PAY_RECEIVABLE_CONTRACT;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.RECEIVER_2;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.SIMPLE_UPDATE_CONTRACT;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.TRANSFER;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.TRANSFERRING_CONTRACT;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.TRANSFER_TO_CALLER;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCreateSuite.EMPTY_CONSTRUCTOR_CONTRACT;
import static com.hedera.services.bdd.suites.contract.precompile.CreatePrecompileSuite.ACCOUNT_2;
import static com.hedera.services.bdd.suites.contract.precompile.CreatePrecompileSuite.AUTO_RENEW_PERIOD;
import static com.hedera.services.bdd.suites.contract.precompile.CreatePrecompileSuite.CONTRACT_ADMIN_KEY;
import static com.hedera.services.bdd.suites.contract.precompile.CreatePrecompileSuite.CREATE_TOKEN_WITH_ALL_CUSTOM_FEES_AVAILABLE;
import static com.hedera.services.bdd.suites.contract.precompile.CreatePrecompileSuite.DEFAULT_AMOUNT_TO_SEND;
import static com.hedera.services.bdd.suites.contract.precompile.CreatePrecompileSuite.ECDSA_KEY;
import static com.hedera.services.bdd.suites.contract.precompile.CreatePrecompileSuite.ED25519KEY;
import static com.hedera.services.bdd.suites.contract.precompile.CreatePrecompileSuite.EXISTING_TOKEN;
import static com.hedera.services.bdd.suites.contract.precompile.CreatePrecompileSuite.EXPLICIT_CREATE_RESULT;
import static com.hedera.services.bdd.suites.contract.precompile.CreatePrecompileSuite.FIRST_CREATE_TXN;
import static com.hedera.services.bdd.suites.contract.precompile.CreatePrecompileSuite.MEMO;
import static com.hedera.services.bdd.suites.contract.precompile.CreatePrecompileSuite.TOKEN_CREATE_CONTRACT;
import static com.hedera.services.bdd.suites.contract.precompile.CreatePrecompileSuite.TOKEN_CREATE_CONTRACT_AS_KEY;
import static com.hedera.services.bdd.suites.contract.precompile.CreatePrecompileSuite.TOKEN_NAME;
import static com.hedera.services.bdd.suites.contract.precompile.CreatePrecompileSuite.TOKEN_SYMBOL;
import static com.hedera.services.bdd.suites.contract.precompile.CryptoTransferHTSSuite.TOTAL_SUPPLY;
import static com.hedera.services.bdd.suites.contract.precompile.CryptoTransferHTSSuite.TRANSFER_MULTIPLE_TOKENS;
import static com.hedera.services.bdd.suites.contract.precompile.ERCPrecompileSuite.ALLOWANCE;
import static com.hedera.services.bdd.suites.contract.precompile.ERCPrecompileSuite.ALLOWANCE_TXN;
import static com.hedera.services.bdd.suites.contract.precompile.ERCPrecompileSuite.APPROVE;
import static com.hedera.services.bdd.suites.contract.precompile.ERCPrecompileSuite.A_CIVILIAN;
import static com.hedera.services.bdd.suites.contract.precompile.ERCPrecompileSuite.B_CIVILIAN;
import static com.hedera.services.bdd.suites.contract.precompile.ERCPrecompileSuite.DO_SPECIFIC_APPROVAL;
import static com.hedera.services.bdd.suites.contract.precompile.ERCPrecompileSuite.DO_TRANSFER_FROM;
import static com.hedera.services.bdd.suites.contract.precompile.ERCPrecompileSuite.ERC_20_CONTRACT;
import static com.hedera.services.bdd.suites.contract.precompile.ERCPrecompileSuite.ERC_20_CONTRACT_NAME;
import static com.hedera.services.bdd.suites.contract.precompile.ERCPrecompileSuite.GET_ALLOWANCE;
import static com.hedera.services.bdd.suites.contract.precompile.ERCPrecompileSuite.MISSING_FROM;
import static com.hedera.services.bdd.suites.contract.precompile.ERCPrecompileSuite.MISSING_TO;
import static com.hedera.services.bdd.suites.contract.precompile.ERCPrecompileSuite.MSG_SENDER_IS_NOT_THE_SAME_AS_FROM;
import static com.hedera.services.bdd.suites.contract.precompile.ERCPrecompileSuite.MSG_SENDER_IS_THE_SAME_AS_FROM;
import static com.hedera.services.bdd.suites.contract.precompile.ERCPrecompileSuite.MULTI_KEY_NAME;
import static com.hedera.services.bdd.suites.contract.precompile.ERCPrecompileSuite.NAME_TXN;
import static com.hedera.services.bdd.suites.contract.precompile.ERCPrecompileSuite.RECIPIENT;
import static com.hedera.services.bdd.suites.contract.precompile.ERCPrecompileSuite.SOME_ERC_20_SCENARIOS;
import static com.hedera.services.bdd.suites.contract.precompile.ERCPrecompileSuite.TRANSFER_FROM;
import static com.hedera.services.bdd.suites.contract.precompile.ERCPrecompileSuite.TRANSFER_FROM_ACCOUNT_TXN;
import static com.hedera.services.bdd.suites.contract.precompile.ERCPrecompileSuite.TRANSFER_SIGNATURE;
import static com.hedera.services.bdd.suites.contract.precompile.ERCPrecompileSuite.TRANSFER_SIG_NAME;
import static com.hedera.services.bdd.suites.contract.precompile.LazyCreateThroughPrecompileSuite.FIRST_META;
import static com.hedera.services.bdd.suites.contract.precompile.LazyCreateThroughPrecompileSuite.mirrorAddrWith;
import static com.hedera.services.bdd.suites.contract.precompile.WipeTokenAccountPrecompileSuite.GAS_TO_OFFER;
import static com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite.ADMIN_KEY;
import static com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite.BASE_APPROVE_TXN;
import static com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite.FUNGIBLE_TOKEN;
import static com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite.NON_FUNGIBLE_TOKEN;
import static com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite.OWNER;
import static com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite.SPENDER;
import static com.hedera.services.bdd.suites.crypto.CryptoCreateSuite.ACCOUNT;
import static com.hedera.services.bdd.suites.crypto.CryptoCreateSuite.LAZY_CREATION_ENABLED;
import static com.hedera.services.bdd.suites.ethereum.EthereumSuite.GAS_LIMIT;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.KNOWABLE_TOKEN;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.MULTI_KEY;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.VANILLA_TOKEN;
import static com.hedera.services.bdd.suites.token.TokenTransactSpecs.SUPPLY_KEY;
import static com.hedera.services.bdd.suites.token.TokenTransactSpecs.TRANSFER_TXN;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hedera.services.yahcli.commands.validation.ValidationCommand.RECEIVER;
import static com.hedera.services.yahcli.commands.validation.ValidationCommand.SENDER;
import static com.hedera.services.yahcli.commands.validation.ValidationCommand.TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_DENOMINATION_MUST_BE_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_MUST_BE_POSITIVE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CUSTOM_FEE_COLLECTOR;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_CHILD_RECORDS_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_GAS_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.esaulpaugh.headlong.abi.Address;
import com.esaulpaugh.headlong.abi.Tuple;
import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.contracts.ParsingConstants.FunctionType;
import com.hedera.node.app.hapi.utils.ethereum.EthTxData;
import com.hedera.node.app.hapi.utils.fee.FeeBuilder;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts;
import com.hedera.services.bdd.spec.assertions.ContractInfoAsserts;
import com.hedera.services.bdd.spec.assertions.NonFungibleTransfers;
import com.hedera.services.bdd.spec.assertions.SomeFungibleTransfers;
import com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.queries.QueryVerbs;
import com.hedera.services.bdd.spec.queries.meta.HapiGetTxnRecord;
import com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hedera.services.bdd.suites.contract.Utils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenPauseStatus;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.stream.IntStream;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

public class LeakyContractTestsSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(LeakyContractTestsSuite.class);
    public static final String CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT1 =
            "contracts.maxRefundPercentOfGasLimit";
    public static final String CREATE_TX = "createTX";
    public static final String CREATE_TX_REC = "createTXRec";
    private static final KeyShape DELEGATE_CONTRACT_KEY_SHAPE =
            KeyShape.threshOf(1, KeyShape.SIMPLE, DELEGATE_CONTRACT);
    private static final String CONTRACT_ALLOW_ASSOCIATIONS_PROPERTY =
            "contracts.allowAutoAssociations";
    private static final String TRANSFER_CONTRACT = "NonDelegateCryptoTransfer";
    private static final String HEDERA_ALLOWANCES_IS_ENABLED = "hedera.allowances.isEnabled";
    private static final String CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS =
            "contracts.allowSystemUseOfHapiSigs";
    private static final String CRYPTO_TRANSFER = "CryptoTransfer";
    private static final String FALSE = "false";
    private static final String TRUE = "true";

    public static void main(String... args) {
        new LeakyContractTestsSuite().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                transferToCaller(),
                resultSizeAffectsFees(),
                payerCannotOverSendValue(),
                propagatesNestedCreations(),
                temporarySStoreRefundTest(),
                transferZeroHbarsToCaller(),
                canCallPendingContractSafely(),
                deletedContractsCannotBeUpdated(),
                createTokenWithInvalidRoyaltyFee(),
                autoAssociationSlotsAppearsInInfo(),
                createTokenWithInvalidFeeCollector(),
                fungibleTokenCreateWithFeesHappyPath(),
                gasLimitOverMaxGasLimitFailsPrecheck(),
                nonFungibleTokenCreateWithFeesHappyPath(),
                createMinChargeIsTXGasUsedByContractCreate(),
                createGasLimitOverMaxGasLimitFailsPrecheck(),
                contractCreationStoragePriceMatchesFinalExpiry(),
                createTokenWithInvalidFixedFeeWithERC721Denomination(),
                maxRefundIsMaxGasRefundConfiguredWhenTXGasPriceIsSmaller(),
                accountWithoutAliasCanMakeEthTxnsDueToAutomaticAliasCreation(),
                createMaxRefundIsMaxGasRefundConfiguredWhenTXGasPriceIsSmaller(),
                lazyCreateThroughPrecompileNotSupportedWhenFlagDisabled(),
                evmLazyCreateViaSolidityCall(),
                evmLazyCreateViaSolidityCallTooManyCreatesFails(),
                erc20TransferFromDoesNotWorkIfFlagIsDisabled(),
                rejectsCreationAndUpdateOfAssociationsWhenFlagDisabled(),
                requiresTopLevelSignatureOrApprovalDependingOnControllingProperty(),
                transferWorksWithTopLevelSignatures(),
                transferDontWorkWithoutTopLevelSignatures(),
                getErc20TokenName(),
                getErc20TokenDecimals(),
                transferErc20TokenFromContractWithApproval(),
                erc20Allowance(),
                erc20Approve(),
                transferErc20TokenFromErc721TokenFails(),
                getErc20TokenDecimalsFromErc721TokenFails(),
                someERC20ApproveAllowanceScenariosPass(),
                someERC20NegativeTransferFromScenariosPass(),
                someERC20ApproveAllowanceScenarioInOneCall(),
                erc20TransferFrom(),
                erc20TransferFromSelf());
    }

    private HapiSpec erc20TransferFromSelf() {
        return propertyPreservingHapiSpec("Erc20TransferFromSelf")
                .preserving(HEDERA_ALLOWANCES_IS_ENABLED)
                .given(
                        overriding(HEDERA_ALLOWANCES_IS_ENABLED, TRUE),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(RECIPIENT),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.FINITE)
                                .initialSupply(10L)
                                .maxSupply(1000L)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY),
                        uploadInitCode(ERC_20_CONTRACT),
                        contractCreate(ERC_20_CONTRACT),
                        tokenAssociate(RECIPIENT, FUNGIBLE_TOKEN),
                        tokenAssociate(ERC_20_CONTRACT, FUNGIBLE_TOKEN),
                        cryptoTransfer(
                                moving(10, FUNGIBLE_TOKEN)
                                        .between(TOKEN_TREASURY, ERC_20_CONTRACT)))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                // ERC_20_CONTRACT should be able to transfer its
                                                // own tokens
                                                contractCall(
                                                                ERC_20_CONTRACT,
                                                                TRANSFER_FROM,
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getTokenID(
                                                                                                FUNGIBLE_TOKEN))),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getAccountID(
                                                                                                ERC_20_CONTRACT))),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getAccountID(
                                                                                                RECIPIENT))),
                                                                BigInteger.TWO)
                                                        .gas(500_000L)
                                                        .via(TRANSFER_FROM_ACCOUNT_TXN)
                                                        // No longer works unless you have allowance
                                                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED))))
                .then(getAccountBalance(RECIPIENT).hasTokenBalance(FUNGIBLE_TOKEN, 0));
    }

    private HapiSpec erc20TransferFrom() {
        final var allowanceTxn2 = "allowanceTxn2";

        return propertyPreservingHapiSpec("ERC_20_ALLOWANCE")
                .preserving(HEDERA_ALLOWANCES_IS_ENABLED)
                .given(
                        overriding(HEDERA_ALLOWANCES_IS_ENABLED, TRUE),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(OWNER).balance(100 * ONE_HUNDRED_HBARS),
                        cryptoCreate(RECIPIENT),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.FINITE)
                                .initialSupply(10L)
                                .maxSupply(1000L)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY),
                        uploadInitCode(ERC_20_CONTRACT),
                        contractCreate(ERC_20_CONTRACT),
                        tokenAssociate(OWNER, FUNGIBLE_TOKEN),
                        tokenAssociate(RECIPIENT, FUNGIBLE_TOKEN),
                        tokenAssociate(ERC_20_CONTRACT, FUNGIBLE_TOKEN),
                        cryptoTransfer(moving(10, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, OWNER)))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                // ERC_20_CONTRACT is approved as spender of
                                                // fungible tokens for OWNER
                                                cryptoApproveAllowance()
                                                        .payingWith(DEFAULT_PAYER)
                                                        .addTokenAllowance(
                                                                OWNER,
                                                                FUNGIBLE_TOKEN,
                                                                ERC_20_CONTRACT,
                                                                2L)
                                                        .via(BASE_APPROVE_TXN)
                                                        .logged()
                                                        .signedBy(DEFAULT_PAYER, OWNER)
                                                        .fee(ONE_HBAR),
                                                // Check that ERC_20_CONTRACT has allowance of 2
                                                contractCall(
                                                                ERC_20_CONTRACT,
                                                                ALLOWANCE,
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getTokenID(
                                                                                                FUNGIBLE_TOKEN))),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getAccountID(
                                                                                                OWNER))),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getAccountID(
                                                                                                ERC_20_CONTRACT))))
                                                        .gas(500_000L)
                                                        .via(ALLOWANCE_TXN)
                                                        .hasKnownStatus(SUCCESS),
                                                // ERC_20_CONTRACT calls the precompile transferFrom
                                                // as the spender
                                                contractCall(
                                                                ERC_20_CONTRACT,
                                                                TRANSFER_FROM,
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getTokenID(
                                                                                                FUNGIBLE_TOKEN))),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getAccountID(
                                                                                                OWNER))),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getAccountID(
                                                                                                RECIPIENT))),
                                                                BigInteger.TWO)
                                                        .gas(500_000L)
                                                        .via(TRANSFER_FROM_ACCOUNT_TXN)
                                                        .hasKnownStatus(SUCCESS),
                                                // ERC_20_CONTRACT should have spent its allowance
                                                contractCall(
                                                                ERC_20_CONTRACT,
                                                                ALLOWANCE,
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getTokenID(
                                                                                                FUNGIBLE_TOKEN))),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getAccountID(
                                                                                                OWNER))),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getAccountID(
                                                                                                ERC_20_CONTRACT))))
                                                        .gas(500_000L)
                                                        .via(allowanceTxn2)
                                                        .hasKnownStatus(SUCCESS))))
                .then(
                        childRecordsCheck(
                                ALLOWANCE_TXN,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .forFunction(
                                                                                FunctionType
                                                                                        .ERC_ALLOWANCE)
                                                                        .withAllowance(2)))),
                        childRecordsCheck(
                                allowanceTxn2,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .forFunction(
                                                                                FunctionType
                                                                                        .ERC_ALLOWANCE)
                                                                        .withAllowance(0)))));
    }

    private HapiSpec someERC20ApproveAllowanceScenariosPass() {
        final AtomicReference<String> tokenMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> contractMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> aCivilianMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> bCivilianMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> zCivilianMirrorAddr = new AtomicReference<>();

        return defaultHapiSpec("someERC20ApproveAllowanceScenariosPass")
                .given(
                        overriding(HEDERA_ALLOWANCES_IS_ENABLED, TRUE),
                        newKeyNamed(MULTI_KEY_NAME),
                        cryptoCreate(A_CIVILIAN)
                                .exposingCreatedIdTo(
                                        id -> aCivilianMirrorAddr.set(asHexedSolidityAddress(id))),
                        cryptoCreate(B_CIVILIAN)
                                .exposingCreatedIdTo(
                                        id -> bCivilianMirrorAddr.set(asHexedSolidityAddress(id))),
                        uploadInitCode(SOME_ERC_20_SCENARIOS),
                        contractCreate(SOME_ERC_20_SCENARIOS).adminKey(MULTI_KEY_NAME),
                        tokenCreate(TOKEN)
                                .supplyKey(MULTI_KEY_NAME)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(B_CIVILIAN)
                                .initialSupply(10)
                                .exposingCreatedIdTo(
                                        idLit ->
                                                tokenMirrorAddr.set(
                                                        asHexedSolidityAddress(
                                                                HapiPropertySource.asToken(
                                                                        idLit)))))
                .when(
                        withOpContext(
                                (spec, opLog) -> {
                                    zCivilianMirrorAddr.set(
                                            asHexedSolidityAddress(
                                                    AccountID.newBuilder()
                                                            .setAccountNum(666_666_666L)
                                                            .build()));
                                    contractMirrorAddr.set(
                                            asHexedSolidityAddress(
                                                    spec.registry()
                                                            .getAccountID(SOME_ERC_20_SCENARIOS)));
                                }),
                        sourcing(
                                () ->
                                        contractCall(
                                                        SOME_ERC_20_SCENARIOS,
                                                        DO_SPECIFIC_APPROVAL,
                                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                                        asHeadlongAddress(
                                                                aCivilianMirrorAddr.get()),
                                                        BigInteger.ZERO)
                                                .via("ACCOUNT_NOT_ASSOCIATED_TXN")
                                                .gas(1_000_000)
                                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        tokenAssociate(SOME_ERC_20_SCENARIOS, TOKEN),
                        sourcing(
                                () ->
                                        contractCall(
                                                        SOME_ERC_20_SCENARIOS,
                                                        DO_SPECIFIC_APPROVAL,
                                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                                        asHeadlongAddress(
                                                                zCivilianMirrorAddr.get()),
                                                        BigInteger.valueOf(5))
                                                .via(MISSING_TO)
                                                .gas(1_000_000)
                                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        sourcing(
                                () ->
                                        contractCall(
                                                        SOME_ERC_20_SCENARIOS,
                                                        DO_SPECIFIC_APPROVAL,
                                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                                        asHeadlongAddress(contractMirrorAddr.get()),
                                                        BigInteger.valueOf(5))
                                                .via("SPENDER_SAME_AS_OWNER_TXN")
                                                .gas(1_000_000)
                                                .hasKnownStatus(SUCCESS)),
                        sourcing(
                                () ->
                                        contractCall(
                                                        SOME_ERC_20_SCENARIOS,
                                                        DO_SPECIFIC_APPROVAL,
                                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                                        asHeadlongAddress(
                                                                aCivilianMirrorAddr.get()),
                                                        BigInteger.valueOf(5))
                                                .via("SUCCESSFUL_APPROVE_TXN")
                                                .gas(1_000_000)
                                                .hasKnownStatus(SUCCESS)),
                        sourcing(
                                () ->
                                        contractCall(
                                                        SOME_ERC_20_SCENARIOS,
                                                        GET_ALLOWANCE,
                                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                                        asHeadlongAddress(contractMirrorAddr.get()),
                                                        asHeadlongAddress(
                                                                aCivilianMirrorAddr.get()))
                                                .via("ALLOWANCE_TXN")
                                                .gas(1_000_000)
                                                .hasKnownStatus(SUCCESS)),
                        sourcing(
                                () ->
                                        contractCallLocal(
                                                SOME_ERC_20_SCENARIOS,
                                                GET_ALLOWANCE,
                                                asHeadlongAddress(tokenMirrorAddr.get()),
                                                asHeadlongAddress(contractMirrorAddr.get()),
                                                asHeadlongAddress(aCivilianMirrorAddr.get()))),
                        sourcing(
                                () ->
                                        contractCall(
                                                        SOME_ERC_20_SCENARIOS,
                                                        DO_SPECIFIC_APPROVAL,
                                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                                        asHeadlongAddress(
                                                                aCivilianMirrorAddr.get()),
                                                        BigInteger.ZERO)
                                                .via("SUCCESSFUL_REVOKE_TXN")
                                                .gas(1_000_000)
                                                .hasKnownStatus(SUCCESS)),
                        sourcing(
                                () ->
                                        contractCall(
                                                        SOME_ERC_20_SCENARIOS,
                                                        GET_ALLOWANCE,
                                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                                        asHeadlongAddress(contractMirrorAddr.get()),
                                                        asHeadlongAddress(
                                                                aCivilianMirrorAddr.get()))
                                                .via("ALLOWANCE_AFTER_REVOKE_TXN")
                                                .gas(1_000_000)
                                                .hasKnownStatus(SUCCESS)),
                        sourcing(
                                () ->
                                        contractCall(
                                                        SOME_ERC_20_SCENARIOS,
                                                        GET_ALLOWANCE,
                                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                                        asHeadlongAddress(
                                                                zCivilianMirrorAddr.get()),
                                                        asHeadlongAddress(
                                                                aCivilianMirrorAddr.get()))
                                                .via("MISSING_OWNER_ID")
                                                .gas(1_000_000)
                                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)))
                .then(
                        childRecordsCheck(
                                "ACCOUNT_NOT_ASSOCIATED_TXN",
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT)),
                        childRecordsCheck(
                                MISSING_TO,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_ALLOWANCE_SPENDER_ID)),
                        childRecordsCheck(
                                "SPENDER_SAME_AS_OWNER_TXN", SUCCESS, recordWith().status(SUCCESS)),
                        childRecordsCheck(
                                "SUCCESSFUL_APPROVE_TXN", SUCCESS, recordWith().status(SUCCESS)),
                        childRecordsCheck(
                                "SUCCESSFUL_REVOKE_TXN", SUCCESS, recordWith().status(SUCCESS)),
                        childRecordsCheck(
                                "MISSING_OWNER_ID",
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_ALLOWANCE_OWNER_ID)),
                        childRecordsCheck(
                                "ALLOWANCE_TXN",
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .forFunction(
                                                                                FunctionType
                                                                                        .ERC_ALLOWANCE)
                                                                        .withAllowance(5L)))),
                        childRecordsCheck(
                                "ALLOWANCE_AFTER_REVOKE_TXN",
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .forFunction(
                                                                                FunctionType
                                                                                        .ERC_ALLOWANCE)
                                                                        .withAllowance(0L)))));
    }

    private HapiSpec someERC20NegativeTransferFromScenariosPass() {
        final AtomicReference<String> tokenMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> contractMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> aCivilianMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> bCivilianMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> zCivilianMirrorAddr = new AtomicReference<>();

        return propertyPreservingHapiSpec("someERC20NegativeTransferFromScenariosPass")
                .preserving(HEDERA_ALLOWANCES_IS_ENABLED)
                .given(
                        overriding(HEDERA_ALLOWANCES_IS_ENABLED, TRUE),
                        newKeyNamed(MULTI_KEY_NAME),
                        cryptoCreate(A_CIVILIAN)
                                .exposingCreatedIdTo(
                                        id -> aCivilianMirrorAddr.set(asHexedSolidityAddress(id))),
                        cryptoCreate(B_CIVILIAN)
                                .exposingCreatedIdTo(
                                        id -> bCivilianMirrorAddr.set(asHexedSolidityAddress(id))),
                        uploadInitCode(SOME_ERC_20_SCENARIOS),
                        contractCreate(SOME_ERC_20_SCENARIOS).adminKey(MULTI_KEY_NAME),
                        tokenCreate(TOKEN)
                                .supplyKey(MULTI_KEY_NAME)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(SOME_ERC_20_SCENARIOS)
                                .initialSupply(10)
                                .exposingCreatedIdTo(
                                        idLit ->
                                                tokenMirrorAddr.set(
                                                        asHexedSolidityAddress(
                                                                HapiPropertySource.asToken(
                                                                        idLit)))))
                .when(
                        withOpContext(
                                (spec, opLog) -> {
                                    zCivilianMirrorAddr.set(
                                            asHexedSolidityAddress(
                                                    AccountID.newBuilder()
                                                            .setAccountNum(666_666_666L)
                                                            .build()));
                                    contractMirrorAddr.set(
                                            asHexedSolidityAddress(
                                                    spec.registry()
                                                            .getAccountID(SOME_ERC_20_SCENARIOS)));
                                }),
                        sourcing(
                                () ->
                                        contractCall(
                                                        SOME_ERC_20_SCENARIOS,
                                                        DO_TRANSFER_FROM,
                                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                                        asHeadlongAddress(contractMirrorAddr.get()),
                                                        asHeadlongAddress(
                                                                bCivilianMirrorAddr.get()),
                                                        BigInteger.ONE)
                                                .payingWith(GENESIS)
                                                .via("TOKEN_NOT_ASSOCIATED_TO_ACCOUNT_TXN")
                                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        tokenAssociate(B_CIVILIAN, TOKEN),
                        sourcing(
                                () ->
                                        contractCall(
                                                        SOME_ERC_20_SCENARIOS,
                                                        DO_TRANSFER_FROM,
                                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                                        asHeadlongAddress(
                                                                zCivilianMirrorAddr.get()),
                                                        asHeadlongAddress(
                                                                bCivilianMirrorAddr.get()),
                                                        BigInteger.ONE)
                                                .payingWith(GENESIS)
                                                .via(MISSING_FROM)
                                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        sourcing(
                                () ->
                                        contractCall(
                                                        SOME_ERC_20_SCENARIOS,
                                                        DO_TRANSFER_FROM,
                                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                                        asHeadlongAddress(contractMirrorAddr.get()),
                                                        asHeadlongAddress(
                                                                bCivilianMirrorAddr.get()),
                                                        BigInteger.ONE)
                                                .payingWith(GENESIS)
                                                .via(MSG_SENDER_IS_THE_SAME_AS_FROM)
                                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        cryptoTransfer(
                                moving(9L, TOKEN).between(SOME_ERC_20_SCENARIOS, B_CIVILIAN)),
                        tokenAssociate(A_CIVILIAN, TOKEN),
                        sourcing(
                                () ->
                                        contractCall(
                                                        SOME_ERC_20_SCENARIOS,
                                                        DO_TRANSFER_FROM,
                                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                                        asHeadlongAddress(
                                                                bCivilianMirrorAddr.get()),
                                                        asHeadlongAddress(
                                                                aCivilianMirrorAddr.get()),
                                                        BigInteger.ONE)
                                                .payingWith(GENESIS)
                                                .via(MSG_SENDER_IS_NOT_THE_SAME_AS_FROM)
                                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        cryptoApproveAllowance()
                                .payingWith(B_CIVILIAN)
                                .addTokenAllowance(B_CIVILIAN, TOKEN, SOME_ERC_20_SCENARIOS, 1L)
                                .signedBy(DEFAULT_PAYER, B_CIVILIAN)
                                .fee(ONE_HBAR),
                        sourcing(
                                () ->
                                        contractCall(
                                                        SOME_ERC_20_SCENARIOS,
                                                        DO_TRANSFER_FROM,
                                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                                        asHeadlongAddress(
                                                                bCivilianMirrorAddr.get()),
                                                        asHeadlongAddress(
                                                                aCivilianMirrorAddr.get()),
                                                        BigInteger.valueOf(5))
                                                .payingWith(GENESIS)
                                                .via(
                                                        "TRY_TO_TRANSFER_MORE_THAN_APPROVED_AMOUNT_TXN")
                                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        cryptoApproveAllowance()
                                .payingWith(B_CIVILIAN)
                                .addTokenAllowance(B_CIVILIAN, TOKEN, SOME_ERC_20_SCENARIOS, 20L)
                                .signedBy(DEFAULT_PAYER, B_CIVILIAN)
                                .fee(ONE_HBAR),
                        sourcing(
                                () ->
                                        contractCall(
                                                        SOME_ERC_20_SCENARIOS,
                                                        DO_TRANSFER_FROM,
                                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                                        asHeadlongAddress(
                                                                bCivilianMirrorAddr.get()),
                                                        asHeadlongAddress(
                                                                aCivilianMirrorAddr.get()),
                                                        BigInteger.valueOf(20))
                                                .payingWith(GENESIS)
                                                .via("TRY_TO_TRANSFER_MORE_THAN_OWNERS_BALANCE_TXN")
                                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)))
                .then(
                        childRecordsCheck(
                                "TOKEN_NOT_ASSOCIATED_TO_ACCOUNT_TXN",
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT)),
                        childRecordsCheck(
                                MISSING_FROM,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_ACCOUNT_ID)),
                        childRecordsCheck(
                                MSG_SENDER_IS_THE_SAME_AS_FROM,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(SPENDER_DOES_NOT_HAVE_ALLOWANCE)),
                        childRecordsCheck(
                                MSG_SENDER_IS_NOT_THE_SAME_AS_FROM,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(SPENDER_DOES_NOT_HAVE_ALLOWANCE)),
                        childRecordsCheck(
                                "TRY_TO_TRANSFER_MORE_THAN_APPROVED_AMOUNT_TXN",
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(AMOUNT_EXCEEDS_ALLOWANCE)),
                        childRecordsCheck(
                                "TRY_TO_TRANSFER_MORE_THAN_OWNERS_BALANCE_TXN",
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INSUFFICIENT_TOKEN_BALANCE)));
    }

    private HapiSpec someERC20ApproveAllowanceScenarioInOneCall() {
        final AtomicReference<String> tokenMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> contractMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> aCivilianMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> bCivilianMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> zCivilianMirrorAddr = new AtomicReference<>();

        return propertyPreservingHapiSpec("someERC20ApproveAllowanceScenarioInOneCall")
                .preserving(HEDERA_ALLOWANCES_IS_ENABLED)
                .given(
                        overriding(HEDERA_ALLOWANCES_IS_ENABLED, TRUE),
                        newKeyNamed(MULTI_KEY_NAME),
                        cryptoCreate(A_CIVILIAN)
                                .exposingCreatedIdTo(
                                        id -> aCivilianMirrorAddr.set(asHexedSolidityAddress(id))),
                        cryptoCreate(B_CIVILIAN)
                                .exposingCreatedIdTo(
                                        id -> bCivilianMirrorAddr.set(asHexedSolidityAddress(id))),
                        uploadInitCode(SOME_ERC_20_SCENARIOS),
                        contractCreate(SOME_ERC_20_SCENARIOS).adminKey(MULTI_KEY_NAME),
                        tokenCreate(TOKEN)
                                .supplyKey(MULTI_KEY_NAME)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(B_CIVILIAN)
                                .initialSupply(10)
                                .exposingCreatedIdTo(
                                        idLit ->
                                                tokenMirrorAddr.set(
                                                        asHexedSolidityAddress(
                                                                HapiPropertySource.asToken(
                                                                        idLit)))),
                        tokenAssociate(SOME_ERC_20_SCENARIOS, TOKEN))
                .when(
                        withOpContext(
                                (spec, opLog) -> {
                                    zCivilianMirrorAddr.set(
                                            asHexedSolidityAddress(
                                                    AccountID.newBuilder()
                                                            .setAccountNum(666_666_666L)
                                                            .build()));
                                    contractMirrorAddr.set(
                                            asHexedSolidityAddress(
                                                    spec.registry()
                                                            .getAccountID(SOME_ERC_20_SCENARIOS)));
                                }),
                        sourcing(
                                () ->
                                        contractCall(
                                                        SOME_ERC_20_SCENARIOS,
                                                        "approveAndGetAllowanceAmount",
                                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                                        asHeadlongAddress(
                                                                aCivilianMirrorAddr.get()),
                                                        BigInteger.valueOf(5))
                                                .via("APPROVE_AND_GET_ALLOWANCE_TXN")
                                                .gas(1_000_000)
                                                .hasKnownStatus(SUCCESS)
                                                .logged()))
                .then(
                        childRecordsCheck(
                                "APPROVE_AND_GET_ALLOWANCE_TXN",
                                SUCCESS,
                                recordWith().status(SUCCESS),
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .forFunction(
                                                                                FunctionType
                                                                                        .ERC_ALLOWANCE)
                                                                        .withAllowance(5L)))));
    }

    private HapiSpec erc20Allowance() {
        return propertyPreservingHapiSpec("ERC_20_ALLOWANCE")
                .preserving(HEDERA_ALLOWANCES_IS_ENABLED)
                .given(
                        overriding(HEDERA_ALLOWANCES_IS_ENABLED, TRUE),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(OWNER).balance(100 * ONE_HUNDRED_HBARS),
                        cryptoCreate(SPENDER),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.FINITE)
                                .initialSupply(10L)
                                .maxSupply(1000L)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY),
                        uploadInitCode(ERC_20_CONTRACT),
                        contractCreate(ERC_20_CONTRACT),
                        tokenAssociate(OWNER, FUNGIBLE_TOKEN),
                        cryptoTransfer(moving(10, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, OWNER)),
                        cryptoApproveAllowance()
                                .payingWith(DEFAULT_PAYER)
                                .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SPENDER, 2L)
                                .via(BASE_APPROVE_TXN)
                                .logged()
                                .signedBy(DEFAULT_PAYER, OWNER)
                                .fee(ONE_HBAR))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                ERC_20_CONTRACT,
                                                                ALLOWANCE,
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getTokenID(
                                                                                                FUNGIBLE_TOKEN))),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getAccountID(
                                                                                                OWNER))),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getAccountID(
                                                                                                SPENDER))))
                                                        .payingWith(OWNER)
                                                        .via(ALLOWANCE_TXN)
                                                        .hasKnownStatus(SUCCESS))))
                .then(
                        getTxnRecord(ALLOWANCE_TXN).andAllChildRecords().logged(),
                        childRecordsCheck(
                                ALLOWANCE_TXN,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .forFunction(
                                                                                FunctionType
                                                                                        .ERC_ALLOWANCE)
                                                                        .withAllowance(2)))));
    }

    private HapiSpec erc20Approve() {
        final var approveTxn = "approveTxn";

        return propertyPreservingHapiSpec("ERC_20_APPROVE")
                .preserving(HEDERA_ALLOWANCES_IS_ENABLED)
                .given(
                        overriding(HEDERA_ALLOWANCES_IS_ENABLED, TRUE),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(OWNER).balance(100 * ONE_HUNDRED_HBARS),
                        cryptoCreate(SPENDER),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.FINITE)
                                .initialSupply(10L)
                                .maxSupply(1000L)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY),
                        uploadInitCode(ERC_20_CONTRACT),
                        contractCreate(ERC_20_CONTRACT),
                        tokenAssociate(OWNER, FUNGIBLE_TOKEN),
                        tokenAssociate(ERC_20_CONTRACT, FUNGIBLE_TOKEN))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                ERC_20_CONTRACT,
                                                                APPROVE,
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getTokenID(
                                                                                                FUNGIBLE_TOKEN))),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getAccountID(
                                                                                                SPENDER))),
                                                                BigInteger.valueOf(10))
                                                        .payingWith(OWNER)
                                                        .gas(4_000_000L)
                                                        .via(approveTxn)
                                                        .hasKnownStatus(SUCCESS))))
                .then(
                        childRecordsCheck(approveTxn, SUCCESS, recordWith().status(SUCCESS)),
                        getTxnRecord(approveTxn).andAllChildRecords().logged());
    }

    private HapiSpec getErc20TokenDecimalsFromErc721TokenFails() {
        final var invalidDecimalsTxn = "decimalsFromErc721Txn";

        return propertyPreservingHapiSpec("ERC_20_DECIMALS_FROM_ERC_721_TOKEN")
                .preserving(HEDERA_ALLOWANCES_IS_ENABLED)
                .given(
                        overriding(HEDERA_ALLOWANCES_IS_ENABLED, TRUE),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT).balance(100 * ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY),
                        mintToken(NON_FUNGIBLE_TOKEN, List.of(FIRST_META)),
                        fileCreate(ERC_20_CONTRACT_NAME),
                        uploadInitCode(ERC_20_CONTRACT),
                        contractCreate(ERC_20_CONTRACT))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                ERC_20_CONTRACT,
                                                                DECIMALS,
                                                                asHeadlongAddress(
                                                                        asHexedAddress(
                                                                                spec.registry()
                                                                                        .getTokenID(
                                                                                                NON_FUNGIBLE_TOKEN))))
                                                        .payingWith(ACCOUNT)
                                                        .via(invalidDecimalsTxn)
                                                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                                        .gas(GAS_TO_OFFER))))
                .then(getTxnRecord(invalidDecimalsTxn).andAllChildRecords().logged());
    }

    private HapiSpec transferErc20TokenFromErc721TokenFails() {
        return propertyPreservingHapiSpec("ERC_20_TRANSFER_FROM_ERC_721_TOKEN")
                .preserving(HEDERA_ALLOWANCES_IS_ENABLED)
                .given(
                        overriding(HEDERA_ALLOWANCES_IS_ENABLED, TRUE),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT).balance(100 * ONE_MILLION_HBARS),
                        cryptoCreate(RECIPIENT),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY),
                        mintToken(NON_FUNGIBLE_TOKEN, List.of(FIRST_META)),
                        tokenAssociate(ACCOUNT, List.of(NON_FUNGIBLE_TOKEN)),
                        tokenAssociate(RECIPIENT, List.of(NON_FUNGIBLE_TOKEN)),
                        cryptoTransfer(
                                        movingUnique(NON_FUNGIBLE_TOKEN, 1)
                                                .between(TOKEN_TREASURY, ACCOUNT))
                                .payingWith(ACCOUNT),
                        uploadInitCode(ERC_20_CONTRACT),
                        contractCreate(ERC_20_CONTRACT))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                ERC_20_CONTRACT,
                                                                TRANSFER,
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getTokenID(
                                                                                                NON_FUNGIBLE_TOKEN))),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getAccountID(
                                                                                                RECIPIENT))),
                                                                BigInteger.TWO)
                                                        .payingWith(ACCOUNT)
                                                        .alsoSigningWithFullPrefix(MULTI_KEY)
                                                        .via(TRANSFER_TXN)
                                                        .gas(GAS_TO_OFFER)
                                                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED))))
                .then(getTxnRecord(TRANSFER_TXN).andAllChildRecords().logged());
    }

    private HapiSpec transferErc20TokenFromContractWithApproval() {
        final var transferFromOtherContractWithSignaturesTxn =
                "transferFromOtherContractWithSignaturesTxn";
        final var nestedContract = "NestedERC20Contract";

        return propertyPreservingHapiSpec("ERC_20_TRANSFER_FROM_CONTRACT_WITH_APPROVAL")
                .preserving(CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS)
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT).balance(10 * ONE_MILLION_HBARS),
                        cryptoCreate(RECIPIENT),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(35)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY),
                        uploadInitCode(ERC_20_CONTRACT, nestedContract),
                        newKeyNamed(TRANSFER_SIG_NAME).shape(SIMPLE.signedWith(ON)),
                        contractCreate(ERC_20_CONTRACT).adminKey(TRANSFER_SIG_NAME),
                        contractCreate(nestedContract).adminKey(TRANSFER_SIG_NAME),
                        overriding(CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS, CRYPTO_TRANSFER))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                tokenAssociate(ACCOUNT, List.of(FUNGIBLE_TOKEN)),
                                                tokenAssociate(RECIPIENT, List.of(FUNGIBLE_TOKEN)),
                                                tokenAssociate(
                                                        ERC_20_CONTRACT, List.of(FUNGIBLE_TOKEN)),
                                                tokenAssociate(
                                                        nestedContract, List.of(FUNGIBLE_TOKEN)),
                                                cryptoTransfer(
                                                                TokenMovement.moving(
                                                                                20, FUNGIBLE_TOKEN)
                                                                        .between(
                                                                                TOKEN_TREASURY,
                                                                                ERC_20_CONTRACT))
                                                        .payingWith(ACCOUNT),
                                                contractCall(
                                                                ERC_20_CONTRACT,
                                                                APPROVE,
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getTokenID(
                                                                                                FUNGIBLE_TOKEN))),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                ERC_20_CONTRACT))),
                                                                BigInteger.valueOf(20))
                                                        .gas(1_000_000)
                                                        .payingWith(ACCOUNT)
                                                        .alsoSigningWithFullPrefix(
                                                                TRANSFER_SIG_NAME),
                                                contractCall(
                                                                ERC_20_CONTRACT,
                                                                TRANSFER_FROM,
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getTokenID(
                                                                                                FUNGIBLE_TOKEN))),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                ERC_20_CONTRACT))),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                nestedContract))),
                                                                BigInteger.valueOf(5))
                                                        .via(TRANSFER_TXN)
                                                        .alsoSigningWithFullPrefix(
                                                                TRANSFER_SIG_NAME)
                                                        .hasKnownStatus(SUCCESS),
                                                contractCall(
                                                                ERC_20_CONTRACT,
                                                                TRANSFER_FROM,
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getTokenID(
                                                                                                FUNGIBLE_TOKEN))),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                ERC_20_CONTRACT))),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                nestedContract))),
                                                                BigInteger.valueOf(5))
                                                        .payingWith(ACCOUNT)
                                                        .alsoSigningWithFullPrefix(
                                                                TRANSFER_SIG_NAME)
                                                        .via(
                                                                transferFromOtherContractWithSignaturesTxn))))
                .then(
                        getContractInfo(ERC_20_CONTRACT).saveToRegistry(ERC_20_CONTRACT),
                        getContractInfo(nestedContract).saveToRegistry(nestedContract),
                        withOpContext(
                                (spec, log) -> {
                                    final var sender =
                                            spec.registry()
                                                    .getContractInfo(ERC_20_CONTRACT)
                                                    .getContractID();
                                    final var receiver =
                                            spec.registry()
                                                    .getContractInfo(nestedContract)
                                                    .getContractID();

                                    var transferRecord =
                                            getTxnRecord(TRANSFER_TXN)
                                                    .hasPriority(
                                                            recordWith()
                                                                    .contractCallResult(
                                                                            resultWith()
                                                                                    .logs(
                                                                                            inOrder(
                                                                                                    logWith()
                                                                                                            .withTopicsInOrder(
                                                                                                                    List
                                                                                                                            .of(
                                                                                                                                    eventSignatureOf(
                                                                                                                                            TRANSFER_SIGNATURE),
                                                                                                                                    parsedToByteString(
                                                                                                                                            sender
                                                                                                                                                    .getContractNum()),
                                                                                                                                    parsedToByteString(
                                                                                                                                            receiver
                                                                                                                                                    .getContractNum())))
                                                                                                            .longValue(
                                                                                                                    5)))))
                                                    .andAllChildRecords();

                                    var transferFromOtherContractWithSignaturesTxnRecord =
                                            getTxnRecord(transferFromOtherContractWithSignaturesTxn)
                                                    .hasPriority(
                                                            recordWith()
                                                                    .contractCallResult(
                                                                            resultWith()
                                                                                    .logs(
                                                                                            inOrder(
                                                                                                    logWith()
                                                                                                            .withTopicsInOrder(
                                                                                                                    List
                                                                                                                            .of(
                                                                                                                                    eventSignatureOf(
                                                                                                                                            TRANSFER_SIGNATURE),
                                                                                                                                    parsedToByteString(
                                                                                                                                            sender
                                                                                                                                                    .getContractNum()),
                                                                                                                                    parsedToByteString(
                                                                                                                                            receiver
                                                                                                                                                    .getContractNum())))
                                                                                                            .longValue(
                                                                                                                    5)))))
                                                    .andAllChildRecords();

                                    allRunFor(
                                            spec,
                                            transferRecord,
                                            transferFromOtherContractWithSignaturesTxnRecord);
                                }),
                        childRecordsCheck(
                                TRANSFER_TXN,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .forFunction(
                                                                                FunctionType
                                                                                        .ERC_TRANSFER)
                                                                        .withErcFungibleTransferStatus(
                                                                                true)))),
                        childRecordsCheck(
                                transferFromOtherContractWithSignaturesTxn,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .forFunction(
                                                                                FunctionType
                                                                                        .ERC_TRANSFER)
                                                                        .withErcFungibleTransferStatus(
                                                                                true)))),
                        getAccountBalance(ERC_20_CONTRACT).hasTokenBalance(FUNGIBLE_TOKEN, 10),
                        getAccountBalance(nestedContract).hasTokenBalance(FUNGIBLE_TOKEN, 10));
    }

    private HapiSpec getErc20TokenDecimals() {
        final var decimals = 10;
        final var decimalsTxn = "decimalsTxn";
        final AtomicReference<String> tokenAddr = new AtomicReference<>();

        return propertyPreservingHapiSpec("ERC_20_DECIMALS")
                .preserving(HEDERA_ALLOWANCES_IS_ENABLED)
                .given(
                        overriding(HEDERA_ALLOWANCES_IS_ENABLED, TRUE),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT).balance(100 * ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(5)
                                .decimals(decimals)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .exposingCreatedIdTo(
                                        id ->
                                                tokenAddr.set(
                                                        asHexedSolidityAddress(
                                                                HapiPropertySource.asToken(id)))),
                        fileCreate(ERC_20_CONTRACT_NAME),
                        uploadInitCode(ERC_20_CONTRACT),
                        contractCreate(ERC_20_CONTRACT))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                ERC_20_CONTRACT,
                                                                DECIMALS,
                                                                asHeadlongAddress(
                                                                        asHexedAddress(
                                                                                spec.registry()
                                                                                        .getTokenID(
                                                                                                FUNGIBLE_TOKEN))))
                                                        .payingWith(ACCOUNT)
                                                        .via(decimalsTxn)
                                                        .hasKnownStatus(SUCCESS)
                                                        .gas(GAS_TO_OFFER))))
                .then(
                        childRecordsCheck(
                                decimalsTxn,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .forFunction(
                                                                                FunctionType
                                                                                        .ERC_DECIMALS)
                                                                        .withDecimals(decimals)))),
                        sourcing(
                                () ->
                                        contractCallLocal(
                                                ERC_20_CONTRACT,
                                                DECIMALS,
                                                asHeadlongAddress(tokenAddr.get()))));
    }

    private HapiSpec getErc20TokenName() {
        return propertyPreservingHapiSpec("ERC_20_NAME")
                .preserving(HEDERA_ALLOWANCES_IS_ENABLED)
                .given(
                        overriding(HEDERA_ALLOWANCES_IS_ENABLED, TRUE),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT).balance(100 * ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(5)
                                .name(TOKEN_NAME)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY),
                        uploadInitCode(ERC_20_CONTRACT),
                        contractCreate(ERC_20_CONTRACT))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                ERC_20_CONTRACT,
                                                                "name",
                                                                asHeadlongAddress(
                                                                        asHexedAddress(
                                                                                spec.registry()
                                                                                        .getTokenID(
                                                                                                FUNGIBLE_TOKEN))))
                                                        .payingWith(ACCOUNT)
                                                        .via(NAME_TXN)
                                                        .gas(4_000_000)
                                                        .hasKnownStatus(SUCCESS))))
                .then(
                        childRecordsCheck(
                                NAME_TXN,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .forFunction(
                                                                                FunctionType
                                                                                        .ERC_NAME)
                                                                        .withName(TOKEN_NAME)))));
    }

    private HapiSpec transferDontWorkWithoutTopLevelSignatures() {
        final String ALLOW_SYSTEM_USE_OF_HAPI_SIGS = CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS;
        final var transferTokenTxn = "transferTokenTxn";
        final var transferTokensTxn = "transferTokensTxn";
        final var transferNFTTxn = "transferNFTTxn";
        final var transferNFTsTxn = "transferNFTsTxn";
        final var contract = "TokenTransferContract";

        final AtomicReference<AccountID> accountID = new AtomicReference<>();
        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
        final AtomicReference<TokenID> vanillaNftID = new AtomicReference<>();
        return propertyPreservingHapiSpec("transferWorksWithTopLevelSignatures")
                .preserving(ALLOW_SYSTEM_USE_OF_HAPI_SIGS)
                .given(
                        // disable top level signatures for all functions
                        overriding(ALLOW_SYSTEM_USE_OF_HAPI_SIGS, ""),
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(RECEIVER),
                        cryptoCreate(RECEIVER_2),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .supplyKey(SUPPLY_KEY)
                                .initialSupply(1_000)
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
                        tokenCreate(KNOWABLE_TOKEN)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .supplyKey(SUPPLY_KEY)
                                .initialSupply(0)
                                .exposingCreatedIdTo(id -> vanillaNftID.set(asToken(id))),
                        tokenAssociate(ACCOUNT, VANILLA_TOKEN, KNOWABLE_TOKEN),
                        tokenAssociate(RECEIVER, VANILLA_TOKEN, KNOWABLE_TOKEN),
                        tokenAssociate(RECEIVER_2, VANILLA_TOKEN, KNOWABLE_TOKEN),
                        mintToken(
                                KNOWABLE_TOKEN,
                                List.of(
                                        copyFromUtf8("dark"),
                                        copyFromUtf8("matter"),
                                        copyFromUtf8("dark1"),
                                        copyFromUtf8("matter1"))),
                        cryptoTransfer(moving(500, VANILLA_TOKEN).between(TOKEN_TREASURY, ACCOUNT)),
                        cryptoTransfer(
                                movingUnique(KNOWABLE_TOKEN, 1, 2, 3, 4)
                                        .between(TOKEN_TREASURY, ACCOUNT)),
                        uploadInitCode(contract),
                        contractCreate(contract))
                .when(
                        // Do transfers by calling contract from EOA, and should be failing with
                        // CONTRACT_REVERT_EXECUTED
                        withOpContext(
                                (spec, opLog) -> {
                                    final var receiver1 =
                                            asHeadlongAddress(
                                                    asAddress(
                                                            spec.registry()
                                                                    .getAccountID(RECEIVER)));
                                    final var receiver2 =
                                            asHeadlongAddress(
                                                    asAddress(
                                                            spec.registry()
                                                                    .getAccountID(RECEIVER_2)));
                                    final var sender =
                                            asHeadlongAddress(
                                                    asAddress(
                                                            spec.registry().getAccountID(ACCOUNT)));
                                    final var amount = 5L;

                                    final var accounts =
                                            new Address[] {sender, receiver1, receiver2};
                                    final var amounts = new long[] {-10L, 5L, 5L};
                                    final var serials = new long[] {2L, 3L};
                                    final var serial = 1L;
                                    allRunFor(
                                            spec,
                                            contractCall(
                                                            contract,
                                                            "transferTokenPublic",
                                                            HapiParserUtil.asHeadlongAddress(
                                                                    asAddress(
                                                                            spec.registry()
                                                                                    .getTokenID(
                                                                                            VANILLA_TOKEN))),
                                                            sender,
                                                            receiver1,
                                                            amount)
                                                    .payingWith(ACCOUNT)
                                                    .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                                    .gas(GAS_TO_OFFER)
                                                    .via(transferTokenTxn),
                                            contractCall(
                                                            contract,
                                                            "transferTokensPublic",
                                                            HapiParserUtil.asHeadlongAddress(
                                                                    asAddress(
                                                                            spec.registry()
                                                                                    .getTokenID(
                                                                                            VANILLA_TOKEN))),
                                                            accounts,
                                                            amounts)
                                                    .payingWith(ACCOUNT)
                                                    .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                                    .gas(GAS_TO_OFFER)
                                                    .via(transferTokensTxn),
                                            contractCall(
                                                            contract,
                                                            "transferNFTPublic",
                                                            HapiParserUtil.asHeadlongAddress(
                                                                    asAddress(
                                                                            spec.registry()
                                                                                    .getTokenID(
                                                                                            KNOWABLE_TOKEN))),
                                                            sender,
                                                            receiver1,
                                                            serial)
                                                    .payingWith(ACCOUNT)
                                                    .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                                    .gas(GAS_TO_OFFER)
                                                    .via(transferNFTTxn),
                                            contractCall(
                                                            contract,
                                                            "transferNFTsPublic",
                                                            HapiParserUtil.asHeadlongAddress(
                                                                    asAddress(
                                                                            spec.registry()
                                                                                    .getTokenID(
                                                                                            KNOWABLE_TOKEN))),
                                                            new Address[] {sender, sender},
                                                            new Address[] {receiver2, receiver2},
                                                            serials)
                                                    .payingWith(ACCOUNT)
                                                    .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                                    .gas(GAS_TO_OFFER)
                                                    .via(transferNFTsTxn));
                                }))
                .then(
                        // Confirm the transactions fails with no top level signatures enabled
                        childRecordsCheck(
                                transferTokenTxn,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(SPENDER_DOES_NOT_HAVE_ALLOWANCE)),
                        childRecordsCheck(
                                transferTokensTxn,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(SPENDER_DOES_NOT_HAVE_ALLOWANCE)),
                        childRecordsCheck(
                                transferNFTTxn,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(SPENDER_DOES_NOT_HAVE_ALLOWANCE)),
                        childRecordsCheck(
                                transferNFTsTxn,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(SPENDER_DOES_NOT_HAVE_ALLOWANCE)),
                        // Confirm the balances are correct
                        getAccountInfo(RECEIVER).hasOwnedNfts(0),
                        getAccountBalance(RECEIVER).hasTokenBalance(VANILLA_TOKEN, 0),
                        getAccountInfo(RECEIVER_2).hasOwnedNfts(0),
                        getAccountBalance(RECEIVER_2).hasTokenBalance(VANILLA_TOKEN, 0),
                        getAccountInfo(ACCOUNT).hasOwnedNfts(4),
                        getAccountBalance(ACCOUNT).hasTokenBalance(VANILLA_TOKEN, 500L));
    }

    private HapiSpec transferWorksWithTopLevelSignatures() {
        final String ALLOW_SYSTEM_USE_OF_HAPI_SIGS = CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS;
        final var transferTokenTxn = "transferTokenTxn";
        final var transferTokensTxn = "transferTokensTxn";
        final var transferNFTTxn = "transferNFTTxn";
        final var transferNFTsTxn = "transferNFTsTxn";
        final var contract = "TokenTransferContract";

        final AtomicReference<AccountID> accountID = new AtomicReference<>();
        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
        final AtomicReference<TokenID> vanillaNftID = new AtomicReference<>();
        return propertyPreservingHapiSpec("transferWorksWithTopLevelSignatures")
                .preserving(ALLOW_SYSTEM_USE_OF_HAPI_SIGS)
                .given(
                        // enable top level signatures for
                        // transferToken/transferTokens/transferNft/transferNfts
                        overriding(ALLOW_SYSTEM_USE_OF_HAPI_SIGS, CRYPTO_TRANSFER),
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(RECEIVER),
                        cryptoCreate(RECEIVER_2),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .supplyKey(SUPPLY_KEY)
                                .initialSupply(1_000)
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
                        tokenCreate(KNOWABLE_TOKEN)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .supplyKey(SUPPLY_KEY)
                                .initialSupply(0)
                                .exposingCreatedIdTo(id -> vanillaNftID.set(asToken(id))),
                        tokenAssociate(ACCOUNT, VANILLA_TOKEN, KNOWABLE_TOKEN),
                        tokenAssociate(RECEIVER, VANILLA_TOKEN, KNOWABLE_TOKEN),
                        tokenAssociate(RECEIVER_2, VANILLA_TOKEN, KNOWABLE_TOKEN),
                        mintToken(
                                KNOWABLE_TOKEN,
                                List.of(
                                        copyFromUtf8("dark"),
                                        copyFromUtf8("matter"),
                                        copyFromUtf8("dark1"),
                                        copyFromUtf8("matter1"))),
                        cryptoTransfer(moving(500, VANILLA_TOKEN).between(TOKEN_TREASURY, ACCOUNT)),
                        cryptoTransfer(
                                movingUnique(KNOWABLE_TOKEN, 1, 2, 3, 4)
                                        .between(TOKEN_TREASURY, ACCOUNT)),
                        uploadInitCode(contract),
                        contractCreate(contract))
                .when(
                        // Do transfers by calling contract from EOA
                        withOpContext(
                                (spec, opLog) -> {
                                    final var receiver1 =
                                            asHeadlongAddress(
                                                    asAddress(
                                                            spec.registry()
                                                                    .getAccountID(RECEIVER)));
                                    final var receiver2 =
                                            asHeadlongAddress(
                                                    asAddress(
                                                            spec.registry()
                                                                    .getAccountID(RECEIVER_2)));
                                    final var sender =
                                            asHeadlongAddress(
                                                    asAddress(
                                                            spec.registry().getAccountID(ACCOUNT)));
                                    final var amount = 5L;

                                    final var accounts =
                                            new Address[] {sender, receiver1, receiver2};
                                    final var amounts = new long[] {-10L, 5L, 5L};
                                    final var serials = new long[] {2L, 3L};
                                    final var serial = 1L;
                                    allRunFor(
                                            spec,
                                            contractCall(
                                                            contract,
                                                            "transferTokenPublic",
                                                            HapiParserUtil.asHeadlongAddress(
                                                                    asAddress(
                                                                            spec.registry()
                                                                                    .getTokenID(
                                                                                            VANILLA_TOKEN))),
                                                            sender,
                                                            receiver1,
                                                            amount)
                                                    .payingWith(ACCOUNT)
                                                    .gas(GAS_TO_OFFER)
                                                    .via(transferTokenTxn),
                                            contractCall(
                                                            contract,
                                                            "transferTokensPublic",
                                                            HapiParserUtil.asHeadlongAddress(
                                                                    asAddress(
                                                                            spec.registry()
                                                                                    .getTokenID(
                                                                                            VANILLA_TOKEN))),
                                                            accounts,
                                                            amounts)
                                                    .payingWith(ACCOUNT)
                                                    .gas(GAS_TO_OFFER)
                                                    .via(transferTokensTxn),
                                            contractCall(
                                                            contract,
                                                            "transferNFTPublic",
                                                            HapiParserUtil.asHeadlongAddress(
                                                                    asAddress(
                                                                            spec.registry()
                                                                                    .getTokenID(
                                                                                            KNOWABLE_TOKEN))),
                                                            sender,
                                                            receiver1,
                                                            serial)
                                                    .payingWith(ACCOUNT)
                                                    .gas(GAS_TO_OFFER)
                                                    .via(transferNFTTxn),
                                            contractCall(
                                                            contract,
                                                            "transferNFTsPublic",
                                                            HapiParserUtil.asHeadlongAddress(
                                                                    asAddress(
                                                                            spec.registry()
                                                                                    .getTokenID(
                                                                                            KNOWABLE_TOKEN))),
                                                            new Address[] {sender, sender},
                                                            new Address[] {receiver2, receiver2},
                                                            serials)
                                                    .payingWith(ACCOUNT)
                                                    .gas(GAS_TO_OFFER)
                                                    .via(transferNFTsTxn));
                                }))
                .then(
                        // Confirm the transactions succeeded
                        getTxnRecord(transferTokenTxn).logged(),
                        childRecordsCheck(
                                transferTokenTxn,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .tokenTransfers(
                                                SomeFungibleTransfers.changingFungibleBalances()
                                                        .including(VANILLA_TOKEN, ACCOUNT, -5L)
                                                        .including(VANILLA_TOKEN, RECEIVER, 5L))),
                        childRecordsCheck(
                                transferTokensTxn,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .tokenTransfers(
                                                SomeFungibleTransfers.changingFungibleBalances()
                                                        .including(VANILLA_TOKEN, ACCOUNT, -10L)
                                                        .including(VANILLA_TOKEN, RECEIVER, 5L)
                                                        .including(VANILLA_TOKEN, RECEIVER_2, 5L))),
                        childRecordsCheck(
                                transferNFTTxn,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .withStatus(SUCCESS)))
                                        .tokenTransfers(
                                                NonFungibleTransfers.changingNFTBalances()
                                                        .including(
                                                                KNOWABLE_TOKEN,
                                                                ACCOUNT,
                                                                RECEIVER,
                                                                1L))),
                        childRecordsCheck(
                                transferNFTsTxn,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .withStatus(SUCCESS)))
                                        .tokenTransfers(
                                                NonFungibleTransfers.changingNFTBalances()
                                                        .including(
                                                                KNOWABLE_TOKEN,
                                                                ACCOUNT,
                                                                RECEIVER_2,
                                                                2L)
                                                        .including(
                                                                KNOWABLE_TOKEN,
                                                                ACCOUNT,
                                                                RECEIVER_2,
                                                                3L))),
                        // Confirm the balances are correct
                        getAccountInfo(RECEIVER).hasOwnedNfts(1),
                        getAccountBalance(RECEIVER).hasTokenBalance(VANILLA_TOKEN, 10L),
                        getAccountInfo(RECEIVER_2).hasOwnedNfts(2),
                        getAccountBalance(RECEIVER_2).hasTokenBalance(VANILLA_TOKEN, 5L),
                        getAccountInfo(ACCOUNT).hasOwnedNfts(1),
                        getAccountBalance(ACCOUNT).hasTokenBalance(VANILLA_TOKEN, 485L));
    }

    HapiSpec payerCannotOverSendValue() {
        final var payerBalance = 666 * ONE_HBAR;
        final var overdraftAmount = payerBalance + ONE_HBAR;
        final var overAmbitiousPayer = "overAmbitiousPayer";
        final var uncheckedCC = "uncheckedCC";
        return defaultHapiSpec("PayerCannotOverSendValue")
                .given(
                        uploadInitCode(PAY_RECEIVABLE_CONTRACT),
                        contractCreate(PAY_RECEIVABLE_CONTRACT).adminKey(THRESHOLD))
                .when(
                        cryptoCreate(overAmbitiousPayer).balance(payerBalance),
                        contractCall(
                                        PAY_RECEIVABLE_CONTRACT,
                                        DEPOSIT,
                                        BigInteger.valueOf(overdraftAmount))
                                .payingWith(overAmbitiousPayer)
                                .sending(overdraftAmount)
                                .hasPrecheck(INSUFFICIENT_PAYER_BALANCE),
                        usableTxnIdNamed(uncheckedCC).payerId(overAmbitiousPayer),
                        uncheckedSubmit(
                                        contractCall(
                                                        PAY_RECEIVABLE_CONTRACT,
                                                        DEPOSIT,
                                                        BigInteger.valueOf(overdraftAmount))
                                                .txnId(uncheckedCC)
                                                .payingWith(overAmbitiousPayer)
                                                .sending(overdraftAmount))
                                .payingWith(GENESIS))
                .then(
                        sleepFor(1_000),
                        getReceipt(uncheckedCC)
                                .hasPriorityStatus(INSUFFICIENT_PAYER_BALANCE)
                                .logged());
    }

    private HapiSpec createTokenWithInvalidFeeCollector() {
        return propertyPreservingHapiSpec("createTokenWithInvalidFeeCollector")
                .preserving(CRYPTO_CREATE_WITH_ALIAS_AND_EVM_ADDRESS_ENABLED)
                .given(
                        overriding(CRYPTO_CREATE_WITH_ALIAS_AND_EVM_ADDRESS_ENABLED, FALSE_VALUE),
                        newKeyNamed(ECDSA_KEY).shape(SECP256K1),
                        cryptoCreate(ACCOUNT).balance(ONE_MILLION_HBARS).key(ECDSA_KEY),
                        uploadInitCode(TOKEN_CREATE_CONTRACT),
                        contractCreate(TOKEN_CREATE_CONTRACT).gas(GAS_TO_OFFER),
                        tokenCreate(EXISTING_TOKEN))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                TOKEN_CREATE_CONTRACT,
                                                                CREATE_TOKEN_WITH_ALL_CUSTOM_FEES_AVAILABLE,
                                                                spec.registry()
                                                                        .getKey(ECDSA_KEY)
                                                                        .getECDSASecp256K1()
                                                                        .toByteArray(),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        (byte[])
                                                                                ArrayUtils
                                                                                        .toPrimitive(
                                                                                                Utils
                                                                                                        .asSolidityAddress(
                                                                                                                0,
                                                                                                                0,
                                                                                                                15252L))),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getTokenID(
                                                                                                EXISTING_TOKEN))),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getAccountID(
                                                                                                ACCOUNT))),
                                                                AUTO_RENEW_PERIOD)
                                                        .via(FIRST_CREATE_TXN)
                                                        .gas(GAS_TO_OFFER)
                                                        .sending(DEFAULT_AMOUNT_TO_SEND)
                                                        .payingWith(ACCOUNT)
                                                        .refusingEthConversion()
                                                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED))))
                .then(
                        getTxnRecord(FIRST_CREATE_TXN).andAllChildRecords().logged(),
                        getAccountBalance(ACCOUNT).logged(),
                        getAccountBalance(TOKEN_CREATE_CONTRACT).logged(),
                        getContractInfo(TOKEN_CREATE_CONTRACT).logged(),
                        childRecordsCheck(
                                FIRST_CREATE_TXN,
                                CONTRACT_REVERT_EXECUTED,
                                TransactionRecordAsserts.recordWith()
                                        .status(INVALID_CUSTOM_FEE_COLLECTOR)
                                        .contractCallResult(
                                                ContractFnResultAsserts.resultWith()
                                                        .error(
                                                                INVALID_CUSTOM_FEE_COLLECTOR
                                                                        .name()))));
    }

    private HapiSpec createTokenWithInvalidFixedFeeWithERC721Denomination() {
        final String feeCollector = ACCOUNT_2;
        final String someARAccount = "someARAccount";
        return propertyPreservingHapiSpec("createTokenWithInvalidFixedFeeWithERC721Denomination")
                .preserving(CRYPTO_CREATE_WITH_ALIAS_AND_EVM_ADDRESS_ENABLED)
                .given(
                        overriding(CRYPTO_CREATE_WITH_ALIAS_AND_EVM_ADDRESS_ENABLED, FALSE_VALUE),
                        newKeyNamed(ECDSA_KEY).shape(SECP256K1),
                        cryptoCreate(ACCOUNT).balance(ONE_MILLION_HBARS).key(ECDSA_KEY),
                        cryptoCreate(feeCollector).keyShape(ED25519_ON).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(someARAccount).keyShape(ED25519_ON).balance(ONE_HUNDRED_HBARS),
                        uploadInitCode(TOKEN_CREATE_CONTRACT),
                        contractCreate(TOKEN_CREATE_CONTRACT).gas(GAS_TO_OFFER),
                        tokenCreate(EXISTING_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .supplyKey(ECDSA_KEY)
                                .initialSupply(0L))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                TOKEN_CREATE_CONTRACT,
                                                                CREATE_TOKEN_WITH_ALL_CUSTOM_FEES_AVAILABLE,
                                                                spec.registry()
                                                                        .getKey(ECDSA_KEY)
                                                                        .getECDSASecp256K1()
                                                                        .toByteArray(),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getAccountID(
                                                                                                feeCollector))),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getTokenID(
                                                                                                EXISTING_TOKEN))),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getAccountID(
                                                                                                someARAccount))),
                                                                AUTO_RENEW_PERIOD)
                                                        .via(FIRST_CREATE_TXN)
                                                        .gas(GAS_TO_OFFER)
                                                        .sending(DEFAULT_AMOUNT_TO_SEND)
                                                        .payingWith(ACCOUNT)
                                                        .refusingEthConversion()
                                                        .alsoSigningWithFullPrefix(
                                                                someARAccount, feeCollector)
                                                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED))))
                .then(
                        getTxnRecord(FIRST_CREATE_TXN).andAllChildRecords().logged(),
                        getAccountBalance(ACCOUNT).logged(),
                        getAccountBalance(TOKEN_CREATE_CONTRACT).logged(),
                        getContractInfo(TOKEN_CREATE_CONTRACT).logged(),
                        childRecordsCheck(
                                FIRST_CREATE_TXN,
                                CONTRACT_REVERT_EXECUTED,
                                TransactionRecordAsserts.recordWith()
                                        .status(CUSTOM_FEE_DENOMINATION_MUST_BE_FUNGIBLE_COMMON)
                                        .contractCallResult(
                                                ContractFnResultAsserts.resultWith()
                                                        .error(
                                                                CUSTOM_FEE_DENOMINATION_MUST_BE_FUNGIBLE_COMMON
                                                                        .name()))));
    }

    private HapiSpec createTokenWithInvalidRoyaltyFee() {
        final String feeCollector = ACCOUNT_2;
        AtomicReference<String> existingToken = new AtomicReference<>();
        final String treasuryAndFeeCollectorKey = "treasuryAndFeeCollectorKey";
        return propertyPreservingHapiSpec("createTokenWithInvalidRoyaltyFee")
                .preserving(CRYPTO_CREATE_WITH_ALIAS_AND_EVM_ADDRESS_ENABLED)
                .given(
                        overriding(CRYPTO_CREATE_WITH_ALIAS_AND_EVM_ADDRESS_ENABLED, FALSE_VALUE),
                        newKeyNamed(ECDSA_KEY).shape(SECP256K1),
                        newKeyNamed(ED25519KEY).shape(ED25519),
                        newKeyNamed(CONTRACT_ADMIN_KEY),
                        newKeyNamed(treasuryAndFeeCollectorKey),
                        cryptoCreate(ACCOUNT).balance(ONE_MILLION_HBARS).key(ECDSA_KEY),
                        cryptoCreate(feeCollector)
                                .key(treasuryAndFeeCollectorKey)
                                .balance(ONE_HUNDRED_HBARS),
                        uploadInitCode(TOKEN_CREATE_CONTRACT),
                        contractCreate(TOKEN_CREATE_CONTRACT)
                                .gas(GAS_TO_OFFER)
                                .adminKey(CONTRACT_ADMIN_KEY),
                        tokenCreate(EXISTING_TOKEN).exposingCreatedIdTo(existingToken::set))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                TOKEN_CREATE_CONTRACT,
                                                                "createNonFungibleTokenWithInvalidRoyaltyFee",
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TOKEN_CREATE_CONTRACT))),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getAccountID(
                                                                                                feeCollector))),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getTokenID(
                                                                                                EXISTING_TOKEN))),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getAccountID(
                                                                                                ACCOUNT))),
                                                                AUTO_RENEW_PERIOD,
                                                                spec.registry()
                                                                        .getKey(ED25519KEY)
                                                                        .getEd25519()
                                                                        .toByteArray())
                                                        .via(FIRST_CREATE_TXN)
                                                        .gas(GAS_TO_OFFER)
                                                        .sending(DEFAULT_AMOUNT_TO_SEND)
                                                        .payingWith(ACCOUNT)
                                                        .signedBy(
                                                                ECDSA_KEY,
                                                                treasuryAndFeeCollectorKey)
                                                        .alsoSigningWithFullPrefix(
                                                                ED25519KEY,
                                                                treasuryAndFeeCollectorKey)
                                                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED))))
                .then(
                        getTxnRecord(FIRST_CREATE_TXN).andAllChildRecords().logged(),
                        getAccountBalance(ACCOUNT).logged(),
                        getAccountBalance(TOKEN_CREATE_CONTRACT).logged(),
                        getContractInfo(TOKEN_CREATE_CONTRACT).logged(),
                        childRecordsCheck(
                                FIRST_CREATE_TXN,
                                CONTRACT_REVERT_EXECUTED,
                                TransactionRecordAsserts.recordWith()
                                        .status(CUSTOM_FEE_MUST_BE_POSITIVE)
                                        .contractCallResult(
                                                ContractFnResultAsserts.resultWith()
                                                        .error(
                                                                CUSTOM_FEE_MUST_BE_POSITIVE
                                                                        .name()))));
    }

    private HapiSpec nonFungibleTokenCreateWithFeesHappyPath() {
        final var createTokenNum = new AtomicLong();
        final var feeCollector = ACCOUNT_2;
        final var treasuryAndFeeCollectorKey = "treasuryAndFeeCollectorKey";
        return propertyPreservingHapiSpec("nonFungibleTokenCreateWithFeesHappyPath")
                .preserving(CRYPTO_CREATE_WITH_ALIAS_AND_EVM_ADDRESS_ENABLED)
                .given(
                        overriding(CRYPTO_CREATE_WITH_ALIAS_AND_EVM_ADDRESS_ENABLED, FALSE_VALUE),
                        newKeyNamed(ECDSA_KEY).shape(SECP256K1),
                        newKeyNamed(ED25519KEY).shape(ED25519),
                        newKeyNamed(treasuryAndFeeCollectorKey),
                        cryptoCreate(ACCOUNT).balance(ONE_MILLION_HBARS).key(ECDSA_KEY),
                        cryptoCreate(feeCollector)
                                .key(treasuryAndFeeCollectorKey)
                                .balance(ONE_HUNDRED_HBARS),
                        uploadInitCode(TOKEN_CREATE_CONTRACT),
                        contractCreate(TOKEN_CREATE_CONTRACT).gas(GAS_TO_OFFER),
                        tokenCreate(EXISTING_TOKEN),
                        tokenAssociate(feeCollector, EXISTING_TOKEN))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                TOKEN_CREATE_CONTRACT,
                                                                "createNonFungibleTokenWithCustomFees",
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TOKEN_CREATE_CONTRACT))),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getAccountID(
                                                                                                feeCollector))),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getTokenID(
                                                                                                EXISTING_TOKEN))),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getAccountID(
                                                                                                ACCOUNT))),
                                                                AUTO_RENEW_PERIOD,
                                                                spec.registry()
                                                                        .getKey(ED25519KEY)
                                                                        .getEd25519()
                                                                        .toByteArray())
                                                        .via(FIRST_CREATE_TXN)
                                                        .gas(GAS_TO_OFFER)
                                                        .sending(DEFAULT_AMOUNT_TO_SEND)
                                                        .payingWith(ACCOUNT)
                                                        .signedBy(
                                                                ECDSA_KEY,
                                                                treasuryAndFeeCollectorKey)
                                                        .alsoSigningWithFullPrefix(
                                                                ED25519KEY,
                                                                treasuryAndFeeCollectorKey)
                                                        .exposingResultTo(
                                                                result -> {
                                                                    log.info(
                                                                            EXPLICIT_CREATE_RESULT,
                                                                            result[0]);
                                                                    final var res =
                                                                            (Address) result[0];
                                                                    createTokenNum.set(
                                                                            res.value()
                                                                                    .longValueExact());
                                                                }),
                                                newKeyNamed(TOKEN_CREATE_CONTRACT_AS_KEY)
                                                        .shape(
                                                                CONTRACT.signedWith(
                                                                        TOKEN_CREATE_CONTRACT)))))
                .then(
                        getTxnRecord(FIRST_CREATE_TXN).andAllChildRecords().logged(),
                        getAccountBalance(ACCOUNT).logged(),
                        getAccountBalance(TOKEN_CREATE_CONTRACT).logged(),
                        getContractInfo(TOKEN_CREATE_CONTRACT).logged(),
                        childRecordsCheck(
                                FIRST_CREATE_TXN,
                                ResponseCodeEnum.SUCCESS,
                                TransactionRecordAsserts.recordWith()
                                        .status(ResponseCodeEnum.SUCCESS)),
                        sourcing(
                                () -> {
                                    final var newToken =
                                            asTokenString(
                                                    TokenID.newBuilder()
                                                            .setTokenNum(createTokenNum.get())
                                                            .build());
                                    return getTokenInfo(newToken)
                                            .logged()
                                            .hasTokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                            .hasSymbol(TOKEN_SYMBOL)
                                            .hasName(TOKEN_NAME)
                                            .hasDecimals(0)
                                            .hasTotalSupply(0)
                                            .hasEntityMemo(MEMO)
                                            .hasTreasury(feeCollector)
                                            .hasAutoRenewAccount(ACCOUNT)
                                            .hasAutoRenewPeriod(AUTO_RENEW_PERIOD)
                                            .hasSupplyType(TokenSupplyType.FINITE)
                                            .hasMaxSupply(400)
                                            .searchKeysGlobally()
                                            .hasAdminKey(TOKEN_CREATE_CONTRACT_AS_KEY)
                                            .hasPauseStatus(TokenPauseStatus.PauseNotApplicable)
                                            .hasCustom(
                                                    royaltyFeeWithFallbackInHbarsInSchedule(
                                                            4, 5, 10, feeCollector))
                                            .hasCustom(
                                                    royaltyFeeWithFallbackInTokenInSchedule(
                                                            4, 5, 10, EXISTING_TOKEN, feeCollector))
                                            .hasCustom(
                                                    royaltyFeeWithoutFallbackInSchedule(
                                                            4, 5, feeCollector));
                                }));
    }

    private HapiSpec fungibleTokenCreateWithFeesHappyPath() {
        final var createdTokenNum = new AtomicLong();
        final var feeCollector = "feeCollector";
        final var arEd25519Key = "arEd25519Key";
        final var initialAutoRenewAccount = "initialAutoRenewAccount";
        return propertyPreservingHapiSpec("fungibleTokenCreateWithFeesHappyPath")
                .preserving(CRYPTO_CREATE_WITH_ALIAS_AND_EVM_ADDRESS_ENABLED)
                .given(
                        overriding(CRYPTO_CREATE_WITH_ALIAS_AND_EVM_ADDRESS_ENABLED, FALSE_VALUE),
                        newKeyNamed(arEd25519Key).shape(ED25519),
                        newKeyNamed(ECDSA_KEY).shape(SECP256K1),
                        cryptoCreate(initialAutoRenewAccount).key(arEd25519Key),
                        cryptoCreate(ACCOUNT).balance(ONE_MILLION_HBARS).key(ECDSA_KEY),
                        cryptoCreate(feeCollector).keyShape(ED25519_ON).balance(ONE_HUNDRED_HBARS),
                        uploadInitCode(TOKEN_CREATE_CONTRACT),
                        contractCreate(TOKEN_CREATE_CONTRACT).gas(GAS_TO_OFFER),
                        tokenCreate(EXISTING_TOKEN),
                        tokenAssociate(feeCollector, EXISTING_TOKEN))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                TOKEN_CREATE_CONTRACT,
                                                                CREATE_TOKEN_WITH_ALL_CUSTOM_FEES_AVAILABLE,
                                                                spec.registry()
                                                                        .getKey(ECDSA_KEY)
                                                                        .getECDSASecp256K1()
                                                                        .toByteArray(),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getAccountID(
                                                                                                feeCollector))),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getTokenID(
                                                                                                EXISTING_TOKEN))),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getAccountID(
                                                                                                initialAutoRenewAccount))),
                                                                AUTO_RENEW_PERIOD)
                                                        .via(FIRST_CREATE_TXN)
                                                        .gas(GAS_TO_OFFER)
                                                        .sending(DEFAULT_AMOUNT_TO_SEND)
                                                        .payingWith(ACCOUNT)
                                                        .refusingEthConversion()
                                                        .alsoSigningWithFullPrefix(
                                                                arEd25519Key, feeCollector)
                                                        .exposingResultTo(
                                                                result -> {
                                                                    log.info(
                                                                            EXPLICIT_CREATE_RESULT,
                                                                            result[0]);
                                                                    final var res =
                                                                            (Address) result[0];
                                                                    createdTokenNum.set(
                                                                            res.value()
                                                                                    .longValueExact());
                                                                }),
                                                newKeyNamed(TOKEN_CREATE_CONTRACT_AS_KEY)
                                                        .shape(
                                                                CONTRACT.signedWith(
                                                                        TOKEN_CREATE_CONTRACT)))))
                .then(
                        getTxnRecord(FIRST_CREATE_TXN).andAllChildRecords().logged(),
                        getAccountBalance(ACCOUNT).logged(),
                        getAccountBalance(TOKEN_CREATE_CONTRACT).logged(),
                        getContractInfo(TOKEN_CREATE_CONTRACT).logged(),
                        childRecordsCheck(
                                FIRST_CREATE_TXN,
                                ResponseCodeEnum.SUCCESS,
                                TransactionRecordAsserts.recordWith()
                                        .status(ResponseCodeEnum.SUCCESS)),
                        sourcing(
                                () -> {
                                    final var newToken =
                                            asTokenString(
                                                    TokenID.newBuilder()
                                                            .setTokenNum(createdTokenNum.get())
                                                            .build());
                                    return getTokenInfo(newToken)
                                            .logged()
                                            .hasTokenType(TokenType.FUNGIBLE_COMMON)
                                            .hasSymbol(TOKEN_SYMBOL)
                                            .hasName(TOKEN_NAME)
                                            .hasDecimals(8)
                                            .hasTotalSupply(200)
                                            .hasEntityMemo(MEMO)
                                            .hasTreasury(TOKEN_CREATE_CONTRACT)
                                            .hasAutoRenewAccount(initialAutoRenewAccount)
                                            .hasAutoRenewPeriod(AUTO_RENEW_PERIOD)
                                            .hasSupplyType(TokenSupplyType.INFINITE)
                                            .searchKeysGlobally()
                                            .hasAdminKey(ECDSA_KEY)
                                            .hasPauseStatus(TokenPauseStatus.PauseNotApplicable)
                                            .hasCustom(
                                                    fixedHtsFeeInSchedule(
                                                            1, EXISTING_TOKEN, feeCollector))
                                            .hasCustom(fixedHbarFeeInSchedule(2, feeCollector))
                                            .hasCustom(
                                                    fixedHtsFeeInSchedule(
                                                            4, newToken, feeCollector))
                                            .hasCustom(
                                                    fractionalFeeInSchedule(
                                                            4,
                                                            5,
                                                            10,
                                                            OptionalLong.of(30),
                                                            true,
                                                            feeCollector));
                                }));
    }

    HapiSpec accountWithoutAliasCanMakeEthTxnsDueToAutomaticAliasCreation() {
        final String ACCOUNT = "account";
        return defaultHapiSpec(
                        "ETX_026_accountWithoutAliasCanMakeEthTxnsDueToAutomaticAliasCreation")
                .given(
                        overriding(CRYPTO_CREATE_WITH_ALIAS_AND_EVM_ADDRESS_ENABLED, FALSE_VALUE),
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(ACCOUNT).key(SECP_256K1_SOURCE_KEY).balance(ONE_HUNDRED_HBARS))
                .when(
                        ethereumContractCreate(PAY_RECEIVABLE_CONTRACT)
                                .type(EthTxData.EthTransactionType.EIP1559)
                                .signingWith(SECP_256K1_SOURCE_KEY)
                                .payingWith(ACCOUNT)
                                .maxGasAllowance(FIVE_HBARS)
                                .nonce(0)
                                .gasLimit(GAS_LIMIT)
                                .hasKnownStatus(INVALID_ACCOUNT_ID))
                .then(overriding(CRYPTO_CREATE_WITH_ALIAS_AND_EVM_ADDRESS_ENABLED, TRUE));
    }

    private HapiSpec transferToCaller() {
        final var transferTxn = TRANSFER_TXN;
        return defaultHapiSpec(TRANSFER_TO_CALLER)
                .given(
                        uploadInitCode(TRANSFERRING_CONTRACT),
                        contractCreate(TRANSFERRING_CONTRACT).balance(10_000L),
                        getAccountInfo(DEFAULT_CONTRACT_SENDER)
                                .savingSnapshot(ACCOUNT_INFO)
                                .payingWith(GENESIS))
                .when(
                        withOpContext(
                                (spec, log) -> {
                                    var transferCall =
                                            contractCall(
                                                            TRANSFERRING_CONTRACT,
                                                            TRANSFER_TO_CALLER,
                                                            BigInteger.valueOf(10))
                                                    .payingWith(DEFAULT_CONTRACT_SENDER)
                                                    .via(transferTxn)
                                                    .logged();

                                    var saveTxnRecord =
                                            getTxnRecord(transferTxn)
                                                    .saveTxnRecordToRegistry("txn")
                                                    .payingWith(GENESIS);
                                    var saveAccountInfoAfterCall =
                                            getAccountInfo(DEFAULT_CONTRACT_SENDER)
                                                    .savingSnapshot(ACCOUNT_INFO_AFTER_CALL)
                                                    .payingWith(GENESIS);
                                    var saveContractInfo =
                                            getContractInfo(TRANSFERRING_CONTRACT)
                                                    .saveToRegistry(CONTRACT_FROM);

                                    allRunFor(
                                            spec,
                                            transferCall,
                                            saveTxnRecord,
                                            saveAccountInfoAfterCall,
                                            saveContractInfo);
                                }))
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
                                    assertEquals(
                                            accountBalanceAfterCall,
                                            accountBalanceBeforeCall - fee + 10L);
                                }),
                        sourcing(
                                () ->
                                        getContractInfo(TRANSFERRING_CONTRACT)
                                                .has(contractWith().balance(10_000L - 10L))));
    }

    private HapiSpec maxRefundIsMaxGasRefundConfiguredWhenTXGasPriceIsSmaller() {
        return defaultHapiSpec("MaxRefundIsMaxGasRefundConfiguredWhenTXGasPriceIsSmaller")
                .given(
                        overriding(CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT, "5"),
                        uploadInitCode(SIMPLE_UPDATE_CONTRACT))
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
                                    assertEquals(285000, gasUsed);
                                }),
                        resetToDefault(CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT));
    }

    @SuppressWarnings("java:S5960")
    private HapiSpec contractCreationStoragePriceMatchesFinalExpiry() {
        final var toyMaker = "ToyMaker";
        final var createIndirectly = "CreateIndirectly";
        final var normalPayer = "normalPayer";
        final var longLivedPayer = "longLivedPayer";
        final var longLifetime = 100 * 7776000L;
        final AtomicLong normalPayerGasUsed = new AtomicLong();
        final AtomicLong longLivedPayerGasUsed = new AtomicLong();
        final AtomicReference<String> toyMakerMirror = new AtomicReference<>();

        return defaultHapiSpec("ContractCreationStoragePriceMatchesFinalExpiry")
                .given(
                        overriding(LEDGER_AUTO_RENEW_PERIOD_MAX_DURATION, "" + longLifetime),
                        cryptoCreate(normalPayer),
                        cryptoCreate(longLivedPayer).autoRenewSecs(longLifetime),
                        uploadInitCode(toyMaker, createIndirectly),
                        contractCreate(toyMaker)
                                .exposingNumTo(
                                        num ->
                                                toyMakerMirror.set(
                                                        asHexedSolidityAddress(0, 0, num))),
                        sourcing(
                                () ->
                                        contractCreate(createIndirectly)
                                                .autoRenewSecs(longLifetime)
                                                .payingWith(GENESIS)))
                .when(
                        contractCall(toyMaker, "make")
                                .payingWith(normalPayer)
                                .exposingGasTo(
                                        (status, gasUsed) -> normalPayerGasUsed.set(gasUsed)),
                        contractCall(toyMaker, "make")
                                .payingWith(longLivedPayer)
                                .exposingGasTo(
                                        (status, gasUsed) -> longLivedPayerGasUsed.set(gasUsed)),
                        assertionsHold(
                                (spec, opLog) ->
                                        assertEquals(
                                                normalPayerGasUsed.get(),
                                                longLivedPayerGasUsed.get(),
                                                "Payer expiry should not affect create storage"
                                                        + " cost")),
                        // Verify that we are still charged a "typical" amount despite the payer and
                        // the original sender contract having extremely long expiry dates
                        sourcing(
                                () ->
                                        contractCall(
                                                        createIndirectly,
                                                        "makeOpaquely",
                                                        asHeadlongAddress(toyMakerMirror.get()))
                                                .payingWith(longLivedPayer)))
                .then(
                        overriding(
                                LEDGER_AUTO_RENEW_PERIOD_MAX_DURATION,
                                "" + DEFAULT_MAX_AUTO_RENEW_PERIOD));
    }

    private HapiSpec gasLimitOverMaxGasLimitFailsPrecheck() {
        return defaultHapiSpec("GasLimitOverMaxGasLimitFailsPrecheck")
                .given(
                        uploadInitCode(SIMPLE_UPDATE_CONTRACT),
                        contractCreate(SIMPLE_UPDATE_CONTRACT).gas(300_000L),
                        overriding(CONTRACTS_MAX_GAS_PER_SEC, "100"))
                .when()
                .then(
                        contractCall(
                                        SIMPLE_UPDATE_CONTRACT,
                                        "set",
                                        BigInteger.valueOf(5),
                                        BigInteger.valueOf(42))
                                .gas(101L)
                                .hasPrecheck(MAX_GAS_LIMIT_EXCEEDED),
                        resetToDefault(CONTRACTS_MAX_GAS_PER_SEC));
    }

    private HapiSpec createGasLimitOverMaxGasLimitFailsPrecheck() {
        return defaultHapiSpec("CreateGasLimitOverMaxGasLimitFailsPrecheck")
                .given(
                        overriding("contracts.maxGasPerSec", "100"),
                        uploadInitCode(EMPTY_CONSTRUCTOR_CONTRACT))
                .when()
                .then(
                        contractCreate(EMPTY_CONSTRUCTOR_CONTRACT)
                                .gas(101L)
                                .hasPrecheck(MAX_GAS_LIMIT_EXCEEDED),
                        UtilVerbs.resetToDefault("contracts.maxGasPerSec"));
    }

    private HapiSpec transferZeroHbarsToCaller() {
        final var transferTxn = TRANSFER_TXN;
        return defaultHapiSpec("transferZeroHbarsToCaller")
                .given(
                        uploadInitCode(TRANSFERRING_CONTRACT),
                        contractCreate(TRANSFERRING_CONTRACT).balance(10_000L),
                        getAccountInfo(DEFAULT_CONTRACT_SENDER)
                                .savingSnapshot(ACCOUNT_INFO)
                                .payingWith(GENESIS))
                .when(
                        withOpContext(
                                (spec, log) -> {
                                    var transferCall =
                                            contractCall(
                                                            TRANSFERRING_CONTRACT,
                                                            TRANSFER_TO_CALLER,
                                                            BigInteger.ZERO)
                                                    .payingWith(DEFAULT_CONTRACT_SENDER)
                                                    .via(transferTxn)
                                                    .logged();

                                    var saveTxnRecord =
                                            getTxnRecord(transferTxn)
                                                    .saveTxnRecordToRegistry("txn_registry")
                                                    .payingWith(GENESIS);
                                    var saveAccountInfoAfterCall =
                                            getAccountInfo(DEFAULT_CONTRACT_SENDER)
                                                    .savingSnapshot(ACCOUNT_INFO_AFTER_CALL)
                                                    .payingWith(GENESIS);
                                    var saveContractInfo =
                                            getContractInfo(TRANSFERRING_CONTRACT)
                                                    .saveToRegistry(CONTRACT_FROM);

                                    allRunFor(
                                            spec,
                                            transferCall,
                                            saveTxnRecord,
                                            saveAccountInfoAfterCall,
                                            saveContractInfo);
                                }))
                .then(
                        assertionsHold(
                                (spec, opLog) -> {
                                    final var fee =
                                            spec.registry()
                                                    .getTransactionRecord("txn_registry")
                                                    .getTransactionFee();
                                    final var accountBalanceBeforeCall =
                                            spec.registry()
                                                    .getAccountInfo(ACCOUNT_INFO)
                                                    .getBalance();
                                    final var accountBalanceAfterCall =
                                            spec.registry()
                                                    .getAccountInfo(ACCOUNT_INFO_AFTER_CALL)
                                                    .getBalance();
                                    final var contractBalanceAfterCall =
                                            spec.registry()
                                                    .getContractInfo(CONTRACT_FROM)
                                                    .getBalance();

                                    assertEquals(
                                            accountBalanceAfterCall,
                                            accountBalanceBeforeCall - fee);
                                    assertEquals(contractBalanceAfterCall, 10_000L);
                                }));
    }

    private HapiSpec resultSizeAffectsFees() {
        final var contract = "VerboseDeposit";
        final var TRANSFER_AMOUNT = 1_000L;
        BiConsumer<TransactionRecord, Logger> resultSizeFormatter =
                (rcd, txnLog) -> {
                    final var result = rcd.getContractCallResult();
                    txnLog.info(
                            "Contract call result FeeBuilder size = {}, fee = {}, result is"
                                    + " [self-reported size = {}, '{}']",
                            () -> FeeBuilder.getContractFunctionSize(result),
                            rcd::getTransactionFee,
                            result.getContractCallResult()::size,
                            result::getContractCallResult);
                    txnLog.info("  Literally :: {}", result);
                };

        return defaultHapiSpec("ResultSizeAffectsFees")
                .given(
                        overriding(CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT, "100"),
                        uploadInitCode(contract),
                        contractCreate(contract))
                .when(
                        contractCall(
                                        contract,
                                        DEPOSIT,
                                        TRANSFER_AMOUNT,
                                        0L,
                                        "So we out-danced thought...")
                                .via("noLogsCallTxn")
                                .sending(TRANSFER_AMOUNT),
                        contractCall(
                                        contract,
                                        DEPOSIT,
                                        TRANSFER_AMOUNT,
                                        5L,
                                        "So we out-danced thought...")
                                .via("loggedCallTxn")
                                .sending(TRANSFER_AMOUNT))
                .then(
                        assertionsHold(
                                (spec, assertLog) -> {
                                    HapiGetTxnRecord noLogsLookup =
                                            QueryVerbs.getTxnRecord("noLogsCallTxn")
                                                    .loggedWith(resultSizeFormatter);
                                    HapiGetTxnRecord logsLookup =
                                            QueryVerbs.getTxnRecord("loggedCallTxn")
                                                    .loggedWith(resultSizeFormatter);
                                    allRunFor(spec, noLogsLookup, logsLookup);
                                    final var unloggedRecord =
                                            noLogsLookup
                                                    .getResponse()
                                                    .getTransactionGetRecord()
                                                    .getTransactionRecord();
                                    final var loggedRecord =
                                            logsLookup
                                                    .getResponse()
                                                    .getTransactionGetRecord()
                                                    .getTransactionRecord();
                                    assertLog.info(
                                            "Fee for logged record   = {}",
                                            loggedRecord::getTransactionFee);
                                    assertLog.info(
                                            "Fee for unlogged record = {}",
                                            unloggedRecord::getTransactionFee);
                                    Assertions.assertNotEquals(
                                            unloggedRecord.getTransactionFee(),
                                            loggedRecord.getTransactionFee(),
                                            "Result size should change the txn fee!");
                                }),
                        resetToDefault(CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT));
    }

    private HapiSpec autoAssociationSlotsAppearsInInfo() {
        final int maxAutoAssociations = 100;
        final int ADVENTUROUS_NETWORK = 1_000;
        final String CONTRACT = "Multipurpose";
        final String associationsLimitProperty = "entities.limitTokenAssociations";
        final String defaultAssociationsLimit =
                HapiSpecSetup.getDefaultNodeProps().get(associationsLimitProperty);

        return defaultHapiSpec("autoAssociationSlotsAppearsInInfo")
                .given(
                        overridingThree(
                                "entities.limitTokenAssociations",
                                TRUE,
                                "tokens.maxPerAccount",
                                "" + 1,
                                CONTRACT_ALLOW_ASSOCIATIONS_PROPERTY,
                                TRUE))
                .when()
                .then(
                        newKeyNamed(ADMIN_KEY),
                        uploadInitCode(CONTRACT),
                        contractCreate(CONTRACT)
                                .adminKey(ADMIN_KEY)
                                .maxAutomaticTokenAssociations(maxAutoAssociations)
                                .hasPrecheck(
                                        REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT),

                        // Default is NOT to limit associations for entities
                        overriding(associationsLimitProperty, defaultAssociationsLimit),
                        contractCreate(CONTRACT)
                                .adminKey(ADMIN_KEY)
                                .maxAutomaticTokenAssociations(maxAutoAssociations),
                        getContractInfo(CONTRACT)
                                .has(
                                        ContractInfoAsserts.contractWith()
                                                .maxAutoAssociations(maxAutoAssociations))
                                .logged(),
                        // Restore default
                        overriding("tokens.maxPerAccount", "" + ADVENTUROUS_NETWORK));
    }

    private HapiSpec createMaxRefundIsMaxGasRefundConfiguredWhenTXGasPriceIsSmaller() {
        return defaultHapiSpec("CreateMaxRefundIsMaxGasRefundConfiguredWhenTXGasPriceIsSmaller")
                .given(
                        overriding(CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT1, "5"),
                        uploadInitCode(EMPTY_CONSTRUCTOR_CONTRACT))
                .when(contractCreate(EMPTY_CONSTRUCTOR_CONTRACT).gas(300_000L).via(CREATE_TX))
                .then(
                        withOpContext(
                                (spec, ignore) -> {
                                    final var subop01 =
                                            getTxnRecord(CREATE_TX)
                                                    .saveTxnRecordToRegistry(CREATE_TX_REC);
                                    allRunFor(spec, subop01);

                                    final var gasUsed =
                                            spec.registry()
                                                    .getTransactionRecord(CREATE_TX_REC)
                                                    .getContractCreateResult()
                                                    .getGasUsed();
                                    assertEquals(285_000L, gasUsed);
                                }),
                        resetToDefault(CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT1));
    }

    private HapiSpec createMinChargeIsTXGasUsedByContractCreate() {
        return defaultHapiSpec("CreateMinChargeIsTXGasUsedByContractCreate")
                .given(
                        overriding(CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT1, "100"),
                        uploadInitCode(EMPTY_CONSTRUCTOR_CONTRACT))
                .when(contractCreate(EMPTY_CONSTRUCTOR_CONTRACT).gas(300_000L).via(CREATE_TX))
                .then(
                        withOpContext(
                                (spec, ignore) -> {
                                    final var subop01 =
                                            getTxnRecord(CREATE_TX)
                                                    .saveTxnRecordToRegistry(CREATE_TX_REC);
                                    allRunFor(spec, subop01);

                                    final var gasUsed =
                                            spec.registry()
                                                    .getTransactionRecord(CREATE_TX_REC)
                                                    .getContractCreateResult()
                                                    .getGasUsed();
                                    assertTrue(gasUsed > 0L);
                                }),
                        resetToDefault(CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT1));
    }

    HapiSpec propagatesNestedCreations() {
        final var call = "callTxn";
        final var creation = "createTxn";
        final var contract = "NestedCreations";

        final var adminKey = "adminKey";
        final var entityMemo = "JUST DO IT";
        final var customAutoRenew = 7776001L;
        final AtomicReference<String> firstLiteralId = new AtomicReference<>();
        final AtomicReference<String> secondLiteralId = new AtomicReference<>();
        final AtomicReference<ByteString> expectedFirstAddress = new AtomicReference<>();
        final AtomicReference<ByteString> expectedSecondAddress = new AtomicReference<>();

        return defaultHapiSpec("PropagatesNestedCreations")
                .given(
                        newKeyNamed(adminKey),
                        uploadInitCode(contract),
                        contractCreate(contract)
                                .stakedNodeId(0)
                                .adminKey(adminKey)
                                .entityMemo(entityMemo)
                                .autoRenewSecs(customAutoRenew)
                                .via(creation))
                .when(contractCall(contract, "propagate").gas(4_000_000L).via(call))
                .then(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var parentNum = spec.registry().getContractId(contract);
                                    final var firstId =
                                            ContractID.newBuilder()
                                                    .setContractNum(parentNum.getContractNum() + 1L)
                                                    .build();
                                    firstLiteralId.set(
                                            HapiPropertySource.asContractString(firstId));
                                    expectedFirstAddress.set(
                                            ByteString.copyFrom(asSolidityAddress(firstId)));
                                    final var secondId =
                                            ContractID.newBuilder()
                                                    .setContractNum(parentNum.getContractNum() + 2L)
                                                    .build();
                                    secondLiteralId.set(
                                            HapiPropertySource.asContractString(secondId));
                                    expectedSecondAddress.set(
                                            ByteString.copyFrom(asSolidityAddress(secondId)));
                                }),
                        sourcing(
                                () ->
                                        childRecordsCheck(
                                                call,
                                                ResponseCodeEnum.SUCCESS,
                                                recordWith()
                                                        .contractCreateResult(
                                                                resultWith()
                                                                        .evmAddress(
                                                                                expectedFirstAddress
                                                                                        .get()))
                                                        .status(ResponseCodeEnum.SUCCESS),
                                                recordWith()
                                                        .contractCreateResult(
                                                                resultWith()
                                                                        .evmAddress(
                                                                                expectedSecondAddress
                                                                                        .get()))
                                                        .status(ResponseCodeEnum.SUCCESS))),
                        sourcing(
                                () ->
                                        getContractInfo(firstLiteralId.get())
                                                .has(
                                                        contractWith()
                                                                .propertiesInheritedFrom(
                                                                        contract))));
    }

    HapiSpec temporarySStoreRefundTest() {
        final var contract = "TemporarySStoreRefund";
        return defaultHapiSpec("TemporarySStoreRefundTest")
                .given(
                        overriding(CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT1, "100"),
                        uploadInitCode(contract),
                        contractCreate(contract))
                .when(
                        contractCall(contract, "holdTemporary", BigInteger.valueOf(10))
                                .via("tempHoldTx"),
                        contractCall(contract, "holdPermanently", BigInteger.valueOf(10))
                                .via("permHoldTx"))
                .then(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var subop01 =
                                            getTxnRecord("tempHoldTx")
                                                    .saveTxnRecordToRegistry("tempHoldTxRec")
                                                    .logged();
                                    final var subop02 =
                                            getTxnRecord("permHoldTx")
                                                    .saveTxnRecordToRegistry("permHoldTxRec")
                                                    .logged();

                                    CustomSpecAssert.allRunFor(spec, subop01, subop02);

                                    final var gasUsedForTemporaryHoldTx =
                                            spec.registry()
                                                    .getTransactionRecord("tempHoldTxRec")
                                                    .getContractCallResult()
                                                    .getGasUsed();
                                    final var gasUsedForPermanentHoldTx =
                                            spec.registry()
                                                    .getTransactionRecord("permHoldTxRec")
                                                    .getContractCallResult()
                                                    .getGasUsed();

                                    Assertions.assertTrue(gasUsedForTemporaryHoldTx < 23535L);
                                    Assertions.assertTrue(gasUsedForPermanentHoldTx > 20000L);
                                }),
                        UtilVerbs.resetToDefault(CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT1));
    }

    private HapiSpec deletedContractsCannotBeUpdated() {
        final var contract = "SelfDestructCallable";

        return defaultHapiSpec("DeletedContractsCannotBeUpdated")
                .given(uploadInitCode(contract), contractCreate(contract).gas(300_000))
                .when(contractCall(contract, "destroy").deferStatusResolution())
                .then(
                        contractUpdate(contract)
                                .newMemo("Hi there!")
                                .hasKnownStatus(INVALID_CONTRACT_ID));
    }

    private HapiSpec canCallPendingContractSafely() {
        final var numSlots = 64L;
        final var createBurstSize = 500;
        final long[] targets = {19, 24};
        final AtomicLong createdFileNum = new AtomicLong();
        final var callTxn = "callTxn";
        final var contract = "FibonacciPlus";
        final var expiry = Instant.now().getEpochSecond() + 7776000;

        return defaultHapiSpec("CanCallPendingContractSafely")
                .given(
                        uploadSingleInitCode(contract, expiry, GENESIS, createdFileNum::set),
                        inParallel(
                                IntStream.range(0, createBurstSize)
                                        .mapToObj(
                                                i ->
                                                        contractCustomCreate(
                                                                        contract,
                                                                        String.valueOf(i),
                                                                        numSlots)
                                                                .fee(ONE_HUNDRED_HBARS)
                                                                .gas(300_000L)
                                                                .payingWith(GENESIS)
                                                                .noLogging()
                                                                .deferStatusResolution()
                                                                .bytecode(contract)
                                                                .adminKey(THRESHOLD))
                                        .toArray(HapiSpecOperation[]::new)))
                .when()
                .then(
                        sourcing(
                                () ->
                                        contractCallWithFunctionAbi(
                                                        "0.0."
                                                                + (createdFileNum.get()
                                                                        + createBurstSize),
                                                        getABIFor(FUNCTION, "addNthFib", contract),
                                                        targets,
                                                        12L)
                                                .payingWith(GENESIS)
                                                .gas(300_000L)
                                                .via(callTxn)));
    }

    private HapiSpec lazyCreateThroughPrecompileNotSupportedWhenFlagDisabled() {
        final var CONTRACT = CRYPTO_TRANSFER;
        final var SENDER = "sender";
        final var FUNGIBLE_TOKEN = "fungibleToken";
        final var DELEGATE_KEY = "contractKey";
        final var NOT_SUPPORTED_TXN = "notSupportedTxn";
        final var TOTAL_SUPPLY = 1_000;
        final var ALLOW_AUTO_ASSOCIATIONS_PROPERTY = CONTRACT_ALLOW_ASSOCIATIONS_PROPERTY;

        return propertyPreservingHapiSpec("lazyCreateThroughPrecompileNotSupportedWhenFlagDisabled")
                .preserving(ALLOW_AUTO_ASSOCIATIONS_PROPERTY, LAZY_CREATION_ENABLED)
                .given(
                        overriding(ALLOW_AUTO_ASSOCIATIONS_PROPERTY, TRUE),
                        UtilVerbs.overriding(LAZY_CREATION_ENABLED, FALSE),
                        cryptoCreate(SENDER).balance(10 * ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(TOTAL_SUPPLY)
                                .treasury(TOKEN_TREASURY),
                        tokenAssociate(SENDER, List.of(FUNGIBLE_TOKEN)),
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoTransfer(moving(200, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, SENDER)),
                        uploadInitCode(CONTRACT),
                        contractCreate(CONTRACT).maxAutomaticTokenAssociations(1),
                        getContractInfo(CONTRACT)
                                .has(ContractInfoAsserts.contractWith().maxAutoAssociations(1))
                                .logged())
                .when(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var ecdsaKey =
                                            spec.registry().getKey(SECP_256K1_SOURCE_KEY);
                                    final var tmp = ecdsaKey.getECDSASecp256K1().toByteArray();
                                    final var addressBytes = recoverAddressFromPubKey(tmp);
                                    final var token = spec.registry().getTokenID(FUNGIBLE_TOKEN);
                                    final var sender = spec.registry().getAccountID(SENDER);
                                    final var amountToBeSent = 50L;

                                    allRunFor(
                                            spec,
                                            newKeyNamed(DELEGATE_KEY)
                                                    .shape(
                                                            DELEGATE_CONTRACT_KEY_SHAPE.signedWith(
                                                                    sigs(ON, CONTRACT))),
                                            cryptoUpdate(SENDER).key(DELEGATE_KEY),
                                            contractCall(
                                                            CONTRACT,
                                                            "transferMultipleTokens",
                                                            tokenTransferLists()
                                                                    .withTokenTransferList(
                                                                            tokenTransferList()
                                                                                    .forToken(token)
                                                                                    .withAccountAmounts(
                                                                                            accountAmount(
                                                                                                    sender,
                                                                                                    -amountToBeSent),
                                                                                            accountAmountAlias(
                                                                                                    addressBytes,
                                                                                                    amountToBeSent))
                                                                                    .build())
                                                                    .build())
                                                    .payingWith(GENESIS)
                                                    .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                                    .via(NOT_SUPPORTED_TXN)
                                                    .gas(GAS_TO_OFFER),
                                            getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                                    .hasCostAnswerPrecheck(INVALID_ACCOUNT_ID),
                                            childRecordsCheck(
                                                    NOT_SUPPORTED_TXN,
                                                    CONTRACT_REVERT_EXECUTED,
                                                    recordWith().status(NOT_SUPPORTED)));
                                }))
                .then();
    }

    private HapiSpec evmLazyCreateViaSolidityCall() {
        final var LAZY_CREATE_CONTRACT = "NestedLazyCreateContract";
        final var ECDSA_KEY = "ECDSAKey";
        final var callLazyCreateFunction = "nestedLazyCreateThenSendMore";
        final var revertingCallLazyCreateFunction = "nestedLazyCreateThenRevert";
        final var lazyCreationProperty = "lazyCreation.enabled";
        final var contractsEvmVersionProperty = "contracts.evm.version";
        final var contractsEvmVersionDynamicProperty = "contracts.evm.version.dynamic";
        final var REVERTING_TXN = "revertingTxn";
        final var depositAmount = 1000;
        final var payTxn = "payTxn";

        return propertyPreservingHapiSpec("evmLazyCreateViaSolidityCall")
                .preserving(
                        lazyCreationProperty,
                        contractsEvmVersionProperty,
                        contractsEvmVersionDynamicProperty)
                .given(
                        overridingThree(
                                lazyCreationProperty,
                                TRUE,
                                contractsEvmVersionProperty,
                                "v0.34",
                                contractsEvmVersionDynamicProperty,
                                TRUE),
                        newKeyNamed(ECDSA_KEY).shape(SECP_256K1_SHAPE),
                        uploadInitCode(LAZY_CREATE_CONTRACT),
                        contractCreate(LAZY_CREATE_CONTRACT).via(CALL_TX_REC),
                        getTxnRecord(CALL_TX_REC).andAllChildRecords().logged())
                .when(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var ecdsaKey = spec.registry().getKey(ECDSA_KEY);
                                    final var tmp = ecdsaKey.getECDSASecp256K1().toByteArray();
                                    final var addressBytes = recoverAddressFromPubKey(tmp);
                                    final var mirrorTxn = "mirrorTxn";
                                    allRunFor(
                                            spec,
                                            contractCall(
                                                            LAZY_CREATE_CONTRACT,
                                                            callLazyCreateFunction,
                                                            mirrorAddrWith(1_234_567_890L))
                                                    .sending(depositAmount)
                                                    .via(mirrorTxn)
                                                    .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                                    .gas(6_000_000),
                                            emptyChildRecordsCheck(
                                                    mirrorTxn, CONTRACT_REVERT_EXECUTED),
                                            contractCall(
                                                            LAZY_CREATE_CONTRACT,
                                                            revertingCallLazyCreateFunction,
                                                            asHeadlongAddress(addressBytes))
                                                    .sending(depositAmount)
                                                    .via(REVERTING_TXN)
                                                    .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                                    .gas(6_000_000),
                                            emptyChildRecordsCheck(
                                                    REVERTING_TXN, CONTRACT_REVERT_EXECUTED),
                                            contractCall(
                                                            LAZY_CREATE_CONTRACT,
                                                            callLazyCreateFunction,
                                                            asHeadlongAddress(addressBytes))
                                                    .via(payTxn)
                                                    .sending(depositAmount)
                                                    .gas(6_000_000));
                                }))
                .then(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var getTxnRecord =
                                            getTxnRecord(payTxn).andAllChildRecords().logged();
                                    allRunFor(spec, getTxnRecord);
                                    final var lazyAccountId =
                                            getTxnRecord
                                                    .getChildRecord(0)
                                                    .getReceipt()
                                                    .getAccountID();
                                    final var name = "lazy";
                                    spec.registry().saveAccountId(name, lazyAccountId);
                                    allRunFor(
                                            spec,
                                            getAccountBalance(name).hasTinyBars(depositAmount));
                                }));
    }

    private HapiSpec requiresTopLevelSignatureOrApprovalDependingOnControllingProperty() {
        final var ignoredTopLevelSigTransfer = "ignoredTopLevelSigTransfer";
        final var ignoredApprovalTransfer = "ignoredApprovalTransfer";
        final var approvedTransfer = "approvedTransfer";
        final AtomicReference<Address> senderAddress = new AtomicReference<>();
        final AtomicReference<Address> receiverAddress = new AtomicReference<>();
        final AtomicReference<Address> tokenAddress = new AtomicReference<>();
        final var amountPerTransfer = 50L;
        return propertyPreservingHapiSpec(
                        "RequiresTopLevelSignatureOrApprovalDependingOnControllingProperty")
                .preserving(CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS)
                .given(
                        cryptoCreate(SENDER)
                                .keyShape(SECP256K1_ON)
                                .exposingEvmAddressTo(senderAddress::set)
                                .maxAutomaticTokenAssociations(1),
                        cryptoCreate(RECEIVER)
                                .keyShape(SECP256K1_ON)
                                .exposingEvmAddressTo(receiverAddress::set)
                                .maxAutomaticTokenAssociations(1),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(TOTAL_SUPPLY)
                                .treasury(TOKEN_TREASURY)
                                .exposingAddressTo(tokenAddress::set),
                        cryptoTransfer(
                                moving(4 * amountPerTransfer, FUNGIBLE_TOKEN)
                                        .between(TOKEN_TREASURY, SENDER)),
                        uploadInitCode(TRANSFER_CONTRACT),
                        contractCreate(TRANSFER_CONTRACT),
                        // First revoke use of top-level signatures from all precompiles
                        overriding(CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS, ""))
                .when(
                        // Then, try to transfer tokens using a top-level signature
                        sourcing(
                                () ->
                                        contractCall(
                                                        TRANSFER_CONTRACT,
                                                        TRANSFER_MULTIPLE_TOKENS,
                                                        (Object)
                                                                new Tuple[] {
                                                                    tokenTransferList()
                                                                            .forTokenAddress(
                                                                                    tokenAddress
                                                                                            .get())
                                                                            .withAccountAmounts(
                                                                                    addressedAccountAmount(
                                                                                            senderAddress
                                                                                                    .get(),
                                                                                            -amountPerTransfer),
                                                                                    addressedAccountAmount(
                                                                                            receiverAddress
                                                                                                    .get(),
                                                                                            +amountPerTransfer))
                                                                            .build()
                                                                })
                                                .alsoSigningWithFullPrefix(SENDER)
                                                .via(ignoredTopLevelSigTransfer)
                                                .gas(GAS_TO_OFFER)
                                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        // Switch to allow use of top-level signatures from CryptoTransfer
                        overriding(CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS, CRYPTO_TRANSFER),
                        // Validate now the top-level signature works
                        sourcing(
                                () ->
                                        contractCall(
                                                        TRANSFER_CONTRACT,
                                                        TRANSFER_MULTIPLE_TOKENS,
                                                        (Object)
                                                                new Tuple[] {
                                                                    tokenTransferList()
                                                                            .forTokenAddress(
                                                                                    tokenAddress
                                                                                            .get())
                                                                            .withAccountAmounts(
                                                                                    addressedAccountAmount(
                                                                                            senderAddress
                                                                                                    .get(),
                                                                                            -amountPerTransfer),
                                                                                    addressedAccountAmount(
                                                                                            receiverAddress
                                                                                                    .get(),
                                                                                            +amountPerTransfer))
                                                                            .build()
                                                                })
                                                .alsoSigningWithFullPrefix(SENDER)
                                                .gas(GAS_TO_OFFER)),
                        // And validate that ONLY top-level signatures work here (i.e. approvals are
                        // not used
                        // automatically) by trying to transfer tokens using an approval without
                        // top-level signature
                        cryptoApproveAllowance()
                                .payingWith(SENDER)
                                .addTokenAllowance(
                                        SENDER,
                                        FUNGIBLE_TOKEN,
                                        TRANSFER_CONTRACT,
                                        4 * amountPerTransfer),
                        sourcing(
                                () ->
                                        contractCall(
                                                        TRANSFER_CONTRACT,
                                                        TRANSFER_MULTIPLE_TOKENS,
                                                        (Object)
                                                                new Tuple[] {
                                                                    tokenTransferList()
                                                                            .forTokenAddress(
                                                                                    tokenAddress
                                                                                            .get())
                                                                            .withAccountAmounts(
                                                                                    addressedAccountAmount(
                                                                                            senderAddress
                                                                                                    .get(),
                                                                                            -amountPerTransfer),
                                                                                    addressedAccountAmount(
                                                                                            receiverAddress
                                                                                                    .get(),
                                                                                            +amountPerTransfer))
                                                                            .build()
                                                                })
                                                .gas(GAS_TO_OFFER)
                                                .via(ignoredApprovalTransfer)
                                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        // Then revoke use of top-level signatures once more, so the approval will
                        // be used automatically
                        overriding(CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS, ""))
                .then(
                        // Validate the approval is used automatically (although not specified in
                        // the contract)
                        sourcing(
                                () ->
                                        contractCall(
                                                        TRANSFER_CONTRACT,
                                                        TRANSFER_MULTIPLE_TOKENS,
                                                        (Object)
                                                                new Tuple[] {
                                                                    tokenTransferList()
                                                                            .forTokenAddress(
                                                                                    tokenAddress
                                                                                            .get())
                                                                            .withAccountAmounts(
                                                                                    addressedAccountAmount(
                                                                                            senderAddress
                                                                                                    .get(),
                                                                                            -amountPerTransfer),
                                                                                    addressedAccountAmount(
                                                                                            receiverAddress
                                                                                                    .get(),
                                                                                            +amountPerTransfer))
                                                                            .build()
                                                                })
                                                .via(approvedTransfer)
                                                .gas(GAS_TO_OFFER)),
                        // Two successful transfers - one with a top-level signature, one with an
                        // approval
                        getAccountBalance(RECEIVER)
                                .hasTokenBalance(FUNGIBLE_TOKEN, 2 * amountPerTransfer),
                        getAccountBalance(SENDER)
                                .hasTokenBalance(FUNGIBLE_TOKEN, 2 * amountPerTransfer),
                        childRecordsCheck(
                                approvedTransfer,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .withStatus(SUCCESS))
                                                        .gasUsed(14085L))
                                        .tokenTransfers(
                                                SomeFungibleTransfers.changingFungibleBalances()
                                                        .including(
                                                                FUNGIBLE_TOKEN,
                                                                SENDER,
                                                                -amountPerTransfer)
                                                        .including(
                                                                FUNGIBLE_TOKEN,
                                                                RECEIVER,
                                                                amountPerTransfer))),
                        // Confirm the failure without access to top-level sigs was due to the
                        // contract not having an allowance
                        childRecordsCheck(
                                ignoredTopLevelSigTransfer,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(SPENDER_DOES_NOT_HAVE_ALLOWANCE)),
                        // Confirm the failure with access to top-level sigs was due to the missing
                        // top-level sig (not the lack of an allowance)
                        childRecordsCheck(
                                ignoredApprovalTransfer,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)));
    }

    private HapiSpec evmLazyCreateViaSolidityCallTooManyCreatesFails() {
        final var LAZY_CREATE_CONTRACT = "NestedLazyCreateContract";
        final var ECDSA_KEY = "ECDSAKey";
        final var ECDSA_KEY2 = "ECDSAKey2";
        final var createTooManyHollowAccounts = "createTooManyHollowAccounts";
        final var lazyCreationProperty = "lazyCreation.enabled";
        final var contractsEvmVersionProperty = "contracts.evm.version";
        final var contractsEvmVersionDynamicProperty = "contracts.evm.version.dynamic";
        final var maxPrecedingRecords = "consensus.handle.maxPrecedingRecords";
        final var depositAmount = 1000;
        return propertyPreservingHapiSpec("evmLazyCreateViaSolidityCallTooManyCreatesFails")
                .preserving(
                        lazyCreationProperty,
                        maxPrecedingRecords,
                        contractsEvmVersionDynamicProperty,
                        contractsEvmVersionDynamicProperty)
                .given(
                        overridingTwo(lazyCreationProperty, TRUE, maxPrecedingRecords, "1"),
                        overridingTwo(
                                contractsEvmVersionProperty,
                                "v0.34",
                                contractsEvmVersionDynamicProperty,
                                TRUE),
                        newKeyNamed(ECDSA_KEY).shape(SECP_256K1_SHAPE),
                        newKeyNamed(ECDSA_KEY2).shape(SECP_256K1_SHAPE),
                        uploadInitCode(LAZY_CREATE_CONTRACT),
                        contractCreate(LAZY_CREATE_CONTRACT).via(CALL_TX_REC),
                        getTxnRecord(CALL_TX_REC).andAllChildRecords().logged())
                .when(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var ecdsaKey = spec.registry().getKey(ECDSA_KEY);
                                    final var tmp = ecdsaKey.getECDSASecp256K1().toByteArray();
                                    final var addressBytes = recoverAddressFromPubKey(tmp);
                                    final var ecdsaKey2 = spec.registry().getKey(ECDSA_KEY2);
                                    final var tmp2 = ecdsaKey2.getECDSASecp256K1().toByteArray();
                                    final var addressBytes2 = recoverAddressFromPubKey(tmp2);
                                    allRunFor(
                                            spec,
                                            contractCall(
                                                            LAZY_CREATE_CONTRACT,
                                                            createTooManyHollowAccounts,
                                                            (Object)
                                                                    asHeadlongAddressArray(
                                                                            addressBytes,
                                                                            addressBytes2))
                                                    .sending(depositAmount)
                                                    .via(TRANSFER_TXN)
                                                    .gas(6_000_000)
                                                    .hasKnownStatus(MAX_CHILD_RECORDS_EXCEEDED),
                                            getAliasedAccountInfo(ecdsaKey.toByteString())
                                                    .logged()
                                                    .hasCostAnswerPrecheck(INVALID_ACCOUNT_ID),
                                            getAliasedAccountInfo(ecdsaKey2.toByteString())
                                                    .logged()
                                                    .hasCostAnswerPrecheck(INVALID_ACCOUNT_ID));
                                }))
                .then(
                        emptyChildRecordsCheck(TRANSFER_TXN, MAX_CHILD_RECORDS_EXCEEDED),
                        resetToDefault(
                                lazyCreationProperty,
                                contractsEvmVersionProperty,
                                maxPrecedingRecords));
    }

    private HapiSpec rejectsCreationAndUpdateOfAssociationsWhenFlagDisabled() {
        return propertyPreservingHapiSpec("rejectsCreationAndUpdateOfAssociationsWhenFlagDisabled")
                .preserving(CONTRACT_ALLOW_ASSOCIATIONS_PROPERTY)
                .given(overriding(CONTRACT_ALLOW_ASSOCIATIONS_PROPERTY, FALSE))
                .when(uploadInitCode(EMPTY_CONSTRUCTOR_CONTRACT))
                .then(
                        contractCreate(EMPTY_CONSTRUCTOR_CONTRACT)
                                .maxAutomaticTokenAssociations(5)
                                .hasPrecheck(NOT_SUPPORTED),
                        contractCreate(EMPTY_CONSTRUCTOR_CONTRACT).maxAutomaticTokenAssociations(0),
                        contractUpdate(EMPTY_CONSTRUCTOR_CONTRACT)
                                .newMaxAutomaticAssociations(5)
                                .hasPrecheck(NOT_SUPPORTED),
                        contractUpdate(EMPTY_CONSTRUCTOR_CONTRACT).newMemo("Hola!"));
    }

    private HapiSpec erc20TransferFromDoesNotWorkIfFlagIsDisabled() {
        return defaultHapiSpec("erc20TransferFromDoesNotWorkIfFlagIsDisabled")
                .given(
                        overriding(HEDERA_ALLOWANCES_IS_ENABLED, FALSE),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(OWNER).balance(100 * ONE_HUNDRED_HBARS),
                        cryptoCreate(RECIPIENT),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.FINITE)
                                .initialSupply(10L)
                                .maxSupply(1000L)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY),
                        uploadInitCode(ERC_20_CONTRACT),
                        contractCreate(ERC_20_CONTRACT),
                        tokenAssociate(OWNER, FUNGIBLE_TOKEN),
                        tokenAssociate(RECIPIENT, FUNGIBLE_TOKEN),
                        tokenAssociate(ERC_20_CONTRACT, FUNGIBLE_TOKEN),
                        cryptoTransfer(moving(10, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, OWNER)))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                ERC_20_CONTRACT,
                                                                TRANSFER_FROM,
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getTokenID(
                                                                                                FUNGIBLE_TOKEN))),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getAccountID(
                                                                                                OWNER))),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getAccountID(
                                                                                                RECIPIENT))),
                                                                BigInteger.TWO)
                                                        .gas(500_000L)
                                                        .via(TRANSFER_FROM_ACCOUNT_TXN)
                                                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED))))
                .then(
                        getTxnRecord(TRANSFER_FROM_ACCOUNT_TXN)
                                .logged(), // has gasUsed little less than supplied 500K in
                        // contractCall result
                        overriding(HEDERA_ALLOWANCES_IS_ENABLED, TRUE));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
