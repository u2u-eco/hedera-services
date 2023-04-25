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
package com.hedera.node.app.service.mono.store.contracts.precompile;

import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.getTinybarsFromTinyCents;
import static com.hedera.node.app.service.mono.state.EntityCreator.EMPTY_MEMO;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_CRYPTO_TRANSFER;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_CRYPTO_TRANSFER_V2;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_TRANSFER_NFT;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_TRANSFER_NFTS;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_TRANSFER_TOKEN;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_TRANSFER_TOKENS;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.CRYPTO_TRANSFER_EMPTY_WRAPPER;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.CRYPTO_TRANSFER_FUNGIBLE_WRAPPER;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.CRYPTO_TRANSFER_FUNGIBLE_WRAPPER_2_ALIASES;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.CRYPTO_TRANSFER_HBAR_FUNGIBLE_NFT_WRAPPER;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.CRYPTO_TRANSFER_HBAR_FUNGIBLE_WRAPPER;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.CRYPTO_TRANSFER_HBAR_NFT_WRAPPER;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.CRYPTO_TRANSFER_HBAR_ONLY_WRAPPER;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.CRYPTO_TRANSFER_HBAR_ONLY_WRAPPER_ALIASED;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.CRYPTO_TRANSFER_NFTS_WRAPPER;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.CRYPTO_TRANSFER_NFTS_WRAPPER_ALIAS_RECEIVER;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.CRYPTO_TRANSFER_NFT_WRAPPER;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.CRYPTO_TRANSFER_RECEIVER_WRAPPER;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.CRYPTO_TRANSFER_SENDER_WRAPPER;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.CRYPTO_TRANSFER_TOKEN_WRAPPER;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.CRYPTO_TRANSFER_TWO_HBAR_ONLY_WRAPPER;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.DEFAULT_GAS_PRICE;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.TEST_CONSENSUS_TIME;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.balanceChangesForLazyCreateFailing;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.balanceChangesForLazyCreateHappyPath;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.contractAddr;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.contractAddress;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.feeCollector;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.hbarAndNftsTransferChanges;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.hbarAndTokenChanges;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.hbarOnlyChanges;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.hbarOnlyChangesAliased;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.nftTransferChanges;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.nftTransferChangesWithCustomFeesForRoyalty;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.nftTransferChangesWithCustomFeesThatAreAlsoApproved;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.nftTransferList;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.nftsTransferChanges;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.nftsTransferList;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.nftsTransferListAliasReceiver;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.receiver;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.recipientAddress;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.sender;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.successResult;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.timestamp;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.tokenTransferChanges;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.tokensTransferChanges;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.tokensTransferChangesAliased2x;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.tokensTransferChangesSenderOnly;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.tokensTransferList;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.tokensTransferListAliasedX2;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.tokensTransferListReceiverOnly;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.tokensTransferListSenderOnly;
import static com.hedera.node.app.service.mono.store.contracts.precompile.impl.TransferPrecompile.addNftExchanges;
import static com.hedera.node.app.service.mono.store.contracts.precompile.impl.TransferPrecompile.addSignedAdjustments;
import static com.hedera.node.app.service.mono.store.contracts.precompile.impl.TransferPrecompile.decodeCryptoTransfer;
import static com.hedera.node.app.service.mono.store.contracts.precompile.impl.TransferPrecompile.decodeCryptoTransferV2;
import static com.hedera.node.app.service.mono.store.contracts.precompile.impl.TransferPrecompile.decodeHbarTransfers;
import static com.hedera.node.app.service.mono.store.contracts.precompile.impl.TransferPrecompile.decodeTokenTransfer;
import static com.hedera.node.app.service.mono.store.contracts.precompile.impl.TransferPrecompile.decodeTransferNFT;
import static com.hedera.node.app.service.mono.store.contracts.precompile.impl.TransferPrecompile.decodeTransferNFTs;
import static com.hedera.node.app.service.mono.store.contracts.precompile.impl.TransferPrecompile.decodeTransferToken;
import static com.hedera.node.app.service.mono.store.contracts.precompile.impl.TransferPrecompile.decodeTransferTokens;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_CHARGING_EXCEEDED_MAX_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALIAS_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN;
import static java.util.function.UnaryOperator.identity;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.esaulpaugh.headlong.util.Integers;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.node.app.hapi.fees.pricing.AssetsLoader;
import com.hedera.node.app.hapi.utils.ByteStringUtils;
import com.hedera.node.app.hapi.utils.fee.FeeBuilder;
import com.hedera.node.app.hapi.utils.fee.FeeObject;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmHTSPrecompiledContract;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmEncodingFacade;
import com.hedera.node.app.service.mono.context.SideEffectsTracker;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.contracts.sources.TxnAwareEvmSigsVerifier;
import com.hedera.node.app.service.mono.exceptions.ResourceLimitException;
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
import com.hedera.node.app.service.mono.records.RecordSubmissions;
import com.hedera.node.app.service.mono.records.RecordsHistorian;
import com.hedera.node.app.service.mono.state.expiry.ExpiringCreations;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.state.migration.HederaTokenRel;
import com.hedera.node.app.service.mono.state.migration.UniqueTokenAdapter;
import com.hedera.node.app.service.mono.state.submerkle.EvmFnResult;
import com.hedera.node.app.service.mono.state.submerkle.ExpirableTxnRecord;
import com.hedera.node.app.service.mono.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.node.app.service.mono.store.contracts.WorldLedgers;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.TransferPrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.node.app.service.mono.store.models.Id;
import com.hedera.node.app.service.mono.store.models.NftId;
import com.hedera.node.app.service.mono.store.tokens.HederaTokenStore;
import com.hedera.node.app.service.mono.txns.crypto.AbstractAutoCreationLogic;
import com.hedera.node.app.service.mono.utils.EntityIdUtils;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.accessors.AccessorFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.io.IOException;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
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
class TransferPrecompilesTest {
    @Mock private Account acc;
    @Mock private Deque<MessageFrame> stack;
    @Mock private Iterator<MessageFrame> dequeIterator;
    @Mock private HederaTokenStore hederaTokenStore;
    @Mock private GlobalDynamicProperties dynamicProperties;
    @Mock private GasCalculator gasCalculator;
    @Mock private MessageFrame frame;

    @Mock(strictness = LENIENT)
    private TxnAwareEvmSigsVerifier sigsVerifier;

    @Mock private RecordsHistorian recordsHistorian;
    @Mock private EncodingFacade encoder;
    @Mock private EvmEncodingFacade evmEncoder;
    @Mock private TransferLogic transferLogic;
    @Mock private SideEffectsTracker sideEffects;
    @Mock private TransactionBody.Builder mockSynthBodyBuilder;
    @Mock private CryptoTransferTransactionBody cryptoTransferTransactionBody;
    @Mock private ExpirableTxnRecord.Builder mockRecordBuilder;
    @Mock private SyntheticTxnFactory syntheticTxnFactory;
    @Mock private HederaStackedWorldStateUpdater worldUpdater;
    @Mock private WorldLedgers wrappedLedgers;
    @Mock private TransactionalLedger<NftId, NftProperty, UniqueTokenAdapter> nfts;
    @Mock private AccessorFactory accessorFactory;
    @Mock private AbstractAutoCreationLogic autoCreationLogic;

    @Mock
    private TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, HederaTokenRel>
            tokenRels;

    @Mock private TransactionalLedger<AccountID, AccountProperty, HederaAccount> accounts;
    @Mock private TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokens;
    @Mock private ExpiringCreations creator;
    @Mock private ImpliedTransfersMarshal impliedTransfersMarshal;
    @Mock private ImpliedTransfers impliedTransfers;
    @Mock private ImpliedTransfersMeta impliedTransfersMeta;
    @Mock private FeeCalculator feeCalculator;
    @Mock private FeeObject mockFeeObject;
    @Mock private StateView stateView;
    @Mock private ContractAliases aliases;
    @Mock private UsagePricesProvider resourceCosts;
    @Mock private InfrastructureFactory infrastructureFactory;
    @Mock private AssetsLoader assetLoader;
    @Mock private HbarCentExchange exchange;
    @Mock private ExchangeRate exchangeRate;
    @Mock private EvmHTSPrecompiledContract evmHTSPrecompiledContract;

    private static final long TEST_SERVICE_FEE = 5_000_000;
    private static final long TEST_NETWORK_FEE = 400_000;
    private static final long TEST_NODE_FEE = 300_000;
    private static final int CENTS_RATE = 12;
    private static final int HBAR_RATE = 1;
    private static final long EXPECTED_GAS_PRICE =
            (TEST_SERVICE_FEE + TEST_NETWORK_FEE + TEST_NODE_FEE) / DEFAULT_GAS_PRICE * 6 / 5;
    private static final long TEST_CRYPTO_TRANSFER_MIN_FEE = 1_000_000;
    private static final Bytes CRYPTO_TRANSFER_HBAR_ONLY_INPUT =
            Bytes.fromHexString(
                    "0x0e71804f00000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000140000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000a00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000002fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff600000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000");
    private static final Bytes CRYPTO_TRANSFER_FUNGIBLE_INPUT =
            Bytes.fromHexString(
                    "0x0e71804f00000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000080000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000030000000000000000000000000000000000000000000000000000000000000060000000000000000000000000000000000000000000000000000000000000014000000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000a00000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000005fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff600000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000");
    private static final Bytes CRYPTO_TRANSFER_NFT_INPUT =
            Bytes.fromHexString(
                    "0x0e71804f000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000800000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000016000000000000000000000000000000000000000000000000000000000000000060000000000000000000000000000000000000000000000000000000000000060000000000000000000000000000000000000000000000000000000000000008000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000700000000000000000000000000000000000000000000000000000000000000080000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000000090000000000000000000000000000000000000000000000000000000000000060000000000000000000000000000000000000000000000000000000000000008000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000b00000000000000000000000000000000000000000000000000000000000000030000000000000000000000000000000000000000000000000000000000000000");
    private static final Bytes CRYPTO_TRANSFER_HBAR_FUNGIBLE_INPUT =
            Bytes.fromHexString(
                    "0x0e71804f00000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000140000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000a00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000002fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff600000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000030000000000000000000000000000000000000000000000000000000000000060000000000000000000000000000000000000000000000000000000000000014000000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000a00000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000005fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff600000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000");
    private static final Bytes CRYPTO_TRANSFER_HBAR_NFT_INPUT =
            Bytes.fromHexString(
                    "0x0e71804f00000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000140000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000a00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000002fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff6000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000016000000000000000000000000000000000000000000000000000000000000000060000000000000000000000000000000000000000000000000000000000000060000000000000000000000000000000000000000000000000000000000000008000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000700000000000000000000000000000000000000000000000000000000000000080000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000000090000000000000000000000000000000000000000000000000000000000000060000000000000000000000000000000000000000000000000000000000000008000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000b00000000000000000000000000000000000000000000000000000000000000030000000000000000000000000000000000000000000000000000000000000000");
    private static final Bytes POSITIVE_FUNGIBLE_AMOUNT_AND_NFT_TRANSFER_CRYPTO_TRANSFER_INPUT =
            Bytes.fromHexString(
                    "0x189a554c00000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000004a4000000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000000c0000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000004a1000000000000000000000000000000000000000000000000000000000000002b000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000004a100000000000000000000000000000000000000000000000000000000000004a10000000000000000000000000000000000000000000000000000000000000048");
    private static final Bytes POSITIVE_FUNGIBLE_AMOUNT_AND_NFT_TRANSFER_CRYPTO_TRANSFER_INPUT_V2 =
            Bytes.fromHexString(
                    "0x0e71804f00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000004a4000000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000000c0000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000004a1000000000000000000000000000000000000000000000000000000000000002b000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000004a100000000000000000000000000000000000000000000000000000000000004a10000000000000000000000000000000000000000000000000000000000000048");
    private static final Bytes NEGATIVE_FUNGIBLE_AMOUNT_CRYPTO_TRANSFER_INPUT =
            Bytes.fromHexString(
                    "0x189a554c00000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000004c0000000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000000c0000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000004bdffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffce0000000000000000000000000000000000000000000000000000000000000000");
    private static final Bytes TRANSFER_TOKEN_INPUT =
            Bytes.fromHexString(
                    "0xeca3691700000000000000000000000000000000000000000000000000000000000004380000000000000000000000000000000000000000000000000000000000000435000000000000000000000000000000000000000000000000000000000000043a0000000000000000000000000000000000000000000000000000000000000014");
    private static final Bytes POSITIVE_AMOUNTS_TRANSFER_TOKENS_INPUT =
            Bytes.fromHexString(
                    "0x82bba4930000000000000000000000000000000000000000000000000000000000000444000000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000000c00000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000044100000000000000000000000000000000000000000000000000000000000004410000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000a0000000000000000000000000000000000000000000000000000000000000014");
    private static final Bytes POSITIVE_NEGATIVE_AMOUNT_TRANSFER_TOKENS_INPUT =
            Bytes.fromHexString(
                    "0x82bba49300000000000000000000000000000000000000000000000000000000000004d8000000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000000c0000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000004d500000000000000000000000000000000000000000000000000000000000004d500000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000014ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffec");
    private static final Bytes TRANSFER_NFT_INPUT =
            Bytes.fromHexString(
                    "0x5cfc901100000000000000000000000000000000000000000000000000000000000004680000000000000000000000000000000000000000000000000000000000000465000000000000000000000000000000000000000000000000000000000000046a0000000000000000000000000000000000000000000000000000000000000065");
    private static final Bytes TRANSFER_NFTS_INPUT =
            Bytes.fromHexString(
                    "0x2c4ba191000000000000000000000000000000000000000000000000000000000000047a000000000000000000000000000000000000000000000000000000000000008000000000000000000000000000000000000000000000000000000000000000e000000000000000000000000000000000000000000000000000000000000001400000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000047700000000000000000000000000000000000000000000000000000000000004770000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000047c000000000000000000000000000000000000000000000000000000000000047c0000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000007b00000000000000000000000000000000000000000000000000000000000000ea");

    private HTSPrecompiledContract subject;
    private MockedStatic<TransferPrecompile> transferPrecompile;
    final Predicate<AccountID> accoundIdExists = acc -> true;

    @BeforeEach
    void setUp() {
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

        transferPrecompile = Mockito.mockStatic(TransferPrecompile.class);
    }

    @AfterEach
    void closeMocks() {
        transferPrecompile.close();
    }

    @Test
    void transferFailsFastGivenWrongSyntheticValidity() {
        givenPricingUtilsContext();
        final Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_TOKENS));
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(infrastructureFactory.newImpliedTransfersMarshal(any()))
                .willReturn(impliedTransfersMarshal);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getRemainingGas()).willReturn(300L);
        final Optional<WorldUpdater> parent = Optional.of(worldUpdater);
        given(worldUpdater.parentUpdater()).willReturn(parent);
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
        given(wrappedLedgers.accounts()).willReturn(accounts);

        given(
                        syntheticTxnFactory.createCryptoTransfer(
                                Collections.singletonList(tokensTransferList)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
        transferPrecompile
                .when(() -> decodeTransferTokens(eq(pretendArguments), any(), any()))
                .thenReturn(CRYPTO_TRANSFER_FUNGIBLE_WRAPPER);
        given(impliedTransfersMarshal.validityWithCurrentProps(cryptoTransferTransactionBody))
                .willReturn(TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN);

        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build()).willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(creator.createUnsuccessfulSyntheticRecord(TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN))
                .willReturn(mockRecordBuilder);
        given(dynamicProperties.shouldExportPrecompileResults()).willReturn(true);
        given(frame.getRemainingGas()).willReturn(100L);
        given(frame.getValue()).willReturn(Wei.ZERO);
        given(frame.getInputData()).willReturn(pretendArguments);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(
                UInt256.valueOf(ResponseCodeEnum.TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN_VALUE), result);
        final ArgumentCaptor<EvmFnResult> captor = ArgumentCaptor.forClass(EvmFnResult.class);
        verify(mockRecordBuilder).setContractCallResult(captor.capture());
        assertEquals(TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN.name(), captor.getValue().getError());
        assertEquals(100L, captor.getValue().getGas());
        assertEquals(0L, captor.getValue().getAmount());
        assertEquals(pretendArguments.toArrayUnsafe(), captor.getValue().getFunctionParameters());
    }

    @Test
    void transferTokenHappyPathWorks() throws InvalidProtocolBufferException {
        final Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_TOKENS));
        givenMinimalFrameContext();
        givenLedgers();
        givenPricingUtilsContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(infrastructureFactory.newImpliedTransfersMarshal(any()))
                .willReturn(impliedTransfersMarshal);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        given(
                        syntheticTxnFactory.createCryptoTransfer(
                                Collections.singletonList(tokensTransferList)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
        given(impliedTransfersMarshal.validityWithCurrentProps(cryptoTransferTransactionBody))
                .willReturn(OK);
        given(
                        sigsVerifier.hasActiveKey(
                                Mockito.anyBoolean(), any(), any(), any(), eq(CryptoTransfer)))
                .willReturn(true);
        given(
                        sigsVerifier.hasActiveKeyOrNoReceiverSigReq(
                                Mockito.anyBoolean(), any(), any(), any(), eq(CryptoTransfer)))
                .willReturn(true);
        transferPrecompile
                .when(() -> decodeTransferTokens(eq(pretendArguments), any(), any()))
                .thenReturn(CRYPTO_TRANSFER_FUNGIBLE_WRAPPER);

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
        given(impliedTransfers.getAllBalanceChanges()).willReturn(tokensTransferChanges);
        given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
        given(impliedTransfersMeta.code()).willReturn(OK);
        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.aliases()).willReturn(aliases);
        given(frame.getSenderAddress()).willReturn(contractAddress);
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
        verify(transferLogic).doZeroSum(tokensTransferChanges);
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void abortsIfImpliedCustomFeesCannotBeAssessed() throws InvalidProtocolBufferException {
        givenPricingUtilsContext();
        final Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_TOKENS));
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(infrastructureFactory.newImpliedTransfersMarshal(any()))
                .willReturn(impliedTransfersMarshal);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getValue()).willReturn(Wei.ZERO);
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getRemainingGas()).willReturn(300L);
        final Optional<WorldUpdater> parent = Optional.of(worldUpdater);
        given(worldUpdater.parentUpdater()).willReturn(parent);
        given(wrappedLedgers.accounts()).willReturn(accounts);

        given(
                        syntheticTxnFactory.createCryptoTransfer(
                                Collections.singletonList(tokensTransferList)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
        given(impliedTransfersMarshal.validityWithCurrentProps(cryptoTransferTransactionBody))
                .willReturn(OK);
        transferPrecompile
                .when(() -> decodeTransferTokens(eq(pretendArguments), any(), any()))
                .thenReturn(CRYPTO_TRANSFER_FUNGIBLE_WRAPPER);

        given(
                        impliedTransfersMarshal.assessCustomFeesAndValidate(
                                anyInt(), anyInt(), anyInt(), any(), any(), any()))
                .willReturn(impliedTransfers);
        given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
        given(impliedTransfersMeta.code())
                .willReturn(CUSTOM_FEE_CHARGING_EXCEEDED_MAX_ACCOUNT_AMOUNTS);
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
                        creator.createUnsuccessfulSyntheticRecord(
                                CUSTOM_FEE_CHARGING_EXCEEDED_MAX_ACCOUNT_AMOUNTS))
                .willReturn(mockRecordBuilder);
        when(accessorFactory.uncheckedSpecializedAccessor(any())).thenCallRealMethod();
        when(accessorFactory.constructSpecializedAccessor(any())).thenCallRealMethod();

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);
        final var statusResult =
                UInt256.valueOf(CUSTOM_FEE_CHARGING_EXCEEDED_MAX_ACCOUNT_AMOUNTS.getNumber());
        assertEquals(statusResult, result);
    }

    @Test
    void transferTokenWithSenderOnlyHappyPathWorks() throws InvalidProtocolBufferException {
        final Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_TOKENS));

        givenMinimalFrameContext();
        givenLedgers();
        givenPricingUtilsContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(infrastructureFactory.newImpliedTransfersMarshal(any()))
                .willReturn(impliedTransfersMarshal);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        given(
                        syntheticTxnFactory.createCryptoTransfer(
                                Collections.singletonList(tokensTransferListSenderOnly)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
        given(
                        sigsVerifier.hasActiveKeyOrNoReceiverSigReq(
                                Mockito.anyBoolean(), any(), any(), any(), eq(CryptoTransfer)))
                .willReturn(true);
        transferPrecompile
                .when(() -> decodeTransferTokens(eq(pretendArguments), any(), any()))
                .thenReturn(CRYPTO_TRANSFER_SENDER_WRAPPER);
        given(impliedTransfersMarshal.validityWithCurrentProps(cryptoTransferTransactionBody))
                .willReturn(OK);

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
        given(impliedTransfers.getAllBalanceChanges()).willReturn(tokensTransferChangesSenderOnly);
        given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
        given(impliedTransfersMeta.code()).willReturn(OK);
        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.aliases()).willReturn(aliases);
        given(frame.getSenderAddress()).willReturn(contractAddress);
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
        verify(transferLogic).doZeroSum(tokensTransferChangesSenderOnly);
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void transferTokenWithReceiverOnlyHappyPathWorks() throws InvalidProtocolBufferException {
        final Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_TOKENS));

        givenMinimalFrameContext();
        givenLedgers();
        givenPricingUtilsContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(infrastructureFactory.newImpliedTransfersMarshal(any()))
                .willReturn(impliedTransfersMarshal);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(
                        syntheticTxnFactory.createCryptoTransfer(
                                Collections.singletonList(tokensTransferListReceiverOnly)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
        given(
                        sigsVerifier.hasActiveKeyOrNoReceiverSigReq(
                                Mockito.anyBoolean(), any(), any(), any(), eq(CryptoTransfer)))
                .willReturn(true);
        transferPrecompile
                .when(() -> decodeTransferTokens(eq(pretendArguments), any(), any()))
                .thenReturn(CRYPTO_TRANSFER_RECEIVER_WRAPPER);
        given(impliedTransfersMarshal.validityWithCurrentProps(cryptoTransferTransactionBody))
                .willReturn(OK);

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
        given(impliedTransfers.getAllBalanceChanges()).willReturn(tokensTransferChangesSenderOnly);
        given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
        given(impliedTransfersMeta.code()).willReturn(OK);
        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.aliases()).willReturn(aliases);
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
        verify(transferLogic).doZeroSum(tokensTransferChangesSenderOnly);
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void transferNftsHappyPathWorks() throws InvalidProtocolBufferException {
        final Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_NFTS));

        givenMinimalFrameContext();
        givenLedgers();
        givenPricingUtilsContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(infrastructureFactory.newImpliedTransfersMarshal(any()))
                .willReturn(impliedTransfersMarshal);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        given(syntheticTxnFactory.createCryptoTransfer(Collections.singletonList(nftsTransferList)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
        given(
                        sigsVerifier.hasActiveKey(
                                Mockito.anyBoolean(), any(), any(), any(), eq(CryptoTransfer)))
                .willReturn(true);
        given(
                        sigsVerifier.hasActiveKeyOrNoReceiverSigReq(
                                Mockito.anyBoolean(), any(), any(), any(), eq(CryptoTransfer)))
                .willReturn(true);
        transferPrecompile
                .when(() -> decodeTransferNFTs(eq(pretendArguments), any(), any()))
                .thenReturn(CRYPTO_TRANSFER_NFTS_WRAPPER);
        given(impliedTransfersMarshal.validityWithCurrentProps(cryptoTransferTransactionBody))
                .willReturn(OK);

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
        given(impliedTransfers.getAllBalanceChanges()).willReturn(nftsTransferChanges);
        given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
        given(impliedTransfersMeta.code()).willReturn(OK);
        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.aliases()).willReturn(aliases);
        given(frame.getSenderAddress()).willReturn(contractAddress);
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
        verify(transferLogic).doZeroSum(nftsTransferChanges);
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void transferNftsHappyPathWithRoyaltyFeeWorks() throws InvalidProtocolBufferException {
        final Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_NFTS));

        final var recipientAddr = Address.ALTBN128_ADD;
        final var senderId = Id.fromGrpcAccount(sender);
        final var receiverId = Id.fromGrpcAccount(receiver);

        givenMinimalFrameContext();
        givenLedgers();
        givenPricingUtilsContext();
        given(frame.getRecipientAddress()).willReturn(recipientAddr);
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(infrastructureFactory.newImpliedTransfersMarshal(any()))
                .willReturn(impliedTransfersMarshal);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        given(syntheticTxnFactory.createCryptoTransfer(Collections.singletonList(nftsTransferList)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
        given(
                        sigsVerifier.hasActiveKey(
                                Mockito.anyBoolean(), any(), any(), any(), eq(CryptoTransfer)))
                .willReturn(true);
        given(
                        sigsVerifier.hasActiveKeyOrNoReceiverSigReq(
                                Mockito.anyBoolean(), any(), any(), any(), eq(CryptoTransfer)))
                .willReturn(true);
        transferPrecompile
                .when(() -> decodeTransferNFTs(eq(pretendArguments), any(), any()))
                .thenReturn(CRYPTO_TRANSFER_NFTS_WRAPPER);
        given(impliedTransfersMarshal.validityWithCurrentProps(cryptoTransferTransactionBody))
                .willReturn(OK);

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
        given(impliedTransfers.getAllBalanceChanges())
                .willReturn(nftTransferChangesWithCustomFeesForRoyalty);
        given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
        given(impliedTransfersMeta.code()).willReturn(OK);
        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.aliases()).willReturn(aliases);
        given(frame.getSenderAddress()).willReturn(contractAddress);
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
        verify(transferLogic).doZeroSum(nftTransferChangesWithCustomFeesForRoyalty);
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);

        verify(sigsVerifier)
                .hasActiveKey(
                        true,
                        senderId.asEvmAddress(),
                        recipientAddr,
                        wrappedLedgers,
                        CryptoTransfer);
        verify(sigsVerifier)
                .hasActiveKey(
                        true,
                        receiverId.asEvmAddress(),
                        recipientAddr,
                        wrappedLedgers,
                        CryptoTransfer);
    }

    @Test
    void transferNftHappyPathWorkForCustomFeesWithApproval() throws InvalidProtocolBufferException {
        final Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_NFT));

        final var recipientAddr = Address.ALTBN128_ADD;
        final var senderId = Id.fromGrpcAccount(sender);
        final var receiverId = Id.fromGrpcAccount(receiver);
        givenMinimalFrameContext();
        given(frame.getRecipientAddress()).willReturn(recipientAddr);
        given(frame.getSenderAddress()).willReturn(contractAddress);
        givenLedgers();
        givenPricingUtilsContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(infrastructureFactory.newImpliedTransfersMarshal(any()))
                .willReturn(impliedTransfersMarshal);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        given(syntheticTxnFactory.createCryptoTransfer(Collections.singletonList(nftTransferList)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
        given(
                        sigsVerifier.hasActiveKey(
                                Mockito.anyBoolean(), any(), any(), any(), eq(CryptoTransfer)))
                .willReturn(true);
        given(
                        sigsVerifier.hasActiveKeyOrNoReceiverSigReq(
                                Mockito.anyBoolean(), any(), any(), any(), eq(CryptoTransfer)))
                .willReturn(true);
        transferPrecompile
                .when(() -> decodeTransferNFT(eq(pretendArguments), any(), any()))
                .thenReturn(CRYPTO_TRANSFER_NFT_WRAPPER);

        given(impliedTransfersMarshal.validityWithCurrentProps(cryptoTransferTransactionBody))
                .willReturn(OK);
        given(infrastructureFactory.newHederaTokenStore(sideEffects, tokens, nfts, tokenRels))
                .willReturn(hederaTokenStore);
        given(worldUpdater.aliases()).willReturn(aliases);

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
        given(impliedTransfers.getAllBalanceChanges())
                .willReturn(nftTransferChangesWithCustomFeesThatAreAlsoApproved);
        given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
        given(impliedTransfersMeta.code()).willReturn(OK);
        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.aliases()).willReturn(aliases);
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
        verify(transferLogic).doZeroSum(nftTransferChangesWithCustomFeesThatAreAlsoApproved);
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
        verify(sigsVerifier)
                .hasActiveKey(
                        true,
                        senderId.asEvmAddress(),
                        recipientAddr,
                        wrappedLedgers,
                        CryptoTransfer);
        verify(sigsVerifier)
                .hasActiveKeyOrNoReceiverSigReq(
                        true,
                        receiverId.asEvmAddress(),
                        recipientAddr,
                        wrappedLedgers,
                        CryptoTransfer);
        verify(sigsVerifier, never())
                .hasActiveKeyOrNoReceiverSigReq(
                        true,
                        EntityIdUtils.asTypedEvmAddress(feeCollector),
                        recipientAddr,
                        wrappedLedgers,
                        CryptoTransfer);
    }

    @Test
    void cryptoTransferHappyPathWorks() throws InvalidProtocolBufferException {
        final Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_CRYPTO_TRANSFER));

        givenMinimalFrameContext();
        givenLedgers();
        givenPricingUtilsContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(infrastructureFactory.newImpliedTransfersMarshal(any()))
                .willReturn(impliedTransfersMarshal);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        given(syntheticTxnFactory.createCryptoTransfer(Collections.singletonList(nftTransferList)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
        given(
                        sigsVerifier.hasActiveKey(
                                Mockito.anyBoolean(), any(), any(), any(), eq(CryptoTransfer)))
                .willReturn(true);
        given(
                        sigsVerifier.hasActiveKeyOrNoReceiverSigReq(
                                Mockito.anyBoolean(), any(), any(), any(), eq(CryptoTransfer)))
                .willReturn(true);
        transferPrecompile
                .when(() -> decodeCryptoTransfer(eq(pretendArguments), any(), any()))
                .thenReturn(CRYPTO_TRANSFER_NFT_WRAPPER);
        given(impliedTransfersMarshal.validityWithCurrentProps(cryptoTransferTransactionBody))
                .willReturn(OK);
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
        given(impliedTransfers.getAllBalanceChanges()).willReturn(nftTransferChanges);
        given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
        given(impliedTransfersMeta.code()).willReturn(OK);
        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.aliases()).willReturn(aliases);
        given(frame.getSenderAddress()).willReturn(contractAddress);
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
        verify(transferLogic).doZeroSum(nftTransferChanges);
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void hbarOnlyTransferHappyPathWorks() throws InvalidProtocolBufferException {
        final Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_CRYPTO_TRANSFER_V2));

        givenMinimalFrameContext();
        givenLedgers();
        givenPricingUtilsContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(infrastructureFactory.newImpliedTransfersMarshal(any()))
                .willReturn(impliedTransfersMarshal);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        given(syntheticTxnFactory.createCryptoTransfer(Collections.emptyList()))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
        given(
                        sigsVerifier.hasActiveKey(
                                Mockito.anyBoolean(), any(), any(), any(), eq(CryptoTransfer)))
                .willReturn(true);
        given(
                        sigsVerifier.hasActiveKeyOrNoReceiverSigReq(
                                Mockito.anyBoolean(), any(), any(), any(), eq(CryptoTransfer)))
                .willReturn(true);
        transferPrecompile
                .when(() -> decodeCryptoTransferV2(eq(pretendArguments), any(), any()))
                .thenReturn(CRYPTO_TRANSFER_HBAR_ONLY_WRAPPER);
        given(impliedTransfersMarshal.validityWithCurrentProps(cryptoTransferTransactionBody))
                .willReturn(OK);
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
        given(impliedTransfers.getAllBalanceChanges()).willReturn(hbarOnlyChanges);
        given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
        given(impliedTransfersMeta.code()).willReturn(OK);
        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.aliases()).willReturn(aliases);
        given(frame.getSenderAddress()).willReturn(contractAddress);
        when(accessorFactory.uncheckedSpecializedAccessor(any())).thenCallRealMethod();
        when(accessorFactory.constructSpecializedAccessor(any())).thenCallRealMethod();
        given(dynamicProperties.isAtomicCryptoTransferEnabled()).willReturn(true);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(transferLogic).doZeroSum(hbarOnlyChanges);
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void hbarFungibleTransferHappyPathWorks() throws InvalidProtocolBufferException {
        final Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_CRYPTO_TRANSFER_V2));

        givenMinimalFrameContext();
        givenLedgers();
        givenPricingUtilsContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(infrastructureFactory.newImpliedTransfersMarshal(any()))
                .willReturn(impliedTransfersMarshal);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        given(
                        syntheticTxnFactory.createCryptoTransfer(
                                Collections.singletonList(tokensTransferList)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
        given(
                        sigsVerifier.hasActiveKey(
                                Mockito.anyBoolean(), any(), any(), any(), eq(CryptoTransfer)))
                .willReturn(true);
        given(
                        sigsVerifier.hasActiveKeyOrNoReceiverSigReq(
                                Mockito.anyBoolean(), any(), any(), any(), eq(CryptoTransfer)))
                .willReturn(true);
        transferPrecompile
                .when(() -> decodeCryptoTransferV2(eq(pretendArguments), any(), any()))
                .thenReturn(CRYPTO_TRANSFER_HBAR_FUNGIBLE_WRAPPER);
        given(impliedTransfersMarshal.validityWithCurrentProps(cryptoTransferTransactionBody))
                .willReturn(OK);
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
        given(impliedTransfers.getAllBalanceChanges()).willReturn(hbarAndTokenChanges);
        given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
        given(impliedTransfersMeta.code()).willReturn(OK);
        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.aliases()).willReturn(aliases);
        given(frame.getSenderAddress()).willReturn(contractAddress);
        when(accessorFactory.uncheckedSpecializedAccessor(any())).thenCallRealMethod();
        when(accessorFactory.constructSpecializedAccessor(any())).thenCallRealMethod();
        given(dynamicProperties.isAtomicCryptoTransferEnabled()).willReturn(true);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(transferLogic).doZeroSum(hbarAndTokenChanges);
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void nftTransferToLazyCreateHappyPathWorks() throws InvalidProtocolBufferException {
        Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_CRYPTO_TRANSFER_V2));
        givenMinimalFrameContext();
        givenLedgers();
        given(exchange.rate(any())).willReturn(exchangeRate);
        given(exchangeRate.getCentEquiv()).willReturn(1);
        given(exchangeRate.getHbarEquiv()).willReturn(1);
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(infrastructureFactory.newImpliedTransfersMarshal(any()))
                .willReturn(impliedTransfersMarshal);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(
                        syntheticTxnFactory.createCryptoTransfer(
                                Collections.singletonList(nftsTransferListAliasReceiver)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
        given(
                        sigsVerifier.hasActiveKey(
                                Mockito.anyBoolean(), any(), any(), any(), eq(CryptoTransfer)))
                .willReturn(true);
        given(
                        sigsVerifier.hasActiveKeyOrNoReceiverSigReq(
                                Mockito.anyBoolean(), any(), any(), any(), eq(CryptoTransfer)))
                .willReturn(true);
        transferPrecompile
                .when(() -> decodeCryptoTransferV2(eq(pretendArguments), any(), any()))
                .thenReturn(CRYPTO_TRANSFER_NFTS_WRAPPER_ALIAS_RECEIVER);
        given(impliedTransfersMarshal.validityWithCurrentProps(cryptoTransferTransactionBody))
                .willReturn(OK);
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
        given(impliedTransfers.getAllBalanceChanges())
                .willReturn(balanceChangesForLazyCreateHappyPath);
        given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
        given(impliedTransfersMeta.code()).willReturn(OK);
        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.aliases()).willReturn(aliases);
        given(frame.getSenderAddress()).willReturn(contractAddress);
        when(accessorFactory.uncheckedSpecializedAccessor(any())).thenCallRealMethod();
        when(accessorFactory.constructSpecializedAccessor(any())).thenCallRealMethod();
        given(dynamicProperties.isAtomicCryptoTransferEnabled()).willReturn(true);
        given(dynamicProperties.isImplicitCreationEnabled()).willReturn(true);
        given(infrastructureFactory.newAutoCreationLogicScopedTo(any()))
                .willReturn(autoCreationLogic);
        final var recordSubmissions = mock(RecordSubmissions.class);
        given(infrastructureFactory.newRecordSubmissionsScopedTo(worldUpdater))
                .willReturn(recordSubmissions);
        final var lazyCreationFee = 500L;
        when(autoCreationLogic.create(
                        balanceChangesForLazyCreateHappyPath.get(0),
                        accounts,
                        balanceChangesForLazyCreateHappyPath))
                .then(
                        invocation -> {
                            balanceChangesForLazyCreateHappyPath
                                    .get(0)
                                    .replaceNonEmptyAliasWith(EntityNum.fromAccountId(receiver));
                            return Pair.of(OK, lazyCreationFee);
                        });

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        final long gasRequirement = subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:

        // 2x NFT transfer + lazy create (CryptoCreate + CryptoUpdate)
        final var expected = 4 * 1_000_000L * 10_000_000_000L;
        assertEquals(expected + (expected / 5), gasRequirement);
        verify(autoCreationLogic)
                .create(
                        balanceChangesForLazyCreateHappyPath.get(0),
                        accounts,
                        balanceChangesForLazyCreateHappyPath);
        verify(autoCreationLogic, never())
                .create(
                        balanceChangesForLazyCreateHappyPath.get(1),
                        accounts,
                        balanceChangesForLazyCreateHappyPath);
        verify(autoCreationLogic).submitRecords(recordSubmissions);
        verify(transferLogic).doZeroSum(balanceChangesForLazyCreateHappyPath);
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
        verify(infrastructureFactory).newAutoCreationLogicScopedTo(worldUpdater);
    }

    @Test
    void lazyCreateExceedingResourceLimitThrows() {
        Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_CRYPTO_TRANSFER_V2));
        given(frame.getContractAddress()).willReturn(contractAddr);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getRemainingGas()).willReturn(300L);
        given(frame.getValue()).willReturn(Wei.ZERO);
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
        givenLedgers();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(infrastructureFactory.newImpliedTransfersMarshal(any()))
                .willReturn(impliedTransfersMarshal);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(
                        syntheticTxnFactory.createCryptoTransfer(
                                Collections.singletonList(nftsTransferListAliasReceiver)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
        given(
                        sigsVerifier.hasActiveKey(
                                Mockito.anyBoolean(), any(), any(), any(), eq(CryptoTransfer)))
                .willReturn(true);
        given(
                        sigsVerifier.hasActiveKeyOrNoReceiverSigReq(
                                Mockito.anyBoolean(), any(), any(), any(), eq(CryptoTransfer)))
                .willReturn(true);
        transferPrecompile
                .when(() -> decodeCryptoTransferV2(eq(pretendArguments), any(), any()))
                .thenReturn(CRYPTO_TRANSFER_NFTS_WRAPPER_ALIAS_RECEIVER);
        given(impliedTransfersMarshal.validityWithCurrentProps(cryptoTransferTransactionBody))
                .willReturn(OK);
        given(infrastructureFactory.newHederaTokenStore(sideEffects, tokens, nfts, tokenRels))
                .willReturn(hederaTokenStore);
        given(
                        infrastructureFactory.newTransferLogic(
                                hederaTokenStore, sideEffects, nfts, accounts, tokenRels))
                .willReturn(transferLogic);
        given(
                        impliedTransfersMarshal.assessCustomFeesAndValidate(
                                anyInt(), anyInt(), anyInt(), any(), any(), any()))
                .willReturn(impliedTransfers);
        given(impliedTransfers.getAllBalanceChanges())
                .willReturn(balanceChangesForLazyCreateHappyPath);
        given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
        given(impliedTransfersMeta.code()).willReturn(OK);
        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.aliases()).willReturn(aliases);
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(dynamicProperties.isAtomicCryptoTransferEnabled()).willReturn(true);
        given(dynamicProperties.isImplicitCreationEnabled()).willReturn(true);
        given(infrastructureFactory.newAutoCreationLogicScopedTo(any()))
                .willReturn(autoCreationLogic);
        final var recordSubmissions = mock(RecordSubmissions.class);
        given(infrastructureFactory.newRecordSubmissionsScopedTo(worldUpdater))
                .willReturn(recordSubmissions);
        final var lazyCreationFee = 500L;
        when(autoCreationLogic.create(
                        balanceChangesForLazyCreateHappyPath.get(0),
                        accounts,
                        balanceChangesForLazyCreateHappyPath))
                .then(invocation -> Pair.of(OK, lazyCreationFee));
        doThrow(ResourceLimitException.class)
                .when(autoCreationLogic)
                .submitRecords(recordSubmissions);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);

        assertThrows(ResourceLimitException.class, () -> subject.computeInternal(frame));

        verify(autoCreationLogic)
                .create(
                        balanceChangesForLazyCreateHappyPath.get(0),
                        accounts,
                        balanceChangesForLazyCreateHappyPath);
        verify(autoCreationLogic, never())
                .create(
                        balanceChangesForLazyCreateHappyPath.get(1),
                        accounts,
                        balanceChangesForLazyCreateHappyPath);
        verify(transferLogic, never()).doZeroSum(any());
        verify(wrappedLedgers, never()).commit();
        verify(worldUpdater, never())
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void hbarTransferToLazyCreateHappyPathWorks() throws InvalidProtocolBufferException {
        Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_CRYPTO_TRANSFER_V2));
        givenMinimalFrameContext();
        givenLedgers();
        given(exchange.rate(any())).willReturn(exchangeRate);
        given(exchangeRate.getCentEquiv()).willReturn(1);
        given(exchangeRate.getHbarEquiv()).willReturn(1);
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(infrastructureFactory.newImpliedTransfersMarshal(any()))
                .willReturn(impliedTransfersMarshal);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(syntheticTxnFactory.createCryptoTransfer(Collections.emptyList()))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
        given(
                        sigsVerifier.hasActiveKey(
                                Mockito.anyBoolean(), any(), any(), any(), eq(CryptoTransfer)))
                .willReturn(true);
        given(
                        sigsVerifier.hasActiveKeyOrNoReceiverSigReq(
                                Mockito.anyBoolean(), any(), any(), any(), eq(CryptoTransfer)))
                .willReturn(true);
        transferPrecompile
                .when(() -> decodeCryptoTransferV2(eq(pretendArguments), any(), any()))
                .thenReturn(CRYPTO_TRANSFER_HBAR_ONLY_WRAPPER_ALIASED);
        given(impliedTransfersMarshal.validityWithCurrentProps(cryptoTransferTransactionBody))
                .willReturn(OK);
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
        given(impliedTransfers.getAllBalanceChanges()).willReturn(hbarOnlyChangesAliased);
        given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
        given(impliedTransfersMeta.code()).willReturn(OK);
        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.aliases()).willReturn(aliases);
        given(frame.getSenderAddress()).willReturn(contractAddress);
        when(accessorFactory.uncheckedSpecializedAccessor(any())).thenCallRealMethod();
        when(accessorFactory.constructSpecializedAccessor(any())).thenCallRealMethod();
        given(dynamicProperties.isAtomicCryptoTransferEnabled()).willReturn(true);
        given(dynamicProperties.isImplicitCreationEnabled()).willReturn(true);
        given(infrastructureFactory.newAutoCreationLogicScopedTo(any()))
                .willReturn(autoCreationLogic);
        final var recordSubmissions = mock(RecordSubmissions.class);
        given(infrastructureFactory.newRecordSubmissionsScopedTo(worldUpdater))
                .willReturn(recordSubmissions);
        final var lazyCreationFee = 500L;
        when(autoCreationLogic.create(
                        hbarOnlyChangesAliased.get(0), accounts, hbarOnlyChangesAliased))
                .then(
                        invocation -> {
                            hbarOnlyChangesAliased
                                    .get(0)
                                    .replaceNonEmptyAliasWith(EntityNum.fromAccountId(receiver));
                            return Pair.of(OK, lazyCreationFee);
                        });

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        final long gasRequirement = subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:

        // 2x hbar transfer + lazy create (CryptoCreate + CryptoUpdate)
        final var hbarCost = 5000000000000000L;
        final var defaultFee = 1_000_000L * 10_000_000_000L;
        final var expected = 2 * hbarCost + 2 * defaultFee;
        assertEquals(expected + (expected / 5), gasRequirement);
        verify(autoCreationLogic)
                .create(hbarOnlyChangesAliased.get(0), accounts, hbarOnlyChangesAliased);
        verify(autoCreationLogic, never())
                .create(hbarOnlyChangesAliased.get(1), accounts, hbarOnlyChangesAliased);
        verify(autoCreationLogic).submitRecords(recordSubmissions);
        verify(transferLogic).doZeroSum(hbarOnlyChangesAliased);
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
        verify(infrastructureFactory).newAutoCreationLogicScopedTo(worldUpdater);
    }

    @Test
    void ftTransferRequestingTwoLazyCreatesHappyPathWorks() throws IOException {
        Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_CRYPTO_TRANSFER_V2));
        givenMinimalFrameContext();
        givenLedgers();
        given(exchange.rate(any())).willReturn(exchangeRate);
        given(exchangeRate.getCentEquiv()).willReturn(1);
        given(exchangeRate.getHbarEquiv()).willReturn(1);
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(infrastructureFactory.newImpliedTransfersMarshal(any()))
                .willReturn(impliedTransfersMarshal);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(
                        syntheticTxnFactory.createCryptoTransfer(
                                Collections.singletonList(tokensTransferListAliasedX2)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
        given(
                        sigsVerifier.hasActiveKey(
                                Mockito.anyBoolean(), any(), any(), any(), eq(CryptoTransfer)))
                .willReturn(true);
        given(
                        sigsVerifier.hasActiveKeyOrNoReceiverSigReq(
                                Mockito.anyBoolean(), any(), any(), any(), eq(CryptoTransfer)))
                .willReturn(true);
        transferPrecompile
                .when(() -> decodeCryptoTransferV2(eq(pretendArguments), any(), any()))
                .thenReturn(CRYPTO_TRANSFER_FUNGIBLE_WRAPPER_2_ALIASES);
        given(impliedTransfersMarshal.validityWithCurrentProps(cryptoTransferTransactionBody))
                .willReturn(OK);
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
        given(impliedTransfers.getAllBalanceChanges()).willReturn(tokensTransferChangesAliased2x);
        given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
        given(impliedTransfersMeta.code()).willReturn(OK);
        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.aliases()).willReturn(aliases);
        given(frame.getSenderAddress()).willReturn(contractAddress);
        when(accessorFactory.uncheckedSpecializedAccessor(any())).thenCallRealMethod();
        when(accessorFactory.constructSpecializedAccessor(any())).thenCallRealMethod();
        given(dynamicProperties.isAtomicCryptoTransferEnabled()).willReturn(true);
        given(dynamicProperties.isImplicitCreationEnabled()).willReturn(true);
        given(infrastructureFactory.newAutoCreationLogicScopedTo(any()))
                .willReturn(autoCreationLogic);
        final var lazyCreationFee = 500L;
        given(
                        autoCreationLogic.create(
                                tokensTransferChangesAliased2x.get(1),
                                accounts,
                                tokensTransferChangesAliased2x))
                .willAnswer(
                        invocation -> {
                            tokensTransferChangesAliased2x
                                    .get(1)
                                    .replaceNonEmptyAliasWith(EntityNum.fromAccountId(receiver));
                            return Pair.of(OK, lazyCreationFee);
                        });
        given(
                        autoCreationLogic.create(
                                tokensTransferChangesAliased2x.get(2),
                                accounts,
                                tokensTransferChangesAliased2x))
                .willAnswer(
                        invocation -> {
                            tokensTransferChangesAliased2x
                                    .get(2)
                                    .replaceNonEmptyAliasWith(EntityNum.fromAccountId(receiver));
                            return Pair.of(OK, lazyCreationFee);
                        });

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        final long gasRequirement = subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        // 2x FT transfer + 2x lazy create (CryptoCreate + CryptoUpdate); each is defaultCost
        final long defaultCost = 1_000_000L * 10_000_000_000L;
        final var expected = 6 * defaultCost;
        assertEquals(expected + (expected / 5), gasRequirement);
        verify(autoCreationLogic, times(2)).create(any(), any(), any());
        verify(autoCreationLogic).submitRecords(any());
        verify(transferLogic).doZeroSum(tokensTransferChangesAliased2x);
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void nftTransferToLazyCreateFailsWhenAutoCreationLogicReturnsNonOk()
            throws InvalidProtocolBufferException {
        Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_CRYPTO_TRANSFER_V2));
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getRemainingGas()).willReturn(300L);
        given(frame.getValue()).willReturn(Wei.ZERO);
        Optional<WorldUpdater> parent = Optional.of(worldUpdater);
        given(worldUpdater.parentUpdater()).willReturn(parent);
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
        givenLedgers();
        givenPricingUtilsContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(infrastructureFactory.newImpliedTransfersMarshal(any()))
                .willReturn(impliedTransfersMarshal);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(
                        syntheticTxnFactory.createCryptoTransfer(
                                Collections.singletonList(nftsTransferListAliasReceiver)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
        transferPrecompile
                .when(() -> decodeCryptoTransferV2(eq(pretendArguments), any(), any()))
                .thenReturn(CRYPTO_TRANSFER_NFTS_WRAPPER_ALIAS_RECEIVER);
        given(impliedTransfersMarshal.validityWithCurrentProps(cryptoTransferTransactionBody))
                .willReturn(OK);
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
                        impliedTransfersMarshal.assessCustomFeesAndValidate(
                                anyInt(), anyInt(), anyInt(), any(), any(), any()))
                .willReturn(impliedTransfers);
        given(impliedTransfers.getAllBalanceChanges())
                .willReturn(balanceChangesForLazyCreateFailing);
        given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
        given(impliedTransfersMeta.code()).willReturn(OK);
        given(frame.getSenderAddress()).willReturn(contractAddress);
        when(accessorFactory.uncheckedSpecializedAccessor(any())).thenCallRealMethod();
        when(accessorFactory.constructSpecializedAccessor(any())).thenCallRealMethod();
        given(dynamicProperties.isAtomicCryptoTransferEnabled()).willReturn(true);
        given(dynamicProperties.isImplicitCreationEnabled()).willReturn(true);
        given(infrastructureFactory.newAutoCreationLogicScopedTo(any()))
                .willReturn(autoCreationLogic);
        final var lazyCreationFee = 500L;
        when(autoCreationLogic.create(
                        balanceChangesForLazyCreateFailing.get(0),
                        accounts,
                        balanceChangesForLazyCreateFailing))
                .then(
                        invocation -> {
                            balanceChangesForLazyCreateFailing
                                    .get(0)
                                    .replaceNonEmptyAliasWith(EntityNum.fromAccountId(receiver));
                            return Pair.of(
                                    MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED,
                                    lazyCreationFee);
                        });
        given(
                        creator.createUnsuccessfulSyntheticRecord(
                                ResponseCodeEnum.MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED))
                .willReturn(mockRecordBuilder);
        given(worldUpdater.aliases()).willReturn(aliases);
        given(
                        aliases.isMirror(
                                Address.wrap(
                                        Bytes.of(
                                                balanceChangesForLazyCreateFailing
                                                        .get(0)
                                                        .getNonEmptyAliasIfPresent()
                                                        .toByteArray()))))
                .willReturn(false);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(
                UInt256.valueOf(MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED.getNumber()),
                result);
        // and:
        verify(frame, never()).getGasPrice();
        verify(autoCreationLogic)
                .create(
                        balanceChangesForLazyCreateFailing.get(0),
                        accounts,
                        balanceChangesForLazyCreateFailing);
        verify(autoCreationLogic, never())
                .create(
                        balanceChangesForLazyCreateFailing.get(1),
                        accounts,
                        balanceChangesForLazyCreateFailing);
        verify(worldUpdater, never()).manageInProgressPrecedingRecord(any(), any(), any());
        verify(frame, never()).decrementRemainingGas(anyLong());
        verify(transferLogic, never()).doZeroSum(any());
        verify(wrappedLedgers, never()).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void transferToLazyCreateFailsWhenNonExistingAddressIsMirrorAddress()
            throws InvalidProtocolBufferException {
        Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_CRYPTO_TRANSFER_V2));
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getRemainingGas()).willReturn(300L);
        given(frame.getValue()).willReturn(Wei.ZERO);
        Optional<WorldUpdater> parent = Optional.of(worldUpdater);
        given(worldUpdater.parentUpdater()).willReturn(parent);
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
        givenLedgers();
        givenPricingUtilsContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(infrastructureFactory.newImpliedTransfersMarshal(any()))
                .willReturn(impliedTransfersMarshal);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(
                        syntheticTxnFactory.createCryptoTransfer(
                                Collections.singletonList(nftsTransferListAliasReceiver)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
        transferPrecompile
                .when(() -> decodeCryptoTransferV2(eq(pretendArguments), any(), any()))
                .thenReturn(CRYPTO_TRANSFER_NFTS_WRAPPER_ALIAS_RECEIVER);
        given(impliedTransfersMarshal.validityWithCurrentProps(cryptoTransferTransactionBody))
                .willReturn(OK);
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
                        impliedTransfersMarshal.assessCustomFeesAndValidate(
                                anyInt(), anyInt(), anyInt(), any(), any(), any()))
                .willReturn(impliedTransfers);
        given(impliedTransfers.getAllBalanceChanges())
                .willReturn(balanceChangesForLazyCreateFailing);
        given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
        given(impliedTransfersMeta.code()).willReturn(OK);
        given(frame.getSenderAddress()).willReturn(contractAddress);
        when(accessorFactory.uncheckedSpecializedAccessor(any())).thenCallRealMethod();
        when(accessorFactory.constructSpecializedAccessor(any())).thenCallRealMethod();
        given(dynamicProperties.isAtomicCryptoTransferEnabled()).willReturn(true);
        given(dynamicProperties.isImplicitCreationEnabled()).willReturn(true);
        given(creator.createUnsuccessfulSyntheticRecord(ResponseCodeEnum.INVALID_ALIAS_KEY))
                .willReturn(mockRecordBuilder);
        given(worldUpdater.aliases()).willReturn(aliases);
        given(
                        aliases.isMirror(
                                Address.wrap(
                                        Bytes.of(
                                                balanceChangesForLazyCreateFailing
                                                        .get(0)
                                                        .getNonEmptyAliasIfPresent()
                                                        .toByteArray()))))
                .willReturn(true);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(UInt256.valueOf(INVALID_ALIAS_KEY.getNumber()), result);
        // and:
        verify(frame, never()).getGasPrice();
        verify(autoCreationLogic, never()).create(any(), any(), any());
        verify(autoCreationLogic, never())
                .create(
                        balanceChangesForLazyCreateFailing.get(1),
                        accounts,
                        balanceChangesForLazyCreateFailing);
        verify(worldUpdater, never()).manageInProgressPrecedingRecord(any(), any(), any());
        verify(frame, never()).decrementRemainingGas(anyLong());
        verify(transferLogic, never()).doZeroSum(any());
        verify(wrappedLedgers, never()).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void lazyCreateFailsWithNonSupportedIfFlagTurnedOff() throws InvalidProtocolBufferException {
        Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_CRYPTO_TRANSFER_V2));
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getRemainingGas()).willReturn(300L);
        given(frame.getValue()).willReturn(Wei.ZERO);
        Optional<WorldUpdater> parent = Optional.of(worldUpdater);
        given(worldUpdater.parentUpdater()).willReturn(parent);
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
        givenLedgers();
        givenPricingUtilsContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(infrastructureFactory.newImpliedTransfersMarshal(any()))
                .willReturn(impliedTransfersMarshal);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(
                        syntheticTxnFactory.createCryptoTransfer(
                                Collections.singletonList(nftsTransferListAliasReceiver)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
        transferPrecompile
                .when(() -> decodeCryptoTransferV2(eq(pretendArguments), any(), any()))
                .thenReturn(CRYPTO_TRANSFER_NFTS_WRAPPER_ALIAS_RECEIVER);
        given(impliedTransfersMarshal.validityWithCurrentProps(cryptoTransferTransactionBody))
                .willReturn(OK);
        given(infrastructureFactory.newHederaTokenStore(sideEffects, tokens, nfts, tokenRels))
                .willReturn(hederaTokenStore);
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
        given(frame.getSenderAddress()).willReturn(contractAddress);
        when(accessorFactory.uncheckedSpecializedAccessor(any())).thenCallRealMethod();
        when(accessorFactory.constructSpecializedAccessor(any())).thenCallRealMethod();
        given(dynamicProperties.isAtomicCryptoTransferEnabled()).willReturn(true);
        given(dynamicProperties.isImplicitCreationEnabled()).willReturn(false);
        given(creator.createUnsuccessfulSyntheticRecord(ResponseCodeEnum.NOT_SUPPORTED))
                .willReturn(mockRecordBuilder);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(UInt256.valueOf(ResponseCodeEnum.NOT_SUPPORTED.getNumber()), result);
        // and:
        verify(autoCreationLogic, never()).create(any(), any(), any());
        verify(worldUpdater, never()).manageInProgressPrecedingRecord(any(), any(), any());
        verify(frame, never()).decrementRemainingGas(anyLong());
        verify(transferLogic, never()).doZeroSum(any());
        verify(wrappedLedgers, never()).commit();
        verify(creator).createUnsuccessfulSyntheticRecord(ResponseCodeEnum.NOT_SUPPORTED);
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void hbarNFTTransferHappyPathWorks() throws InvalidProtocolBufferException {
        final Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_CRYPTO_TRANSFER_V2));

        givenMinimalFrameContext();
        givenLedgers();
        givenPricingUtilsContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(infrastructureFactory.newImpliedTransfersMarshal(any()))
                .willReturn(impliedTransfersMarshal);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        given(syntheticTxnFactory.createCryptoTransfer(Collections.singletonList(nftsTransferList)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
        given(
                        sigsVerifier.hasActiveKey(
                                Mockito.anyBoolean(), any(), any(), any(), eq(CryptoTransfer)))
                .willReturn(true);
        given(
                        sigsVerifier.hasActiveKeyOrNoReceiverSigReq(
                                Mockito.anyBoolean(), any(), any(), any(), eq(CryptoTransfer)))
                .willReturn(true);
        transferPrecompile
                .when(() -> decodeCryptoTransferV2(eq(pretendArguments), any(), any()))
                .thenReturn(CRYPTO_TRANSFER_HBAR_NFT_WRAPPER);
        given(impliedTransfersMarshal.validityWithCurrentProps(cryptoTransferTransactionBody))
                .willReturn(OK);
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
        given(impliedTransfers.getAllBalanceChanges()).willReturn(hbarAndNftsTransferChanges);
        given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
        given(impliedTransfersMeta.code()).willReturn(OK);
        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.aliases()).willReturn(aliases);
        given(frame.getSenderAddress()).willReturn(contractAddress);
        when(accessorFactory.uncheckedSpecializedAccessor(any())).thenCallRealMethod();
        when(accessorFactory.constructSpecializedAccessor(any())).thenCallRealMethod();
        given(dynamicProperties.isAtomicCryptoTransferEnabled()).willReturn(true);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(transferLogic).doZeroSum(hbarAndNftsTransferChanges);
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void transferFailsAndCatchesProperly() throws InvalidProtocolBufferException {
        final Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_TOKEN));

        givenMinimalFrameContext();
        givenLedgers();
        givenPricingUtilsContext();
        given(dynamicProperties.maxNumWithHapiSigsAccess()).willReturn(Long.MAX_VALUE);
        given(dynamicProperties.systemContractsWithTopLevelSigsAccess())
                .willReturn(Set.of(CryptoTransfer));
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(infrastructureFactory.newImpliedTransfersMarshal(any()))
                .willReturn(impliedTransfersMarshal);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        given(
                        sigsVerifier.hasActiveKeyOrNoReceiverSigReq(
                                Mockito.anyBoolean(), any(), any(), any(), eq(CryptoTransfer)))
                .willReturn(true);
        given(
                        sigsVerifier.hasActiveKey(
                                Mockito.anyBoolean(), any(), any(), any(), eq(CryptoTransfer)))
                .willReturn(true);
        given(impliedTransfersMarshal.validityWithCurrentProps(cryptoTransferTransactionBody))
                .willReturn(OK);
        given(infrastructureFactory.newHederaTokenStore(sideEffects, tokens, nfts, tokenRels))
                .willReturn(hederaTokenStore);
        given(
                        infrastructureFactory.newTransferLogic(
                                hederaTokenStore, sideEffects, nfts, accounts, tokenRels))
                .willReturn(transferLogic);
        transferPrecompile
                .when(() -> decodeTransferToken(eq(pretendArguments), any(), any()))
                .thenReturn(CRYPTO_TRANSFER_TOKEN_WRAPPER);
        given(
                        impliedTransfersMarshal.assessCustomFeesAndValidate(
                                anyInt(), anyInt(), anyInt(), any(), any(), any()))
                .willReturn(impliedTransfers);
        given(impliedTransfers.getAllBalanceChanges()).willReturn(tokenTransferChanges);
        given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
        given(impliedTransfersMeta.code()).willReturn(OK);
        given(syntheticTxnFactory.createCryptoTransfer(any())).willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
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
        given(creator.createUnsuccessfulSyntheticRecord(ResponseCodeEnum.FAIL_INVALID))
                .willReturn(mockRecordBuilder);
        given(frame.getSenderAddress()).willReturn(contractAddress);

        doThrow(new InvalidTransactionException(ResponseCodeEnum.FAIL_INVALID))
                .when(transferLogic)
                .doZeroSum(tokenTransferChanges);
        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.aliases()).willReturn(aliases);
        when(accessorFactory.uncheckedSpecializedAccessor(any())).thenCallRealMethod();
        when(accessorFactory.constructSpecializedAccessor(any())).thenCallRealMethod();

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertNotEquals(successResult, result);
        // and:
        verify(transferLogic).doZeroSum(tokenTransferChanges);
        verify(wrappedLedgers, never()).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void transferWithWrongInput() {
        final Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_TOKEN));
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
        givenIfDelegateCall();
        transferPrecompile
                .when(() -> decodeTransferToken(eq(pretendArguments), any(), any()))
                .thenThrow(new IndexOutOfBoundsException());

        subject.prepareFields(frame);
        final var result = subject.computePrecompile(pretendArguments, frame);

        assertDoesNotThrow(() -> subject.prepareComputation(pretendArguments, a -> a));
        assertNull(result.getOutput());
    }

    @Test
    void gasRequirementReturnsCorrectValueForTransferNfts() {
        // given
        givenMinFrameContext();
        givenPricingUtilsContext();
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_NFTS));
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(infrastructureFactory.newImpliedTransfersMarshal(any()))
                .willReturn(impliedTransfersMarshal);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(syntheticTxnFactory.createCryptoTransfer(any()))
                .willReturn(
                        TransactionBody.newBuilder()
                                .setCryptoTransfer(CryptoTransferTransactionBody.newBuilder()));
        given(feeCalculator.computeFee(any(), any(), any(), any()))
                .willReturn(new FeeObject(TEST_NODE_FEE, TEST_NETWORK_FEE, TEST_SERVICE_FEE));
        given(feeCalculator.estimatedGasPriceInTinybars(any(), any()))
                .willReturn(DEFAULT_GAS_PRICE);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        transferPrecompile
                .when(() -> decodeTransferNFTs(eq(input), any(), any()))
                .thenReturn(CRYPTO_TRANSFER_EMPTY_WRAPPER);

        subject.prepareFields(frame);
        subject.prepareComputation(input, a -> a);
        final long result = subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);

        // then
        assertEquals(EXPECTED_GAS_PRICE, result);
    }

    @Test
    void gasRequirementReturnsCorrectValueForTransferNft() {
        // given
        givenMinFrameContext();
        givenPricingUtilsContext();
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_NFT));
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(infrastructureFactory.newImpliedTransfersMarshal(any()))
                .willReturn(impliedTransfersMarshal);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(syntheticTxnFactory.createCryptoTransfer(any()))
                .willReturn(
                        TransactionBody.newBuilder()
                                .setCryptoTransfer(CryptoTransferTransactionBody.newBuilder()));
        given(feeCalculator.computeFee(any(), any(), any(), any()))
                .willReturn(new FeeObject(TEST_NODE_FEE, TEST_NETWORK_FEE, TEST_SERVICE_FEE));
        given(feeCalculator.estimatedGasPriceInTinybars(any(), any()))
                .willReturn(DEFAULT_GAS_PRICE);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        transferPrecompile
                .when(() -> decodeTransferNFT(eq(input), any(), any()))
                .thenReturn(CRYPTO_TRANSFER_EMPTY_WRAPPER);

        subject.prepareFields(frame);
        subject.prepareComputation(input, a -> a);
        final long result = subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);

        // then
        assertEquals(EXPECTED_GAS_PRICE, result);
    }

    @Test
    void gasRequirementReturnsCorrectValueForSingleCryptoTransfer() {
        // given
        givenMinFrameContext();
        givenPricingUtilsContext();
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_CRYPTO_TRANSFER));
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(infrastructureFactory.newImpliedTransfersMarshal(any()))
                .willReturn(impliedTransfersMarshal);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(syntheticTxnFactory.createCryptoTransfer(any()))
                .willReturn(
                        TransactionBody.newBuilder()
                                .setCryptoTransfer(CryptoTransferTransactionBody.newBuilder()));
        given(feeCalculator.computeFee(any(), any(), any(), any()))
                .willReturn(new FeeObject(TEST_NODE_FEE, TEST_NETWORK_FEE, TEST_SERVICE_FEE));
        given(feeCalculator.estimatedGasPriceInTinybars(any(), any()))
                .willReturn(DEFAULT_GAS_PRICE);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        transferPrecompile
                .when(() -> decodeCryptoTransfer(eq(input), any(), any()))
                .thenReturn(CRYPTO_TRANSFER_EMPTY_WRAPPER);

        subject.prepareFields(frame);
        subject.prepareComputation(input, a -> a);
        final long result = subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);

        // then
        assertEquals(EXPECTED_GAS_PRICE, result);
    }

    @Test
    void gasRequirementReturnsCorrectValueForMultipleCryptoTransfers() {
        // given
        givenMinFrameContext();
        givenPricingUtilsContext();
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_CRYPTO_TRANSFER));
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(infrastructureFactory.newImpliedTransfersMarshal(any()))
                .willReturn(impliedTransfersMarshal);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(syntheticTxnFactory.createCryptoTransfer(any()))
                .willReturn(
                        TransactionBody.newBuilder()
                                .setCryptoTransfer(
                                        CryptoTransferTransactionBody.newBuilder()
                                                .addTokenTransfers(
                                                        TokenTransferList.newBuilder().build())
                                                .addTokenTransfers(
                                                        TokenTransferList.newBuilder().build())
                                                .addTokenTransfers(
                                                        TokenTransferList.newBuilder().build())));
        given(feeCalculator.computeFee(any(), any(), any(), any()))
                .willReturn(new FeeObject(TEST_NODE_FEE, TEST_NETWORK_FEE, TEST_SERVICE_FEE));
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(feeCalculator.estimatedGasPriceInTinybars(any(), any()))
                .willReturn(DEFAULT_GAS_PRICE);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        transferPrecompile
                .when(() -> decodeCryptoTransfer(eq(input), any(), any()))
                .thenReturn(CRYPTO_TRANSFER_EMPTY_WRAPPER);

        subject.prepareFields(frame);
        subject.prepareComputation(input, a -> a);
        final long result = subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);

        // then
        assertEquals(EXPECTED_GAS_PRICE, result);
    }

    @Test
    void gasRequirementReturnsCorrectValueForTransferMultipleTokens() {
        // given
        givenMinFrameContext();
        givenPricingUtilsContext();
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_TOKENS));
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(infrastructureFactory.newImpliedTransfersMarshal(any()))
                .willReturn(impliedTransfersMarshal);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(syntheticTxnFactory.createCryptoTransfer(any()))
                .willReturn(
                        TransactionBody.newBuilder()
                                .setCryptoTransfer(CryptoTransferTransactionBody.newBuilder()));
        given(feeCalculator.computeFee(any(), any(), any(), any()))
                .willReturn(new FeeObject(TEST_NODE_FEE, TEST_NETWORK_FEE, TEST_SERVICE_FEE));
        given(feeCalculator.estimatedGasPriceInTinybars(any(), any()))
                .willReturn(DEFAULT_GAS_PRICE);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        transferPrecompile
                .when(() -> decodeTransferTokens(eq(input), any(), any()))
                .thenReturn(CRYPTO_TRANSFER_EMPTY_WRAPPER);

        subject.prepareFields(frame);
        subject.prepareComputation(input, a -> a);
        final long result = subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);

        // then
        assertEquals(EXPECTED_GAS_PRICE, result);
    }

    @Test
    void gasRequirementReturnsCorrectValueForTransferSingleToken() {
        // given
        givenMinFrameContext();
        givenPricingUtilsContext();
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_TOKEN));
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(infrastructureFactory.newImpliedTransfersMarshal(any()))
                .willReturn(impliedTransfersMarshal);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(syntheticTxnFactory.createCryptoTransfer(any()))
                .willReturn(
                        TransactionBody.newBuilder()
                                .setCryptoTransfer(CryptoTransferTransactionBody.newBuilder()));
        given(feeCalculator.computeFee(any(), any(), any(), any()))
                .willReturn(new FeeObject(TEST_NODE_FEE, TEST_NETWORK_FEE, TEST_SERVICE_FEE));
        given(feeCalculator.estimatedGasPriceInTinybars(any(), any()))
                .willReturn(DEFAULT_GAS_PRICE);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        transferPrecompile
                .when(() -> decodeTransferToken(eq(input), any(), any()))
                .thenReturn(CRYPTO_TRANSFER_EMPTY_WRAPPER);

        subject.prepareFields(frame);
        subject.prepareComputation(input, a -> a);

        final long result = subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);

        // then
        assertEquals(EXPECTED_GAS_PRICE, result);
    }

    @Test
    void minimumFeeInTinybarsHbarOnlyCryptoTransfer() {
        final var feeBuilder = Mockito.mockStatic(FeeBuilder.class);

        // given
        givenMinFrameContext();
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_CRYPTO_TRANSFER_V2));
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(infrastructureFactory.newImpliedTransfersMarshal(any()))
                .willReturn(impliedTransfersMarshal);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(syntheticTxnFactory.createCryptoTransfer(any()))
                .willReturn(
                        TransactionBody.newBuilder()
                                .setCryptoTransfer(CryptoTransferTransactionBody.newBuilder()));
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        transferPrecompile
                .when(() -> decodeCryptoTransferV2(eq(input), any(), any()))
                .thenReturn(CRYPTO_TRANSFER_HBAR_ONLY_WRAPPER);

        feeBuilder
                .when(() -> getTinybarsFromTinyCents(any(), anyLong()))
                .thenReturn(TEST_CRYPTO_TRANSFER_MIN_FEE);

        given(dynamicProperties.isAtomicCryptoTransferEnabled()).willReturn(true);

        subject.prepareFields(frame);
        subject.prepareComputation(input, a -> a);
        final var minimumFeeInTinybars = subject.getPrecompile().getMinimumFeeInTinybars(timestamp);

        // then
        assertEquals(TEST_CRYPTO_TRANSFER_MIN_FEE, minimumFeeInTinybars);

        feeBuilder.close();
    }

    @Test
    void minimumFeeInTinybarsTwoHbarCryptoTransfer() {
        final var feeBuilder = Mockito.mockStatic(FeeBuilder.class);

        // given
        givenMinFrameContext();
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_CRYPTO_TRANSFER_V2));
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(infrastructureFactory.newImpliedTransfersMarshal(any()))
                .willReturn(impliedTransfersMarshal);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(syntheticTxnFactory.createCryptoTransfer(any()))
                .willReturn(
                        TransactionBody.newBuilder()
                                .setCryptoTransfer(CryptoTransferTransactionBody.newBuilder()));
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        transferPrecompile
                .when(() -> decodeCryptoTransferV2(eq(input), any(), any()))
                .thenReturn(CRYPTO_TRANSFER_TWO_HBAR_ONLY_WRAPPER);

        feeBuilder
                .when(() -> getTinybarsFromTinyCents(any(), anyLong()))
                .thenReturn(TEST_CRYPTO_TRANSFER_MIN_FEE);

        given(dynamicProperties.isAtomicCryptoTransferEnabled()).willReturn(true);
        given(dynamicProperties.isImplicitCreationEnabled()).willReturn(true);

        subject.prepareFields(frame);
        subject.prepareComputation(input, a -> a);
        final var minimumFeeInTinybars = subject.getPrecompile().getMinimumFeeInTinybars(timestamp);

        // then
        // expect 2 times the fee as there are two transfers
        assertEquals(2 * TEST_CRYPTO_TRANSFER_MIN_FEE, minimumFeeInTinybars);

        feeBuilder.close();
    }

    @Test
    void minimumFeeInTinybarsHbarFungibleCryptoTransfer() {
        final var feeBuilder = Mockito.mockStatic(FeeBuilder.class);

        // given
        givenMinFrameContext();
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_CRYPTO_TRANSFER_V2));
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(infrastructureFactory.newImpliedTransfersMarshal(any()))
                .willReturn(impliedTransfersMarshal);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(syntheticTxnFactory.createCryptoTransfer(any()))
                .willReturn(
                        TransactionBody.newBuilder()
                                .setCryptoTransfer(CryptoTransferTransactionBody.newBuilder()));
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        transferPrecompile
                .when(() -> decodeCryptoTransferV2(eq(input), any(), any()))
                .thenReturn(CRYPTO_TRANSFER_HBAR_FUNGIBLE_WRAPPER);

        feeBuilder
                .when(() -> getTinybarsFromTinyCents(any(), anyLong()))
                .thenReturn(TEST_CRYPTO_TRANSFER_MIN_FEE);

        given(dynamicProperties.isAtomicCryptoTransferEnabled()).willReturn(true);

        subject.prepareFields(frame);
        subject.prepareComputation(input, a -> a);
        final var minimumFeeInTinybars = subject.getPrecompile().getMinimumFeeInTinybars(timestamp);

        // then
        // 1 for hbars and 1 for fungible tokens
        assertEquals(2 * TEST_CRYPTO_TRANSFER_MIN_FEE, minimumFeeInTinybars);

        feeBuilder.close();
    }

    @Test
    void minimumFeeInTinybarsWithLazyCreateButNotEnabled() {
        var feeBuilder = Mockito.mockStatic(FeeBuilder.class);

        // given
        givenMinFrameContext();
        Bytes input = Bytes.of(Integers.toBytes(ABI_ID_CRYPTO_TRANSFER));
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(infrastructureFactory.newImpliedTransfersMarshal(any()))
                .willReturn(impliedTransfersMarshal);
        given(impliedTransfersMarshal.validityWithCurrentProps(any())).willReturn(OK);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(syntheticTxnFactory.createCryptoTransfer(any()))
                .willReturn(
                        TransactionBody.newBuilder()
                                .setCryptoTransfer(CryptoTransferTransactionBody.newBuilder()));
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        transferPrecompile
                .when(() -> decodeCryptoTransfer(eq(input), any(), any()))
                .thenReturn(CRYPTO_TRANSFER_NFTS_WRAPPER_ALIAS_RECEIVER);

        feeBuilder
                .when(() -> getTinybarsFromTinyCents(any(), anyLong()))
                .thenReturn(TEST_CRYPTO_TRANSFER_MIN_FEE);
        given(dynamicProperties.isImplicitCreationEnabled()).willReturn(false);

        subject.prepareFields(frame);
        subject.prepareComputation(input, a -> a);
        var minimumFeeInTinybars = subject.getPrecompile().getMinimumFeeInTinybars(timestamp);

        // then 2 for 2 NFT exchanges and lazy creation not accounted for
        assertEquals(2 * TEST_CRYPTO_TRANSFER_MIN_FEE, minimumFeeInTinybars);

        feeBuilder.close();
    }

    @Test
    void minimumFeeInTinybarsWithLazyCreateAttemptAndEnabled() {
        var feeBuilder = Mockito.mockStatic(FeeBuilder.class);

        // given
        givenMinFrameContext();
        Bytes input = Bytes.of(Integers.toBytes(ABI_ID_CRYPTO_TRANSFER));
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(infrastructureFactory.newImpliedTransfersMarshal(any()))
                .willReturn(impliedTransfersMarshal);
        given(impliedTransfersMarshal.validityWithCurrentProps(any())).willReturn(OK);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(syntheticTxnFactory.createCryptoTransfer(any()))
                .willReturn(
                        TransactionBody.newBuilder()
                                .setCryptoTransfer(CryptoTransferTransactionBody.newBuilder()));
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        transferPrecompile
                .when(() -> decodeCryptoTransfer(eq(input), any(), any()))
                .thenReturn(CRYPTO_TRANSFER_NFTS_WRAPPER_ALIAS_RECEIVER);

        feeBuilder
                .when(() -> getTinybarsFromTinyCents(any(), anyLong()))
                .thenReturn(TEST_CRYPTO_TRANSFER_MIN_FEE);
        given(dynamicProperties.isImplicitCreationEnabled()).willReturn(true);

        subject.prepareFields(frame);
        subject.prepareComputation(input, a -> a);
        var minimumFeeInTinybars = subject.getPrecompile().getMinimumFeeInTinybars(timestamp);

        // 2 NFT Exchanges + 1 Lazy Creation (which is 2x the fee)
        assertEquals(4 * TEST_CRYPTO_TRANSFER_MIN_FEE, minimumFeeInTinybars);

        feeBuilder.close();
    }

    @Test
    void minimumFeeInTinybarsHbarNftCryptoTransfer() {
        final var feeBuilder = Mockito.mockStatic(FeeBuilder.class);

        // given
        givenMinFrameContext();
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_CRYPTO_TRANSFER_V2));
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(infrastructureFactory.newImpliedTransfersMarshal(any()))
                .willReturn(impliedTransfersMarshal);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(syntheticTxnFactory.createCryptoTransfer(any()))
                .willReturn(
                        TransactionBody.newBuilder()
                                .setCryptoTransfer(CryptoTransferTransactionBody.newBuilder()));
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        transferPrecompile
                .when(() -> decodeCryptoTransferV2(eq(input), any(), any()))
                .thenReturn(CRYPTO_TRANSFER_HBAR_NFT_WRAPPER);

        feeBuilder
                .when(() -> getTinybarsFromTinyCents(any(), anyLong()))
                .thenReturn(TEST_CRYPTO_TRANSFER_MIN_FEE);

        given(dynamicProperties.isAtomicCryptoTransferEnabled()).willReturn(true);

        subject.prepareFields(frame);
        subject.prepareComputation(input, a -> a);
        final var minimumFeeInTinybars = subject.getPrecompile().getMinimumFeeInTinybars(timestamp);

        // then
        // 2 for nfts transfers and 1 for hbars
        assertEquals(3 * TEST_CRYPTO_TRANSFER_MIN_FEE, minimumFeeInTinybars);

        feeBuilder.close();
    }

    @Test
    void minimumFeeInTinybarsHbarFungibleNftCryptoTransfer() {
        final var feeBuilder = Mockito.mockStatic(FeeBuilder.class);

        // given
        givenMinFrameContext();
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_CRYPTO_TRANSFER_V2));
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(infrastructureFactory.newImpliedTransfersMarshal(any()))
                .willReturn(impliedTransfersMarshal);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(syntheticTxnFactory.createCryptoTransfer(any()))
                .willReturn(
                        TransactionBody.newBuilder()
                                .setCryptoTransfer(CryptoTransferTransactionBody.newBuilder()));
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        transferPrecompile
                .when(() -> decodeCryptoTransferV2(eq(input), any(), any()))
                .thenReturn(CRYPTO_TRANSFER_HBAR_FUNGIBLE_NFT_WRAPPER);

        feeBuilder
                .when(() -> getTinybarsFromTinyCents(any(), anyLong()))
                .thenReturn(TEST_CRYPTO_TRANSFER_MIN_FEE);

        given(dynamicProperties.isAtomicCryptoTransferEnabled()).willReturn(true);

        subject.prepareFields(frame);
        subject.prepareComputation(input, a -> a);
        final var minimumFeeInTinybars = subject.getPrecompile().getMinimumFeeInTinybars(timestamp);

        // then
        // 1 for fungible + 2 for nfts transfers + 1 for hbars
        assertEquals(4 * TEST_CRYPTO_TRANSFER_MIN_FEE, minimumFeeInTinybars);

        feeBuilder.close();
    }

    @Test
    void decodeCryptoTransferPositiveFungibleAmountAndNftTransfer() {
        transferPrecompile
                .when(
                        () ->
                                decodeCryptoTransfer(
                                        POSITIVE_FUNGIBLE_AMOUNT_AND_NFT_TRANSFER_CRYPTO_TRANSFER_INPUT,
                                        identity(),
                                        accoundIdExists))
                .thenCallRealMethod();
        transferPrecompile
                .when(() -> decodeTokenTransfer(any(), any(), any(), any()))
                .thenCallRealMethod();
        transferPrecompile
                .when(() -> decodeHbarTransfers(any(), any(), any(), any()))
                .thenCallRealMethod();
        final var decodedInput =
                decodeCryptoTransfer(
                        POSITIVE_FUNGIBLE_AMOUNT_AND_NFT_TRANSFER_CRYPTO_TRANSFER_INPUT,
                        identity(),
                        accoundIdExists);
        final var fungibleTransfers =
                decodedInput.tokenTransferWrappers().get(0).fungibleTransfers();
        final var nftExchanges = decodedInput.tokenTransferWrappers().get(0).nftExchanges();

        assertNotNull(fungibleTransfers);
        assertNotNull(nftExchanges);
        assertEquals(1, fungibleTransfers.size());
        assertEquals(1, nftExchanges.size());
        assertTrue(fungibleTransfers.get(0).getDenomination().getTokenNum() > 0);
        assertTrue(fungibleTransfers.get(0).receiver().getAccountNum() > 0);
        assertEquals(43, fungibleTransfers.get(0).receiverAdjustment().getAmount());
        assertTrue(nftExchanges.get(0).getTokenType().getTokenNum() > 0);
        assertTrue(nftExchanges.get(0).asGrpc().getReceiverAccountID().getAccountNum() > 0);
        assertTrue(nftExchanges.get(0).asGrpc().getSenderAccountID().getAccountNum() > 0);
        assertEquals(72, nftExchanges.get(0).asGrpc().getSerialNumber());
    }

    @Test
    void decodeCryptoTransferPositiveFungibleAmountAndNftTransferNonExisting() {
        final Predicate<AccountID> nonExistingPredicate = acc -> false;
        transferPrecompile
                .when(
                        () ->
                                decodeCryptoTransfer(
                                        POSITIVE_FUNGIBLE_AMOUNT_AND_NFT_TRANSFER_CRYPTO_TRANSFER_INPUT,
                                        identity(),
                                        nonExistingPredicate))
                .thenCallRealMethod();
        transferPrecompile
                .when(() -> decodeTokenTransfer(any(), any(), any(), any()))
                .thenCallRealMethod();
        transferPrecompile
                .when(() -> decodeHbarTransfers(any(), any(), any(), any()))
                .thenCallRealMethod();
        final var decodedInput =
                decodeCryptoTransfer(
                        POSITIVE_FUNGIBLE_AMOUNT_AND_NFT_TRANSFER_CRYPTO_TRANSFER_INPUT,
                        identity(),
                        nonExistingPredicate);
        final var fungibleTransfers =
                decodedInput.tokenTransferWrappers().get(0).fungibleTransfers();
        final var nftExchanges = decodedInput.tokenTransferWrappers().get(0).nftExchanges();

        final ByteString alias =
                ByteStringUtils.wrapUnsafely(
                        EntityIdUtils.asTypedEvmAddress(
                                        AccountID.newBuilder().setAccountNum(1185).build())
                                .toArrayUnsafe());
        assertNotNull(fungibleTransfers);
        assertNotNull(nftExchanges);
        assertEquals(1, fungibleTransfers.size());
        assertEquals(1, nftExchanges.size());
        assertTrue(fungibleTransfers.get(0).getDenomination().getTokenNum() > 0);
        final var expectedReceiver = AccountID.newBuilder().setAlias(alias).build();
        assertEquals(expectedReceiver, fungibleTransfers.get(0).receiver());
        assertEquals(43, fungibleTransfers.get(0).receiverAdjustment().getAmount());
        assertTrue(nftExchanges.get(0).getTokenType().getTokenNum() > 0);
        assertEquals(expectedReceiver, nftExchanges.get(0).asGrpc().getReceiverAccountID());
        assertTrue(nftExchanges.get(0).asGrpc().getSenderAccountID().getAccountNum() > 0);
        assertEquals(72, nftExchanges.get(0).asGrpc().getSerialNumber());
    }

    @Test
    void decodeCryptoTransferNegativeFungibleAmount() {
        transferPrecompile
                .when(
                        () ->
                                decodeCryptoTransfer(
                                        NEGATIVE_FUNGIBLE_AMOUNT_CRYPTO_TRANSFER_INPUT,
                                        identity(),
                                        accoundIdExists))
                .thenCallRealMethod();
        transferPrecompile
                .when(() -> decodeTokenTransfer(any(), any(), any(), any()))
                .thenCallRealMethod();
        transferPrecompile
                .when(() -> decodeHbarTransfers(any(), any(), any(), any()))
                .thenCallRealMethod();
        final var decodedInput =
                decodeCryptoTransfer(
                        NEGATIVE_FUNGIBLE_AMOUNT_CRYPTO_TRANSFER_INPUT,
                        identity(),
                        accoundIdExists);
        final var hbarTransfers = decodedInput.transferWrapper().hbarTransfers();
        final var fungibleTransfers =
                decodedInput.tokenTransferWrappers().get(0).fungibleTransfers();

        assertNotNull(fungibleTransfers);
        assertEquals(1, fungibleTransfers.size());
        assertTrue(fungibleTransfers.get(0).getDenomination().getTokenNum() > 0);
        assertTrue(fungibleTransfers.get(0).sender().getAccountNum() > 0);
        assertEquals(50, fungibleTransfers.get(0).amount());
        assertEquals(0, hbarTransfers.size());
    }

    @Test
    void decodeTransferTokenInput() {
        transferPrecompile
                .when(() -> decodeTransferToken(TRANSFER_TOKEN_INPUT, identity(), accoundIdExists))
                .thenCallRealMethod();
        final var decodedInput =
                decodeTransferToken(TRANSFER_TOKEN_INPUT, identity(), accoundIdExists);
        final var hbarTransfers = decodedInput.transferWrapper().hbarTransfers();
        final var fungibleTransfer =
                decodedInput.tokenTransferWrappers().get(0).fungibleTransfers().get(0);

        assertTrue(fungibleTransfer.sender().getAccountNum() > 0);
        assertTrue(fungibleTransfer.receiver().getAccountNum() > 0);
        assertTrue(fungibleTransfer.getDenomination().getTokenNum() > 0);
        assertEquals(20, fungibleTransfer.amount());
        assertEquals(0, hbarTransfers.size());
    }

    @Test
    void decodeTransferTokensPositiveAmounts() {
        transferPrecompile
                .when(
                        () ->
                                decodeTransferTokens(
                                        POSITIVE_AMOUNTS_TRANSFER_TOKENS_INPUT,
                                        identity(),
                                        accoundIdExists))
                .thenCallRealMethod();
        transferPrecompile
                .when(() -> addSignedAdjustments(any(), any(), any(), any(), any()))
                .thenCallRealMethod();
        final var decodedInput =
                decodeTransferTokens(
                        POSITIVE_AMOUNTS_TRANSFER_TOKENS_INPUT, identity(), accoundIdExists);
        final var hbarTransfers = decodedInput.transferWrapper().hbarTransfers();
        final var fungibleTransfers =
                decodedInput.tokenTransferWrappers().get(0).fungibleTransfers();

        assertEquals(2, fungibleTransfers.size());
        assertTrue(fungibleTransfers.get(0).getDenomination().getTokenNum() > 0);
        assertTrue(fungibleTransfers.get(1).getDenomination().getTokenNum() > 0);
        assertNull(fungibleTransfers.get(0).sender());
        assertNull(fungibleTransfers.get(1).sender());
        assertTrue(fungibleTransfers.get(0).receiver().getAccountNum() > 0);
        assertTrue(fungibleTransfers.get(1).receiver().getAccountNum() > 0);
        assertEquals(10, fungibleTransfers.get(0).amount());
        assertEquals(20, fungibleTransfers.get(1).amount());
        assertEquals(0, hbarTransfers.size());
    }

    @Test
    void decodeTransferTokensPositiveAmountsWithAliases() {
        final Predicate<AccountID> nonExistingPredicate = acc -> false;
        transferPrecompile
                .when(
                        () ->
                                decodeTransferTokens(
                                        POSITIVE_AMOUNTS_TRANSFER_TOKENS_INPUT,
                                        identity(),
                                        nonExistingPredicate))
                .thenCallRealMethod();
        transferPrecompile
                .when(() -> addSignedAdjustments(any(), any(), any(), any(), any()))
                .thenCallRealMethod();
        final var decodedInput =
                decodeTransferTokens(
                        POSITIVE_AMOUNTS_TRANSFER_TOKENS_INPUT, identity(), nonExistingPredicate);
        final var hbarTransfers = decodedInput.transferWrapper().hbarTransfers();
        final var fungibleTransfers =
                decodedInput.tokenTransferWrappers().get(0).fungibleTransfers();

        final var alias =
                ByteStringUtils.wrapUnsafely(
                        EntityIdUtils.asTypedEvmAddress(
                                        AccountID.newBuilder().setAccountNum(1089).build())
                                .toArrayUnsafe());
        assertEquals(2, fungibleTransfers.size());
        assertTrue(fungibleTransfers.get(0).getDenomination().getTokenNum() > 0);
        assertTrue(fungibleTransfers.get(1).getDenomination().getTokenNum() > 0);
        assertNull(fungibleTransfers.get(0).sender());
        assertNull(fungibleTransfers.get(1).sender());
        assertEquals(
                AccountID.newBuilder().setAlias(alias).build(),
                fungibleTransfers.get(0).receiver());
        assertEquals(
                AccountID.newBuilder().setAlias(alias).build(),
                fungibleTransfers.get(1).receiver());
        assertEquals(10, fungibleTransfers.get(0).amount());
        assertEquals(20, fungibleTransfers.get(1).amount());
        assertEquals(0, hbarTransfers.size());
    }

    @Test
    void decodeTransferTokensPositiveNegativeAmount() {
        transferPrecompile
                .when(
                        () ->
                                decodeTransferTokens(
                                        POSITIVE_NEGATIVE_AMOUNT_TRANSFER_TOKENS_INPUT,
                                        identity(),
                                        accoundIdExists))
                .thenCallRealMethod();
        transferPrecompile
                .when(() -> addSignedAdjustments(any(), any(), any(), any(), any()))
                .thenCallRealMethod();
        final var decodedInput =
                decodeTransferTokens(
                        POSITIVE_NEGATIVE_AMOUNT_TRANSFER_TOKENS_INPUT,
                        identity(),
                        accoundIdExists);
        final var hbarTransfers = decodedInput.transferWrapper().hbarTransfers();
        final var fungibleTransfers =
                decodedInput.tokenTransferWrappers().get(0).fungibleTransfers();

        assertEquals(2, fungibleTransfers.size());
        assertTrue(fungibleTransfers.get(0).getDenomination().getTokenNum() > 0);
        assertTrue(fungibleTransfers.get(1).getDenomination().getTokenNum() > 0);
        assertNull(fungibleTransfers.get(0).sender());
        assertNull(fungibleTransfers.get(1).receiver());
        assertTrue(fungibleTransfers.get(0).receiver().getAccountNum() > 0);
        assertTrue(fungibleTransfers.get(1).sender().getAccountNum() > 0);
        assertEquals(20, fungibleTransfers.get(0).amount());
        assertEquals(20, fungibleTransfers.get(1).amount());
        assertEquals(0, hbarTransfers.size());
    }

    @Test
    void decodeTransferNFTInput() {
        transferPrecompile
                .when(() -> decodeTransferNFT(TRANSFER_NFT_INPUT, identity(), accoundIdExists))
                .thenCallRealMethod();
        final var decodedInput = decodeTransferNFT(TRANSFER_NFT_INPUT, identity(), accoundIdExists);
        final var hbarTransfers = decodedInput.transferWrapper().hbarTransfers();
        final var nonFungibleTransfer =
                decodedInput.tokenTransferWrappers().get(0).nftExchanges().get(0);

        assertTrue(nonFungibleTransfer.asGrpc().getSenderAccountID().getAccountNum() > 0);
        assertTrue(nonFungibleTransfer.asGrpc().getReceiverAccountID().getAccountNum() > 0);
        assertTrue(nonFungibleTransfer.getTokenType().getTokenNum() > 0);
        assertEquals(101, nonFungibleTransfer.asGrpc().getSerialNumber());
        assertEquals(0, hbarTransfers.size());
    }

    @Test
    void decodeTransferNFTsInput() {
        transferPrecompile
                .when(() -> decodeTransferNFTs(TRANSFER_NFTS_INPUT, identity(), accoundIdExists))
                .thenCallRealMethod();
        transferPrecompile
                .when(() -> decodeTokenTransfer(any(), any(), any(), any()))
                .thenCallRealMethod();
        transferPrecompile
                .when(() -> decodeHbarTransfers(any(), any(), any(), any()))
                .thenCallRealMethod();
        transferPrecompile
                .when(() -> addNftExchanges(any(), any(), any(), any(), any(), any()))
                .thenCallRealMethod();
        final var decodedInput =
                decodeTransferNFTs(TRANSFER_NFTS_INPUT, identity(), accoundIdExists);
        final var hbarTransfers = decodedInput.transferWrapper().hbarTransfers();
        final var nonFungibleTransfers = decodedInput.tokenTransferWrappers().get(0).nftExchanges();

        assertEquals(2, nonFungibleTransfers.size());
        assertTrue(nonFungibleTransfers.get(0).asGrpc().getSenderAccountID().getAccountNum() > 0);
        assertTrue(nonFungibleTransfers.get(1).asGrpc().getSenderAccountID().getAccountNum() > 0);
        assertTrue(nonFungibleTransfers.get(0).asGrpc().getReceiverAccountID().getAccountNum() > 0);
        assertTrue(nonFungibleTransfers.get(1).asGrpc().getReceiverAccountID().getAccountNum() > 0);
        assertTrue(nonFungibleTransfers.get(0).getTokenType().getTokenNum() > 0);
        assertTrue(nonFungibleTransfers.get(1).getTokenType().getTokenNum() > 0);
        assertEquals(123, nonFungibleTransfers.get(0).asGrpc().getSerialNumber());
        assertEquals(234, nonFungibleTransfers.get(1).asGrpc().getSerialNumber());
        assertEquals(0, hbarTransfers.size());
    }

    @Test
    void decodeTransferNFTsInputWithAlias() {
        final Predicate<AccountID> nonExistingPredicate = acc -> false;
        transferPrecompile
                .when(
                        () ->
                                decodeTransferNFTs(
                                        TRANSFER_NFTS_INPUT, identity(), nonExistingPredicate))
                .thenCallRealMethod();
        transferPrecompile
                .when(() -> addNftExchanges(any(), any(), any(), any(), any(), any()))
                .thenCallRealMethod();
        transferPrecompile
                .when(() -> decodeTokenTransfer(any(), any(), any(), any()))
                .thenCallRealMethod();
        transferPrecompile
                .when(() -> decodeHbarTransfers(any(), any(), any(), any()))
                .thenCallRealMethod();
        final var decodedInput =
                decodeTransferNFTs(TRANSFER_NFTS_INPUT, identity(), nonExistingPredicate);
        final var hbarTransfers = decodedInput.transferWrapper().hbarTransfers();
        final var nonFungibleTransfers = decodedInput.tokenTransferWrappers().get(0).nftExchanges();

        final var alias =
                ByteStringUtils.wrapUnsafely(
                        EntityIdUtils.asTypedEvmAddress(
                                        AccountID.newBuilder().setAccountNum(1148).build())
                                .toArrayUnsafe());
        assertEquals(2, nonFungibleTransfers.size());
        assertTrue(nonFungibleTransfers.get(0).asGrpc().getSenderAccountID().getAccountNum() > 0);
        assertTrue(nonFungibleTransfers.get(1).asGrpc().getSenderAccountID().getAccountNum() > 0);
        final var expectedReceiver = AccountID.newBuilder().setAlias(alias).build();
        assertEquals(expectedReceiver, nonFungibleTransfers.get(0).asGrpc().getReceiverAccountID());
        assertEquals(expectedReceiver, nonFungibleTransfers.get(1).asGrpc().getReceiverAccountID());
        assertTrue(nonFungibleTransfers.get(0).getTokenType().getTokenNum() > 0);
        assertTrue(nonFungibleTransfers.get(1).getTokenType().getTokenNum() > 0);
        assertEquals(123, nonFungibleTransfers.get(0).asGrpc().getSerialNumber());
        assertEquals(234, nonFungibleTransfers.get(1).asGrpc().getSerialNumber());
        assertEquals(0, hbarTransfers.size());
    }

    @Test
    void decodeCryptoTransferHBarOnlyTransfer() {
        transferPrecompile
                .when(
                        () ->
                                decodeCryptoTransferV2(
                                        CRYPTO_TRANSFER_HBAR_ONLY_INPUT,
                                        identity(),
                                        accoundIdExists))
                .thenCallRealMethod();
        transferPrecompile
                .when(() -> decodeHbarTransfers(any(), any(), any(), any()))
                .thenCallRealMethod();
        final var decodedInput =
                decodeCryptoTransferV2(
                        CRYPTO_TRANSFER_HBAR_ONLY_INPUT, identity(), accoundIdExists);
        final var hbarTransfers = decodedInput.transferWrapper().hbarTransfers();
        final var tokenTransferWrappers = decodedInput.tokenTransferWrappers();

        assertNotNull(hbarTransfers);
        assertNotNull(tokenTransferWrappers);
        assertEquals(2, hbarTransfers.size());
        assertEquals(0, tokenTransferWrappers.size());

        assertHbarTransfers(hbarTransfers);
    }

    @Test
    void decodeCryptoTransferFungibleTransfer() {
        transferPrecompile
                .when(
                        () ->
                                decodeCryptoTransferV2(
                                        CRYPTO_TRANSFER_FUNGIBLE_INPUT,
                                        identity(),
                                        accoundIdExists))
                .thenCallRealMethod();
        transferPrecompile
                .when(() -> decodeTokenTransfer(any(), any(), any(), any()))
                .thenCallRealMethod();
        transferPrecompile
                .when(() -> decodeHbarTransfers(any(), any(), any(), any()))
                .thenCallRealMethod();
        final var decodedInput =
                decodeCryptoTransferV2(CRYPTO_TRANSFER_FUNGIBLE_INPUT, identity(), accoundIdExists);
        final var hbarTransfers = decodedInput.transferWrapper().hbarTransfers();
        final var fungibleTransfers =
                decodedInput.tokenTransferWrappers().get(0).fungibleTransfers();
        final var nonFungibleTransfers = decodedInput.tokenTransferWrappers().get(0).nftExchanges();

        assertNotNull(hbarTransfers);
        assertNotNull(fungibleTransfers);
        assertNotNull(nonFungibleTransfers);
        assertEquals(0, hbarTransfers.size());
        assertEquals(2, fungibleTransfers.size());
        assertEquals(0, nonFungibleTransfers.size());

        assertFungibleTransfers(fungibleTransfers);
    }

    @Test
    void decodeCryptoTransferNftTransfer() {
        transferPrecompile
                .when(
                        () ->
                                decodeCryptoTransferV2(
                                        CRYPTO_TRANSFER_NFT_INPUT, identity(), accoundIdExists))
                .thenCallRealMethod();
        transferPrecompile
                .when(() -> decodeTokenTransfer(any(), any(), any(), any()))
                .thenCallRealMethod();
        transferPrecompile
                .when(() -> decodeHbarTransfers(any(), any(), any(), any()))
                .thenCallRealMethod();
        final var decodedInput =
                decodeCryptoTransferV2(CRYPTO_TRANSFER_NFT_INPUT, identity(), accoundIdExists);
        final var hbarTransfers = decodedInput.transferWrapper().hbarTransfers();
        final var fungibleTransfers =
                decodedInput.tokenTransferWrappers().get(0).fungibleTransfers();
        final var nonFungibleTransfers1 =
                decodedInput.tokenTransferWrappers().get(0).nftExchanges();
        final var nonFungibleTransfers2 =
                decodedInput.tokenTransferWrappers().get(1).nftExchanges();

        assertNotNull(hbarTransfers);
        assertNotNull(fungibleTransfers);
        assertNotNull(nonFungibleTransfers1);
        assertNotNull(nonFungibleTransfers2);
        assertEquals(0, hbarTransfers.size());
        assertEquals(0, fungibleTransfers.size());
        assertEquals(1, nonFungibleTransfers1.size());
        assertEquals(1, nonFungibleTransfers2.size());

        assertNftTransfers(nonFungibleTransfers1, nonFungibleTransfers2);
    }

    @Test
    void decodeCryptoTransferHbarFungibleTransfer() {
        transferPrecompile
                .when(
                        () ->
                                decodeCryptoTransferV2(
                                        CRYPTO_TRANSFER_HBAR_FUNGIBLE_INPUT,
                                        identity(),
                                        accoundIdExists))
                .thenCallRealMethod();
        transferPrecompile
                .when(() -> decodeTokenTransfer(any(), any(), any(), any()))
                .thenCallRealMethod();
        transferPrecompile
                .when(() -> decodeHbarTransfers(any(), any(), any(), any()))
                .thenCallRealMethod();
        final var decodedInput =
                decodeCryptoTransferV2(
                        CRYPTO_TRANSFER_HBAR_FUNGIBLE_INPUT, identity(), accoundIdExists);
        final var hbarTransfers = decodedInput.transferWrapper().hbarTransfers();
        final var fungibleTransfers =
                decodedInput.tokenTransferWrappers().get(0).fungibleTransfers();
        final var nonFungibleTransfers = decodedInput.tokenTransferWrappers().get(0).nftExchanges();

        assertNotNull(hbarTransfers);
        assertNotNull(fungibleTransfers);
        assertNotNull(nonFungibleTransfers);
        assertEquals(2, hbarTransfers.size());
        assertEquals(2, fungibleTransfers.size());
        assertEquals(0, nonFungibleTransfers.size());

        assertHbarTransfers(hbarTransfers);
        assertFungibleTransfers(fungibleTransfers);
    }

    @Test
    void decodeCryptoTransferHbarNFTTransfer() {
        transferPrecompile
                .when(
                        () ->
                                decodeCryptoTransferV2(
                                        CRYPTO_TRANSFER_HBAR_NFT_INPUT,
                                        identity(),
                                        accoundIdExists))
                .thenCallRealMethod();
        transferPrecompile
                .when(() -> decodeTokenTransfer(any(), any(), any(), any()))
                .thenCallRealMethod();
        transferPrecompile
                .when(() -> decodeHbarTransfers(any(), any(), any(), any()))
                .thenCallRealMethod();
        final var decodedInput =
                decodeCryptoTransferV2(CRYPTO_TRANSFER_HBAR_NFT_INPUT, identity(), accoundIdExists);
        final var hbarTransfers = decodedInput.transferWrapper().hbarTransfers();
        final var fungibleTransfers =
                decodedInput.tokenTransferWrappers().get(0).fungibleTransfers();
        final var nonFungibleTransfers1 =
                decodedInput.tokenTransferWrappers().get(0).nftExchanges();
        final var nonFungibleTransfers2 =
                decodedInput.tokenTransferWrappers().get(1).nftExchanges();

        assertNotNull(hbarTransfers);
        assertNotNull(fungibleTransfers);
        assertNotNull(nonFungibleTransfers1);
        assertNotNull(nonFungibleTransfers2);
        assertEquals(2, hbarTransfers.size());
        assertEquals(0, fungibleTransfers.size());
        assertEquals(1, nonFungibleTransfers1.size());
        assertEquals(1, nonFungibleTransfers2.size());

        assertHbarTransfers(hbarTransfers);
        assertNftTransfers(nonFungibleTransfers1, nonFungibleTransfers2);
    }

    private void assertHbarTransfers(final List<SyntheticTxnFactory.HbarTransfer> hbarTransfers) {
        assertEquals(10, hbarTransfers.get(0).amount());
        assertTrue(!hbarTransfers.get(0).isApproval());
        assertNull(hbarTransfers.get(0).sender());
        assertEquals(1, hbarTransfers.get(0).receiver().getAccountNum());

        assertEquals(10, hbarTransfers.get(1).amount());
        assertTrue(!hbarTransfers.get(1).isApproval());
        assertNull(hbarTransfers.get(1).receiver());
        assertEquals(2, hbarTransfers.get(1).sender().getAccountNum());
    }

    private void assertFungibleTransfers(
            final List<SyntheticTxnFactory.FungibleTokenTransfer> fungibleTokenTransfers) {
        assertEquals(10, fungibleTokenTransfers.get(0).amount());
        assertTrue(fungibleTokenTransfers.get(0).isApproval());
        assertNull(fungibleTokenTransfers.get(0).sender());
        assertEquals(4, fungibleTokenTransfers.get(0).receiver().getAccountNum());
        assertEquals(3, fungibleTokenTransfers.get(0).getDenomination().getTokenNum());

        assertEquals(10, fungibleTokenTransfers.get(1).amount());
        assertTrue(!fungibleTokenTransfers.get(1).isApproval());
        assertNull(fungibleTokenTransfers.get(1).receiver());
        assertEquals(5, fungibleTokenTransfers.get(1).sender().getAccountNum());
        assertEquals(3, fungibleTokenTransfers.get(1).getDenomination().getTokenNum());
    }

    private void assertNftTransfers(
            final List<SyntheticTxnFactory.NftExchange> nftExchanges1,
            final List<SyntheticTxnFactory.NftExchange> nftExchanges2) {
        assertEquals(6, nftExchanges1.get(0).getTokenType().getTokenNum());
        assertEquals(2, nftExchanges1.get(0).getSerialNo());
        assertEquals(7, nftExchanges1.get(0).asGrpc().getSenderAccountID().getAccountNum());
        assertEquals(8, nftExchanges1.get(0).asGrpc().getReceiverAccountID().getAccountNum());
        assertTrue(nftExchanges1.get(0).isApproval());

        assertEquals(9, nftExchanges2.get(0).getTokenType().getTokenNum());
        assertEquals(3, nftExchanges2.get(0).getSerialNo());
        assertEquals(10, nftExchanges2.get(0).asGrpc().getSenderAccountID().getAccountNum());
        assertEquals(11, nftExchanges2.get(0).asGrpc().getReceiverAccountID().getAccountNum());
        assertTrue(!nftExchanges2.get(0).isApproval());
    }

    private void givenMinimalFrameContext() {
        given(frame.getContractAddress()).willReturn(contractAddr);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getRemainingGas()).willReturn(300L);
        given(frame.getValue()).willReturn(Wei.ZERO);
        final Optional<WorldUpdater> parent = Optional.of(worldUpdater);
        given(worldUpdater.parentUpdater()).willReturn(parent);
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
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

    private void givenMinFrameContext() {
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
        given(wrappedLedgers.accounts()).willReturn(accounts);
    }

    private void givenIfDelegateCall() {
        given(frame.getContractAddress()).willReturn(contractAddress);
        given(frame.getRecipientAddress()).willReturn(recipientAddress);
        given(worldUpdater.get(recipientAddress)).willReturn(acc);
        given(acc.getNonce()).willReturn(-1L);
        given(frame.getMessageFrameStack()).willReturn(stack);
        given(frame.getMessageFrameStack().iterator()).willReturn(dequeIterator);
    }
}
