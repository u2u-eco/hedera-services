/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.store.contracts.precompile.utils;

import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.fungibleTokenAddr;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.evm.store.contracts.WorldStateAccount;
import com.hedera.node.app.service.mono.store.contracts.HederaStackedWorldStateUpdater;
import java.util.ArrayDeque;
import java.util.Deque;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KeyActivationUtilsTest {
    @Mock private MessageFrame grandparent;
    @Mock private MessageFrame parent;
    @Mock private MessageFrame messageFrame;
    @Mock private WorldStateAccount worldStateAccount;
    @Mock private HederaStackedWorldStateUpdater worldUpdater;

    @Test
    void testsAccountIsToken() {
        given(messageFrame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.get(any())).willReturn(worldStateAccount);
        given(worldStateAccount.getNonce()).willReturn(-1L);

        var result = KeyActivationUtils.isToken(messageFrame, fungibleTokenAddr);

        assertTrue(result);
    }

    @Test
    void legacyActivationTestDetectsReceiverMatch() {
        final Deque<MessageFrame> stack = new ArrayDeque<>();
        stack.push(grandparent);
        stack.push(parent);
        stack.push(messageFrame);
        given(messageFrame.getMessageFrameStack()).willReturn(stack);

        final var granted = Address.BLAKE2B_F_COMPRESSION;

        given(grandparent.getRecipientAddress()).willReturn(Address.BLAKE2B_F_COMPRESSION);
        given(parent.getRecipientAddress()).willReturn(Address.BLS12_G1ADD);

        final var subject = KeyActivationUtils.legacyActivationTestFor(messageFrame);
        assertTrue(subject.stackIncludesReceiver(granted));
    }

    @Test
    void testsAccountIsNotToken() {
        given(messageFrame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.get(any())).willReturn(worldStateAccount);
        given(worldStateAccount.getNonce()).willReturn(1L);

        var result = KeyActivationUtils.isToken(messageFrame, fungibleTokenAddr);

        assertFalse(result);
    }
}
