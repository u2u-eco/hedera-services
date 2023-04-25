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
package com.hedera.node.app.service.mono.ledger.backing;

import static com.hedera.node.app.service.mono.utils.EntityIdUtils.readableId;
import static com.hedera.node.app.service.mono.utils.EntityNumPair.fromAccountTokenRel;

import com.hedera.node.app.service.mono.state.migration.HederaTokenRel;
import com.hedera.node.app.service.mono.state.migration.TokenRelStorageAdapter;
import com.hedera.node.app.service.mono.utils.EntityNumPair;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.Set;
import java.util.function.Supplier;
import org.apache.commons.lang3.tuple.Pair;

/**
 * A store that provides efficient access to the mutable representations of token relationships,
 * indexed by ({@code AccountID}, {@code TokenID}) pairs. This class is <b>not</b> thread-safe, and
 * should never be used by any thread other than the {@code handleTransaction} thread.
 */
public class BackingTokenRels implements BackingStore<Pair<AccountID, TokenID>, HederaTokenRel> {
    private final Supplier<TokenRelStorageAdapter> delegate;

    public BackingTokenRels(Supplier<TokenRelStorageAdapter> delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean contains(final Pair<AccountID, TokenID> key) {
        return delegate.get().containsKey(forMerkleMap(key));
    }

    @Override
    public HederaTokenRel getRef(Pair<AccountID, TokenID> key) {
        return delegate.get().getForModify(fromAccountTokenRel(key.getLeft(), key.getRight()));
    }

    @Override
    public void put(Pair<AccountID, TokenID> key, HederaTokenRel status) {
        final var curTokenRels = delegate.get();
        final var merkleKey = forMerkleMap(key);
        if (!curTokenRels.containsKey(merkleKey)) {
            curTokenRels.put(merkleKey, status);
        }
    }

    @Override
    public void remove(Pair<AccountID, TokenID> id) {
        delegate.get().remove(fromAccountTokenRel(id));
    }

    @Override
    public HederaTokenRel getImmutableRef(Pair<AccountID, TokenID> key) {
        return delegate.get().get(fromAccountTokenRel(key));
    }

    @Override
    public Set<Pair<AccountID, TokenID>> idSet() {
        throw new UnsupportedOperationException();
    }

    @Override
    public long size() {
        return delegate.get().size();
    }

    public static Pair<AccountID, TokenID> asTokenRel(AccountID account, TokenID token) {
        return Pair.of(account, token);
    }

    public static String readableTokenRel(Pair<AccountID, TokenID> rel) {
        return String.format("%s <-> %s", readableId(rel.getLeft()), readableId(rel.getRight()));
    }

    private EntityNumPair forMerkleMap(Pair<AccountID, TokenID> key) {
        return EntityNumPair.fromLongs(key.getLeft().getAccountNum(), key.getRight().getTokenNum());
    }

    /* -- only for unit tests */
    public Supplier<TokenRelStorageAdapter> getDelegate() {
        return delegate;
    }
}
