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
package com.hedera.node.app.service.mono.fees.charging;

import static com.hedera.node.app.service.mono.context.properties.StaticPropertiesHolder.STATIC_PROPERTIES;
import static com.hedera.node.app.service.mono.ledger.TransactionalLedger.activeLedgerWrapping;
import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.*;
import static com.hedera.node.app.service.mono.records.TxnAwareRecordsHistorian.DEFAULT_SOURCE_ID;
import static com.hedera.node.app.service.mono.state.EntityCreator.NO_CUSTOM_FEES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_BALANCES_FOR_STORAGE_RENT;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.node.app.service.mono.context.SideEffectsTracker;
import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.fees.HbarCentExchange;
import com.hedera.node.app.service.mono.ledger.TransactionalLedger;
import com.hedera.node.app.service.mono.ledger.interceptors.AccountsCommitInterceptor;
import com.hedera.node.app.service.mono.ledger.properties.AccountProperty;
import com.hedera.node.app.service.mono.records.RecordsHistorian;
import com.hedera.node.app.service.mono.state.EntityCreator;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.store.contracts.KvUsageInfo;
import com.hedera.node.app.service.mono.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.stream.proto.TransactionSidecarRecord.Builder;
import com.hederahashgraph.api.proto.java.AccountID;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

/** {@inheritDoc} */
@Singleton
public class RecordedStorageFeeCharging implements StorageFeeCharging {
    private static final List<Builder> NO_SIDECARS = Collections.emptyList();
    public static final String MEMO = "Contract storage fees";

    // Used to create the synthetic record if itemizing is enabled
    private final EntityCreator creator;
    // Used to get the current exchange rate
    private final HbarCentExchange exchange;
    // Used to track the storage fee payments in a succeeding child record
    private final RecordsHistorian recordsHistorian;
    // Used to create the synthetic CryptoTransfer for storage fee payments
    private final SyntheticTxnFactory syntheticTxnFactory;
    // Used to get the current consensus time
    private final TransactionContext txnCtx;
    // Used to get the storage slot lifetime and pricing tiers
    private final GlobalDynamicProperties dynamicProperties;
    // Used to charge the auto-renewal fee
    private final NonHapiFeeCharging nonHapiFeeCharging;

    @Inject
    public RecordedStorageFeeCharging(
            final EntityCreator creator,
            final HbarCentExchange exchange,
            final RecordsHistorian recordsHistorian,
            final TransactionContext txnCtx,
            final SyntheticTxnFactory syntheticTxnFactory,
            final GlobalDynamicProperties dynamicProperties,
            final NonHapiFeeCharging nonHapiFeeCharging) {
        this.txnCtx = txnCtx;
        this.creator = creator;
        this.exchange = exchange;
        this.recordsHistorian = recordsHistorian;
        this.dynamicProperties = dynamicProperties;
        this.syntheticTxnFactory = syntheticTxnFactory;
        this.nonHapiFeeCharging = nonHapiFeeCharging;
    }

    /** {@inheritDoc} */
    @Override
    public void chargeStorageRent(
            final long totalKvPairs,
            final Map<Long, KvUsageInfo> newUsageInfos,
            final TransactionalLedger<AccountID, AccountProperty, HederaAccount> accounts) {
        if (newUsageInfos.isEmpty()) {
            return;
        }
        final var storagePriceTiers = dynamicProperties.storagePriceTiers();
        if (storagePriceTiers.promotionalOfferCovers(totalKvPairs)) {
            return;
        }
        if (!dynamicProperties.shouldItemizeStorageFees()) {
            chargeStorageFeesInternal(totalKvPairs, newUsageInfos, storagePriceTiers, accounts);
        } else {
            final var wrappedAccounts = activeLedgerWrapping(accounts);
            final var sideEffects = new SideEffectsTracker();
            final var accountsCommitInterceptor = new AccountsCommitInterceptor(sideEffects);
            wrappedAccounts.setCommitInterceptor(accountsCommitInterceptor);

            chargeStorageFeesInternal(
                    totalKvPairs, newUsageInfos, storagePriceTiers, wrappedAccounts);
            wrappedAccounts.commit();

            final var charges = sideEffects.getNetTrackedHbarChanges();
            if (!charges.isEmpty()) {
                final var synthBody = syntheticTxnFactory.synthHbarTransfer(charges);
                final var synthRecord =
                        creator.createSuccessfulSyntheticRecord(NO_CUSTOM_FEES, sideEffects, MEMO);
                recordsHistorian.trackFollowingChildRecord(
                        DEFAULT_SOURCE_ID, synthBody, synthRecord, NO_SIDECARS);
            }
        }
    }

    @VisibleForTesting
    void chargeStorageFeesInternal(
            final long totalKvPairs,
            final Map<Long, KvUsageInfo> newUsageInfos,
            final ContractStoragePriceTiers storagePriceTiers,
            final TransactionalLedger<AccountID, AccountProperty, HederaAccount> accounts) {
        final var now = txnCtx.consensusTime();
        final var rate = exchange.activeRate(now);
        final var thisSecond = now.getEpochSecond();

        if (!newUsageInfos.isEmpty()) {
            newUsageInfos.forEach(
                    (num, usageInfo) -> {
                        if (usageInfo.hasPositiveUsageDelta()) {
                            final var id = keyFor(num);
                            var lifetime = (long) accounts.get(id, EXPIRY) - thisSecond;
                            if (lifetime < 0) {
                                // This is possible if the contract is expired with funds, but
                                // hasn't been visited recently in the auto-renew cycle; we can
                                // use its auto-renew period as an approximation
                                lifetime = (long) accounts.get(id, AUTO_RENEW_PERIOD);
                            }
                            final var fee =
                                    storagePriceTiers.priceOfPendingUsage(
                                            rate, totalKvPairs, lifetime, usageInfo);
                            if (fee > 0) {
                                final var autoRenewId =
                                        (EntityId) accounts.get(id, AUTO_RENEW_ACCOUNT_ID);
                                nonHapiFeeCharging.chargeNonHapiFee(
                                        autoRenewId,
                                        id,
                                        fee,
                                        accounts,
                                        INSUFFICIENT_BALANCES_FOR_STORAGE_RENT);
                            }
                        }
                    });
        }
    }

    private AccountID keyFor(final Long num) {
        return STATIC_PROPERTIES.scopedAccountWith(num);
    }
}
