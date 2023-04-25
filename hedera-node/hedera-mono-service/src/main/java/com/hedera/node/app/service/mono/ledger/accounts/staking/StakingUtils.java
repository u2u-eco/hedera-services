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
package com.hedera.node.app.service.mono.ledger.accounts.staking;

import static com.hedera.node.app.service.mono.ledger.accounts.HederaAccountCustomizer.STAKED_ACCOUNT_ID_CASE;
import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.DECLINE_REWARD;
import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.IS_DELETED;
import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.STAKED_ID;
import static com.hedera.node.app.service.mono.utils.Units.HBARS_TO_TINYBARS;

import com.hedera.node.app.service.mono.ledger.EntityChangeSet;
import com.hedera.node.app.service.mono.ledger.properties.AccountProperty;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hederahashgraph.api.proto.java.AccountID;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Map;
import java.util.function.Predicate;

public class StakingUtils {
    private static final Predicate<HederaAccount> HAS_BEEN_DELETED = HederaAccount::isDeleted;
    private static final Predicate<HederaAccount> IS_DECLINING_REWARD =
            HederaAccount::isDeclinedReward;

    // Sentinel value for a field that wasn't applicable to this transaction
    public static final long NA = Long.MIN_VALUE;
    // A non-sentinel negative value to indicate an account has not been rewarded since its last
    // stake meta change
    public static final long NOT_REWARDED_SINCE_LAST_STAKING_META_CHANGE = -1;

    private StakingUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static long getAccountStakeeNum(@NonNull final Map<AccountProperty, Object> changes) {
        final var entityId = (long) changes.getOrDefault(STAKED_ID, 0L);
        // Node ids are negative and account ids are positive
        return (entityId < 0) ? 0 : entityId;
    }

    public static long getNodeStakeeNum(@NonNull final Map<AccountProperty, Object> changes) {
        final var entityId = (long) changes.getOrDefault(STAKED_ID, 0L);
        // Node ids are negative
        return (entityId < 0) ? entityId : 0;
    }

    public static long finalBalanceGiven(
            @Nullable final HederaAccount account,
            @NonNull final Map<AccountProperty, Object> changes) {
        if (changes.containsKey(BALANCE)) {
            return (long) changes.get(BALANCE);
        } else {
            return (account == null) ? 0 : account.getBalance();
        }
    }

    public static boolean finalDeclineRewardGiven(
            @Nullable final HederaAccount account,
            @NonNull final Map<AccountProperty, Object> changes) {
        return internalFinalBooleanGiven(account, changes, DECLINE_REWARD, IS_DECLINING_REWARD);
    }

    public static boolean finalIsDeletedGiven(
            @Nullable final HederaAccount account,
            @NonNull final Map<AccountProperty, Object> changes) {
        return internalFinalBooleanGiven(account, changes, IS_DELETED, HAS_BEEN_DELETED);
    }

    private static boolean internalFinalBooleanGiven(
            @Nullable final HederaAccount account,
            @NonNull final Map<AccountProperty, Object> changes,
            @NonNull final AccountProperty property,
            @NonNull final Predicate<HederaAccount> predicate) {
        if (changes.containsKey(property)) {
            return (Boolean) changes.get(property);
        } else {
            return account != null && predicate.test(account);
        }
    }

    public static long finalStakedToMeGiven(
            final int stakeeI,
            @Nullable final HederaAccount account,
            @NonNull final long[] stakedToMeUpdates) {
        if (stakedToMeUpdates[stakeeI] != NA) {
            return stakedToMeUpdates[stakeeI];
        } else {
            return (account == null) ? 0 : account.getStakedToMe();
        }
    }

    public static void updateStakedToMe(
            final int stakeeI,
            final long delta,
            @NonNull final long[] stakedToMeUpdates,
            @NonNull
                    final EntityChangeSet<AccountID, HederaAccount, AccountProperty>
                            pendingChanges) {
        if (stakedToMeUpdates[stakeeI] != NA) {
            stakedToMeUpdates[stakeeI] += delta;
        } else {
            // In theory this could be null if a multi-step contract operation created an account
            // and then staked to it
            final var account = pendingChanges.entity(stakeeI);
            final var alreadyStaked = account == null ? 0L : account.getStakedToMe();
            stakedToMeUpdates[stakeeI] = alreadyStaked + delta;
        }
    }

    public static void updateBalance(
            final long delta,
            final int rewardAccountI,
            @NonNull
                    final EntityChangeSet<AccountID, HederaAccount, AccountProperty>
                            pendingChanges) {
        final var mutableChanges = pendingChanges.changes(rewardAccountI);
        if (mutableChanges.containsKey(BALANCE)) {
            mutableChanges.put(BALANCE, (long) mutableChanges.get(BALANCE) + delta);
        } else {
            final var newBalance = pendingChanges.entity(rewardAccountI).getBalance() + delta;
            mutableChanges.put(BALANCE, newBalance);
        }
    }

    public static boolean hasStakeMetaChanges(
            @NonNull final Map<AccountProperty, Object> changes,
            @Nullable final HederaAccount account) {
        return (changes.containsKey(DECLINE_REWARD)
                        && (account == null
                                || account.isDeclinedReward()
                                        != (boolean) changes.get(DECLINE_REWARD)))
                || (changes.containsKey(STAKED_ID)
                        && (account == null
                                || account.getStakedId() != (long) changes.get(STAKED_ID)));
    }

    public static long roundedToHbar(final long value) {
        return (value / HBARS_TO_TINYBARS) * HBARS_TO_TINYBARS;
    }

    public static boolean validSentinel(
            final String idCase, final AccountID stakedAccountId, final long stakedNodeId) {
        // sentinel values on -1 for stakedNodeId and 0.0.0 for stakedAccountId are used to reset
        // staking on an account
        if (idCase.matches(STAKED_ACCOUNT_ID_CASE)) {
            final var sentinelAccount = EntityId.fromIdentityCode(0).toGrpcAccountId();
            return stakedAccountId.equals(sentinelAccount);
        } else {
            return stakedNodeId == -1;
        }
    }
}
