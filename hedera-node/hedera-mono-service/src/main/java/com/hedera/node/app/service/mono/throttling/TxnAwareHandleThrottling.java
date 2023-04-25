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
package com.hedera.node.app.service.mono.throttling;

import com.hedera.node.app.hapi.utils.sysfiles.domain.throttling.ThrottleDefinitions;
import com.hedera.node.app.hapi.utils.throttles.DeterministicThrottle;
import com.hedera.node.app.hapi.utils.throttles.GasLimitDeterministicThrottle;
import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import java.util.List;

public class TxnAwareHandleThrottling implements FunctionalityThrottling {
    private final TransactionContext txnCtx;
    private final TimedFunctionalityThrottling delegate;

    public TxnAwareHandleThrottling(
            TransactionContext txnCtx, TimedFunctionalityThrottling delegate) {
        this.txnCtx = txnCtx;
        this.delegate = delegate;
    }

    @Override
    public boolean shouldThrottleTxn(TxnAccessor accessor) {
        return delegate.shouldThrottleTxn(accessor, txnCtx.consensusTime());
    }

    @Override
    public boolean shouldThrottleNOfUnscaled(final int n, final HederaFunctionality function) {
        return delegate.shouldThrottleNOfUnscaled(n, function, txnCtx.consensusTime());
    }

    @Override
    public boolean shouldThrottleQuery(final HederaFunctionality queryFunction, final Query query) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean wasLastTxnGasThrottled() {
        return delegate.wasLastTxnGasThrottled();
    }

    @Override
    public void leakUnusedGasPreviouslyReserved(TxnAccessor accessor, long value) {
        delegate.leakUnusedGasPreviouslyReserved(accessor, value);
    }

    @Override
    public List<DeterministicThrottle> activeThrottlesFor(HederaFunctionality function) {
        return delegate.activeThrottlesFor(function);
    }

    @Override
    public List<DeterministicThrottle> allActiveThrottles() {
        return delegate.allActiveThrottles();
    }

    @Override
    public GasLimitDeterministicThrottle gasLimitThrottle() {
        return delegate.gasLimitThrottle();
    }

    @Override
    public void resetUsage() {
        delegate.resetUsage();
    }

    @Override
    public void rebuildFor(ThrottleDefinitions defs) {
        delegate.rebuildFor(defs);
    }

    @Override
    public void applyGasConfig() {
        delegate.applyGasConfig();
    }
}
