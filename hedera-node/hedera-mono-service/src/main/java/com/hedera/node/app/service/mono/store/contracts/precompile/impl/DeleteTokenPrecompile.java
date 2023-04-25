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

import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.BYTES32;
import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.INT;
import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade.decodeFunctionCall;
import static com.hedera.node.app.service.mono.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.DELETE;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenDelete;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.node.app.service.mono.context.SideEffectsTracker;
import com.hedera.node.app.service.mono.contracts.sources.EvmSigsVerifier;
import com.hedera.node.app.service.mono.ledger.accounts.ContractAliases;
import com.hedera.node.app.service.mono.store.contracts.WorldLedgers;
import com.hedera.node.app.service.mono.store.contracts.precompile.InfrastructureFactory;
import com.hedera.node.app.service.mono.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.DeleteWrapper;
import com.hedera.node.app.service.mono.store.contracts.precompile.utils.KeyActivationUtils;
import com.hedera.node.app.service.mono.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.node.app.service.mono.store.models.Id;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Objects;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class DeleteTokenPrecompile extends AbstractWritePrecompile {
    private static final Function DELETE_TOKEN_FUNCTION = new Function("deleteToken(address)", INT);
    private static final Bytes DELETE_TOKEN_SELECTOR = Bytes.wrap(DELETE_TOKEN_FUNCTION.selector());
    private static final ABIType<Tuple> DELETE_TOKEN_DECODER = TypeFactory.create(BYTES32);
    private DeleteWrapper deleteOp;
    private final ContractAliases aliases;
    private final EvmSigsVerifier sigsVerifier;

    public DeleteTokenPrecompile(
            WorldLedgers ledgers,
            ContractAliases aliases,
            EvmSigsVerifier sigsVerifier,
            SideEffectsTracker sideEffectsTracker,
            SyntheticTxnFactory syntheticTxnFactory,
            InfrastructureFactory infrastructureFactory,
            PrecompilePricingUtils precompilePricingUtils) {
        super(
                ledgers,
                sideEffectsTracker,
                syntheticTxnFactory,
                infrastructureFactory,
                precompilePricingUtils);
        this.aliases = aliases;
        this.sigsVerifier = sigsVerifier;
    }

    @Override
    public TransactionBody.Builder body(Bytes input, UnaryOperator<byte[]> aliasResolver) {
        deleteOp = decodeDelete(input);
        transactionBody = syntheticTxnFactory.createDelete(deleteOp);
        return transactionBody;
    }

    @Override
    public long getMinimumFeeInTinybars(Timestamp consensusTime) {
        Objects.requireNonNull(
                deleteOp, "`body` method should be called before `getMinimumFeeInTinybars`");
        return pricingUtils.getMinimumPriceInTinybars(DELETE, consensusTime);
    }

    @Override
    public void run(MessageFrame frame) {
        Objects.requireNonNull(deleteOp);

        /* --- Check required signatures --- */
        final var tokenId = Id.fromGrpcToken(deleteOp.tokenID());
        final var hasRequiredSigs =
                KeyActivationUtils.validateKey(
                        frame,
                        tokenId.asEvmAddress(),
                        sigsVerifier::hasActiveAdminKey,
                        ledgers,
                        aliases,
                        TokenDelete);
        validateTrue(hasRequiredSigs, INVALID_SIGNATURE);

        /* --- Build the necessary infrastructure to execute the transaction --- */
        final var accountStore = infrastructureFactory.newAccountStore(ledgers.accounts());
        final var tokenStore =
                infrastructureFactory.newTokenStore(
                        accountStore,
                        sideEffects,
                        ledgers.tokens(),
                        ledgers.nfts(),
                        ledgers.tokenRels());

        final var deleteLogic = infrastructureFactory.newDeleteLogic(accountStore, tokenStore);
        final var validity = deleteLogic.validate(transactionBody.build());
        validateTrue(validity == OK, validity);

        /* --- Execute the transaction and capture its results --- */
        deleteLogic.delete(deleteOp.tokenID());
    }

    public static DeleteWrapper decodeDelete(final Bytes input) {
        final Tuple decodedArguments =
                decodeFunctionCall(input, DELETE_TOKEN_SELECTOR, DELETE_TOKEN_DECODER);
        final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(0));

        return new DeleteWrapper(tokenID);
    }
}
