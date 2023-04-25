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
package com.hedera.node.app.service.mono.store.contracts.precompile;

import static com.hedera.node.app.service.mono.state.EntityCreator.EMPTY_MEMO;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_ALLOWANCE;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_APPROVE;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_APPROVE_NFT;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_ERC_ALLOWANCE;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_ERC_APPROVE;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_ERC_BALANCE_OF_TOKEN;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_ERC_DECIMALS;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_ERC_GET_APPROVED;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_ERC_IS_APPROVED_FOR_ALL;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_ERC_NAME;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_ERC_OWNER_OF_NFT;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_ERC_SET_APPROVAL_FOR_ALL;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_ERC_SYMBOL;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_ERC_TOKEN_URI_NFT;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_ERC_TOTAL_SUPPLY_TOKEN;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_ERC_TRANSFER;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_ERC_TRANSFER_FROM;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_GET_APPROVED;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_IS_APPROVED_FOR_ALL;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_REDIRECT_FOR_TOKEN;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_SET_APPROVAL_FOR_ALL;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_TRANSFER_FROM;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_TRANSFER_FROM_NFT;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSPrecompiledContract.HTS_PRECOMPILED_CONTRACT_ADDRESS;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.AMOUNT;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.CRYPTO_TRANSFER_FUNGIBLE_WRAPPER;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.NOT_SUPPORTED_FUNGIBLE_OPERATION_REASON;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.TEST_CONSENSUS_TIME;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.contractAddr;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.contractAddress;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.failResult;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.fungibleTokenAddr;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.invalidFullPrefix;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.ownerEntity;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.precompiledContract;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.receiver;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.recipientAddress;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.sender;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.senderAddress;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.serialNumber;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.successResult;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.timestamp;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.token;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.tokenAddress;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.tokenTransferChanges;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.tokensTransferList;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.esaulpaugh.headlong.util.Integers;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.node.app.hapi.fees.pricing.AssetsLoader;
import com.hedera.node.app.hapi.utils.fee.FeeObject;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmHTSPrecompiledContract;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.BalanceOfWrapper;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmEncodingFacade;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.TokenAllowanceWrapper;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.node.app.service.mono.context.SideEffectsTracker;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.contracts.sources.TxnAwareEvmSigsVerifier;
import com.hedera.node.app.service.mono.fees.FeeCalculator;
import com.hedera.node.app.service.mono.fees.HbarCentExchange;
import com.hedera.node.app.service.mono.fees.calculation.UsagePricesProvider;
import com.hedera.node.app.service.mono.grpc.marshalling.ImpliedTransfers;
import com.hedera.node.app.service.mono.grpc.marshalling.ImpliedTransfersMarshal;
import com.hedera.node.app.service.mono.grpc.marshalling.ImpliedTransfersMeta;
import com.hedera.node.app.service.mono.ledger.TransactionalLedger;
import com.hedera.node.app.service.mono.ledger.TransferLogic;
import com.hedera.node.app.service.mono.ledger.accounts.ContractAliases;
import com.hedera.node.app.service.mono.ledger.properties.AccountProperty;
import com.hedera.node.app.service.mono.ledger.properties.NftProperty;
import com.hedera.node.app.service.mono.ledger.properties.TokenProperty;
import com.hedera.node.app.service.mono.ledger.properties.TokenRelProperty;
import com.hedera.node.app.service.mono.records.RecordsHistorian;
import com.hedera.node.app.service.mono.state.expiry.ExpiringCreations;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.state.migration.HederaTokenRel;
import com.hedera.node.app.service.mono.state.migration.UniqueTokenAdapter;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.EvmFnResult;
import com.hedera.node.app.service.mono.state.submerkle.ExpirableTxnRecord;
import com.hedera.node.app.service.mono.state.submerkle.FcTokenAllowanceId;
import com.hedera.node.app.service.mono.store.AccountStore;
import com.hedera.node.app.service.mono.store.TypedTokenStore;
import com.hedera.node.app.service.mono.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.node.app.service.mono.store.contracts.WorldLedgers;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.ApproveWrapper;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.CryptoTransferWrapper;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.TokenTransferWrapper;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.TransferWrapper;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.AllowancePrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.ApprovePrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.BalanceOfPrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.ERCTransferPrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.TransferPrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.node.app.service.mono.store.models.Account;
import com.hedera.node.app.service.mono.store.models.NftId;
import com.hedera.node.app.service.mono.store.tokens.HederaTokenStore;
import com.hedera.node.app.service.mono.txns.crypto.ApproveAllowanceLogic;
import com.hedera.node.app.service.mono.txns.crypto.validators.ApproveAllowanceChecks;
import com.hedera.node.app.service.mono.txns.crypto.validators.DeleteAllowanceChecks;
import com.hedera.node.app.service.mono.utils.EntityIdUtils;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.accessors.AccessorFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.CryptoApproveAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ERC20PrecompilesTest {

    @Mock private org.hyperledger.besu.evm.account.Account acc;
    @Mock private Deque<MessageFrame> stack;
    @Mock private Iterator<MessageFrame> dequeIterator;
    @Mock private GlobalDynamicProperties dynamicProperties;
    @Mock private GasCalculator gasCalculator;
    @Mock private MessageFrame frame;

    @Mock(strictness = LENIENT)
    private TxnAwareEvmSigsVerifier sigsVerifier;

    @Mock private RecordsHistorian recordsHistorian;
    @Mock private EncodingFacade encoder;
    @Mock private EvmEncodingFacade evmEncoder;
    @Mock private SideEffectsTracker sideEffects;
    @Mock private TransactionBody.Builder mockSynthBodyBuilder;
    @Mock private ExpirableTxnRecord.Builder mockRecordBuilder;
    @Mock private SyntheticTxnFactory syntheticTxnFactory;
    @Mock private HederaStackedWorldStateUpdater worldUpdater;
    @Mock private WorldLedgers wrappedLedgers;
    @Mock private TransactionalLedger<NftId, NftProperty, UniqueTokenAdapter> nfts;

    @Mock
    private TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, HederaTokenRel>
            tokenRels;

    @Mock private TransactionalLedger<AccountID, AccountProperty, HederaAccount> accounts;
    @Mock private TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokens;
    @Mock private ExpiringCreations creator;
    @Mock private ImpliedTransfersMarshal impliedTransfersMarshal;
    @Mock private FeeCalculator feeCalculator;
    @Mock private StateView stateView;
    @Mock private CryptoTransferTransactionBody cryptoTransferTransactionBody;
    @Mock private ImpliedTransfersMeta impliedTransfersMeta;
    @Mock private ImpliedTransfers impliedTransfers;
    @Mock private TransferLogic transferLogic;
    @Mock private HederaTokenStore hederaTokenStore;
    @Mock private FeeObject mockFeeObject;
    @Mock private ContractAliases aliases;
    @Mock private UsagePricesProvider resourceCosts;
    @Mock private BlockValues blockValues;
    @Mock private InfrastructureFactory infrastructureFactory;
    @Mock private ApproveAllowanceChecks allowanceChecks;
    @Mock private DeleteAllowanceChecks deleteAllowanceChecks;
    @Mock private CryptoApproveAllowanceTransactionBody cryptoApproveAllowanceTransactionBody;
    @Mock private AccountStore accountStore;
    @Mock private TypedTokenStore tokenStore;
    @Mock private ApproveAllowanceLogic approveAllowanceLogic;
    @Mock private AssetsLoader assetLoader;
    @Mock private HbarCentExchange exchange;
    @Mock private ExchangeRate exchangeRate;
    @Mock private AccessorFactory accessorFactory;
    @Mock private Account account;
    @Mock private EvmHTSPrecompiledContract evmHTSPrecompiledContract;

    private static final int CENTS_RATE = 12;
    private static final int HBAR_RATE = 1;

    private HTSPrecompiledContract subject;
    private MockedStatic<EntityIdUtils> entityIdUtils;
    private MockedStatic<ERCTransferPrecompile> ercTransferPrecompile;
    private MockedStatic<AllowancePrecompile> allowancePrecompile;
    private MockedStatic<BalanceOfPrecompile> balanceOfPrecompile;
    private MockedStatic<ApprovePrecompile> approvePrecompile;

    @BeforeEach
    void setUp() throws IOException {
        final Map<HederaFunctionality, Map<SubType, BigDecimal>> canonicalPrices = new HashMap<>();
        final Map<SubType, BigDecimal> type = new HashMap<>();
        type.put(SubType.TOKEN_FUNGIBLE_COMMON, BigDecimal.valueOf(0));
        type.put(SubType.TOKEN_NON_FUNGIBLE_UNIQUE, BigDecimal.valueOf(0));
        canonicalPrices.put(HederaFunctionality.CryptoTransfer, type);
        given(assetLoader.loadCanonicalPrices()).willReturn(canonicalPrices);
        final PrecompilePricingUtils precompilePricingUtils =
                new PrecompilePricingUtils(
                        assetLoader,
                        exchange,
                        () -> feeCalculator,
                        resourceCosts,
                        stateView,
                        accessorFactory);
        subject =
                new HTSPrecompiledContract(
                        dynamicProperties,
                        gasCalculator,
                        recordsHistorian,
                        sigsVerifier,
                        encoder,
                        evmEncoder,
                        syntheticTxnFactory,
                        creator,
                        () -> feeCalculator,
                        stateView,
                        precompilePricingUtils,
                        infrastructureFactory,
                        evmHTSPrecompiledContract);
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        entityIdUtils = Mockito.mockStatic(EntityIdUtils.class);
        entityIdUtils
                .when(() -> EntityIdUtils.accountIdFromEvmAddress(senderAddress))
                .thenReturn(sender);
        entityIdUtils
                .when(
                        () ->
                                EntityIdUtils.contractIdFromEvmAddress(
                                        Address.fromHexString(HTS_PRECOMPILED_CONTRACT_ADDRESS)
                                                .toArray()))
                .thenReturn(precompiledContract);
        entityIdUtils.when(() -> EntityIdUtils.asTypedEvmAddress(sender)).thenReturn(senderAddress);
        entityIdUtils.when(() -> EntityIdUtils.asTypedEvmAddress(token)).thenReturn(tokenAddress);
        entityIdUtils
                .when(() -> EntityIdUtils.asTypedEvmAddress(receiver))
                .thenReturn(recipientAddress);
        entityIdUtils
                .when(() -> EntityIdUtils.tokenIdFromEvmAddress(fungibleTokenAddr.toArray()))
                .thenReturn(token);
        entityIdUtils
                .when(() -> EntityIdUtils.tokenIdFromEvmAddress(fungibleTokenAddr))
                .thenReturn(token);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        ercTransferPrecompile = Mockito.mockStatic(ERCTransferPrecompile.class);
        allowancePrecompile = Mockito.mockStatic(AllowancePrecompile.class);
        balanceOfPrecompile = Mockito.mockStatic(BalanceOfPrecompile.class);
        approvePrecompile = Mockito.mockStatic(ApprovePrecompile.class);
    }

    @AfterEach
    void closeMocks() {
        entityIdUtils.close();
        ercTransferPrecompile.close();
        allowancePrecompile.close();
        balanceOfPrecompile.close();
        approvePrecompile.close();
    }

    @Test
    void ercAllowanceDisabled() {
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);

        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
        final Bytes pretendArgumentsApprove =
                Bytes.concatenate(
                        Bytes.of(Integers.toBytes(ABI_ID_REDIRECT_FOR_TOKEN)),
                        fungibleTokenAddr,
                        Bytes.of(Integers.toBytes(ABI_ID_ERC_APPROVE)));

        // when:
        subject.prepareFields(frame);

        assertThrows(
                InvalidTransactionException.class,
                () -> subject.prepareComputation(pretendArgumentsApprove, a -> a));

        final Bytes pretendArgumentsTransferFrom =
                Bytes.concatenate(
                        Bytes.of(Integers.toBytes(ABI_ID_REDIRECT_FOR_TOKEN)),
                        fungibleTokenAddr,
                        Bytes.of(Integers.toBytes(ABI_ID_ERC_TRANSFER_FROM)));

        // when:
        subject.prepareFields(frame);

        assertThrows(
                InvalidTransactionException.class,
                () -> subject.prepareComputation(pretendArgumentsTransferFrom, a -> a));

        final Bytes pretendArgumentsAllowance =
                Bytes.concatenate(
                        Bytes.of(Integers.toBytes(ABI_ID_REDIRECT_FOR_TOKEN)),
                        fungibleTokenAddr,
                        Bytes.of(Integers.toBytes(ABI_ID_ERC_ALLOWANCE)));

        // when:
        subject.prepareFields(frame);

        assertThrows(
                InvalidTransactionException.class,
                () -> subject.prepareComputation(pretendArgumentsAllowance, a -> a));

        final Bytes pretendArgumentsApproveForAll =
                Bytes.concatenate(
                        Bytes.of(Integers.toBytes(ABI_ID_REDIRECT_FOR_TOKEN)),
                        fungibleTokenAddr,
                        Bytes.of(Integers.toBytes(ABI_ID_ERC_SET_APPROVAL_FOR_ALL)));

        // when:
        subject.prepareFields(frame);

        assertThrows(
                InvalidTransactionException.class,
                () -> subject.prepareComputation(pretendArgumentsApproveForAll, a -> a));

        final Bytes pretendArgumentsGetApproved =
                Bytes.concatenate(
                        Bytes.of(Integers.toBytes(ABI_ID_REDIRECT_FOR_TOKEN)),
                        fungibleTokenAddr,
                        Bytes.of(Integers.toBytes(ABI_ID_ERC_GET_APPROVED)));

        // when:
        subject.prepareFields(frame);

        assertThrows(
                InvalidTransactionException.class,
                () -> subject.prepareComputation(pretendArgumentsGetApproved, a -> a));

        final Bytes pretendArgumentsApprovedForAll =
                Bytes.concatenate(
                        Bytes.of(Integers.toBytes(ABI_ID_REDIRECT_FOR_TOKEN)),
                        fungibleTokenAddr,
                        Bytes.of(Integers.toBytes(ABI_ID_ERC_IS_APPROVED_FOR_ALL)));

        // when:
        subject.prepareFields(frame);

        assertThrows(
                InvalidTransactionException.class,
                () -> subject.prepareComputation(pretendArgumentsApprovedForAll, a -> a));
    }

    @Test
    void hapiAllowanceDisabled() {
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);

        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
        final Bytes pretendArgumentsApprove =
                Bytes.concatenate(Bytes.of(Integers.toBytes(ABI_ID_APPROVE)));

        // when:
        subject.prepareFields(frame);

        assertThrows(
                InvalidTransactionException.class,
                () -> subject.prepareComputation(pretendArgumentsApprove, a -> a));

        final Bytes pretendArgumentsAllowance =
                Bytes.concatenate(Bytes.of(Integers.toBytes(ABI_ID_ALLOWANCE)));

        // when:
        subject.prepareFields(frame);

        assertThrows(
                InvalidTransactionException.class,
                () -> subject.prepareComputation(pretendArgumentsAllowance, a -> a));

        final Bytes pretendArgumentsApproveForAll =
                Bytes.concatenate(Bytes.of(Integers.toBytes(ABI_ID_SET_APPROVAL_FOR_ALL)));

        // when:
        subject.prepareFields(frame);

        assertThrows(
                InvalidTransactionException.class,
                () -> subject.prepareComputation(pretendArgumentsApproveForAll, a -> a));

        final Bytes pretendArgumentsGetApproved =
                Bytes.concatenate(Bytes.of(Integers.toBytes(ABI_ID_GET_APPROVED)));

        // when:
        subject.prepareFields(frame);

        assertThrows(
                InvalidTransactionException.class,
                () -> subject.prepareComputation(pretendArgumentsGetApproved, a -> a));

        final Bytes pretendArgumentsApprovedForAll =
                Bytes.concatenate(Bytes.of(Integers.toBytes(ABI_ID_IS_APPROVED_FOR_ALL)));

        // when:
        subject.prepareFields(frame);

        assertThrows(
                InvalidTransactionException.class,
                () -> subject.prepareComputation(pretendArgumentsApprovedForAll, a -> a));
    }

    @Test
    void invalidNestedFunctionSelector() {
        final Bytes nestedPretendArguments = Bytes.of(0, 0, 0, 0);
        givenIfDelegateCall();
        final Bytes pretendArguments =
                givenMinimalFrameContextWithoutParentUpdater(nestedPretendArguments);

        given(wrappedLedgers.typeOf(token)).willReturn(TokenType.FUNGIBLE_COMMON);

        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        final var result = subject.computePrecompile(pretendArguments, frame);
        assertNull(result.getOutput());
    }

    @Test
    void gasCalculationForReadOnlyMethod() {
        final Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_NAME));
        final Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);

        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments))
                .willReturn(mockSynthBodyBuilder);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any()))
                .willReturn(mockFeeObject);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockFeeObject.getNodeFee()).willReturn(1L);
        given(mockFeeObject.getNetworkFee()).willReturn(1L);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        givenIfDelegateCall();
        given(evmEncoder.encodeName(any())).willReturn(successResult);
        given(wrappedLedgers.typeOf(token)).willReturn(TokenType.FUNGIBLE_COMMON);
        given(frame.getBlockValues()).willReturn(blockValues);
        given(blockValues.getTimestamp()).willReturn(TEST_CONSENSUS_TIME);
        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.compute(pretendArguments, frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void gasCalculationForModifyingMethod() throws InvalidProtocolBufferException {
        final Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_TRANSFER));
        final Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
        givenLedgers();
        givenPricingUtilsContext();

        given(frame.getContractAddress()).willReturn(contractAddr);
        given(
                        syntheticTxnFactory.createCryptoTransfer(
                                Collections.singletonList(tokensTransferList)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
        given(impliedTransfersMarshal.validityWithCurrentProps(cryptoTransferTransactionBody))
                .willReturn(OK);
        given(sigsVerifier.hasActiveKey(anyBoolean(), any(), any(), any(), eq(CryptoTransfer)))
                .willReturn(true);
        given(
                        sigsVerifier.hasActiveKeyOrNoReceiverSigReq(
                                anyBoolean(), any(), any(), any(), eq(CryptoTransfer)))
                .willReturn(true, true);
        given(infrastructureFactory.newImpliedTransfersMarshal(any()))
                .willReturn(impliedTransfersMarshal);
        given(infrastructureFactory.newHederaTokenStore(sideEffects, tokens, nfts, tokenRels))
                .willReturn(hederaTokenStore);

        given(
                        infrastructureFactory.newTransferLogic(
                                hederaTokenStore, sideEffects, nfts, accounts, tokenRels))
                .willReturn(transferLogic);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build())
                .willReturn(
                        TransactionBody.newBuilder()
                                .setCryptoTransfer(cryptoTransferTransactionBody)
                                .build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(
                        impliedTransfersMarshal.assessCustomFeesAndValidate(
                                anyInt(), anyInt(), anyInt(), any(), any(), any()))
                .willReturn(impliedTransfers);
        given(impliedTransfers.getAllBalanceChanges()).willReturn(tokenTransferChanges);
        given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
        given(impliedTransfersMeta.code()).willReturn(OK);
        ercTransferPrecompile
                .when(
                        () ->
                                ERCTransferPrecompile.decodeERCTransfer(
                                        eq(nestedPretendArguments), any(), any(), any(), any()))
                .thenReturn(CRYPTO_TRANSFER_FUNGIBLE_WRAPPER);
        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.aliases()).willReturn(aliases);
        given(wrappedLedgers.typeOf(token)).willReturn(TokenType.FUNGIBLE_COMMON);
        given(encoder.encodeEcFungibleTransfer(true)).willReturn(successResult);
        givenIfDelegateCall();
        given(frame.getBlockValues()).willReturn(blockValues);
        given(blockValues.getTimestamp()).willReturn(TEST_CONSENSUS_TIME);
        when(accessorFactory.uncheckedSpecializedAccessor(any())).thenCallRealMethod();
        when(accessorFactory.constructSpecializedAccessor(any())).thenCallRealMethod();
        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computePrecompile(pretendArguments, frame);

        // then:
        assertEquals(successResult, result.getOutput());
        // and:
        verify(transferLogic).doZeroSum(tokenTransferChanges);
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void name() {
        final Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_NAME));
        final Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);

        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments))
                .willReturn(mockSynthBodyBuilder);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any()))
                .willReturn(mockFeeObject);
        given(mockFeeObject.getNodeFee()).willReturn(1L);
        given(mockFeeObject.getNetworkFee()).willReturn(1L);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(evmEncoder.encodeName(any())).willReturn(successResult);
        given(wrappedLedgers.typeOf(token)).willReturn(TokenType.FUNGIBLE_COMMON);
        given(dynamicProperties.shouldExportPrecompileResults()).willReturn(true);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
        final ArgumentCaptor<EvmFnResult> captor = ArgumentCaptor.forClass(EvmFnResult.class);
        verify(mockRecordBuilder).setContractCallResult(captor.capture());
        assertEquals(0L, captor.getValue().getGas());
        assertEquals(0L, captor.getValue().getAmount());
        assertEquals(EvmFnResult.EMPTY, captor.getValue().getFunctionParameters());
    }

    @Test
    void symbol() {
        final Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_SYMBOL));
        final Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);

        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments))
                .willReturn(mockSynthBodyBuilder);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);

        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any()))
                .willReturn(mockFeeObject);
        given(mockFeeObject.getNodeFee()).willReturn(1L);
        given(mockFeeObject.getNetworkFee()).willReturn(1L);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(evmEncoder.encodeSymbol(any())).willReturn(successResult);
        given(wrappedLedgers.typeOf(token)).willReturn(TokenType.FUNGIBLE_COMMON);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void decimals() {
        final Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_DECIMALS));
        final Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);

        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments))
                .willReturn(mockSynthBodyBuilder);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);

        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any()))
                .willReturn(mockFeeObject);
        given(mockFeeObject.getNodeFee()).willReturn(1L);
        given(mockFeeObject.getNetworkFee()).willReturn(1L);
        given(mockFeeObject.getServiceFee()).willReturn(1L);

        given(wrappedLedgers.decimalsOf(token)).willReturn(10);
        given(evmEncoder.encodeDecimals(10)).willReturn(successResult);
        given(wrappedLedgers.typeOf(token)).willReturn(TokenType.FUNGIBLE_COMMON);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void totalSupply() {
        final Bytes nestedPretendArguments =
                Bytes.of(Integers.toBytes(ABI_ID_ERC_TOTAL_SUPPLY_TOKEN));
        final Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments))
                .willReturn(mockSynthBodyBuilder);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);

        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any()))
                .willReturn(mockFeeObject);
        given(mockFeeObject.getNodeFee()).willReturn(1L);
        given(mockFeeObject.getNetworkFee()).willReturn(1L);
        given(mockFeeObject.getServiceFee()).willReturn(1L);

        given(wrappedLedgers.totalSupplyOf(token)).willReturn(10L);
        given(evmEncoder.encodeTotalSupply(10L)).willReturn(successResult);
        given(wrappedLedgers.typeOf(token)).willReturn(TokenType.FUNGIBLE_COMMON);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void ercAllowance() {
        final TreeMap<FcTokenAllowanceId, Long> allowances = new TreeMap<>();
        allowances.put(
                FcTokenAllowanceId.from(
                        EntityNum.fromLong(token.getTokenNum()),
                        EntityNum.fromLong(receiver.getAccountNum())),
                10L);

        final Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_ALLOWANCE));
        final Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
        given(wrappedLedgers.accounts()).willReturn(accounts);
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments))
                .willReturn(mockSynthBodyBuilder);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);

        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any()))
                .willReturn(mockFeeObject);
        given(mockFeeObject.getNodeFee()).willReturn(1L);
        given(mockFeeObject.getNetworkFee()).willReturn(1L);
        given(mockFeeObject.getServiceFee()).willReturn(1L);

        given(accounts.contains(any())).willReturn(true);
        allowancePrecompile
                .when(() -> AllowancePrecompile.decodeTokenAllowance(any(), any(), any()))
                .thenReturn(ALLOWANCE_WRAPPER);
        given(accounts.get(any(), any())).willReturn(allowances);
        given(evmEncoder.encodeAllowance(10L)).willReturn(successResult);
        given(wrappedLedgers.typeOf(token)).willReturn(TokenType.FUNGIBLE_COMMON);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void hapiAllowance() {
        final TreeMap<FcTokenAllowanceId, Long> alowances = new TreeMap<>();
        alowances.put(
                FcTokenAllowanceId.from(
                        EntityNum.fromLong(token.getTokenNum()),
                        EntityNum.fromLong(receiver.getAccountNum())),
                10L);

        final Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ALLOWANCE));
        givenMinimalFrameContext(pretendArguments);
        given(wrappedLedgers.accounts()).willReturn(accounts);
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments))
                .willReturn(mockSynthBodyBuilder);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);

        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any()))
                .willReturn(mockFeeObject);
        given(mockFeeObject.getNodeFee()).willReturn(1L);
        given(mockFeeObject.getNetworkFee()).willReturn(1L);
        given(mockFeeObject.getServiceFee()).willReturn(1L);

        given(accounts.contains(any())).willReturn(true);
        allowancePrecompile
                .when(
                        () ->
                                AllowancePrecompile.decodeTokenAllowance(
                                        eq(pretendArguments), any(), any()))
                .thenReturn(ALLOWANCE_WRAPPER);
        given(accounts.get(any(), any())).willReturn(alowances);
        given(encoder.encodeAllowance(SUCCESS.getNumber(), 10L)).willReturn(successResult);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void balanceOf() {
        final Bytes nestedPretendArguments =
                Bytes.of(Integers.toBytes(ABI_ID_ERC_BALANCE_OF_TOKEN));
        final Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);

        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments))
                .willReturn(mockSynthBodyBuilder);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);

        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any()))
                .willReturn(mockFeeObject);
        given(mockFeeObject.getNodeFee()).willReturn(1L);
        given(mockFeeObject.getNetworkFee()).willReturn(1L);
        given(mockFeeObject.getServiceFee()).willReturn(1L);

        balanceOfPrecompile
                .when(() -> BalanceOfPrecompile.decodeBalanceOf(eq(nestedPretendArguments), any()))
                .thenReturn(BALANCE_OF_WRAPPER);
        given(wrappedLedgers.balanceOf(any(), any())).willReturn(10L);
        given(evmEncoder.encodeBalance(10L)).willReturn(successResult);

        entityIdUtils
                .when(() -> EntityIdUtils.tokenIdFromEvmAddress(fungibleTokenAddr.toArray()))
                .thenReturn(token);
        entityIdUtils
                .when(
                        () ->
                                EntityIdUtils.contractIdFromEvmAddress(
                                        Address.fromHexString(HTS_PRECOMPILED_CONTRACT_ADDRESS)
                                                .toArray()))
                .thenReturn(precompiledContract);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void allowanceValidation() {
        givenPricingUtilsContext();

        final Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_APPROVE));
        final Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);

        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);

        given(syntheticTxnFactory.createFungibleApproval(APPROVE_WRAPPER, new EntityId(0, 0, 7)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.build()).willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(EntityIdUtils.accountIdFromEvmAddress((Address) any())).willReturn(sender);

        approvePrecompile
                .when(
                        () ->
                                ApprovePrecompile.decodeTokenApprove(
                                        eq(nestedPretendArguments),
                                        eq(token),
                                        eq(true),
                                        any(),
                                        any()))
                .thenReturn(APPROVE_WRAPPER);
        given(wrappedLedgers.typeOf(token)).willReturn(TokenType.FUNGIBLE_COMMON);
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(failResult, result);
    }

    @Test
    void ercApprove() {
        final List<CryptoAllowance> cryptoAllowances = new ArrayList<>();
        final List<TokenAllowance> tokenAllowances = new ArrayList<>();
        final List<NftAllowance> nftAllowances = new ArrayList<>();

        final Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_APPROVE));
        final Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
        givenLedgers();
        givenPricingUtilsContext();

        given(wrappedLedgers.tokens()).willReturn(tokens);
        given(wrappedLedgers.accounts()).willReturn(accounts);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);

        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);

        given(syntheticTxnFactory.createFungibleApproval(eq(APPROVE_WRAPPER), any()))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.build()).willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoApproveAllowance())
                .willReturn(cryptoApproveAllowanceTransactionBody);

        given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
        given(
                        infrastructureFactory.newTokenStore(
                                accountStore, sideEffects, tokens, nfts, tokenRels))
                .willReturn(tokenStore);
        given(infrastructureFactory.newApproveAllowanceLogic(accountStore, tokenStore))
                .willReturn(approveAllowanceLogic);
        given(EntityIdUtils.accountIdFromEvmAddress((Address) any())).willReturn(sender);
        given(accountStore.loadAccount(any())).willReturn(account);
        given(infrastructureFactory.newApproveAllowanceChecks()).willReturn(allowanceChecks);
        given(infrastructureFactory.newDeleteAllowanceChecks()).willReturn(deleteAllowanceChecks);

        given(
                        allowanceChecks.allowancesValidation(
                                cryptoAllowances,
                                tokenAllowances,
                                nftAllowances,
                                account,
                                accountStore,
                                tokenStore))
                .willReturn(OK);

        approvePrecompile
                .when(
                        () ->
                                ApprovePrecompile.decodeTokenApprove(
                                        eq(nestedPretendArguments),
                                        eq(token),
                                        eq(true),
                                        any(),
                                        any()))
                .thenReturn(APPROVE_WRAPPER);
        given(wrappedLedgers.typeOf(token)).willReturn(TokenType.FUNGIBLE_COMMON);
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
        given(encoder.encodeApprove(true)).willReturn(successResult);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void hapiApprove() {
        final List<CryptoAllowance> cryptoAllowances = new ArrayList<>();
        final List<TokenAllowance> tokenAllowances = new ArrayList<>();
        final List<NftAllowance> nftAllowances = new ArrayList<>();

        final Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_APPROVE));
        givenMinimalFrameContext(pretendArguments);
        givenLedgers();
        givenPricingUtilsContext();

        given(wrappedLedgers.tokens()).willReturn(tokens);
        given(wrappedLedgers.accounts()).willReturn(accounts);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);

        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);

        given(syntheticTxnFactory.createFungibleApproval(eq(APPROVE_WRAPPER), any()))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.build()).willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoApproveAllowance())
                .willReturn(cryptoApproveAllowanceTransactionBody);

        given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
        given(
                        infrastructureFactory.newTokenStore(
                                accountStore, sideEffects, tokens, nfts, tokenRels))
                .willReturn(tokenStore);
        given(infrastructureFactory.newApproveAllowanceLogic(accountStore, tokenStore))
                .willReturn(approveAllowanceLogic);

        given(accountStore.loadAccount(any())).willReturn(account);
        given(infrastructureFactory.newApproveAllowanceChecks()).willReturn(allowanceChecks);
        given(infrastructureFactory.newDeleteAllowanceChecks()).willReturn(deleteAllowanceChecks);

        given(
                        allowanceChecks.allowancesValidation(
                                cryptoAllowances,
                                tokenAllowances,
                                nftAllowances,
                                account,
                                accountStore,
                                tokenStore))
                .willReturn(OK);

        approvePrecompile
                .when(
                        () ->
                                ApprovePrecompile.decodeTokenApprove(
                                        eq(pretendArguments), eq(null), eq(true), any(), any()))
                .thenReturn(APPROVE_WRAPPER);
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
        given(encoder.encodeApprove(SUCCESS.getNumber(), true)).willReturn(successResult);
        given(wrappedLedgers.canonicalAddress(recipientAddress)).willReturn(recipientAddress);
        given(wrappedLedgers.canonicalAddress(contractAddress)).willReturn(senderAddress);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
        verify(frame)
                .addLog(
                        EncodingFacade.LogBuilder.logBuilder()
                                .forLogger(tokenAddress)
                                .forEventSignature(AbiConstants.APPROVAL_EVENT)
                                .forIndexedArgument(senderAddress)
                                .forIndexedArgument(recipientAddress)
                                .forDataItem(APPROVE_WRAPPER.amount())
                                .build());
    }

    @Test
    void hapiApproveNFT() {
        final List<CryptoAllowance> cryptoAllowances = new ArrayList<>();
        final List<TokenAllowance> tokenAllowances = new ArrayList<>();
        final List<NftAllowance> nftAllowances = new ArrayList<>();

        final Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_APPROVE_NFT));
        givenMinimalFrameContext(pretendArguments);
        givenLedgers();
        givenPricingUtilsContext();

        given(wrappedLedgers.tokens()).willReturn(tokens);
        given(wrappedLedgers.accounts()).willReturn(accounts);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);

        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);

        given(syntheticTxnFactory.createNonfungibleApproval(eq(APPROVE_NFT_WRAPPER), any(), any()))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.build()).willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoApproveAllowance())
                .willReturn(cryptoApproveAllowanceTransactionBody);

        given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
        given(
                        infrastructureFactory.newTokenStore(
                                accountStore, sideEffects, tokens, nfts, tokenRels))
                .willReturn(tokenStore);
        given(infrastructureFactory.newApproveAllowanceLogic(accountStore, tokenStore))
                .willReturn(approveAllowanceLogic);
        given(EntityIdUtils.accountIdFromEvmAddress((Address) any())).willReturn(sender);
        given(accountStore.loadAccount(any())).willReturn(account);
        given(infrastructureFactory.newApproveAllowanceChecks()).willReturn(allowanceChecks);
        given(infrastructureFactory.newDeleteAllowanceChecks()).willReturn(deleteAllowanceChecks);

        given(wrappedLedgers.ownerIfPresent(any())).willReturn(ownerEntity);
        given(
                        allowanceChecks.allowancesValidation(
                                cryptoAllowances,
                                tokenAllowances,
                                nftAllowances,
                                account,
                                accountStore,
                                tokenStore))
                .willReturn(OK);

        approvePrecompile
                .when(
                        () ->
                                ApprovePrecompile.decodeTokenApprove(
                                        eq(pretendArguments), eq(null), eq(false), any(), any()))
                .thenReturn(APPROVE_NFT_WRAPPER);
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
        given(encoder.encodeApproveNFT(SUCCESS.getNumber())).willReturn(successResult);
        given(wrappedLedgers.canonicalAddress(recipientAddress)).willReturn(recipientAddress);
        given(wrappedLedgers.canonicalAddress(contractAddress)).willReturn(senderAddress);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
        verify(frame)
                .addLog(
                        EncodingFacade.LogBuilder.logBuilder()
                                .forLogger(tokenAddress)
                                .forEventSignature(AbiConstants.APPROVAL_EVENT)
                                .forIndexedArgument(senderAddress)
                                .forIndexedArgument(recipientAddress)
                                .forIndexedArgument(APPROVE_NFT_WRAPPER.serialNumber())
                                .build());
    }

    @Test
    void transfer() throws InvalidProtocolBufferException {
        final Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_TRANSFER));
        final Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
        givenLedgers();
        givenPricingUtilsContext();
        final var special = Address.fromHexString("0x0000000000000000000000000000000000000007");
        given(dynamicProperties.contractsWithSpecialHapiSigsAccess()).willReturn(Set.of(special));
        given(dynamicProperties.systemContractsWithTopLevelSigsAccess())
                .willReturn(Set.of(CryptoTransfer));

        given(frame.getContractAddress()).willReturn(contractAddr);
        given(
                        syntheticTxnFactory.createCryptoTransfer(
                                Collections.singletonList(tokensTransferList)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
        given(impliedTransfersMarshal.validityWithCurrentProps(cryptoTransferTransactionBody))
                .willReturn(OK);
        given(sigsVerifier.hasActiveKey(anyBoolean(), any(), any(), any(), eq(CryptoTransfer)))
                .willReturn(true);
        given(
                        sigsVerifier.hasActiveKeyOrNoReceiverSigReq(
                                anyBoolean(), any(), any(), any(), eq(CryptoTransfer)))
                .willReturn(true, true);
        given(infrastructureFactory.newImpliedTransfersMarshal(any()))
                .willReturn(impliedTransfersMarshal);
        given(infrastructureFactory.newHederaTokenStore(sideEffects, tokens, nfts, tokenRels))
                .willReturn(hederaTokenStore);

        given(
                        infrastructureFactory.newTransferLogic(
                                hederaTokenStore, sideEffects, nfts, accounts, tokenRels))
                .willReturn(transferLogic);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build())
                .willReturn(
                        TransactionBody.newBuilder()
                                .setCryptoTransfer(cryptoTransferTransactionBody)
                                .build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(
                        impliedTransfersMarshal.assessCustomFeesAndValidate(
                                anyInt(), anyInt(), anyInt(), any(), any(), any()))
                .willReturn(impliedTransfers);
        given(impliedTransfers.getAllBalanceChanges()).willReturn(tokenTransferChanges);
        given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
        given(impliedTransfersMeta.code()).willReturn(OK);
        ercTransferPrecompile
                .when(
                        () ->
                                ERCTransferPrecompile.decodeERCTransfer(
                                        eq(nestedPretendArguments), any(), any(), any(), any()))
                .thenReturn(CRYPTO_TRANSFER_FUNGIBLE_WRAPPER);

        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.aliases()).willReturn(aliases);
        given(wrappedLedgers.typeOf(token)).willReturn(TokenType.FUNGIBLE_COMMON);
        given(wrappedLedgers.canonicalAddress(recipientAddress)).willReturn(recipientAddress);
        given(wrappedLedgers.canonicalAddress(senderAddress)).willReturn(senderAddress);
        given(encoder.encodeEcFungibleTransfer(true)).willReturn(successResult);
        when(accessorFactory.uncheckedSpecializedAccessor(any())).thenCallRealMethod();
        when(accessorFactory.constructSpecializedAccessor(any())).thenCallRealMethod();
        final var log =
                EncodingFacade.LogBuilder.logBuilder()
                        .forLogger(tokenAddress)
                        .forEventSignature(AbiConstants.TRANSFER_EVENT)
                        .forIndexedArgument(senderAddress)
                        .forIndexedArgument(recipientAddress)
                        .forDataItem(AMOUNT)
                        .build();
        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(transferLogic).doZeroSum(tokenTransferChanges);
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
        verify(frame).addLog(log);
    }

    @Test
    void transferFrom() throws InvalidProtocolBufferException {
        final Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_TRANSFER_FROM));
        final Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
        givenLedgers();
        givenPricingUtilsContext();

        given(frame.getContractAddress()).willReturn(contractAddr);
        given(
                        syntheticTxnFactory.createCryptoTransfer(
                                Collections.singletonList(TOKEN_TRANSFER_FROM_WRAPPER)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
        given(impliedTransfersMarshal.validityWithCurrentProps(cryptoTransferTransactionBody))
                .willReturn(OK);
        given(sigsVerifier.hasActiveKey(anyBoolean(), any(), any(), any(), eq(CryptoTransfer)))
                .willReturn(true);
        given(
                        sigsVerifier.hasActiveKeyOrNoReceiverSigReq(
                                anyBoolean(), any(), any(), any(), eq(CryptoTransfer)))
                .willReturn(true, true);

        given(infrastructureFactory.newImpliedTransfersMarshal(any()))
                .willReturn(impliedTransfersMarshal);
        given(infrastructureFactory.newHederaTokenStore(sideEffects, tokens, nfts, tokenRels))
                .willReturn(hederaTokenStore);

        given(
                        infrastructureFactory.newTransferLogic(
                                hederaTokenStore, sideEffects, nfts, accounts, tokenRels))
                .willReturn(transferLogic);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build())
                .willReturn(
                        TransactionBody.newBuilder()
                                .setCryptoTransfer(cryptoTransferTransactionBody)
                                .build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(
                        impliedTransfersMarshal.assessCustomFeesAndValidate(
                                anyInt(), anyInt(), anyInt(), any(), any(), any()))
                .willReturn(impliedTransfers);
        given(impliedTransfers.getAllBalanceChanges()).willReturn(tokenTransferChanges);
        given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
        given(impliedTransfersMeta.code()).willReturn(OK);

        ercTransferPrecompile
                .when(
                        () ->
                                ERCTransferPrecompile.decodeERCTransferFrom(
                                        eq(nestedPretendArguments),
                                        any(),
                                        eq(true),
                                        any(),
                                        any(),
                                        any(),
                                        any()))
                .thenReturn(CRYPTO_TRANSFER_TOKEN_FROM_WRAPPER);

        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.aliases()).willReturn(aliases);
        given(wrappedLedgers.typeOf(token)).willReturn(TokenType.FUNGIBLE_COMMON);
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
        given(encoder.encodeEcFungibleTransfer(true)).willReturn(successResult);
        when(accessorFactory.uncheckedSpecializedAccessor(any())).thenCallRealMethod();
        when(accessorFactory.constructSpecializedAccessor(any())).thenCallRealMethod();
        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(transferLogic).doZeroSum(tokenTransferChanges);
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void transferFromHapiFungible() throws InvalidProtocolBufferException {
        final var pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_FROM));
        givenMinimalFrameContext(Bytes.EMPTY);
        givenLedgers();
        givenPricingUtilsContext();
        given(dynamicProperties.maxNumWithHapiSigsAccess()).willReturn(Long.MAX_VALUE);
        given(dynamicProperties.systemContractsWithTopLevelSigsAccess())
                .willReturn(Set.of(CryptoTransfer));

        given(frame.getContractAddress()).willReturn(contractAddr);
        given(
                        syntheticTxnFactory.createCryptoTransfer(
                                Collections.singletonList(TOKEN_TRANSFER_FROM_WRAPPER)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
        given(impliedTransfersMarshal.validityWithCurrentProps(cryptoTransferTransactionBody))
                .willReturn(OK);
        given(sigsVerifier.hasActiveKey(anyBoolean(), any(), any(), any(), eq(CryptoTransfer)))
                .willReturn(true);
        given(
                        sigsVerifier.hasActiveKeyOrNoReceiverSigReq(
                                anyBoolean(), any(), any(), any(), eq(CryptoTransfer)))
                .willReturn(true, true);
        given(infrastructureFactory.newImpliedTransfersMarshal(any()))
                .willReturn(impliedTransfersMarshal);
        given(infrastructureFactory.newHederaTokenStore(sideEffects, tokens, nfts, tokenRels))
                .willReturn(hederaTokenStore);

        given(
                        infrastructureFactory.newTransferLogic(
                                hederaTokenStore, sideEffects, nfts, accounts, tokenRels))
                .willReturn(transferLogic);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build())
                .willReturn(
                        TransactionBody.newBuilder()
                                .setCryptoTransfer(cryptoTransferTransactionBody)
                                .build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(
                        impliedTransfersMarshal.assessCustomFeesAndValidate(
                                anyInt(), anyInt(), anyInt(), any(), any(), any()))
                .willReturn(impliedTransfers);
        given(impliedTransfers.getAllBalanceChanges()).willReturn(tokenTransferChanges);
        given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
        given(impliedTransfersMeta.code()).willReturn(OK);

        ercTransferPrecompile
                .when(
                        () ->
                                ERCTransferPrecompile.decodeERCTransferFrom(
                                        eq(pretendArguments),
                                        eq(null),
                                        eq(true),
                                        any(),
                                        any(),
                                        any(),
                                        any()))
                .thenReturn(CRYPTO_TRANSFER_TOKEN_FROM_WRAPPER);

        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.aliases()).willReturn(aliases);
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
        when(accessorFactory.uncheckedSpecializedAccessor(any())).thenCallRealMethod();
        when(accessorFactory.constructSpecializedAccessor(any())).thenCallRealMethod();
        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(transferLogic).doZeroSum(tokenTransferChanges);
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
        verify(frame)
                .addLog(
                        EncodingFacade.LogBuilder.logBuilder()
                                .forLogger(EntityIdUtils.asTypedEvmAddress(token))
                                .forEventSignature(AbiConstants.TRANSFER_EVENT)
                                .forIndexedArgument(sender)
                                .forIndexedArgument(receiver)
                                .forDataItem(AMOUNT)
                                .build());
    }

    @Test
    void transferFromNFTHapi() throws InvalidProtocolBufferException {
        final var pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_FROM_NFT));
        given(dynamicProperties.maxNumWithHapiSigsAccess()).willReturn(Long.MAX_VALUE);
        given(dynamicProperties.systemContractsWithTopLevelSigsAccess())
                .willReturn(Set.of(CryptoTransfer));
        givenMinimalFrameContext(Bytes.EMPTY);
        givenLedgers();
        givenPricingUtilsContext();

        given(frame.getContractAddress()).willReturn(contractAddr);
        given(
                        syntheticTxnFactory.createCryptoTransfer(
                                Collections.singletonList(TOKEN_TRANSFER_FROM_NFT_WRAPPER)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
        given(impliedTransfersMarshal.validityWithCurrentProps(cryptoTransferTransactionBody))
                .willReturn(OK);
        given(sigsVerifier.hasActiveKey(anyBoolean(), any(), any(), any(), eq(CryptoTransfer)))
                .willReturn(true);
        given(
                        sigsVerifier.hasActiveKeyOrNoReceiverSigReq(
                                anyBoolean(), any(), any(), any(), eq(CryptoTransfer)))
                .willReturn(true);
        given(infrastructureFactory.newImpliedTransfersMarshal(any()))
                .willReturn(impliedTransfersMarshal);
        given(infrastructureFactory.newHederaTokenStore(sideEffects, tokens, nfts, tokenRels))
                .willReturn(hederaTokenStore);

        given(
                        infrastructureFactory.newTransferLogic(
                                hederaTokenStore, sideEffects, nfts, accounts, tokenRels))
                .willReturn(transferLogic);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build())
                .willReturn(
                        TransactionBody.newBuilder()
                                .setCryptoTransfer(cryptoTransferTransactionBody)
                                .build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(
                        impliedTransfersMarshal.assessCustomFeesAndValidate(
                                anyInt(), anyInt(), anyInt(), any(), any(), any()))
                .willReturn(impliedTransfers);
        given(impliedTransfers.getAllBalanceChanges()).willReturn(tokenTransferChanges);
        given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
        given(impliedTransfersMeta.code()).willReturn(OK);
        given(wrappedLedgers.nfts()).willReturn(nfts);
        given(nfts.contains(NftId.fromGrpc(token, serialNumber))).willReturn(true);

        ercTransferPrecompile
                .when(
                        () ->
                                ERCTransferPrecompile.decodeERCTransferFrom(
                                        eq(pretendArguments),
                                        eq(null),
                                        eq(false),
                                        any(),
                                        any(),
                                        any(),
                                        any()))
                .thenReturn(CRYPTO_TRANSFER_TOKEN_FROM_NFT_WRAPPER);

        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.aliases()).willReturn(aliases);
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
        when(accessorFactory.uncheckedSpecializedAccessor(any())).thenCallRealMethod();
        when(accessorFactory.constructSpecializedAccessor(any())).thenCallRealMethod();
        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(transferLogic).doZeroSum(tokenTransferChanges);
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
        verify(frame)
                .addLog(
                        EncodingFacade.LogBuilder.logBuilder()
                                .forLogger(EntityIdUtils.asTypedEvmAddress(token))
                                .forEventSignature(AbiConstants.TRANSFER_EVENT)
                                .forIndexedArgument(sender)
                                .forIndexedArgument(receiver)
                                .forIndexedArgument(serialNumber)
                                .build());
    }

    @Test
    void transferFails() throws InvalidProtocolBufferException {
        final Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_TRANSFER));
        final Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
        givenLedgers();
        givenPricingUtilsContext();

        given(frame.getContractAddress()).willReturn(contractAddr);
        given(
                        syntheticTxnFactory.createCryptoTransfer(
                                Collections.singletonList(tokensTransferList)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
        given(impliedTransfersMarshal.validityWithCurrentProps(cryptoTransferTransactionBody))
                .willReturn(OK);
        given(sigsVerifier.hasActiveKey(anyBoolean(), any(), any(), any(), eq(CryptoTransfer)))
                .willReturn(false);
        given(infrastructureFactory.newImpliedTransfersMarshal(any()))
                .willReturn(impliedTransfersMarshal);
        given(infrastructureFactory.newHederaTokenStore(sideEffects, tokens, nfts, tokenRels))
                .willReturn(hederaTokenStore);

        given(
                        creator.createUnsuccessfulSyntheticRecord(
                                INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE))
                .willReturn(mockRecordBuilder);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build())
                .willReturn(
                        TransactionBody.newBuilder()
                                .setCryptoTransfer(cryptoTransferTransactionBody)
                                .build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(
                        impliedTransfersMarshal.assessCustomFeesAndValidate(
                                anyInt(), anyInt(), anyInt(), any(), any(), any()))
                .willReturn(impliedTransfers);
        given(impliedTransfers.getAllBalanceChanges()).willReturn(tokenTransferChanges);
        given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
        given(impliedTransfersMeta.code()).willReturn(OK);
        ercTransferPrecompile
                .when(
                        () ->
                                ERCTransferPrecompile.decodeERCTransfer(
                                        eq(nestedPretendArguments), any(), any(), any(), any()))
                .thenReturn(CRYPTO_TRANSFER_FUNGIBLE_WRAPPER);

        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.aliases()).willReturn(aliases);
        given(wrappedLedgers.typeOf(token)).willReturn(TokenType.FUNGIBLE_COMMON);
        when(accessorFactory.uncheckedSpecializedAccessor(any())).thenCallRealMethod();
        when(accessorFactory.constructSpecializedAccessor(any())).thenCallRealMethod();
        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(invalidFullPrefix, result);
    }

    @Test
    void onlyFallsBackToApprovalWithoutTopLevelSigs() throws InvalidProtocolBufferException {
        final Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_TRANSFER));
        final Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
        given(dynamicProperties.maxNumWithHapiSigsAccess()).willReturn(Long.MAX_VALUE);
        given(dynamicProperties.systemContractsWithTopLevelSigsAccess())
                .willReturn(Set.of(CryptoTransfer));
        givenLedgers();
        givenPricingUtilsContext();

        given(frame.getContractAddress()).willReturn(contractAddr);
        given(
                        syntheticTxnFactory.createCryptoTransfer(
                                Collections.singletonList(tokensTransferList)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
        given(impliedTransfersMarshal.validityWithCurrentProps(cryptoTransferTransactionBody))
                .willReturn(OK);
        given(sigsVerifier.hasActiveKey(anyBoolean(), any(), any(), any(), eq(CryptoTransfer)))
                .willReturn(false);
        given(infrastructureFactory.newImpliedTransfersMarshal(any()))
                .willReturn(impliedTransfersMarshal);
        given(infrastructureFactory.newHederaTokenStore(sideEffects, tokens, nfts, tokenRels))
                .willReturn(hederaTokenStore);

        given(
                        creator.createUnsuccessfulSyntheticRecord(
                                INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE))
                .willReturn(mockRecordBuilder);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build())
                .willReturn(
                        TransactionBody.newBuilder()
                                .setCryptoTransfer(cryptoTransferTransactionBody)
                                .build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(
                        impliedTransfersMarshal.assessCustomFeesAndValidate(
                                anyInt(), anyInt(), anyInt(), any(), any(), any()))
                .willReturn(impliedTransfers);
        given(impliedTransfers.getAllBalanceChanges()).willReturn(tokenTransferChanges);
        given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
        given(impliedTransfersMeta.code()).willReturn(OK);
        ercTransferPrecompile
                .when(
                        () ->
                                ERCTransferPrecompile.decodeERCTransfer(
                                        eq(nestedPretendArguments), any(), any(), any(), any()))
                .thenReturn(CRYPTO_TRANSFER_FUNGIBLE_WRAPPER);

        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.aliases()).willReturn(aliases);
        given(wrappedLedgers.typeOf(token)).willReturn(TokenType.FUNGIBLE_COMMON);
        when(accessorFactory.uncheckedSpecializedAccessor(any())).thenCallRealMethod();
        when(accessorFactory.constructSpecializedAccessor(any())).thenCallRealMethod();
        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(invalidFullPrefix, result);
    }

    @Test
    void withoutTopLevelSigsOrActiveSigFallsBackToApproval() throws InvalidProtocolBufferException {
        final var pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_FROM_NFT));
        givenMinimalFrameContext(Bytes.EMPTY);
        givenLedgers();
        givenPricingUtilsContext();

        given(frame.getContractAddress()).willReturn(contractAddr);
        given(
                        syntheticTxnFactory.createCryptoTransfer(
                                Collections.singletonList(TOKEN_TRANSFER_FROM_NFT_WRAPPER)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
        given(impliedTransfersMarshal.validityWithCurrentProps(cryptoTransferTransactionBody))
                .willReturn(OK);
        given(sigsVerifier.hasActiveKey(anyBoolean(), any(), any(), any(), eq(CryptoTransfer)))
                .willReturn(false);
        given(
                        sigsVerifier.hasActiveKeyOrNoReceiverSigReq(
                                anyBoolean(), any(), any(), any(), eq(CryptoTransfer)))
                .willReturn(true);
        given(infrastructureFactory.newImpliedTransfersMarshal(any()))
                .willReturn(impliedTransfersMarshal);
        given(infrastructureFactory.newHederaTokenStore(sideEffects, tokens, nfts, tokenRels))
                .willReturn(hederaTokenStore);

        given(
                        infrastructureFactory.newTransferLogic(
                                hederaTokenStore, sideEffects, nfts, accounts, tokenRels))
                .willReturn(transferLogic);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build())
                .willReturn(
                        TransactionBody.newBuilder()
                                .setCryptoTransfer(cryptoTransferTransactionBody)
                                .build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(
                        impliedTransfersMarshal.assessCustomFeesAndValidate(
                                anyInt(), anyInt(), anyInt(), any(), any(), any()))
                .willReturn(impliedTransfers);
        given(impliedTransfers.getAllBalanceChanges()).willReturn(tokenTransferChanges);
        given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
        given(impliedTransfersMeta.code()).willReturn(OK);
        given(wrappedLedgers.nfts()).willReturn(nfts);
        given(nfts.contains(NftId.fromGrpc(token, serialNumber))).willReturn(true);

        ercTransferPrecompile
                .when(
                        () ->
                                ERCTransferPrecompile.decodeERCTransferFrom(
                                        eq(pretendArguments),
                                        eq(null),
                                        eq(false),
                                        any(),
                                        any(),
                                        any(),
                                        any()))
                .thenReturn(CRYPTO_TRANSFER_TOKEN_FROM_NFT_WRAPPER);

        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.aliases()).willReturn(aliases);
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
        when(accessorFactory.uncheckedSpecializedAccessor(any())).thenCallRealMethod();
        when(accessorFactory.constructSpecializedAccessor(any())).thenCallRealMethod();
        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        ((TransferPrecompile) subject.getPrecompile()).setCanFallbackToApprovals(true);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(transferLogic).doZeroSum(tokenTransferChanges);
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
        verify(frame)
                .addLog(
                        EncodingFacade.LogBuilder.logBuilder()
                                .forLogger(EntityIdUtils.asTypedEvmAddress(token))
                                .forEventSignature(AbiConstants.TRANSFER_EVENT)
                                .forIndexedArgument(sender)
                                .forIndexedArgument(receiver)
                                .forIndexedArgument(serialNumber)
                                .build());
    }

    @Test
    void ownerOfNotSupported() {
        final Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_OWNER_OF_NFT));
        final Bytes pretendArguments =
                givenMinimalFrameContextWithoutParentUpdater(nestedPretendArguments);

        given(wrappedLedgers.typeOf(token)).willReturn(TokenType.FUNGIBLE_COMMON);
        subject.prepareFields(frame);

        final var exception =
                assertThrows(
                        InvalidTransactionException.class,
                        () -> subject.prepareComputation(pretendArguments, a -> a));
        assertEquals(NOT_SUPPORTED_FUNGIBLE_OPERATION_REASON, exception.getMessage());
    }

    @Test
    void tokenURINotSupported() {
        final Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_TOKEN_URI_NFT));
        final Bytes pretendArguments =
                givenMinimalFrameContextWithoutParentUpdater(nestedPretendArguments);

        given(wrappedLedgers.typeOf(token)).willReturn(TokenType.FUNGIBLE_COMMON);
        subject.prepareFields(frame);

        final var exception =
                assertThrows(
                        InvalidTransactionException.class,
                        () -> subject.prepareComputation(pretendArguments, a -> a));
        assertEquals(NOT_SUPPORTED_FUNGIBLE_OPERATION_REASON, exception.getMessage());
    }

    private Bytes givenMinimalFrameContext(final Bytes nestedArg) {
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getRemainingGas()).willReturn(300L);
        given(frame.getValue()).willReturn(Wei.ZERO);
        final Optional<WorldUpdater> parent = Optional.of(worldUpdater);
        given(worldUpdater.parentUpdater()).willReturn(parent);
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
        return Bytes.concatenate(
                Bytes.of(Integers.toBytes(ABI_ID_REDIRECT_FOR_TOKEN)),
                fungibleTokenAddr,
                nestedArg);
    }

    private Bytes givenMinimalFrameContextWithoutParentUpdater(final Bytes nestedArg) {
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
        return Bytes.concatenate(
                Bytes.of(Integers.toBytes(ABI_ID_REDIRECT_FOR_TOKEN)),
                fungibleTokenAddr,
                nestedArg);
    }

    private void givenLedgers() {
        given(wrappedLedgers.accounts()).willReturn(accounts);
        given(wrappedLedgers.tokenRels()).willReturn(tokenRels);
        given(wrappedLedgers.nfts()).willReturn(nfts);
        given(wrappedLedgers.tokens()).willReturn(tokens);
    }

    private void givenPricingUtilsContext() {
        given(exchange.rate(any())).willReturn(exchangeRate);
        given(exchangeRate.getCentEquiv()).willReturn(CENTS_RATE);
        given(exchangeRate.getHbarEquiv()).willReturn(HBAR_RATE);
    }

    public static final BalanceOfWrapper<AccountID> BALANCE_OF_WRAPPER =
            new BalanceOfWrapper<>(sender);

    public static final TokenAllowanceWrapper<TokenID, AccountID, AccountID> ALLOWANCE_WRAPPER =
            new TokenAllowanceWrapper<>(token, sender, receiver);

    public static final TokenTransferWrapper TOKEN_TRANSFER_FROM_WRAPPER =
            new TokenTransferWrapper(
                    new ArrayList<>() {},
                    List.of(
                            new SyntheticTxnFactory.FungibleTokenTransfer(
                                    AMOUNT, true, token, null, receiver),
                            new SyntheticTxnFactory.FungibleTokenTransfer(
                                    -AMOUNT, true, token, sender, null)));

    public static final CryptoTransferWrapper CRYPTO_TRANSFER_TOKEN_FROM_WRAPPER =
            new CryptoTransferWrapper(
                    new TransferWrapper(Collections.emptyList()),
                    Collections.singletonList(TOKEN_TRANSFER_FROM_WRAPPER));
    public static final TokenTransferWrapper TOKEN_TRANSFER_FROM_NFT_WRAPPER =
            new TokenTransferWrapper(
                    List.of(
                            SyntheticTxnFactory.NftExchange.fromApproval(
                                    serialNumber, token, sender, receiver)),
                    new ArrayList<>() {});
    public static final CryptoTransferWrapper CRYPTO_TRANSFER_TOKEN_FROM_NFT_WRAPPER =
            new CryptoTransferWrapper(
                    new TransferWrapper(Collections.emptyList()),
                    Collections.singletonList(TOKEN_TRANSFER_FROM_NFT_WRAPPER));

    public static final ApproveWrapper APPROVE_WRAPPER =
            new ApproveWrapper(token, receiver, BigInteger.ONE, BigInteger.ZERO, true);

    public static final ApproveWrapper APPROVE_NFT_WRAPPER =
            new ApproveWrapper(token, receiver, BigInteger.ONE, BigInteger.ZERO, false);

    private void givenIfDelegateCall() {
        given(frame.getContractAddress()).willReturn(contractAddress);
        given(frame.getRecipientAddress()).willReturn(recipientAddress);
        given(worldUpdater.get(recipientAddress)).willReturn(acc);
        given(acc.getNonce()).willReturn(-1L);
        given(frame.getMessageFrameStack()).willReturn(stack);
        given(frame.getMessageFrameStack().iterator()).willReturn(dequeIterator);
    }
}
