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
package com.hedera.node.app.service.mono.ledger.accounts;

import static com.hedera.node.app.service.mono.utils.EntityNum.MISSING_NUM;
import static com.hedera.node.app.service.mono.utils.MiscUtils.isRecoveredEvmAddress;
import static com.swirlds.common.utility.CommonUtils.hex;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.node.app.hapi.utils.ByteStringUtils;
import com.hedera.node.app.service.evm.accounts.HederaEvmContractAliases;
import com.hedera.node.app.service.evm.utils.EthSigsUtils;
import com.hedera.node.app.service.mono.ledger.SigImpactHistorian;
import com.hedera.node.app.service.mono.legacy.core.jproto.JECDSASecp256k1Key;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.migration.AccountStorageAdapter;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hederahashgraph.api.proto.java.Key;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.codec.DecoderException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.datatypes.Address;

/**
 * Handles a map with all the accounts that are auto-created. The map will be re-built on restart,
 * reconnect. Entries from the map are removed when the entity expires
 */
@Singleton
public class AliasManager extends HederaEvmContractAliases implements ContractAliases {
    private static final Logger log = LogManager.getLogger(AliasManager.class);

    private static final String NON_TRANSACTIONAL_MSG =
            "Base alias manager does not buffer changes";
    public static final UnaryOperator<byte[]> ADDRESS_RECOVERY_FN =
            EthSigsUtils::recoverAddressFromPubKey;

    private final Supplier<Map<ByteString, EntityNum>> aliases;

    @Inject
    public AliasManager(final Supplier<Map<ByteString, EntityNum>> aliases) {
        this.aliases = aliases;
    }

    @Override
    public void revert() {
        throw new UnsupportedOperationException(NON_TRANSACTIONAL_MSG);
    }

    @Override
    public void filterPendingChanges(final Predicate<Address> filter) {
        throw new UnsupportedOperationException(NON_TRANSACTIONAL_MSG);
    }

    @Override
    public void commit(final @Nullable SigImpactHistorian observer) {
        throw new UnsupportedOperationException(NON_TRANSACTIONAL_MSG);
    }

    @Override
    public void link(final Address alias, final Address address) {
        link(ByteString.copyFrom(alias.toArrayUnsafe()), EntityNum.fromEvmAddress(address));
    }

    @Override
    public void unlink(final Address alias) {
        unlink(ByteString.copyFrom(alias.toArrayUnsafe()));
    }

    @Override
    public Address resolveForEvm(final Address addressOrAlias) {
        if (isMirror(addressOrAlias)) {
            return addressOrAlias;
        }
        final var aliasKey = ByteString.copyFrom(addressOrAlias.toArrayUnsafe());
        final var contractNum = curAliases().get(aliasKey);
        // If we cannot resolve to a mirror address, we return the missing alias and let a
        // downstream component fail the transaction by returning null from its get() method.
        // Cf. the address validator provided by ContractsModule#provideAddressValidator().
        return (contractNum == null) ? addressOrAlias : contractNum.toEvmAddress();
    }

    @Override
    public boolean isInUse(final Address address) {
        return curAliases().containsKey(ByteString.copyFrom(address.toArrayUnsafe()));
    }

    public void link(final ByteString alias, final EntityNum num) {
        curAliases().put(alias, num);
    }

    public boolean maybeLinkEvmAddress(@Nullable final JKey key, final EntityNum num) {
        return maybeLinkEvmAddress(key, num, ADDRESS_RECOVERY_FN);
    }

    boolean maybeLinkEvmAddress(
            @Nullable final JKey key,
            final EntityNum num,
            final UnaryOperator<byte[]> addressRecovery) {
        final var evmAddress = tryAddressRecovery(key, addressRecovery);
        if (isRecoveredEvmAddress(evmAddress)) {
            link(ByteStringUtils.wrapUnsafely(evmAddress), num);
            return true;
        } else {
            return false;
        }
    }

    @Nullable
    public static byte[] tryAddressRecovery(
            @Nullable final JKey key, final UnaryOperator<byte[]> addressRecovery) {
        if (key != null && key.hasECDSAsecp256k1Key()) {
            // Only compressed keys are stored at the moment
            final var keyBytes = key.getECDSASecp256k1Key();
            if (keyBytes.length == JECDSASecp256k1Key.ECDSA_SECP256K1_COMPRESSED_KEY_LENGTH) {
                final var evmAddress = addressRecovery.apply(keyBytes);
                if (isRecoveredEvmAddress(evmAddress)) {
                    return evmAddress;
                } else {
                    // Not ever expected, since above checks should imply a valid input to the
                    // LibSecp256k1 library
                    log.warn("Unable to recover EVM address from {}", () -> hex(keyBytes));
                }
            }
        }
        return null;
    }

    public void unlink(final ByteString alias) {
        curAliases().remove(alias);
    }

    /**
     * From given MerkleMap of accounts, populate the auto accounts creations map. Iterate through
     * each account in accountsMap and add an entry to autoAccountsMap if {@code alias} exists on
     * the account.
     *
     * @param accounts the current accounts
     * @param observer an observer to be called with each traversed account
     */
    public void rebuildAliasesMap(
            final AccountStorageAdapter accounts,
            final BiConsumer<EntityNum, HederaAccount> observer) {
        final var numCreate2Aliases = new AtomicInteger();
        final var numEOAliases = new AtomicInteger();
        final var workingAliases = curAliases();
        workingAliases.clear();
        accounts.forEach(
                (k, v) -> {
                    final var alias = v.getAlias();
                    observer.accept(k, v);
                    if (!alias.isEmpty()) {
                        workingAliases.put(alias, k);
                        if (v.isSmartContract()) {
                            numCreate2Aliases.getAndIncrement();
                        }
                        if (alias.size() > EVM_ADDRESS_LEN) {
                            try {
                                final Key key = Key.parseFrom(v.getAlias());
                                final JKey jKey = JKey.mapKey(key);
                                if (maybeLinkEvmAddress(jKey, EntityNum.fromInt(v.number()))) {
                                    numEOAliases.incrementAndGet();
                                }
                            } catch (final InvalidProtocolBufferException
                                    | DecoderException
                                    | IllegalArgumentException ignore) {
                                // any expected exception means no eth mapping
                            }
                        }

                        if (alias.size() == EVM_ADDRESS_LEN) {
                            numEOAliases.incrementAndGet();
                        }
                    }
                });
        log.info(
                "Rebuild complete, re-mapped {} aliases ({} from CREATE2, {} externally owned"
                        + " accounts)",
                workingAliases::size,
                numCreate2Aliases::get,
                numEOAliases::get);
    }

    /**
     * Ensures an alias is no longer in use, returning whether it previously was.
     *
     * @param alias the alias to forget
     * @return whether it was present
     */
    public boolean forgetAlias(final ByteString alias) {
        if (alias.isEmpty()) {
            return false;
        }
        return curAliases().remove(alias) != null;
    }

    public void forgetEvmAddress(final ByteString alias) {
        var evmAddress = keyAliasToEVMAddress(alias);
        if (evmAddress != null) {
            curAliases().remove(ByteString.copyFrom(evmAddress));
        }
    }

    /**
     * Returns the entityNum for the given alias
     *
     * @param alias alias of the accountId
     * @return EntityNum mapped to the given alias.
     */
    public EntityNum lookupIdBy(final ByteString alias) {
        var entityNum = curAliases().getOrDefault(alias, MISSING_NUM);

        // We don't want to treat a Key-derived alias as "missing" if its auto-created account
        // would collide with an existing EVM address; so check for that case now
        if (alias.size() > EVM_ADDRESS_LEN && entityNum == MISSING_NUM) {
            // if we don't find entity num for key alias we can try to derive EVM address from it
            // and look it up
            var evmAddress = keyAliasToEVMAddress(alias);
            if (evmAddress != null) {
                entityNum = curAliases().getOrDefault(ByteString.copyFrom(evmAddress), MISSING_NUM);
            }
        }

        return entityNum;
    }

    private Map<ByteString, EntityNum> curAliases() {
        return aliases.get();
    }

    @VisibleForTesting
    Map<ByteString, EntityNum> getAliases() {
        return curAliases();
    }

    @Nullable
    public static byte[] keyAliasToEVMAddress(final ByteString alias) {
        try {
            final Key key = Key.parseFrom(alias);
            final JKey jKey = JKey.mapKey(key);
            return tryAddressRecovery(jKey, ADDRESS_RECOVERY_FN);
        } catch (InvalidProtocolBufferException
                | DecoderException
                | IllegalArgumentException ignore) {
            // any expected exception means no eth mapping
            return null;
        }
    }
}
