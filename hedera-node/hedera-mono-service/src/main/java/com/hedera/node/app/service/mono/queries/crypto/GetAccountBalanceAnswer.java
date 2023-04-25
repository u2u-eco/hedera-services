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

import static com.hedera.node.app.service.mono.context.primitives.StateView.doBoundedIteration;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.asAccount;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.isAlias;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetAccountBalance;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.ledger.accounts.AliasManager;
import com.hedera.node.app.service.mono.queries.AnswerService;
import com.hedera.node.app.service.mono.state.migration.AccountStorageAdapter;
import com.hedera.node.app.service.mono.txns.validation.OptionValidator;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.accessors.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.CryptoGetAccountBalanceQuery;
import com.hederahashgraph.api.proto.java.CryptoGetAccountBalanceResponse;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenBalance;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class GetAccountBalanceAnswer implements AnswerService {
    private final AliasManager aliasManager;
    private final OptionValidator optionValidator;
    private final GlobalDynamicProperties dynamicProperties;

    @Inject
    public GetAccountBalanceAnswer(
            final AliasManager aliasManager,
            final OptionValidator optionValidator,
            final GlobalDynamicProperties dynamicProperties) {
        this.aliasManager = aliasManager;
        this.optionValidator = optionValidator;
        this.dynamicProperties = dynamicProperties;
    }

    @Override
    public ResponseCodeEnum checkValidity(final Query query, final StateView view) {
        final AccountStorageAdapter accounts = view.accounts();
        final CryptoGetAccountBalanceQuery op = query.getCryptogetAccountBalance();
        return validityOf(op, accounts);
    }

    @Override
    public boolean requiresNodePayment(final Query query) {
        return false;
    }

    @Override
    public boolean needsAnswerOnlyCost(final Query query) {
        return false;
    }

    @Override
    public Response responseGiven(
            final Query query,
            @Nullable final StateView view,
            final ResponseCodeEnum validity,
            final long cost) {
        final CryptoGetAccountBalanceQuery op = query.getCryptogetAccountBalance();

        final var id = targetOf(op);
        final CryptoGetAccountBalanceResponse.Builder opAnswer =
                CryptoGetAccountBalanceResponse.newBuilder()
                        .setHeader(answerOnlyHeader(validity))
                        .setAccountID(id);

        if (validity == OK) {
            final var accounts = Objects.requireNonNull(view).accounts();
            final var key = EntityNum.fromAccountId(id);
            final var account = accounts.get(key);
            opAnswer.setBalance(account.getBalance());
            final var maxRels = dynamicProperties.maxTokensRelsPerInfoQuery();
            final var firstRel = account.getLatestAssociation();
            doBoundedIteration(
                    view.tokenAssociations(),
                    view.tokens(),
                    firstRel,
                    maxRels,
                    (token, rel) ->
                            opAnswer.addTokenBalances(
                                    TokenBalance.newBuilder()
                                            .setTokenId(token.grpcId())
                                            .setDecimals(token.decimals())
                                            .setBalance(rel.getBalance())
                                            .build()));
        }

        return Response.newBuilder().setCryptogetAccountBalance(opAnswer).build();
    }

    @Override
    public Optional<SignedTxnAccessor> extractPaymentFrom(final Query query) {
        return Optional.empty();
    }

    private ResponseCodeEnum validityOf(
            final CryptoGetAccountBalanceQuery op, final AccountStorageAdapter accounts) {
        if (op.hasContractID()) {
            final var effId = resolvedContract(op.getContractID());
            return optionValidator.queryableContractStatus(effId, accounts);
        } else if (op.hasAccountID()) {
            final var effId = resolvedNonContract(op.getAccountID());
            return optionValidator.queryableAccountStatus(effId, accounts);
        } else {
            return INVALID_ACCOUNT_ID;
        }
    }

    private AccountID targetOf(final CryptoGetAccountBalanceQuery op) {
        if (op.hasContractID()) {
            return asAccount(resolvedContract(op.getContractID()));
        } else {
            return resolvedNonContract(op.getAccountID());
        }
    }

    private AccountID resolvedNonContract(final AccountID idOrAlias) {
        if (isAlias(idOrAlias)) {
            final var id = aliasManager.lookupIdBy(idOrAlias.getAlias());
            return id.toGrpcAccountId();
        } else {
            return idOrAlias;
        }
    }

    private ContractID resolvedContract(final ContractID idOrAlias) {
        if (isAlias(idOrAlias)) {
            final var id = aliasManager.lookupIdBy(idOrAlias.getEvmAddress());
            return id.toGrpcContractID();
        } else {
            return idOrAlias;
        }
    }

    @Override
    public ResponseCodeEnum extractValidityFrom(final Response response) {
        return response.getCryptogetAccountBalance().getHeader().getNodeTransactionPrecheckCode();
    }

    @Override
    public HederaFunctionality canonicalFunction() {
        return CryptoGetAccountBalance;
    }
}
