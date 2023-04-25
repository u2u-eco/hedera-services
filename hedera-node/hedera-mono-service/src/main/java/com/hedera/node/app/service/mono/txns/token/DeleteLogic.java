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

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.node.app.service.mono.ledger.SigImpactHistorian;
import com.hedera.node.app.service.mono.store.AccountStore;
import com.hedera.node.app.service.mono.store.TypedTokenStore;
import com.hedera.node.app.service.mono.store.models.Id;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import javax.inject.Inject;

public class DeleteLogic {
    private final AccountStore accountStore;
    private final TypedTokenStore tokenStore;
    private final SigImpactHistorian sigImpactHistorian;

    @Inject
    public DeleteLogic(
            final AccountStore accountStore,
            final TypedTokenStore tokenStore,
            final SigImpactHistorian sigImpactHistorian) {
        this.tokenStore = tokenStore;
        this.accountStore = accountStore;
        this.sigImpactHistorian = sigImpactHistorian;
    }

    public void delete(TokenID grpcTokenId) {
        // --- Convert to model id ---
        final var targetTokenId = Id.fromGrpcToken(grpcTokenId);
        // --- Load the model object ---
        final var loadedToken = tokenStore.loadToken(targetTokenId);

        // --- Do the business logic ---
        loadedToken.delete();

        // --- Persist the updated model ---
        tokenStore.commitToken(loadedToken);
        accountStore.commitAccount(loadedToken.getTreasury());
        sigImpactHistorian.markEntityChanged(grpcTokenId.getTokenNum());
    }

    public ResponseCodeEnum validate(final TransactionBody txnBody) {
        final TokenDeleteTransactionBody op = txnBody.getTokenDeletion();

        if (!op.hasToken()) {
            return INVALID_TOKEN_ID;
        }

        return OK;
    }
}
