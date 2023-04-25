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
package com.hedera.node.app.service.evm.store.contracts.precompile.impl;

import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.decodeFunctionCall;
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.BYTES32;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.TokenInfoWrapper;
import org.apache.tuweni.bytes.Bytes;

public interface EvmFungibleTokenInfoPrecompile {

    Function GET_FUNGIBLE_TOKEN_INFO_FUNCTION = new Function("getFungibleTokenInfo(address)");
    Bytes GET_FUNGIBLE_TOKEN_INFO_SELECTOR =
            Bytes.wrap(GET_FUNGIBLE_TOKEN_INFO_FUNCTION.selector());
    ABIType<Tuple> GET_FUNGIBLE_TOKEN_INFO_DECODER = TypeFactory.create(BYTES32);

    static TokenInfoWrapper<byte[]> decodeGetFungibleTokenInfo(final Bytes input) {
        final Tuple decodedArguments =
                decodeFunctionCall(
                        input, GET_FUNGIBLE_TOKEN_INFO_SELECTOR, GET_FUNGIBLE_TOKEN_INFO_DECODER);

        return TokenInfoWrapper.forFungibleToken(decodedArguments.get(0));
    }
}
