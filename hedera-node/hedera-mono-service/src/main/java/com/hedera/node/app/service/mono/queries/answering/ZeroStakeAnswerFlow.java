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
package com.hedera.node.app.service.mono.queries.answering;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.queries.AnswerFlow;
import com.hedera.node.app.service.mono.queries.AnswerService;
import com.hedera.node.app.service.mono.throttling.FunctionalityThrottling;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import java.util.function.Supplier;

public class ZeroStakeAnswerFlow implements AnswerFlow {
    private final Supplier<StateView> stateViews;
    private final QueryHeaderValidity queryHeaderValidity;
    private final FunctionalityThrottling throttles;

    public ZeroStakeAnswerFlow(
            QueryHeaderValidity queryHeaderValidity,
            Supplier<StateView> stateViews,
            FunctionalityThrottling throttles) {
        this.queryHeaderValidity = queryHeaderValidity;
        this.stateViews = stateViews;
        this.throttles = throttles;
    }

    @Override
    public Response satisfyUsing(AnswerService service, Query query) {
        var view = stateViews.get();

        if (throttles.shouldThrottleQuery(service.canonicalFunction(), query)) {
            return service.responseGiven(query, view, BUSY);
        }

        var validity = queryHeaderValidity.checkHeader(query);
        if (validity == OK) {
            validity = service.checkValidity(query, view);
        }

        return service.responseGiven(query, view, validity);
    }
}
