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
package com.hedera.node.app.service.mono.state.initialization;

import static com.hedera.node.app.service.mono.context.properties.StaticPropertiesHolder.STATIC_PROPERTIES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.hedera.node.app.service.mono.ledger.accounts.HederaAccountCustomizer;
import com.hedera.node.app.service.mono.ledger.backing.BackingStore;
import com.hedera.node.app.service.mono.legacy.core.jproto.JEd25519Key;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.spi.numbers.HederaAccountNumbers;
import com.hedera.test.mocks.MockAccountNumbers;
import com.hederahashgraph.api.proto.java.AccountID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class TreasuryClonerTest {

    private static final long pretendExpiry = 1_234_567L;
    private static final JKey pretendTreasuryKey =
            new JEd25519Key("a123456789a123456789a123456789a1".getBytes());
    private static final HederaAccountNumbers nums = new MockAccountNumbers();

    @Mock private BackingStore<AccountID, HederaAccount> accounts;

    private TreasuryCloner subject;

    @BeforeEach
    void setUp() {
        subject = new TreasuryCloner(nums, MerkleAccount::new, accounts);
    }

    @Test
    void clonesAsExpected() {
        willAnswer(
                        invocationOnMock ->
                                ((AccountID) invocationOnMock.getArgument(0)).getAccountNum()
                                        == 666L)
                .given(accounts)
                .contains(any());
        willAnswer(
                        invocationOnMock -> {
                            final var id = (AccountID) invocationOnMock.getArgument(0);
                            if (id.getAccountNum() == 2L || id.getAccountNum() == 666L) {
                                return accountWith(pretendExpiry, pretendTreasuryKey);
                            }
                            return null;
                        })
                .given(accounts)
                .getImmutableRef(any());

        subject.ensureTreasuryClonesExist();
        final var created = subject.getClonesCreated();

        for (long i = 200; i <= 750L; i++) {
            if (i != 666 && !(i >= 350 && i < 400)) {
                verify(accounts).put(idFor(i), accountWith(pretendExpiry, pretendTreasuryKey));
            }
        }
        assertEquals(500, created.size());
        verifyNoMoreInteractions(accounts);

        subject.forgetCreatedClones();
        assertTrue(subject.getClonesCreated().isEmpty());
    }

    private AccountID idFor(final long num) {
        return STATIC_PROPERTIES.scopedAccountWith(num);
    }

    public static MerkleAccount accountWith(final long expiry, final JKey someKey) {
        return (MerkleAccount)
                new HederaAccountCustomizer()
                        .isReceiverSigRequired(true)
                        .isDeleted(false)
                        .expiry(expiry)
                        .memo("123")
                        .isSmartContract(false)
                        .key(someKey)
                        .autoRenewPeriod(expiry)
                        .customizing(new MerkleAccount());
    }
}
