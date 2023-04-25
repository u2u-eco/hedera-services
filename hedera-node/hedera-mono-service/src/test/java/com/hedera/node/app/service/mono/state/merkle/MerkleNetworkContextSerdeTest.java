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
package com.hedera.node.app.service.mono.state.merkle;

import com.hedera.test.serde.SelfSerializableDataTest;
import com.hedera.test.serde.SerializedForms;
import com.hedera.test.utils.SeededPropertySource;
import java.util.Optional;
import java.util.function.BiConsumer;

public class MerkleNetworkContextSerdeTest extends SelfSerializableDataTest<MerkleNetworkContext> {
    public static final int NUM_TEST_CASES = 3 * MIN_TEST_CASES_PER_VERSION;

    @Override
    protected Class<MerkleNetworkContext> getType() {
        return MerkleNetworkContext.class;
    }

    @Override
    protected Optional<BiConsumer<MerkleNetworkContext, MerkleNetworkContext>>
            customAssertEquals() {
        return Optional.of(MerkleNetworkContextTest::assertEqualContexts);
    }

    @Override
    protected int getNumTestCasesFor(final int version) {
        return NUM_TEST_CASES;
    }

    @Override
    protected byte[] getSerializedForm(final int version, final int testCaseNo) {
        return SerializedForms.loadForm(MerkleNetworkContext.class, version, testCaseNo);
    }

    @Override
    protected MerkleNetworkContext getExpectedObject(final int version, final int testCaseNo) {
        final var propertySource = SeededPropertySource.forSerdeTest(version, testCaseNo);
        if (version < MerkleNetworkContext.RELEASE_0310_VERSION) {
            if (version < MerkleNetworkContext.RELEASE_0300_VERSION) {
                if (version < MerkleNetworkContext.RELEASE_0270_VERSION) {
                    final var seeded = propertySource.next0260NetworkContext();
                    if (version < MerkleNetworkContext.RELEASE_0260_VERSION) {
                        seeded.setBlockNo(0L);
                        seeded.setFirstConsTimeOfCurrentBlock(null);
                        seeded.getBlockHashes().clear();
                    }
                    return seeded;
                } else {
                    return propertySource.next0270NetworkContext();
                }
            } else {
                return propertySource.next0300NetworkContext();
            }
        } else {
            return version == MerkleNetworkContext.RELEASE_0310_VERSION
                    ? propertySource.next0310NetworkContext()
                    : propertySource.next0320NetworkContext();
        }
    }

    @Override
    protected MerkleNetworkContext getExpectedObject(final SeededPropertySource propertySource) {
        return propertySource.next0320NetworkContext();
    }
}
