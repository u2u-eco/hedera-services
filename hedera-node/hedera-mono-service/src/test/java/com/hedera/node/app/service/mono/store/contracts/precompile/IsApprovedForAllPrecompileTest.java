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

import static com.hedera.node.app.service.mono.store.contracts.precompile.impl.IsApprovedForAllPrecompile.decodeIsApprovedForAll;
import static java.util.function.UnaryOperator.identity;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hederahashgraph.api.proto.java.TokenID;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IsApprovedForAllPrecompileTest {
    public static final Bytes IS_APPROVED_FOR_ALL_INPUT_ERC =
            Bytes.fromHexString(
                    "0x618dc65e00000000000000000000000000000000000003ece985e9c50000000000000000000000000000000000000000000000000000000027bc86aa00000000000000000000000000000000000000000000000000000000000003eb");
    public static final Bytes IS_APPROVED_FOR_ALL_INPUT_HAPI =
            Bytes.fromHexString(
                    "0xf49f40db0000000000000000000000000000000000000000000000000000000000001234000000000000000000000000000000000000000000000000000000000000065b000000000000000000000000000000000000000000000000000000000000065c");
    private static final long TOKEN_NUM_HAPI_TOKEN = 0x1234;
    private static final long ACCOUNT_NUM_IS_APPROVED_FOR_ALL_OWNER = 0x65b;
    private static final long ACCOUNT_NUM_IS_APPROVED_FOR_ALL_OPERATOR = 0x65c;
    private static final long ACCOUNT_NUM_IS_APPROVED_FOR_ALL_OWNER2 = 666666666;
    private static final long ACCOUNT_NUM_IS_APPROVED_FOR_ALL_OPERATOR2 = 1003;
    private static final TokenID TOKEN_ID = TokenID.newBuilder().setTokenNum(1004).build();

    @Test
    void decodeIsApprovedForAllERC() {
        final var decodedInput =
                decodeIsApprovedForAll(IS_APPROVED_FOR_ALL_INPUT_ERC, TOKEN_ID, identity());

        assertEquals(TOKEN_ID.getTokenNum(), decodedInput.token().getTokenNum());
        assertEquals(ACCOUNT_NUM_IS_APPROVED_FOR_ALL_OWNER2, decodedInput.owner().getAccountNum());
        assertEquals(
                ACCOUNT_NUM_IS_APPROVED_FOR_ALL_OPERATOR2, decodedInput.operator().getAccountNum());
    }

    @Test
    void decodeIsApprovedForAllHAPI() {
        final var decodedInput =
                decodeIsApprovedForAll(IS_APPROVED_FOR_ALL_INPUT_HAPI, null, identity());

        assertEquals(TOKEN_NUM_HAPI_TOKEN, decodedInput.token().getTokenNum());
        assertEquals(ACCOUNT_NUM_IS_APPROVED_FOR_ALL_OWNER, decodedInput.owner().getAccountNum());
        assertEquals(
                ACCOUNT_NUM_IS_APPROVED_FOR_ALL_OPERATOR, decodedInput.operator().getAccountNum());
    }
}
