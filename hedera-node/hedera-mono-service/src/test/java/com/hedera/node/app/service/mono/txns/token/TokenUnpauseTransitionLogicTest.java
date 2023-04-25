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
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_PAUSE_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.store.TypedTokenStore;
import com.hedera.node.app.service.mono.store.models.Id;
import com.hedera.node.app.service.mono.store.models.Token;
import com.hedera.node.app.service.mono.utils.accessors.SignedTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenUnpauseTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TokenUnpauseTransitionLogicTest {
    private long tokenNum = 12345L;
    private TokenID tokenID = IdUtils.asToken("0.0." + tokenNum);
    private Id tokenId = new Id(0, 0, tokenNum);

    private TypedTokenStore tokenStore;
    private TransactionContext txnCtx;
    private SignedTxnAccessor accessor;
    private Token token;

    private TransactionBody tokenUnpauseTxn;
    private TokenUnpauseTransitionLogic subject;

    @BeforeEach
    void setup() {
        tokenStore = mock(TypedTokenStore.class);
        accessor = mock(SignedTxnAccessor.class);
        token = mock(Token.class);

        txnCtx = mock(TransactionContext.class);

        UnpauseLogic unpauseLogic = new UnpauseLogic(tokenStore);
        subject = new TokenUnpauseTransitionLogic(txnCtx, unpauseLogic);
    }

    @Test
    void capturesInvalidPause() {
        givenValidTxnCtx();
        doThrow(new InvalidTransactionException(TOKEN_HAS_NO_PAUSE_KEY))
                .when(token)
                .changePauseStatus(false);

        assertFailsWith(() -> subject.doStateTransition(), TOKEN_HAS_NO_PAUSE_KEY);
        verify(tokenStore, never()).commitToken(token);
    }

    @Test
    void followsHappyPath() {
        givenValidTxnCtx();
        given(token.hasPauseKey()).willReturn(true);

        subject.doStateTransition();

        verify(token).changePauseStatus(false);
        verify(tokenStore).commitToken(token);
    }

    @Test
    void hasCorrectApplicability() {
        givenValidTxnCtx();

        assertTrue(subject.applicability().test(tokenUnpauseTxn));
        assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
    }

    @Test
    void acceptsValidTxn() {
        givenValidTxnCtx();

        assertEquals(OK, subject.semanticCheck().apply(tokenUnpauseTxn));
    }

    @Test
    void rejectsMissingToken() {
        givenMissingToken();

        assertEquals(INVALID_TOKEN_ID, subject.semanticCheck().apply(tokenUnpauseTxn));
    }

    private void givenValidTxnCtx() {
        tokenUnpauseTxn =
                TransactionBody.newBuilder()
                        .setTokenUnpause(TokenUnpauseTransactionBody.newBuilder().setToken(tokenID))
                        .build();
        given(accessor.getTxn()).willReturn(tokenUnpauseTxn);
        given(txnCtx.accessor()).willReturn(accessor);
        given(tokenStore.loadPossiblyPausedToken(tokenId)).willReturn(token);
    }

    private void givenMissingToken() {
        tokenUnpauseTxn =
                TransactionBody.newBuilder()
                        .setTokenUnpause(TokenUnpauseTransactionBody.newBuilder())
                        .build();
    }

    private void assertFailsWith(Runnable something, ResponseCodeEnum status) {
        final var ex = assertThrows(InvalidTransactionException.class, something::run);
        assertEquals(status, ex.getResponseCode());
    }
}
