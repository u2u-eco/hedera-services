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
package com.hedera.node.app.service.evm.contracts.operations;

/*
 * -
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 *
 */

import com.google.common.annotations.VisibleForTesting;
import java.util.function.BiPredicate;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.BalanceOperation;

/**
 * Hedera adapted version of the {@link BalanceOperation}. Performs an existence check on the
 * requested {@link Address} Halts the execution of the {@link MessageFrame} with {@link
 * HederaExceptionalHaltReason#INVALID_SOLIDITY_ADDRESS} if the account does not exist or it is
 * deleted.
 */
public class HederaBalanceOperation extends BalanceOperation {

    private BiPredicate<Address, MessageFrame> addressValidator;

    public HederaBalanceOperation(
            GasCalculator gasCalculator, BiPredicate<Address, MessageFrame> addressValidator) {
        super(gasCalculator);
        this.addressValidator = addressValidator;
    }

    @Override
    public OperationResult execute(MessageFrame frame, EVM evm) {
        return HederaEvmOperationsUtil.addressCheckExecution(
                frame,
                () -> frame.getStackItem(0),
                () -> cost(true),
                () -> super.execute(frame, evm),
                addressValidator);
    }

    @VisibleForTesting
    public BiPredicate<Address, MessageFrame> getAddressValidator() {
        return addressValidator;
    }

    @VisibleForTesting
    public void setAddressValidator(BiPredicate<Address, MessageFrame> addressValidator) {
        this.addressValidator = addressValidator;
    }
}
