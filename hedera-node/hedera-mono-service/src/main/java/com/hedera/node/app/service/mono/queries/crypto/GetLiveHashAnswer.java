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
package com.hedera.node.app.service.mono.queries.crypto;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetLiveHash;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;

import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.queries.AnswerService;
import com.hedera.node.app.service.mono.utils.accessors.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.CryptoGetLiveHashQuery;
import com.hederahashgraph.api.proto.java.CryptoGetLiveHashResponse;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class GetLiveHashAnswer implements AnswerService {
    @Inject
    public GetLiveHashAnswer() {
        // Default Constructor
    }

    @Override
    public Response responseGiven(
            final Query query,
            @Nullable final StateView view,
            final ResponseCodeEnum validity,
            final long cost) {
        final CryptoGetLiveHashQuery op = query.getCryptoGetLiveHash();
        final ResponseType type = op.getHeader().getResponseType();

        final CryptoGetLiveHashResponse.Builder response = CryptoGetLiveHashResponse.newBuilder();
        if (type == COST_ANSWER) {
            response.setHeader(costAnswerHeader(NOT_SUPPORTED, 0L));
        } else {
            response.setHeader(answerOnlyHeader(NOT_SUPPORTED));
        }
        return Response.newBuilder().setCryptoGetLiveHash(response).build();
    }

    @Override
    public ResponseCodeEnum extractValidityFrom(final Response response) {
        return response.getCryptoGetLiveHash().getHeader().getNodeTransactionPrecheckCode();
    }

    @Override
    public ResponseCodeEnum checkValidity(final Query query, final StateView view) {
        return NOT_SUPPORTED;
    }

    @Override
    public HederaFunctionality canonicalFunction() {
        return CryptoGetLiveHash;
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
