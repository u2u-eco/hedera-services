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
package com.hedera.node.app.service.mono.store.schedule;

import com.hedera.node.app.service.mono.state.submerkle.RichInstant;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleSecondVirtualValue;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue;
import com.hedera.node.app.service.mono.store.CreationResult;
import com.hedera.node.app.service.mono.store.Store;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import org.apache.commons.lang3.tuple.Pair;

/** Defines a type able to manage Scheduled entities. */
public interface ScheduleStore extends Store<ScheduleID, ScheduleVirtualValue> {
    ScheduleID MISSING_SCHEDULE = ScheduleID.getDefaultInstance();

    void apply(ScheduleID id, Consumer<ScheduleVirtualValue> change);

    ResponseCodeEnum deleteAt(ScheduleID id, Instant consensusTime);

    CreationResult<ScheduleID> createProvisionally(
            ScheduleVirtualValue candidate, RichInstant consensusTime);

    Pair<ScheduleID, ScheduleVirtualValue> lookupSchedule(byte[] bodyBytes);

    ResponseCodeEnum preMarkAsExecuted(ScheduleID id);

    ResponseCodeEnum markAsExecuted(ScheduleID id, Instant consensusTime);

    void expire(ScheduleID id);

    default ScheduleID resolve(final ScheduleID id) {
        return exists(id) ? id : MISSING_SCHEDULE;
    }

    ScheduleVirtualValue getNoError(ScheduleID id);

    List<ScheduleID> nextSchedulesToExpire(Instant consensusTime);

    @Nullable
    ScheduleID nextScheduleToEvaluate(Instant consensusTime);

    @Nullable
    ScheduleSecondVirtualValue getBySecond(long second);
}
