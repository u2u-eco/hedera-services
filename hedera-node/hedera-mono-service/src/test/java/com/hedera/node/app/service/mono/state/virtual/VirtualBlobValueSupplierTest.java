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
package com.hedera.node.app.service.mono.state.virtual;

import static com.hedera.node.app.service.mono.state.virtual.VirtualBlobValueSupplier.CLASS_ID;
import static com.hedera.node.app.service.mono.state.virtual.VirtualBlobValueSupplier.CURRENT_VERSION;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class VirtualBlobValueSupplierTest {
    private VirtualBlobValueSupplier subject = new VirtualBlobValueSupplier();

    @Test
    void gettersWork() {
        assertEquals(CLASS_ID, subject.getClassId());
        assertEquals(CURRENT_VERSION, subject.getVersion());
    }

    @Test
    void delegatesAsExpected() {
        final var virtualBlobValue = subject.get();

        assertEquals(VirtualBlobValue.CLASS_ID, virtualBlobValue.getClassId());
    }

    @Test
    void serdesAreNoop() {
        assertDoesNotThrow(() -> subject.deserialize(null, 1));
        assertDoesNotThrow(() -> subject.serialize(null));
    }
}
