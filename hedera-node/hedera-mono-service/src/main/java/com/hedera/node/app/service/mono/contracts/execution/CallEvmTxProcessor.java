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
package com.hedera.node.app.service.mono.contracts.execution;

import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hedera.node.app.service.mono.contracts.ContractsV_0_30Module.EVM_VERSION_0_30;

import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.ledger.accounts.AliasManager;
import com.hedera.node.app.service.mono.store.contracts.CodeCache;
import com.hedera.node.app.service.mono.store.contracts.HederaMutableWorldState;
import com.hedera.node.app.service.mono.store.models.Account;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.code.CodeV0;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;
import org.hyperledger.besu.evm.processor.MessageCallProcessor;

@Singleton
public class CallEvmTxProcessor extends EvmTxProcessor {
    private final CodeCache codeCache;
    private final AliasManager aliasManager;

    @Inject
    public CallEvmTxProcessor(
            final HederaMutableWorldState worldState,
            final LivePricesSource livePricesSource,
            final CodeCache codeCache,
            final GlobalDynamicProperties dynamicProperties,
            final GasCalculator gasCalculator,
            final Map<String, Provider<MessageCallProcessor>> mcps,
            final Map<String, Provider<ContractCreationProcessor>> ccps,
            final AliasManager aliasManager,
            final InHandleBlockMetaSource blockMetaSource) {
        super(
                worldState,
                livePricesSource,
                dynamicProperties,
                gasCalculator,
                mcps,
                ccps,
                blockMetaSource);
        this.codeCache = codeCache;
        this.aliasManager = aliasManager;
    }

    public TransactionProcessingResult execute(
            final Account sender,
            final Address receiver,
            final long providedGasLimit,
            final long value,
            final Bytes callData,
            final Instant consensusTime) {
        final long gasPrice = gasPriceTinyBarsGiven(consensusTime, false);

        return super.execute(
                sender,
                receiver,
                gasPrice,
                providedGasLimit,
                value,
                callData,
                false,
                false,
                aliasManager.resolveForEvm(receiver),
                null,
                0,
                null);
    }

    public TransactionProcessingResult executeEth(
            final Account sender,
            final Address receiver,
            final long providedGasLimit,
            final long value,
            final Bytes callData,
            final Instant consensusTime,
            final BigInteger userOfferedGasPrice,
            final Account relayer,
            final long maxGasAllowanceInTinybars) {
        final long gasPrice = gasPriceTinyBarsGiven(consensusTime, true);

        return super.execute(
                sender,
                receiver,
                gasPrice,
                providedGasLimit,
                value,
                callData,
                false,
                false,
                aliasManager.resolveForEvm(receiver),
                userOfferedGasPrice,
                maxGasAllowanceInTinybars,
                relayer);
    }

    @Override
    protected HederaFunctionality getFunctionType() {
        return HederaFunctionality.ContractCall;
    }

    @Override
    protected MessageFrame buildInitialFrame(
            final MessageFrame.Builder baseInitialFrame,
            final Address to,
            final Bytes payload,
            final long value) {
        Code code;
        if (dynamicProperties.evmVersion().equals(EVM_VERSION_0_30)) {
            code = codeCache.getIfPresent(aliasManager.resolveForEvm(to));
        } else {
            final var resolvedForEvm = aliasManager.resolveForEvm(to);
            code =
                    aliasManager.isMirror(resolvedForEvm)
                            ? codeCache.getIfPresent(resolvedForEvm)
                            : null;
        }
        /* The ContractCallTransitionLogic would have rejected a missing or deleted
         * contract, so at this point we should have non-null bytecode available.
         * If there is no bytecode, it means we have a non-token and non-contract account,
         * hence the code should be null and there must be a value transfer.
         */
        validateTrue(code != null || value > 0, ResponseCodeEnum.INVALID_ETHEREUM_TRANSACTION);

        return baseInitialFrame
                .type(MessageFrame.Type.MESSAGE_CALL)
                .address(to)
                .contract(to)
                .inputData(payload)
                .code(code == null ? CodeV0.EMPTY_CODE : code)
                .build();
    }
}
