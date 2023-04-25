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
package com.hedera.node.app.service.mono.txns.span;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.node.app.service.mono.ServicesState;
import com.hedera.node.app.service.mono.sigs.EventExpansion;
import com.hedera.node.app.service.mono.utils.accessors.AccessorFactory;
import com.hedera.node.app.service.mono.utils.accessors.PlatformTxnAccessor;
import com.hedera.node.app.service.mono.utils.accessors.SwirldsTxnAccessor;
import com.swirlds.common.system.Round;
import com.swirlds.common.system.SwirldDualState;
import com.swirlds.common.system.events.Event;
import com.swirlds.common.system.transaction.Transaction;

/**
 * Encapsulates a "span" that tracks our contact with a given {@link Transaction} between the {@link
 * EventExpansion#expandAllSigs(Event, ServicesState)} and {@link
 * ServicesState#handleConsensusRound(Round, SwirldDualState)} platform callbacks.
 *
 * <p>At first this span only tracks the {@link PlatformTxnAccessor} parsed from the transaction
 * contents in an expiring cache. Since the parsing is a pure function of the contents, this is a
 * trivial exercise.
 *
 * <p>However, a major (perhaps <i>the</i> major) performance optimization available to Services
 * will be to,
 *
 * <ol>
 *   <li>Expand signatures from the latest signed state.
 *   <li>Track the expanded signatures, along with the entities involved, in the transaction's span.
 *   <li>From {@code handleTransaction}, alert the {@code ExpandHandleSpan} when an entity's keys or
 *       usability changes; this will invalidate the signatures for any span involving the entity.
 *   <li>When a transaction reaches {@code handleTransaction} with valid expanded signatures, simply
 *       reuse them instead of recomputing them.
 * </ol>
 */
public class ExpandHandleSpan {
    private final SpanMapManager spanMapManager;
    private final AccessorFactory factory;

    public ExpandHandleSpan(final SpanMapManager spanMapManager, final AccessorFactory factory) {
        this.spanMapManager = spanMapManager;
        this.factory = factory;
    }

    public SwirldsTxnAccessor track(final Transaction transaction)
            throws InvalidProtocolBufferException {
        final var accessor = spanAccessorFor(transaction.getContents());
        transaction.setMetadata(accessor);
        return accessor;
    }

    public SwirldsTxnAccessor accessorFor(final Transaction transaction)
            throws InvalidProtocolBufferException {
        final SwirldsTxnAccessor cachedAccessor = transaction.getMetadata();
        if (cachedAccessor != null) {
            spanMapManager.rationalizeSpan(cachedAccessor);
            transaction.setMetadata(null);
            return cachedAccessor;
        } else {
            return spanAccessorFor(transaction.getContents());
        }
    }

    SwirldsTxnAccessor spanAccessorFor(final byte[] contents)
            throws InvalidProtocolBufferException {
        final var accessor = factory.nonTriggeredTxn(contents);
        spanMapManager.expandSpan(accessor);
        return PlatformTxnAccessor.from(accessor);
    }
}
