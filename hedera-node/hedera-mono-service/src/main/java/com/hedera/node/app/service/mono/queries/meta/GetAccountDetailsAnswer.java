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
package com.hedera.node.app.service.mono.queries.meta;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.GetAccountDetails;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;

import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.ledger.accounts.AliasManager;
import com.hedera.node.app.service.mono.queries.AnswerService;
import com.hedera.node.app.service.mono.txns.validation.OptionValidator;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.accessors.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.GetAccountDetailsQuery;
import com.hederahashgraph.api.proto.java.GetAccountDetailsResponse;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Transaction;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;
import java.util.Optional;
import javax.inject.Inject;

public class GetAccountDetailsAnswer implements AnswerService {
    private final OptionValidator optionValidator;
    private final AliasManager aliasManager;
    private final GlobalDynamicProperties dynamicProperties;

    @Inject
    public GetAccountDetailsAnswer(
            final OptionValidator optionValidator,
            final AliasManager aliasManager,
            final GlobalDynamicProperties dynamicProperties) {
        this.optionValidator = optionValidator;
        this.aliasManager = aliasManager;
        this.dynamicProperties = dynamicProperties;
    }

    @Override
    public ResponseCodeEnum checkValidity(final Query query, final StateView view) {
        final AccountID id = query.getAccountDetails().getAccountId();
        final var entityNum =
                id.getAlias().isEmpty()
                        ? EntityNum.fromAccountId(id)
                        : aliasManager.lookupIdBy(id.getAlias());
        return optionValidator.queryableAccountOrContractStatus(entityNum, view.accounts());
    }

    @Override
    public Response responseGiven(
            final Query query,
            final @Nullable StateView view,
            final ResponseCodeEnum validity,
            final long cost) {
        final GetAccountDetailsQuery op = query.getAccountDetails();
        final GetAccountDetailsResponse.Builder response = GetAccountDetailsResponse.newBuilder();

        final ResponseType type = op.getHeader().getResponseType();
        if (validity != OK) {
            response.setHeader(header(validity, type, cost));
        } else {
            if (type == COST_ANSWER) {
                response.setHeader(costAnswerHeader(OK, cost));
            } else {
                final AccountID id = op.getAccountId();
                final var optionalDetails =
                        Objects.requireNonNull(view)
                                .accountDetails(
                                        id,
                                        aliasManager,
                                        dynamicProperties.maxTokensRelsPerInfoQuery());
                if (optionalDetails.isPresent()) {
                    response.setHeader(answerOnlyHeader(OK));
                    response.setAccountDetails(optionalDetails.get());
                } else {
                    response.setHeader(answerOnlyHeader(FAIL_INVALID));
                }
            }
        }
        return Response.newBuilder().setAccountDetails(response).build();
    }

    @Override
    public boolean needsAnswerOnlyCost(final Query query) {
        return COST_ANSWER == query.getAccountDetails().getHeader().getResponseType();
    }

    @Override
    public boolean requiresNodePayment(final Query query) {
        return typicallyRequiresNodePayment(
                query.getAccountDetails().getHeader().getResponseType());
    }

    @Override
    public Optional<SignedTxnAccessor> extractPaymentFrom(final Query query) {
        final Transaction paymentTxn = query.getAccountDetails().getHeader().getPayment();
        return Optional.of(SignedTxnAccessor.uncheckedFrom(paymentTxn));
    }

    @Override
    public ResponseCodeEnum extractValidityFrom(final Response response) {
        return response.getAccountDetails().getHeader().getNodeTransactionPrecheckCode();
    }

    @Override
    public HederaFunctionality canonicalFunction() {
        return GetAccountDetails;
    }
}
