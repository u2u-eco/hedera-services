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
package com.hedera.node.app.service.mono.state.virtual;

import static org.assertj.core.api.Assertions.assertThat;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;

class UniqueTokenKeySupplierTest {
    @Test
    void tokenSupplier_whenCalledMultipleTimes_producesNewCopies() {
        final UniqueTokenKeySupplier supplier = new UniqueTokenKeySupplier();
        final UniqueTokenKey key1 = supplier.get();
        final UniqueTokenKey key2 = supplier.get();
        assertThat(key1).isNotNull();
        assertThat(key2).isNotNull();
        assertThat(key1).isNotSameAs(key2);
    }

    // Test invariants. The below tests are designed to fail if one accidentally modifies specified
    // constants.
    @Test
    void checkClassId_isExpected() {
        assertThat(new UniqueTokenKeySupplier().getClassId()).isEqualTo(0x8232d5e6ed77cc5cL);
    }

    @Test
    void checkCurrentVersion_isExpected() {
        assertThat(new UniqueTokenKeySupplier().getVersion()).isEqualTo(1);
    }

    @Test
    void noopFunctions_forTestCoverage() {
        final UniqueTokenKeySupplier supplier = new UniqueTokenKeySupplier();
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final SerializableDataOutputStream dataOutputStream =
                new SerializableDataOutputStream(outputStream);
        supplier.serialize(dataOutputStream);
        assertThat(outputStream.toByteArray()).isEmpty();

        final SerializableDataInputStream dataInputStream =
                new SerializableDataInputStream(
                        new ByteArrayInputStream(outputStream.toByteArray()));
        supplier.deserialize(dataInputStream, 1);
    }
}
