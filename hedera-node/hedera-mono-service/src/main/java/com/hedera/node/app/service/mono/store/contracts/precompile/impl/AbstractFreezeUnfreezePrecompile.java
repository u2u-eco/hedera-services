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

import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenFreezeAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUnfreezeAccount;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.node.app.service.evm.store.contracts.precompile.codec.TokenFreezeUnfreezeWrapper;
import com.hedera.node.app.service.mono.context.SideEffectsTracker;
import com.hedera.node.app.service.mono.contracts.sources.EvmSigsVerifier;
import com.hedera.node.app.service.mono.ledger.accounts.ContractAliases;
import com.hedera.node.app.service.mono.store.contracts.WorldLedgers;
import com.hedera.node.app.service.mono.store.contracts.precompile.InfrastructureFactory;
import com.hedera.node.app.service.mono.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.node.app.service.mono.store.contracts.precompile.utils.KeyActivationUtils;
import com.hedera.node.app.service.mono.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.node.app.service.mono.store.models.Id;
import com.hedera.node.app.service.mono.txns.token.FreezeLogic;
import com.hedera.node.app.service.mono.txns.token.UnfreezeLogic;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.Objects;
import org.hyperledger.besu.evm.frame.MessageFrame;

/* --- Constructor functional interfaces for mocking --- */
public abstract class AbstractFreezeUnfreezePrecompile extends AbstractWritePrecompile {

    private final boolean hasFreezeLogic;
    protected final ContractAliases aliases;
    protected final EvmSigsVerifier sigsVerifier;
    protected TokenFreezeUnfreezeWrapper<TokenID, AccountID> freezeUnfreezeOp;

    protected AbstractFreezeUnfreezePrecompile(
            WorldLedgers ledgers,
            final ContractAliases aliases,
            final EvmSigsVerifier sigsVerifier,
            SideEffectsTracker sideEffects,
            SyntheticTxnFactory syntheticTxnFactory,
            InfrastructureFactory infrastructureFactory,
            PrecompilePricingUtils pricingUtils,
            boolean hasFreezeLogic) {
        super(ledgers, sideEffects, syntheticTxnFactory, infrastructureFactory, pricingUtils);
        this.aliases = aliases;
        this.sigsVerifier = sigsVerifier;
        this.hasFreezeLogic = hasFreezeLogic;
    }

    @Override
    public void run(MessageFrame frame) {
        Objects.requireNonNull(freezeUnfreezeOp);

        /* --- Check required signatures --- */
        final var tokenId = Id.fromGrpcToken(freezeUnfreezeOp.token());
        final var accountId = Id.fromGrpcAccount(freezeUnfreezeOp.account());
        final var hasRequiredSigs =
                KeyActivationUtils.validateKey(
                        frame,
                        tokenId.asEvmAddress(),
                        sigsVerifier::hasActiveFreezeKey,
                        ledgers,
                        aliases,
                        hasFreezeLogic ? TokenFreezeAccount : TokenUnfreezeAccount);
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

        /* --- Execute the transaction and capture its results --- */
        if (hasFreezeLogic) {
            final var freezeLogic = infrastructureFactory.newFreezeLogic(accountStore, tokenStore);
            executeForFreeze(freezeLogic, tokenId, accountId);
        } else {
            final var unfreezeLogic =
                    infrastructureFactory.newUnfreezeLogic(accountStore, tokenStore);
            executeForUnfreeze(unfreezeLogic, tokenId, accountId);
        }
    }

    private void executeForFreeze(FreezeLogic freezeLogic, Id tokenId, Id accountId) {
        validateLogic(freezeLogic.validate(transactionBody.build()));
        freezeLogic.freeze(tokenId, accountId);
    }

    private void executeForUnfreeze(UnfreezeLogic unfreezeLogic, Id tokenId, Id accountId) {
        validateLogic(unfreezeLogic.validate(transactionBody.build()));
        unfreezeLogic.unfreeze(tokenId, accountId);
    }

    private void validateLogic(ResponseCodeEnum validity) {
        validateTrue(validity == OK, validity);
    }
}
