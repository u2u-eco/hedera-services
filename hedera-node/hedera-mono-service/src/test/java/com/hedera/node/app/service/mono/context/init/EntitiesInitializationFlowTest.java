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
package com.hedera.node.app.service.mono.context.init;

import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.mono.ledger.SigImpactHistorian;
import com.hedera.node.app.service.mono.state.expiry.ExpiryManager;
import com.hedera.node.app.service.mono.state.logic.NetworkCtxManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EntitiesInitializationFlowTest {
    @Mock private ExpiryManager expiryManager;
    @Mock private NetworkCtxManager networkCtxManager;
    @Mock private SigImpactHistorian sigImpactHistorian;

    private EntitiesInitializationFlow subject;

    @BeforeEach
    void setUp() {
        subject =
                new EntitiesInitializationFlow(
                        expiryManager, sigImpactHistorian, networkCtxManager);
    }

    @Test
    void runsAsExpected() {
        // when:
        subject.run();

        // then:
        verify(expiryManager).reviewExistingPayerRecords();
        verify(expiryManager).reviewExistingShortLivedEntities();
        verify(sigImpactHistorian).invalidateCurrentWindow();
        verify(networkCtxManager).setObservableFilesNotLoaded();
        verify(networkCtxManager).loadObservableSysFilesIfNeeded();
    }
}
