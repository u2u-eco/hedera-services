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
package com.hedera.node.app.service.mono.txns.token;

import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.store.AccountStore;
import com.hedera.node.app.service.mono.store.TypedTokenStore;
import com.hedera.node.app.service.mono.store.models.Id;
import com.hedera.node.app.service.mono.store.models.OwnershipTracker;
import com.hedera.node.app.service.mono.utils.accessors.TokenWipeAccessor;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenWipeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class WipeLogic {
    private final TypedTokenStore tokenStore;
    private final AccountStore accountStore;
    private final GlobalDynamicProperties dynamicProperties;

    @Inject
    public WipeLogic(
            final TypedTokenStore tokenStore,
            final AccountStore accountStore,
            final GlobalDynamicProperties dynamicProperties) {
        this.tokenStore = tokenStore;
        this.accountStore = accountStore;
        this.dynamicProperties = dynamicProperties;
    }

    public void wipe(
            final Id targetTokenId,
            final Id targetAccountId,
            final long amount,
            List<Long> serialNumbersList) {
        // De-duplicate serial numbers
        serialNumbersList = new ArrayList<>(new LinkedHashSet<>(serialNumbersList));

        /* --- Load the model objects --- */
        final var token = tokenStore.loadToken(targetTokenId);
        final var account = accountStore.loadAccount(targetAccountId);
        final var accountRel = tokenStore.loadTokenRelationship(token, account);

        /* --- Instantiate change trackers --- */
        final var ownershipTracker = new OwnershipTracker();

        /* --- Do the business logic --- */
        if (token.getType().equals(TokenType.FUNGIBLE_COMMON)) {
            token.wipe(accountRel, amount);
        } else {
            tokenStore.loadUniqueTokens(token, serialNumbersList);
            token.wipe(ownershipTracker, accountRel, serialNumbersList);
        }
        /* --- Persist the updated models --- */
        tokenStore.commitToken(token);
        tokenStore.commitTokenRelationships(List.of(accountRel));
        tokenStore.commitTrackers(ownershipTracker);
        accountStore.commitAccount(account);
    }

    public ResponseCodeEnum validateSyntax(final TransactionBody txn) {
        TokenWipeAccountTransactionBody op = txn.getTokenWipe();
        return TokenWipeAccessor.validateSyntax(
                op, dynamicProperties.areNftsEnabled(), dynamicProperties.maxBatchSizeWipe());
    }
}
