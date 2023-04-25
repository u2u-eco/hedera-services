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
package com.hedera.node.app.service.mono.utils;

import com.swirlds.common.FastCopyable;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;

public class FcLong implements SelfSerializable, FastCopyable {
    private static final long CLASS_ID = 0x1d8fc60c62dc8982L;

    private long value;

    public FcLong(long value) {
        this.value = value;
    }

    @Override
    public FcLong copy() {
        return new FcLong(value);
    }

    @Override
    public void deserialize(SerializableDataInputStream in, int version) throws IOException {
        value = in.readLong();
    }

    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        out.writeLong(value);
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || FcLong.class != o.getClass()) {
            return false;
        }

        var that = (FcLong) o;
        return this.value == that.value;
    }
}
