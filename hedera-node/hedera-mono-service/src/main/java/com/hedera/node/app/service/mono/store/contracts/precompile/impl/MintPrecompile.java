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
package com.hedera.node.app.service.mono.store.contracts.precompile.impl;

import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.INT;
import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade.decodeFunctionCall;
import static com.hedera.node.app.service.mono.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.MINT_FUNGIBLE;
import static com.hedera.node.app.service.mono.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.MINT_NFT;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.ByteStringUtils;
import com.hedera.node.app.service.mono.context.SideEffectsTracker;
import com.hedera.node.app.service.mono.contracts.sources.EvmSigsVerifier;
import com.hedera.node.app.service.mono.ledger.accounts.ContractAliases;
import com.hedera.node.app.service.mono.records.RecordsHistorian;
import com.hedera.node.app.service.mono.state.submerkle.ExpirableTxnRecord;
import com.hedera.node.app.service.mono.store.contracts.WorldLedgers;
import com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants;
import com.hedera.node.app.service.mono.store.contracts.precompile.InfrastructureFactory;
import com.hedera.node.app.service.mono.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.MintWrapper;
import com.hedera.node.app.service.mono.store.contracts.precompile.utils.KeyActivationUtils;
import com.hedera.node.app.service.mono.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.node.app.service.mono.store.models.Id;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class MintPrecompile extends AbstractWritePrecompile {
    private final int functionId;
    private static final List<ByteString> NO_METADATA = Collections.emptyList();
    private static final String MINT = String.format(FAILURE_MESSAGE, "mint");
    private static final Function MINT_TOKEN_FUNCTION =
            new Function("mintToken(address,uint64,bytes[])", INT);
    private static final Function MINT_TOKEN_FUNCTION_V2 =
            new Function("mintToken(address,int64,bytes[])", INT);
    public static final Bytes MINT_TOKEN_SELECTOR = Bytes.wrap(MINT_TOKEN_FUNCTION.selector());
    private static final Bytes MINT_TOKEN_SELECTOR_V2 =
            Bytes.wrap(MINT_TOKEN_FUNCTION_V2.selector());
    private static final ABIType<Tuple> MINT_TOKEN_DECODER =
            TypeFactory.create("(bytes32,int64,bytes[])");
    private final EncodingFacade encoder;
    private final ContractAliases aliases;
    private final EvmSigsVerifier sigsVerifier;
    private final RecordsHistorian recordsHistorian;

    private MintWrapper mintOp;

    public MintPrecompile(
            final WorldLedgers ledgers,
            final EncodingFacade encoder,
            final ContractAliases aliases,
            final EvmSigsVerifier sigsVerifier,
            final RecordsHistorian recordsHistorian,
            final SideEffectsTracker sideEffects,
            final SyntheticTxnFactory syntheticTxnFactory,
            final InfrastructureFactory infrastructureFactory,
            final PrecompilePricingUtils pricingUtils,
            final int functionId) {
        super(ledgers, sideEffects, syntheticTxnFactory, infrastructureFactory, pricingUtils);
        this.encoder = encoder;
        this.aliases = aliases;
        this.sigsVerifier = sigsVerifier;
        this.recordsHistorian = recordsHistorian;
        this.functionId = functionId;
    }

    @Override
    public TransactionBody.Builder body(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        this.transactionBody = null;
        mintOp =
                switch (functionId) {
                    case AbiConstants.ABI_ID_MINT_TOKEN -> decodeMint(input);
                    case AbiConstants.ABI_ID_MINT_TOKEN_V2 -> decodeMintV2(input);
                    default -> null;
                };
        transactionBody = syntheticTxnFactory.createMint(mintOp);
        return transactionBody;
    }

    @Override
    public void run(final MessageFrame frame) {
        Objects.requireNonNull(mintOp, "`body` method should be called before `run`");

        // --- Check required signatures ---
        final var tokenId = Id.fromGrpcToken(Objects.requireNonNull(mintOp).tokenType());
        final var hasRequiredSigs =
                KeyActivationUtils.validateKey(
                        frame,
                        tokenId.asEvmAddress(),
                        sigsVerifier::hasActiveSupplyKey,
                        ledgers,
                        aliases,
                        TokenMint);
        validateTrue(hasRequiredSigs, INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE, MINT);

        /* --- Build the necessary infrastructure to execute the transaction --- */
        final var accountStore = infrastructureFactory.newAccountStore(ledgers.accounts());
        final var tokenStore =
                infrastructureFactory.newTokenStore(
                        accountStore,
                        sideEffects,
                        ledgers.tokens(),
                        ledgers.nfts(),
                        ledgers.tokenRels());
        final var mintLogic = infrastructureFactory.newMintLogic(accountStore, tokenStore);

        final var validity = mintLogic.validateSyntax(transactionBody.build());
        validateTrue(validity == OK, validity);

        /* --- Execute the transaction and capture its results --- */
        if (mintOp.type() == NON_FUNGIBLE_UNIQUE) {
            final var newMeta = mintOp.metadata();
            final var creationTime = recordsHistorian.nextFollowingChildConsensusTime();
            mintLogic.mint(tokenId, newMeta.size(), 0, newMeta, creationTime);
        } else {
            mintLogic.mint(tokenId, 0, mintOp.amount(), NO_METADATA, Instant.EPOCH);
        }
    }

    @Override
    public long getMinimumFeeInTinybars(final Timestamp consensusTime) {
        final var isNftMint = Objects.requireNonNull(mintOp).type() == NON_FUNGIBLE_UNIQUE;
        return pricingUtils.getMinimumPriceInTinybars(
                isNftMint ? MINT_NFT : MINT_FUNGIBLE, consensusTime);
    }

    @Override
    public Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
        final var receiptBuilder = childRecord.getReceiptBuilder();
        validateTrue(receiptBuilder != null, FAIL_INVALID);
        return encoder.encodeMintSuccess(
                childRecord.getReceiptBuilder().getNewTotalSupply(),
                childRecord.getReceiptBuilder().getSerialNumbers());
    }

    @Override
    public Bytes getFailureResultFor(final ResponseCodeEnum status) {
        return encoder.encodeMintFailure(status);
    }

    public static MintWrapper decodeMint(final Bytes input) {
        return getMintWrapper(input, MINT_TOKEN_SELECTOR);
    }

    private static MintWrapper getMintWrapper(final Bytes input, final Bytes mintTokenSelector) {
        final Tuple decodedArguments =
                decodeFunctionCall(input, mintTokenSelector, MINT_TOKEN_DECODER);

        final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(0));
        final var fungibleAmount = (long) decodedArguments.get(1);
        final var metadataList = (byte[][]) decodedArguments.get(2);
        final List<ByteString> wrappedMetadata = new ArrayList<>();
        for (final var meta : metadataList) {
            wrappedMetadata.add(ByteStringUtils.wrapUnsafely(meta));
        }
        // We allow zero unit operations on fungible tokens
        if (fungibleAmount > 0 || (fungibleAmount == 0 && wrappedMetadata.isEmpty())) {
            return MintWrapper.forFungible(tokenID, fungibleAmount);
        } else {
            return MintWrapper.forNonFungible(tokenID, wrappedMetadata);
        }
    }

    public static MintWrapper decodeMintV2(final Bytes input) {
        return getMintWrapper(input, MINT_TOKEN_SELECTOR_V2);
    }
}
