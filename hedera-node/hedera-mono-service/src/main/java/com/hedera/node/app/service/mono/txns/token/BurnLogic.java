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
package com.hedera.node.app.service.mono.txns.token;

import static com.hedera.node.app.service.mono.txns.token.TokenOpsValidator.validateTokenOpsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_BURN_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;

import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.store.AccountStore;
import com.hedera.node.app.service.mono.store.TypedTokenStore;
import com.hedera.node.app.service.mono.store.models.Id;
import com.hedera.node.app.service.mono.store.models.OwnershipTracker;
import com.hedera.node.app.service.mono.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenBurnTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class BurnLogic {
    private final OptionValidator validator;
    private final TypedTokenStore tokenStore;
    private final AccountStore accountStore;
    private final GlobalDynamicProperties dynamicProperties;

    @Inject
    public BurnLogic(
            final OptionValidator validator,
            final TypedTokenStore tokenStore,
            final AccountStore accountStore,
            final GlobalDynamicProperties dynamicProperties) {
        this.validator = validator;
        this.tokenStore = tokenStore;
        this.accountStore = accountStore;
        this.dynamicProperties = dynamicProperties;
    }

    public void burn(final Id targetId, final long amount, List<Long> serialNumbersList) {
        // De-duplicate serial numbers
        serialNumbersList = new ArrayList<>(new LinkedHashSet<>(serialNumbersList));

        /* --- Load the models --- */
        final var token = tokenStore.loadToken(targetId);
        final var treasuryRel = tokenStore.loadTokenRelationship(token, token.getTreasury());
        final var ownershipTracker = new OwnershipTracker();
        /* --- Do the business logic --- */
        if (token.getType().equals(TokenType.FUNGIBLE_COMMON)) {
            token.burn(treasuryRel, amount);
        } else {
            tokenStore.loadUniqueTokens(token, serialNumbersList);
            token.burn(ownershipTracker, treasuryRel, serialNumbersList);
        }
        /* --- Persist the updated models --- */
        tokenStore.commitToken(token);
        tokenStore.commitTokenRelationships(List.of(treasuryRel));
        tokenStore.commitTrackers(ownershipTracker);
        accountStore.commitAccount(token.getTreasury());
    }

    public ResponseCodeEnum validateSyntax(final TransactionBody txn) {
        final TokenBurnTransactionBody op = txn.getTokenBurn();

        if (!op.hasToken()) {
            return INVALID_TOKEN_ID;
        }

        return validateTokenOpsWith(
                op.getSerialNumbersCount(),
                op.getAmount(),
                dynamicProperties.areNftsEnabled(),
                INVALID_TOKEN_BURN_AMOUNT,
                op.getSerialNumbersList(),
                validator::maxBatchSizeBurnCheck);
    }
}
