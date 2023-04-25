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
package com.hedera.test.factories.scenarios;

import static com.hedera.test.factories.txns.ConsensusCreateTopicFactory.SIMPLE_TOPIC_ADMIN_KEY;
import static com.hedera.test.factories.txns.ConsensusCreateTopicFactory.newSignedConsensusCreateTopic;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER_ID;

import com.hedera.node.app.service.mono.utils.accessors.PlatformTxnAccessor;

public enum ConsensusCreateTopicScenarios implements TxnHandlingScenario {
    CONSENSUS_CREATE_TOPIC_NO_ADDITIONAL_KEYS_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(newSignedConsensusCreateTopic().get());
        }
    },
    CONSENSUS_CREATE_TOPIC_ADMIN_KEY_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    newSignedConsensusCreateTopic()
                            .adminKey(SIMPLE_TOPIC_ADMIN_KEY)
                            .nonPayerKts(SIMPLE_TOPIC_ADMIN_KEY)
                            .get());
        }
    },
    CONSENSUS_CREATE_TOPIC_ADMIN_KEY_AND_AUTORENEW_ACCOUNT_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    newSignedConsensusCreateTopic()
                            .adminKey(SIMPLE_TOPIC_ADMIN_KEY)
                            .autoRenewAccountId(MISC_ACCOUNT_ID)
                            .nonPayerKts(SIMPLE_TOPIC_ADMIN_KEY, MISC_ACCOUNT_KT)
                            .get());
        }
    },

    CONSENSUS_CREATE_TOPIC_ADMIN_KEY_AND_AUTORENEW_ACCOUNT_AS_PAYER_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    newSignedConsensusCreateTopic()
                            .adminKey(SIMPLE_TOPIC_ADMIN_KEY)
                            .autoRenewAccountId(DEFAULT_PAYER_ID)
                            .nonPayerKts(SIMPLE_TOPIC_ADMIN_KEY)
                            .get());
        }
    },

    CONSENSUS_CREATE_TOPIC_ADMIN_KEY_AND_AUTORENEW_ACCOUNT_AS_CUSTOM_PAYER_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    newSignedConsensusCreateTopic()
                            .adminKey(SIMPLE_TOPIC_ADMIN_KEY)
                            .autoRenewAccountId(CUSTOM_PAYER_ACCOUNT_ID)
                            .nonPayerKts(SIMPLE_TOPIC_ADMIN_KEY)
                            .get());
        }
    },
    CONSENSUS_CREATE_TOPIC_MISSING_AUTORENEW_ACCOUNT_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    newSignedConsensusCreateTopic().autoRenewAccountId(MISSING_ACCOUNT_ID).get());
        }
    }
}
