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

import static com.hedera.node.app.service.mono.config.HederaNumbers.FIRST_POST_SYSTEM_FILE_ENTITY;
import static com.hedera.node.app.service.mono.config.HederaNumbers.FIRST_RESERVED_SYSTEM_CONTRACT;
import static com.hedera.node.app.service.mono.config.HederaNumbers.LAST_RESERVED_SYSTEM_CONTRACT;
import static com.hedera.node.app.service.mono.config.HederaNumbers.NUM_RESERVED_SYSTEM_ENTITIES;
import static com.hedera.node.app.service.mono.context.properties.StaticPropertiesHolder.STATIC_PROPERTIES;

import com.hedera.node.app.service.mono.ledger.accounts.HederaAccountCustomizer;
import com.hedera.node.app.service.mono.ledger.backing.BackingStore;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.spi.numbers.HederaAccountNumbers;
import com.hederahashgraph.api.proto.java.AccountID;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.LongStream;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class TreasuryCloner {

    private static final Logger log = LogManager.getLogger(TreasuryCloner.class);

    private final HederaAccountNumbers accountNums;
    private final Supplier<HederaAccount> accountSupplier;
    private final BackingStore<AccountID, HederaAccount> accounts;
    private final List<HederaAccount> clonesCreated = new ArrayList<>();

    @Inject
    public TreasuryCloner(
            final HederaAccountNumbers accountNums,
            final Supplier<HederaAccount> accountSupplier,
            final BackingStore<AccountID, HederaAccount> accounts) {
        this.accountSupplier = accountSupplier;
        this.accountNums = accountNums;
        this.accounts = accounts;
    }

    public void ensureTreasuryClonesExist() {
        final var treasuryId = STATIC_PROPERTIES.scopedAccountWith(accountNums.treasury());
        final var treasury = accounts.getImmutableRef(treasuryId);
        for (final var num : nonContractSystemNums()) {
            final var nextCloneId = STATIC_PROPERTIES.scopedAccountWith(num);
            if (accounts.contains(nextCloneId)) {
                // In ^0.28.6, all accounts will either exist (restart) or not exist (genesis)
                continue;
            }
            final var nextClone =
                    new HederaAccountCustomizer()
                            .isReceiverSigRequired(treasury.isReceiverSigRequired())
                            .isDeclinedReward(treasury.isDeclinedReward())
                            .isDeleted(false)
                            .expiry(treasury.getExpiry())
                            .memo(treasury.getMemo())
                            .isSmartContract(false)
                            .key(treasury.getAccountKey())
                            .autoRenewPeriod(treasury.getAutoRenewSecs())
                            .customizing(accountSupplier.get());
            accounts.put(nextCloneId, nextClone);
            clonesCreated.add(nextClone);
        }
        log.info(
                "Created {} zero-balance accounts cloning treasury properties in the {}-{} range",
                clonesCreated.size(),
                FIRST_POST_SYSTEM_FILE_ENTITY,
                NUM_RESERVED_SYSTEM_ENTITIES);
    }

    public List<HederaAccount> getClonesCreated() {
        return clonesCreated;
    }

    public void forgetCreatedClones() {
        clonesCreated.clear();
    }

    private long[] nonContractSystemNums() {
        return LongStream.rangeClosed(FIRST_POST_SYSTEM_FILE_ENTITY, NUM_RESERVED_SYSTEM_ENTITIES)
                .filter(
                        i ->
                                i < FIRST_RESERVED_SYSTEM_CONTRACT
                                        || i > LAST_RESERVED_SYSTEM_CONTRACT)
                .toArray();
    }
}
