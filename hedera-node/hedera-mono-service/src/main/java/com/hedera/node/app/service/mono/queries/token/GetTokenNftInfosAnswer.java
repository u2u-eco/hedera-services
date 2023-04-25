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
package com.hedera.node.app.service.mono.queries.token;

import static com.hedera.node.app.service.mono.queries.token.GetAccountNftInfosAnswer.IRRELEVANT_PAYMENT_EXTRACTOR;
import static com.hedera.node.app.service.mono.queries.token.GetAccountNftInfosAnswer.IRRELEVANT_RESPONSE_TYPE_EXTRACTOR;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;

import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.queries.AbstractAnswer;
import com.hedera.node.app.service.mono.utils.accessors.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.TokenGetNftInfosQuery;
import com.hederahashgraph.api.proto.java.TokenGetNftInfosResponse;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class GetTokenNftInfosAnswer extends AbstractAnswer {
    @Inject
    public GetTokenNftInfosAnswer() {
        super(
                HederaFunctionality.TokenGetNftInfos,
                IRRELEVANT_PAYMENT_EXTRACTOR,
                IRRELEVANT_RESPONSE_TYPE_EXTRACTOR,
                response ->
                        response.getTokenGetNftInfos().getHeader().getNodeTransactionPrecheckCode(),
                (query, view) -> NOT_SUPPORTED);
    }

    @Override
    public Response responseGiven(
            final Query query,
            @Nullable final StateView view,
            final ResponseCodeEnum validity,
            final long cost) {
        final TokenGetNftInfosQuery op = query.getTokenGetNftInfos();
        final TokenGetNftInfosResponse.Builder response = TokenGetNftInfosResponse.newBuilder();

        final ResponseType type = op.getHeader().getResponseType();
        if (type == COST_ANSWER) {
            response.setHeader(costAnswerHeader(NOT_SUPPORTED, 0L));
        } else {
            response.setHeader(answerOnlyHeader(NOT_SUPPORTED));
        }
        return Response.newBuilder().setTokenGetNftInfos(response).build();
    }

    @Override
    public Optional<SignedTxnAccessor> extractPaymentFrom(final Query query) {
        return Optional.empty();
    }

    @Override
    public boolean needsAnswerOnlyCost(final Query query) {
        return false;
    }

    @Override
    public boolean requiresNodePayment(final Query query) {
        return false;
    }
}
