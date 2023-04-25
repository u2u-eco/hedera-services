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
package com.hedera.node.app.service.mono.queries.contract;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.GetBySolidityID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;

import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.queries.AbstractAnswer;
import com.hederahashgraph.api.proto.java.GetBySolidityIDQuery;
import com.hederahashgraph.api.proto.java.GetBySolidityIDResponse;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import edu.umd.cs.findbugs.annotations.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class GetBySolidityIdAnswer extends AbstractAnswer {
    @Inject
    public GetBySolidityIdAnswer() {
        super(
                GetBySolidityID,
                query -> null,
                query -> query.getGetBySolidityID().getHeader().getResponseType(),
                response ->
                        response.getGetBySolidityID().getHeader().getNodeTransactionPrecheckCode(),
                (query, view) -> NOT_SUPPORTED);
    }

    @Override
    public Response responseGiven(
            final Query query,
            @Nullable final StateView view,
            final ResponseCodeEnum validity,
            final long cost) {
        final GetBySolidityIDQuery op = query.getGetBySolidityID();
        final ResponseType type = op.getHeader().getResponseType();

        final GetBySolidityIDResponse.Builder response = GetBySolidityIDResponse.newBuilder();
        if (type == COST_ANSWER) {
            response.setHeader(costAnswerHeader(NOT_SUPPORTED, 0L));
        } else {
            response.setHeader(answerOnlyHeader(NOT_SUPPORTED));
        }
        return Response.newBuilder().setGetBySolidityID(response).build();
    }
}
