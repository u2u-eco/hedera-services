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
package com.hedera.node.app.service.mono.state.virtual.entities;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OnDiskTokenRelSupplierTest {
    private final OnDiskTokenRelSupplier subject = new OnDiskTokenRelSupplier();

    @Mock private SerializableDataInputStream in;
    @Mock private SerializableDataOutputStream out;

    @Test
    void ioIsNoop() throws IOException {
        subject.serialize(out);
        subject.deserialize(in, 1);

        verifyNoInteractions(in);
    }

    @Test
    void hasExpectedProfile() {
        assertEquals(0x0e52cff909625f55L, subject.getClassId());
        assertEquals(1, subject.getVersion());
    }

    @Test
    void createsOnDiskAccounts() {
        assertInstanceOf(OnDiskTokenRel.class, subject.get());
    }
}
