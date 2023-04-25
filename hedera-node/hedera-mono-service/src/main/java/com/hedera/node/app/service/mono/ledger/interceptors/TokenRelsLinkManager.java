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
package com.hedera.node.app.service.mono.ledger.interceptors;

import static com.hedera.node.app.service.mono.utils.MapValueListUtils.insertInPlaceAtMapValueListHead;
import static com.hedera.node.app.service.mono.utils.MapValueListUtils.removeInPlaceFromMapValueList;

import com.hedera.node.app.service.mono.state.expiry.TokenRelsListMutation;
import com.hedera.node.app.service.mono.state.migration.AccountStorageAdapter;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.state.migration.HederaTokenRel;
import com.hedera.node.app.service.mono.state.migration.TokenRelStorageAdapter;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.EntityNumPair;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class TokenRelsLinkManager {
    private final Supplier<AccountStorageAdapter> accounts;
    private final Supplier<TokenRelStorageAdapter> tokenRels;

    @Inject
    public TokenRelsLinkManager(
            final Supplier<AccountStorageAdapter> accounts,
            final Supplier<TokenRelStorageAdapter> tokenRels) {
        this.accounts = accounts;
        this.tokenRels = tokenRels;
    }

    /**
     * Updates the linked list in the {@code tokenRels} map for the given account, including its
     * head token number in the {@code accounts} map if needed.
     *
     * <p><b>IMPORTANT:</b> each new {@link HederaTokenRel} must have its {@code numbers} field set;
     * that is, {@link HederaTokenRel#getRelatedTokenNum()} cannot return zero! This contract is
     * respected by the sole client of this class, the {@link LinkAwareTokenRelsCommitInterceptor}.
     *
     * @param accountNum the account whose list is being updated
     * @param dissociatedTokenNums the numbers of the tokens being dissociated
     * @param newTokenRels the new token relationships being created
     */
    void updateLinks(
            final EntityNum accountNum,
            @Nullable final List<EntityNum> dissociatedTokenNums,
            @Nullable final List<HederaTokenRel> newTokenRels) {
        final var primitiveNum = accountNum.longValue();
        final var curTokenRels = tokenRels.get();
        final var listMutation = new TokenRelsListMutation(primitiveNum, curTokenRels);

        final var curAccounts = accounts.get();
        final var mutableAccount = curAccounts.getForModify(accountNum);
        var rootKey = rootKeyOf(primitiveNum, mutableAccount);
        if (rootKey != null && dissociatedTokenNums != null) {
            for (final var tokenNum : dissociatedTokenNums) {
                final var tbdKey = EntityNumPair.fromNums(accountNum, tokenNum);
                rootKey = removeInPlaceFromMapValueList(tbdKey, rootKey, listMutation);
            }
        }
        if (newTokenRels != null) {
            HederaTokenRel rootRel = null;
            for (final var newRel : newTokenRels) {
                final var literalTokenNum = newRel.getRelatedTokenNum();
                final var newKey = EntityNumPair.fromLongs(primitiveNum, literalTokenNum);
                rootKey =
                        insertInPlaceAtMapValueListHead(
                                newKey, newRel, rootKey, rootRel, listMutation);
                rootRel = newRel;
            }
        }
        final var newHeadTokenId = (rootKey == null) ? 0 : rootKey.getLowOrderAsLong();
        mutableAccount.setHeadTokenId(newHeadTokenId);
    }

    @Nullable
    private EntityNumPair rootKeyOf(final long primitiveNum, final HederaAccount account) {
        final var headNum = account.getHeadTokenId();
        return headNum == 0 ? null : EntityNumPair.fromLongs(primitiveNum, headNum);
    }
}
