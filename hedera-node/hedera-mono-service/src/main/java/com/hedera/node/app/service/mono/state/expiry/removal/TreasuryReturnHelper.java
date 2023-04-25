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

import static com.hedera.node.app.service.mono.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static com.hedera.node.app.service.mono.utils.NftNumPair.MISSING_NFT_NUM_PAIR;

import com.hedera.node.app.service.mono.state.expiry.classification.EntityLookup;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.state.migration.TokenRelStorageAdapter;
import com.hedera.node.app.service.mono.state.migration.UniqueTokenMapAdapter;
import com.hedera.node.app.service.mono.state.submerkle.CurrencyAdjustments;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.NftAdjustments;
import com.hedera.node.app.service.mono.store.models.NftId;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.EntityNumPair;
import com.hedera.node.app.service.mono.utils.NftNumPair;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class TreasuryReturnHelper {
    private static final Logger log = LogManager.getLogger(TreasuryReturnHelper.class);
    private final Supplier<TokenRelStorageAdapter> tokenRels;
    private final EntityLookup entityLookup;

    @Inject
    public TreasuryReturnHelper(
            final EntityLookup entityLookup, final Supplier<TokenRelStorageAdapter> tokenRels) {
        this.tokenRels = tokenRels;
        this.entityLookup = entityLookup;
    }

    boolean updateNftReturns(
            final EntityNum expiredNum,
            final EntityNum tokenNum,
            final MerkleToken token,
            final long serialNo,
            final List<EntityId> tokenTypes,
            final List<NftAdjustments> returnExchanges) {
        final var tokenId = tokenNum.toEntityId();
        var typeI = tokenTypes.indexOf(tokenId);
        if (typeI == -1) {
            tokenTypes.add(tokenId);
            returnExchanges.add(new NftAdjustments());
            typeI = tokenTypes.size() - 1;
        }
        if (token.isDeleted()) {
            returnExchanges
                    .get(typeI)
                    .appendAdjust(expiredNum.toEntityId(), MISSING_ENTITY_ID, serialNo);
            return false;
        } else {
            returnExchanges
                    .get(typeI)
                    .appendAdjust(expiredNum.toEntityId(), token.treasury(), serialNo);
            try {
                // Update treasury's owned NFTs
                final var mutableTreasury = entityLookup.getMutableAccount(token.treasuryNum());
                mutableTreasury.setNftsOwned(mutableTreasury.getNftsOwned() + 1);
            } catch (Exception ex) {
                log.error("Error updating treasury's owned NFTs", ex);
            }

            incrementTreasuryBalance(token, tokenNum, 1, tokenRels.get());
            return true;
        }
    }

    EntityNumPair burnOrReturnNft(
            final boolean burn, final NftId rootKey, final UniqueTokenMapAdapter nfts) {
        final NftNumPair nextKey;
        if (burn) {
            final var burnedNft = nfts.get(rootKey);
            nextKey = burnedNft.getNext();
            nfts.remove(rootKey);
        } else {
            final var returnedNft = nfts.getForModify(rootKey);
            nextKey = returnedNft.getNext();
            returnedNft.setOwner(MISSING_ENTITY_ID);
        }
        return effective(nextKey);
    }

    void updateFungibleReturns(
            final EntityNum expiredNum,
            final EntityNum tokenNum,
            final MerkleToken token,
            final long balance,
            final List<CurrencyAdjustments> returnTransfers,
            final TokenRelStorageAdapter curRels) {
        if (token.isDeleted() || !incrementTreasuryBalance(token, tokenNum, balance, curRels)) {
            final var burnTransfer =
                    new CurrencyAdjustments(
                            new long[] {-balance}, new long[] {expiredNum.longValue()});
            returnTransfers.add(burnTransfer);
        } else {
            addProperReturn(expiredNum, token, balance, returnTransfers);
        }
    }

    private boolean incrementTreasuryBalance(
            final MerkleToken token,
            final EntityNum tokenNum,
            final long balance,
            final TokenRelStorageAdapter curRels) {
        try {
            final var treasuryNum = token.treasury().asNum();
            final var treasuryRelKey = EntityNumPair.fromNums(treasuryNum, tokenNum);
            final var treasuryRel = curRels.getForModify(treasuryRelKey);
            final long newTreasuryBalance = treasuryRel.getBalance() + balance;
            treasuryRel.setBalance(newTreasuryBalance);
            return true;
        } catch (final Exception internal) {
            log.warn(
                    "Undeleted token {} treasury {} should be valid, but",
                    tokenNum.toIdString(),
                    token.treasury(),
                    internal);
            return false;
        }
    }

    private void addProperReturn(
            final EntityNum expiredAccountNum,
            final MerkleToken token,
            final long balance,
            final List<CurrencyAdjustments> returnTransfers) {

        final var treasuryNum = token.treasury().asNum();
        final boolean listDebitFirst = expiredAccountNum.compareTo(treasuryNum) < 0;
        // For consistency, order the transfer list by increasing account number
        returnTransfers.add(
                new CurrencyAdjustments(
                        listDebitFirst
                                ? new long[] {-balance, +balance}
                                : new long[] {+balance, -balance},
                        listDebitFirst
                                ? new long[] {
                                    expiredAccountNum.longValue(), treasuryNum.longValue()
                                }
                                : new long[] {
                                    treasuryNum.longValue(), expiredAccountNum.longValue()
                                }));
    }

    private EntityNumPair effective(@Nullable final NftNumPair nextKey) {
        if (nextKey == null) {
            return null;
        } else {
            return nextKey == MISSING_NFT_NUM_PAIR ? null : nextKey.asEntityNumPair();
        }
    }
}
