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
package com.hedera.node.app.service.mono.sigs.metadata;

import static com.hedera.node.app.service.mono.sigs.metadata.SafeLookupResult.failure;
import static com.hedera.node.app.service.mono.sigs.metadata.ScheduleSigningMetadata.from;
import static com.hedera.node.app.service.mono.sigs.metadata.TokenMetaUtils.signingMetaFrom;
import static com.hedera.node.app.service.mono.sigs.order.KeyOrderingFailure.MISSING_SCHEDULE;
import static com.hedera.node.app.service.mono.sigs.order.KeyOrderingFailure.MISSING_TOKEN;

import com.hedera.node.app.service.mono.ledger.accounts.AliasManager;
import com.hedera.node.app.service.mono.sigs.metadata.lookups.AccountSigMetaLookup;
import com.hedera.node.app.service.mono.sigs.metadata.lookups.ContractSigMetaLookup;
import com.hedera.node.app.service.mono.sigs.metadata.lookups.DefaultAccountLookup;
import com.hedera.node.app.service.mono.sigs.metadata.lookups.DefaultContractLookup;
import com.hedera.node.app.service.mono.sigs.metadata.lookups.DefaultTopicLookup;
import com.hedera.node.app.service.mono.sigs.metadata.lookups.FileSigMetaLookup;
import com.hedera.node.app.service.mono.sigs.metadata.lookups.HfsSigMetaLookup;
import com.hedera.node.app.service.mono.sigs.metadata.lookups.TopicSigMetaLookup;
import com.hedera.node.app.service.mono.sigs.order.LinkedRefs;
import com.hedera.node.app.service.mono.state.merkle.MerkleTopic;
import com.hedera.node.app.service.mono.state.migration.AccountStorageAdapter;
import com.hedera.node.app.service.mono.store.schedule.ScheduleStore;
import com.hedera.node.app.service.mono.store.tokens.TokenStore;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TopicID;
import com.swirlds.merkle.map.MerkleMap;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Convenience class that gives unified access to Hedera signing metadata by delegating to
 * type-specific lookups.
 */
public final class DelegatingSigMetadataLookup implements SigMetadataLookup {
    public static final Instant PRETEND_SIGNING_TIME = Instant.ofEpochSecond(1_234_567L, 890);
    public static final Function<
                    TokenStore, Function<TokenID, SafeLookupResult<TokenSigningMetadata>>>
            REF_LOOKUP_FACTORY =
                    tokenStore ->
                            ref -> {
                                TokenID id;
                                return TokenStore.MISSING_TOKEN.equals(id = tokenStore.resolve(ref))
                                        ? failure(MISSING_TOKEN)
                                        : new SafeLookupResult<>(
                                                signingMetaFrom(tokenStore.get(id)));
                            };
    public static final Function<
                    ScheduleStore, Function<ScheduleID, SafeLookupResult<ScheduleSigningMetadata>>>
            SCHEDULE_REF_LOOKUP_FACTORY =
                    scheduleStore ->
                            ref -> {
                                ScheduleID id;
                                return ScheduleStore.MISSING_SCHEDULE.equals(
                                                id = scheduleStore.resolve(ref))
                                        ? failure(MISSING_SCHEDULE)
                                        : new SafeLookupResult<>(from(scheduleStore.get(id)));
                            };
    private final FileSigMetaLookup fileSigMetaLookup;
    private final AccountSigMetaLookup accountSigMetaLookup;
    private final ContractSigMetaLookup contractSigMetaLookup;
    private final TopicSigMetaLookup topicSigMetaLookup;

    private final Function<TokenID, SafeLookupResult<TokenSigningMetadata>> tokenSigMetaLookup;
    private final Function<ScheduleID, SafeLookupResult<ScheduleSigningMetadata>>
            scheduleSigMetaLookup;

    public static DelegatingSigMetadataLookup defaultLookupsFor(
            final AliasManager aliasManager,
            final HfsSigMetaLookup hfsSigMetaLookup,
            final Supplier<AccountStorageAdapter> accounts,
            final Supplier<MerkleMap<EntityNum, MerkleTopic>> topics,
            final Function<TokenID, SafeLookupResult<TokenSigningMetadata>> tokenLookup,
            final Function<ScheduleID, SafeLookupResult<ScheduleSigningMetadata>> scheduleLookup) {
        return new DelegatingSigMetadataLookup(
                hfsSigMetaLookup,
                new DefaultAccountLookup(aliasManager, accounts),
                new DefaultContractLookup(accounts),
                new DefaultTopicLookup(topics),
                tokenLookup,
                scheduleLookup);
    }

    public DelegatingSigMetadataLookup(
            final FileSigMetaLookup fileSigMetaLookup,
            final AccountSigMetaLookup accountSigMetaLookup,
            final ContractSigMetaLookup contractSigMetaLookup,
            final TopicSigMetaLookup topicSigMetaLookup,
            final Function<TokenID, SafeLookupResult<TokenSigningMetadata>> tokenSigMetaLookup,
            final Function<ScheduleID, SafeLookupResult<ScheduleSigningMetadata>>
                    scheduleSigMetaLookup) {
        this.fileSigMetaLookup = fileSigMetaLookup;
        this.accountSigMetaLookup = accountSigMetaLookup;
        this.contractSigMetaLookup = contractSigMetaLookup;
        this.topicSigMetaLookup = topicSigMetaLookup;
        this.tokenSigMetaLookup = tokenSigMetaLookup;
        this.scheduleSigMetaLookup = scheduleSigMetaLookup;
    }

    @Override
    public SafeLookupResult<FileSigningMetadata> fileSigningMetaFor(
            final FileID id, final @Nullable LinkedRefs linkedRefs) {
        return fileSigMetaLookup.safeLookup(id);
    }

    @Override
    public SafeLookupResult<ScheduleSigningMetadata> scheduleSigningMetaFor(
            final ScheduleID id, final LinkedRefs linkedRefs) {
        return scheduleSigMetaLookup.apply(id);
    }

    @Override
    public SafeLookupResult<AccountSigningMetadata> accountSigningMetaFor(
            final AccountID id, final LinkedRefs linkedRefs) {
        return accountSigMetaLookup.safeLookup(id);
    }

    @Override
    public SafeLookupResult<TopicSigningMetadata> topicSigningMetaFor(
            final TopicID id, final @Nullable LinkedRefs linkedRefs) {
        return topicSigMetaLookup.safeLookup(id);
    }

    @Override
    public SafeLookupResult<TokenSigningMetadata> tokenSigningMetaFor(
            final TokenID id, final @Nullable LinkedRefs linkedRefs) {
        return tokenSigMetaLookup.apply(id);
    }

    @Override
    public SafeLookupResult<AccountSigningMetadata> aliasableAccountSigningMetaFor(
            final AccountID idOrAlias, final LinkedRefs linkedRefs) {
        return accountSigMetaLookup.aliasableSafeLookup(idOrAlias);
    }

    @Override
    public SafeLookupResult<ContractSigningMetadata> aliasableContractSigningMetaFor(
            final ContractID idOrAlias, @Nullable final LinkedRefs linkedRefs) {
        return contractSigMetaLookup.safeLookup(idOrAlias);
    }

    @Override
    public Instant sourceSignedAt() {
        return PRETEND_SIGNING_TIME;
    }
}
