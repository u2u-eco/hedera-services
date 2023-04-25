/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.state.expiry.removal;

import static com.hedera.test.utils.TxnUtils.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.node.app.service.mono.state.expiry.classification.EntityLookup;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.state.merkle.MerkleTokenRelStatus;
import com.hedera.node.app.service.mono.state.merkle.MerkleUniqueToken;
import com.hedera.node.app.service.mono.state.migration.TokenRelStorageAdapter;
import com.hedera.node.app.service.mono.state.migration.UniqueTokenAdapter;
import com.hedera.node.app.service.mono.state.migration.UniqueTokenMapAdapter;
import com.hedera.node.app.service.mono.state.submerkle.CurrencyAdjustments;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.NftAdjustments;
import com.hedera.node.app.service.mono.state.submerkle.RichInstant;
import com.hedera.node.app.service.mono.store.models.NftId;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.EntityNumPair;
import com.hedera.node.app.service.mono.utils.NftNumPair;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TreasuryReturnHelperTest {
    @Mock private TokenRelStorageAdapter tokenRels;
    @Mock private UniqueTokenMapAdapter nfts;
    @Mock private EntityLookup lookup;
    @Mock private MerkleAccount treasury;
    @Mock private MerkleTokenRelStatus rel;

    private final List<CurrencyAdjustments> returnTransfers = new ArrayList<>();
    private final List<EntityId> tokenTypes = new ArrayList<>();
    private final List<NftAdjustments> returnExchanges = new ArrayList<>();

    private TreasuryReturnHelper subject;

    @BeforeEach
    void setUp() {
        subject = new TreasuryReturnHelper(lookup, () -> tokenRels);
    }

    @Test
    void justInsertsBurnIfTokenIsDeleted() {
        final var didReturn =
                subject.updateNftReturns(
                        expiredAccountNum,
                        deletedTokenNum,
                        deletedToken,
                        serialNo,
                        tokenTypes,
                        returnExchanges);

        final var ttls =
                List.of(
                        burnExchangeOf(
                                deletedTokenNum.toGrpcTokenId(),
                                expiredAccountNum.toGrpcAccountId(),
                                serialNo));
        assertFalse(didReturn);
        assertEquals(exchangesFrom(ttls), returnExchanges);
        assertEquals(1, tokenTypes.size());
    }

    @Test
    void namejustRemovesIfWasBurn() {
        given(nfts.get(aNftKey)).willReturn(someNft);
        someNft.setNext(bNftKey.asNftNumPair());

        final var newRoot = subject.burnOrReturnNft(true, aNftKey, nfts);

        verify(nfts).remove(aNftKey);
        assertEquals(bNftKey, newRoot);
    }

    @Test
    void clearsOwnerIfNotBurn() {
        given(nfts.getForModify(aNftKey)).willReturn(someNft);
        someNft.setNext(NftNumPair.MISSING_NFT_NUM_PAIR);

        final var newRoot = subject.burnOrReturnNft(false, aNftKey, nfts);

        verify(nfts, never()).remove(aNftKey);
        assertEquals(EntityId.MISSING_ENTITY_ID, someNft.getOwner());
        assertNull(newRoot);
    }

    @Test
    void worksAroundNullNext() {
        given(nfts.getForModify(aNftKey)).willReturn(someNft);
        someNft.setNext(null);

        final var newRoot = subject.burnOrReturnNft(false, aNftKey, nfts);

        verify(nfts, never()).remove(aNftKey);
        assertEquals(EntityId.MISSING_ENTITY_ID, someNft.getOwner());
        assertNull(newRoot);
    }

    @Test
    void justAppendsBurnIfTokenIsDeleted() {
        final List<EntityId> tokenTypes = new ArrayList<>();
        tokenTypes.add(deletedTokenNum.toEntityId());
        returnExchanges.add(new NftAdjustments());

        final var didReturn =
                subject.updateNftReturns(
                        expiredAccountNum,
                        deletedTokenNum,
                        deletedToken,
                        serialNo,
                        tokenTypes,
                        returnExchanges);

        final var ttls =
                List.of(
                        burnExchangeOf(
                                deletedTokenNum.toGrpcTokenId(),
                                expiredAccountNum.toGrpcAccountId(),
                                serialNo));
        assertFalse(didReturn);
        assertEquals(exchangesFrom(ttls), returnExchanges);
        assertEquals(1, tokenTypes.size());
    }

    @Test
    void appendsAndChangedNumOwnedNftsIfTokenNotDeleted() {
        final List<EntityId> tokenTypes = new ArrayList<>();
        tokenTypes.add(nonFungibleTokenNum.toEntityId());
        returnExchanges.add(new NftAdjustments());
        given(lookup.getMutableAccount(treasuryNum)).willReturn(treasury);
        given(
                        tokenRels.getForModify(
                                EntityNumPair.fromLongs(
                                        treasuryNum.longValue(), nonFungibleTokenNum.longValue())))
                .willReturn(rel);
        given(treasury.getNftsOwned()).willReturn(0L);
        given(rel.getBalance()).willReturn(0L);

        final var didReturn =
                subject.updateNftReturns(
                        expiredAccountNum,
                        nonFungibleTokenNum,
                        nonFungibleToken,
                        serialNo,
                        tokenTypes,
                        returnExchanges);

        final var ttls =
                List.of(
                        returnExchangeOf(
                                nonFungibleTokenNum.toGrpcTokenId(),
                                expiredAccountNum.toGrpcAccountId(),
                                treasuryNum.toGrpcAccountId(),
                                serialNo));
        assertTrue(didReturn);
        assertEquals(exchangesFrom(ttls), returnExchanges);
        assertEquals(1, tokenTypes.size());
        verify(treasury).setNftsOwned(1L);
        verify(rel).setBalance(1L);
    }

    @Test
    void justReportsDebitIfTokenIsDeleted() {
        subject.updateFungibleReturns(
                expiredAccountNum,
                deletedTokenNum,
                deletedToken,
                tokenBalance,
                returnTransfers,
                tokenRels);

        final var ttls =
                List.of(
                        asymmetricTtlOf(
                                deletedTokenNum.toGrpcTokenId(),
                                expiredAccountNum.toGrpcAccountId(),
                                tokenBalance));
        assertEquals(adjustmentsFrom(ttls), returnTransfers);
    }

    @Test
    void doesBurnForNonzeroFungibleBalanceButWithBadTreasuryRel() {
        subject.updateFungibleReturns(
                expiredAccountNum,
                fungibleTokenNum,
                fungibleToken,
                tokenBalance,
                returnTransfers,
                tokenRels);

        final var ttls =
                List.of(
                        asymmetricTtlOf(
                                deletedTokenNum.toGrpcTokenId(),
                                expiredAccountNum.toGrpcAccountId(),
                                tokenBalance));
        assertEquals(adjustmentsFrom(ttls), returnTransfers);
    }

    @Test
    void doesTreasuryReturnForNonzeroFungibleBalance() {
        final var treasuryRel = mutableRel(tokenBalance);
        givenModifiableRelPresent(treasuryNum, fungibleTokenNum, treasuryRel);

        subject.updateFungibleReturns(
                expiredAccountNum,
                fungibleTokenNum,
                fungibleToken,
                tokenBalance,
                returnTransfers,
                tokenRels);

        final var ttls =
                List.of(
                        ttlOf(
                                survivedTokenGrpcId,
                                expiredAccountNum.toGrpcAccountId(),
                                treasuryId.toGrpcAccountId(),
                                tokenBalance));
        assertEquals(adjustmentsFrom(ttls), returnTransfers);
        assertEquals(2 * tokenBalance, treasuryRel.getBalance());
    }

    @Test
    void ordersTreasuryReturnsByAccountNumber() {
        final var treasuryRel = mutableRel(tokenBalance);
        givenModifiableRelPresent(treasuryNum, fungibleTokenNum, treasuryRel);

        subject.updateFungibleReturns(
                olderExpiredAccountNum,
                fungibleTokenNum,
                fungibleToken,
                tokenBalance,
                returnTransfers,
                tokenRels);

        final var ttls =
                List.of(
                        ttlOf(
                                survivedTokenGrpcId,
                                treasuryId.toGrpcAccountId(),
                                olderExpiredAccountNum.toGrpcAccountId(),
                                -tokenBalance));
        assertEquals(adjustmentsFrom(ttls), returnTransfers);
        assertEquals(2 * tokenBalance, treasuryRel.getBalance());
    }

    private void givenModifiableRelPresent(
            EntityNum account, EntityNum token, MerkleTokenRelStatus mutableRel) {
        var rel = EntityNumPair.fromLongs(account.longValue(), token.longValue());
        given(tokenRels.getForModify(rel)).willReturn(mutableRel);
    }

    private MerkleTokenRelStatus mutableRel(long balance) {
        return new MerkleTokenRelStatus(balance, false, false, true);
    }

    private final long serialNo = 666L;
    private final long tokenBalance = 1_234L;
    private final EntityId expiredTreasuryId = new EntityId(0, 0, 2L);
    private final EntityNum treasuryNum = EntityNum.fromLong(666L);
    private final EntityNum expiredAccountNum = expiredTreasuryId.asNum();
    private final EntityNum olderExpiredAccountNum = EntityNum.fromLong(1_000_000L);
    private final EntityNum deletedTokenNum = EntityNum.fromLong(1234L);
    private final EntityNum fungibleTokenNum = EntityNum.fromLong(4321L);
    private final EntityNum nonFungibleTokenNum = EntityNum.fromLong(5678L);
    private final EntityId treasuryId = treasuryNum.toEntityId();
    private final TokenID survivedTokenGrpcId = fungibleTokenNum.toGrpcTokenId();
    private final MerkleToken deletedToken =
            new MerkleToken(
                    Long.MAX_VALUE,
                    1L,
                    0,
                    "GONE",
                    "Long lost dream",
                    true,
                    true,
                    expiredTreasuryId);
    private final MerkleToken fungibleToken =
            new MerkleToken(
                    Long.MAX_VALUE, 1L, 0, "HERE", "Dreams never die", true, true, treasuryId);
    private final MerkleToken nonFungibleToken =
            new MerkleToken(
                    Long.MAX_VALUE, 1L, 0, "HERE", "Dreams never die", true, true, treasuryId);

    {
        deletedToken.setDeleted(true);
        fungibleToken.setTokenType(TokenType.FUNGIBLE_COMMON);
        nonFungibleToken.setTokenType(TokenType.NON_FUNGIBLE_UNIQUE);
        nonFungibleToken.setTreasury(treasuryNum.toEntityId());
    }

    private final NftId aNftKey =
            NftId.withDefaultShardRealm(nonFungibleTokenNum.longValue(), 666L);
    private final EntityNumPair bNftKey =
            EntityNumPair.fromLongs(deletedTokenNum.longValue(), 777L);
    private final UniqueTokenAdapter someNft =
            UniqueTokenAdapter.wrap(
                    new MerkleUniqueToken(
                            expiredAccountNum.toEntityId(),
                            "A".getBytes(),
                            RichInstant.MISSING_INSTANT));
}
