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
package com.hedera.node.app.service.mono.state.forensics;

import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.mono.ServicesState;
import com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext;
import com.hedera.node.app.service.mono.state.merkle.MerkleScheduledTransactions;
import com.hedera.node.app.service.mono.state.merkle.MerkleSpecialFiles;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.state.merkle.MerkleTopic;
import com.hedera.node.app.service.mono.state.migration.AccountStorageAdapter;
import com.hedera.node.app.service.mono.state.migration.TokenRelStorageAdapter;
import com.hedera.node.app.service.mono.state.migration.UniqueTokenMapAdapter;
import com.hedera.node.app.service.mono.stream.RecordsRunningHashLeaf;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.ImmutableHash;
import com.swirlds.common.crypto.RunningHash;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, LogCaptureExtension.class})
class HashLoggerTest {
    @Mock private AccountStorageAdapter accounts;
    @Mock private MerkleMap<EntityNum, MerkleTopic> topics;
    @Mock private MerkleMap<EntityNum, MerkleToken> tokens;
    @Mock private UniqueTokenMapAdapter uniqueTokens;
    @Mock private MerkleScheduledTransactions schedules;
    @Mock private TokenRelStorageAdapter tokenAssociations;
    @Mock private MerkleNetworkContext networkCtx;
    @Mock private AddressBook addressBook;
    @Mock private MerkleSpecialFiles specialFiles;
    @Mock private ServicesState state;
    @Mock private RunningHash runningHash;
    @Mock private RecordsRunningHashLeaf runningHashLeaf;

    @LoggingTarget private LogCaptor logCaptor;

    @LoggingSubject private final HashLogger subject = new HashLogger();

    @Test
    void logsAsExpected() {
        final var desired =
                "[SwirldState Hashes]\n"
                    + "Overall                ::"
                    + " 303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030\n"
                    + "Accounts               ::"
                    + " 313131313131313131313131313131313131313131313131313131313131313131313131313131313131313131313131\n"
                    + "Storage                :: <N/A>\n"
                    + "Topics                 ::"
                    + " 323232323232323232323232323232323232323232323232323232323232323232323232323232323232323232323232\n"
                    + "Tokens                 ::"
                    + " 333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333\n"
                    + "TokenAssociations      ::"
                    + " 373737373737373737373737373737373737373737373737373737373737373737373737373737373737373737373737\n"
                    + "SpecialFiles           ::"
                    + " 5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a\n"
                    + "ScheduledTxs           ::"
                    + " 353535353535353535353535353535353535353535353535353535353535353535353535353535353535353535353535\n"
                    + "NetworkContext         ::"
                    + " 383838383838383838383838383838383838383838383838383838383838383838383838383838383838383838383838\n"
                    + "AddressBook            ::"
                    + " 393939393939393939393939393939393939393939393939393939393939393939393939393939393939393939393939\n"
                    + "RecordsRunningHashLeaf ::"
                    + " 585858585858585858585858585858585858585858585858585858585858585858585858585858585858585858585858\n"
                    + "  ↪ Running hash       ::"
                    + " 595959595959595959595959595959595959595959595959595959595959595959595959595959595959595959595959\n"
                    + "UniqueTokens           ::"
                    + " 343434343434343434343434343434343434343434343434343434343434343434343434343434343434343434343434\n"
                    + "ContractStorage        :: <N/A>";

        given(state.getHash()).willReturn(hashOf('0'));
        given(state.accounts()).willReturn(accounts);
        given(accounts.getHash()).willReturn(hashOf('1'));
        given(state.topics()).willReturn(topics);
        given(topics.getHash()).willReturn(hashOf('2'));
        given(state.tokens()).willReturn(tokens);
        given(tokens.getHash()).willReturn(hashOf('3'));
        given(state.uniqueTokens()).willReturn(uniqueTokens);
        given(uniqueTokens.getHash()).willReturn(hashOf('4'));
        given(state.scheduleTxs()).willReturn(schedules);
        given(schedules.getHash()).willReturn(hashOf('5'));
        given(state.tokenAssociations()).willReturn(tokenAssociations);
        given(tokenAssociations.getHash()).willReturn(hashOf('7'));
        given(state.networkCtx()).willReturn(networkCtx);
        given(networkCtx.getHash()).willReturn(hashOf('8'));
        given(state.addressBook()).willReturn(addressBook);
        given(addressBook.getHash()).willReturn(hashOf('9'));
        given(state.specialFiles()).willReturn(specialFiles);
        given(specialFiles.getHash()).willReturn(hashOf('Z'));
        // and:
        given(runningHashLeaf.getRunningHash()).willReturn(runningHash);
        given(state.runningHashLeaf()).willReturn(runningHashLeaf);
        given(runningHash.getHash()).willReturn(hashOf('Y'));
        given(runningHashLeaf.getHash()).willReturn(hashOf('X'));

        // when:
        subject.logHashesFor(state);

        // then:
        Assertions.assertEquals(desired, logCaptor.infoLogs().get(0));
    }

    private Hash hashOf(final char repeatedC) {
        final var ans = new byte[48];
        for (int i = 0; i < 48; i++) {
            ans[i] = (byte) repeatedC;
        }
        return new ImmutableHash(ans);
    }
}
