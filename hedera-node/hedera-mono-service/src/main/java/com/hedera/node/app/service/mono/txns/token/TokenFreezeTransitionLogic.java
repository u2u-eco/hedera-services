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

import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.store.models.Id;
import com.hedera.node.app.service.mono.txns.TransitionLogic;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class TokenFreezeTransitionLogic implements TransitionLogic {
    private final TransactionContext txnCtx;
    private final FreezeLogic freezeLogic;

    @Inject
    public TokenFreezeTransitionLogic(
            final TransactionContext txnCtx, final FreezeLogic freezeLogic) {
        this.txnCtx = txnCtx;
        this.freezeLogic = freezeLogic;
    }

    @Override
    public void doStateTransition() {
        /* --- Translate from gRPC types --- */
        final var op = txnCtx.accessor().getTxn().getTokenFreeze();
        /* --- Convert to model ids --- */
        final var targetTokenId = Id.fromGrpcToken(op.getToken());
        final var targetAccountId = Id.fromGrpcAccount(op.getAccount());
        /* --- Do the business logic --- */
        freezeLogic.freeze(targetTokenId, targetAccountId);
    }

    @Override
    public Predicate<TransactionBody> applicability() {
        return TransactionBody::hasTokenFreeze;
    }

    @Override
    public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
        return this::validate;
    }

    public ResponseCodeEnum validate(TransactionBody txnBody) {
        return freezeLogic.validate(txnBody);
    }
}
