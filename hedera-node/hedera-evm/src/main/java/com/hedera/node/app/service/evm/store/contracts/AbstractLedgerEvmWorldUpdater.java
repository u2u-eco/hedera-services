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
package com.hedera.node.app.service.evm.store.contracts;

import com.hedera.node.app.service.evm.accounts.AccountAccessor;
import com.hedera.node.app.service.evm.store.models.UpdatedHederaEvmAccount;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.evm.worldstate.WorldView;
import org.hyperledger.besu.evm.worldstate.WrappedEvmAccount;

public abstract class AbstractLedgerEvmWorldUpdater<W extends WorldView, A extends Account>
        implements WorldUpdater {

    protected final W world;
    protected final AccountAccessor accountAccessor;
    private Map<Address, UpdatedHederaEvmAccount<A>> updatedEvmAccounts = new HashMap<>();
    private HederaEvmEntityAccess hederaEvmEntityAccess;

    protected AbstractLedgerEvmWorldUpdater(final W world, final AccountAccessor accountAccessor) {
        this.world = world;
        this.accountAccessor = accountAccessor;
    }

    protected AbstractLedgerEvmWorldUpdater(
            final W world,
            final AccountAccessor accountAccessor,
            final HederaEvmEntityAccess hederaEvmEntityAccess) {
        this(world, accountAccessor);
        this.hederaEvmEntityAccess = hederaEvmEntityAccess;
    }

    /**
     * Given an address, returns an account that can be mutated <b>with the assurance</b> that these
     * mutations will be tracked in the change-set represented by this {@link WorldUpdater}; and
     * either committed or reverted atomically with all other mutations in the change-set.
     *
     * @param address the address of interest
     * @return a tracked mutable account for the given address
     */
    public abstract A getForMutation(Address address);

    protected W wrappedWorldView() {
        return world;
    }

    @Override
    public EvmAccount createAccount(Address address, long nonce, Wei balance) {
        return null;
    }

    @Override
    public void deleteAccount(Address address) {
        // The method is an intentionally-blank. If given implementation need it can be overridden
    }

    @Override
    public Collection<? extends Account> getTouchedAccounts() {
        return Collections.emptyList();
    }

    @Override
    public Collection<Address> getDeletedAccountAddresses() {
        return Collections.emptyList();
    }

    @Override
    public void revert() {
        // The method is an intentionally-blank. If given implementation need it can be overridden
    }

    @Override
    public void commit() {
        // The method is an intentionally-blank. If given implementation need it can be overridden
    }

    @Override
    public Optional<WorldUpdater> parentUpdater() {
        return Optional.empty();
    }

    @Override
    public WorldUpdater updater() {
        return this;
    }

    public boolean isTokenAddress(Address address) {
        return accountAccessor.isTokenAddress(address);
    }

    @Override
    public Account get(Address address) {
        if (!address.equals(accountAccessor.canonicalAddress(address))) {
            return null;
        }
        final var extantMutable = this.updatedEvmAccounts.get(address);
        if (extantMutable != null) {
            return extantMutable;
        }

        return world.get(address);
    }

    @Override
    public EvmAccount getAccount(Address address) {
        final var extantMutable = this.updatedEvmAccounts.get(address);
        if (extantMutable != null) {
            return new WrappedEvmAccount(extantMutable);
        }

        final var origin = getForMutation(address);
        if (origin == null) {
            return null;
        }
        final var newMutable = new UpdatedHederaEvmAccount<>(origin);
        return new WrappedEvmAccount(track(newMutable));
    }

    private UpdatedHederaEvmAccount<A> track(final UpdatedHederaEvmAccount<A> account) {
        account.setEvmEntityAccess(hederaEvmEntityAccess);
        updatedEvmAccounts.put(account.getAddress(), account);
        return account;
    }
}
