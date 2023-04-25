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
package com.hedera.node.app.state.merkle;

import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.StateDefinition;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Holds metadata related to a registered service's state.
 *
 * @param <K> The type of the state key
 * @param <V> The type of the state value
 */
public final class StateMetadata<K extends Comparable<K>, V> {
    private static final String ON_DISK_KEY_CLASS_ID_SUFFIX = "OnDiskKey";
    private static final String ON_DISK_KEY_SERIALIZER_CLASS_ID_SUFFIX = "OnDiskKeySerializer";
    private static final String ON_DISK_VALUE_CLASS_ID_SUFFIX = "OnDiskValue";
    private static final String ON_DISK_VALUE_SERIALIZER_CLASS_ID_SUFFIX = "OnDiskValueSerializer";
    private static final String IN_MEMORY_VALUE_CLASS_ID_SUFFIX = "InMemoryValue";
    private static final String SINGLETON_CLASS_ID_SUFFIX = "SingletonLeaf";

    private final String serviceName;
    private final Schema schema;
    private final StateDefinition<K, V> stateDefinition;
    private final long onDiskKeyClassId;
    private final long onDiskKeySerializerClassId;
    private final long onDiskValueClassId;
    private final long onDiskValueSerializerClassId;
    private final long inMemoryValueClassId;
    private final long singletonClassId;

    /**
     * Create an instance.
     *
     * @param serviceName The name of the service
     * @param schema The {@link Schema} that defined the state
     * @param stateDefinition The {@link StateDefinition}
     */
    public StateMetadata(
            @NonNull String serviceName,
            @NonNull Schema schema,
            @NonNull StateDefinition<K, V> stateDefinition) {
        this.serviceName = StateUtils.validateServiceName(serviceName);
        this.schema = schema;
        this.stateDefinition = stateDefinition;

        final var stateKey = stateDefinition.stateKey();
        final var version = schema.getVersion();
        this.onDiskKeyClassId =
                StateUtils.computeClassId(
                        serviceName, stateKey, version, ON_DISK_KEY_CLASS_ID_SUFFIX);
        this.onDiskKeySerializerClassId =
                StateUtils.computeClassId(
                        serviceName, stateKey, version, ON_DISK_KEY_SERIALIZER_CLASS_ID_SUFFIX);
        this.onDiskValueClassId =
                StateUtils.computeClassId(
                        serviceName, stateKey, version, ON_DISK_VALUE_CLASS_ID_SUFFIX);
        this.onDiskValueSerializerClassId =
                StateUtils.computeClassId(
                        serviceName, stateKey, version, ON_DISK_VALUE_SERIALIZER_CLASS_ID_SUFFIX);
        this.inMemoryValueClassId =
                StateUtils.computeClassId(
                        serviceName, stateKey, version, IN_MEMORY_VALUE_CLASS_ID_SUFFIX);
        this.singletonClassId =
                StateUtils.computeClassId(
                        serviceName, stateKey, version, SINGLETON_CLASS_ID_SUFFIX);
    }

    public String serviceName() {
        return serviceName;
    }

    public Schema schema() {
        return schema;
    }

    public StateDefinition<K, V> stateDefinition() {
        return stateDefinition;
    }

    public long onDiskKeyClassId() {
        return onDiskKeyClassId;
    }

    public long onDiskKeySerializerClassId() {
        return onDiskKeySerializerClassId;
    }

    public long onDiskValueClassId() {
        return onDiskValueClassId;
    }

    public long onDiskValueSerializerClassId() {
        return onDiskValueSerializerClassId;
    }

    public long inMemoryValueClassId() {
        return inMemoryValueClassId;
    }

    public long singletonClassId() {
        return singletonClassId;
    }
}
