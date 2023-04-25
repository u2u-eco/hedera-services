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
package com.hedera.node.app.service.mono.state.logic;

import com.hedera.node.app.service.mono.ServicesState;
import com.hedera.node.app.service.mono.txns.network.UpgradeActions;
import com.swirlds.common.notification.listeners.ReconnectCompleteListener;
import com.swirlds.common.notification.listeners.ReconnectCompleteNotification;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class ReconnectListener implements ReconnectCompleteListener {
    private static final Logger log = LogManager.getLogger(ReconnectListener.class);

    private final UpgradeActions upgradeActions;

    @Inject
    public ReconnectListener(final UpgradeActions upgradeActions) {
        this.upgradeActions = upgradeActions;
    }

    @Override
    public void notify(final ReconnectCompleteNotification notification) {
        log.info(
                "Notification Received: Reconnect Finished. "
                        + "consensusTimestamp: {}, roundNumber: {}, sequence: {}",
                notification.getConsensusTimestamp(),
                notification.getRoundNumber(),
                notification.getSequence());
        final ServicesState state = (ServicesState) notification.getState();
        state.logSummary();
        upgradeActions.catchUpOnMissedSideEffects();
    }
}
