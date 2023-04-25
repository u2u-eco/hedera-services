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

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_KYC_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;

import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.store.AccountStore;
import com.hedera.node.app.service.mono.store.TypedTokenStore;
import com.hedera.node.app.service.mono.store.models.Account;
import com.hedera.node.app.service.mono.store.models.Id;
import com.hedera.node.app.service.mono.store.models.Token;
import com.hedera.node.app.service.mono.store.models.TokenRelationship;
import com.hedera.node.app.service.mono.utils.accessors.SignedTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenRevokeKycTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TokenRevokeKycTransitionLogicTest {
    private long tokenNum = 12345L;
    private long accountNum = 54321L;
    private TokenID tokenID = IdUtils.asToken("0.0." + tokenNum);
    private AccountID accountID = IdUtils.asAccount("0.0." + accountNum);
    private Id tokenId = new Id(0, 0, tokenNum);
    private Id accountId = new Id(0, 0, accountNum);

    private TypedTokenStore tokenStore;
    private AccountStore accountStore;
    private TransactionContext txnCtx;
    private SignedTxnAccessor accessor;
    private TokenRelationship tokenRelationship;
    private Token token;
    private Account account;

    private TransactionBody tokenRevokeKycTxn;
    private TokenRevokeKycTransitionLogic subject;

    @BeforeEach
    void setup() {
        accountStore = mock(AccountStore.class);
        tokenStore = mock(TypedTokenStore.class);
        accessor = mock(SignedTxnAccessor.class);
        tokenRelationship = mock(TokenRelationship.class);
        token = mock(Token.class);
        account = mock(Account.class);

        txnCtx = mock(TransactionContext.class);
        accountStore = mock(AccountStore.class);
        tokenStore = mock(TypedTokenStore.class);

        RevokeKycLogic revokeKycLogic = new RevokeKycLogic(tokenStore, accountStore);

        subject = new TokenRevokeKycTransitionLogic(txnCtx, revokeKycLogic);
    }

    @Test
    void capturesInvalidRevokeKyc() {
        givenValidTxnCtx();
        // and:
        doThrow(new InvalidTransactionException(TOKEN_HAS_NO_KYC_KEY))
                .when(tokenRelationship)
                .changeKycState(false);

        // then:
        assertFailsWith(() -> subject.doStateTransition(), TOKEN_HAS_NO_KYC_KEY);
        verify(tokenStore, never()).commitTokenRelationships(List.of(tokenRelationship));
    }

    @Test
    void followsHappyPath() {
        givenValidTxnCtx();
        // and:
        given(token.hasKycKey()).willReturn(true);

        // when:
        subject.doStateTransition();

        // then:
        verify(tokenRelationship).changeKycState(false);
        verify(tokenStore).commitTokenRelationships(List.of(tokenRelationship));
    }

    @Test
    void hasCorrectApplicability() {
        givenValidTxnCtx();

        // expect:
        assertTrue(subject.applicability().test(tokenRevokeKycTxn));
        assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
    }

    @Test
    void acceptsValidTxn() {
        givenValidTxnCtx();

        // expect:
        assertEquals(OK, subject.semanticCheck().apply(tokenRevokeKycTxn));
    }

    @Test
    void rejectsMissingToken() {
        givenMissingToken();

        // expect:
        assertEquals(INVALID_TOKEN_ID, subject.semanticCheck().apply(tokenRevokeKycTxn));
    }

    @Test
    void rejectsMissingAccount() {
        givenMissingAccount();

        // expect:
        assertEquals(INVALID_ACCOUNT_ID, subject.semanticCheck().apply(tokenRevokeKycTxn));
    }

    private void givenValidTxnCtx() {
        tokenRevokeKycTxn =
                TransactionBody.newBuilder()
                        .setTokenRevokeKyc(
                                TokenRevokeKycTransactionBody.newBuilder()
                                        .setAccount(accountID)
                                        .setToken(tokenID))
                        .build();
        given(accessor.getTxn()).willReturn(tokenRevokeKycTxn);
        given(txnCtx.accessor()).willReturn(accessor);
        given(tokenStore.loadToken(tokenId)).willReturn(token);
        given(accountStore.loadAccount(accountId)).willReturn(account);
        given(tokenStore.loadTokenRelationship(token, account)).willReturn(tokenRelationship);
    }

    private void givenMissingToken() {
        tokenRevokeKycTxn =
                TransactionBody.newBuilder()
                        .setTokenRevokeKyc(TokenRevokeKycTransactionBody.newBuilder())
                        .build();
    }

    private void givenMissingAccount() {
        tokenRevokeKycTxn =
                TransactionBody.newBuilder()
                        .setTokenRevokeKyc(
                                TokenRevokeKycTransactionBody.newBuilder().setToken(tokenID))
                        .build();
    }

    private void assertFailsWith(Runnable something, ResponseCodeEnum status) {
        var ex = assertThrows(InvalidTransactionException.class, something::run);
        assertEquals(status, ex.getResponseCode());
    }
}
