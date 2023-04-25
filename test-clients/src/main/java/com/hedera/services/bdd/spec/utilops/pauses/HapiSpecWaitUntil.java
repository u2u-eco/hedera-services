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
package com.hedera.services.bdd.spec.utilops.pauses;

import static com.swirlds.common.stream.LinkedObjectStreamUtilities.getPeriod;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HapiSpecWaitUntil extends UtilOp {
    static final Logger log = LogManager.getLogger(HapiSpecWaitUntil.class);

    private long timeMs;
    private long stakePeriodMins;
    private long targetTimeOffsetSecs;
    private boolean startOfNextStakingPeriod;

    public HapiSpecWaitUntil(String timeOfDay) throws ParseException {
        timeMs = convertToEpochMillis(timeOfDay);
    }

    public static HapiSpecWaitUntil untilStartOfNextStakingPeriod(final long stakePeriodMins) {
        return new HapiSpecWaitUntil(stakePeriodMins);
    }

    public static HapiSpecWaitUntil untilJustBeforeStakingPeriod(
            final long stakePeriodMins, final long secondsBefore) {
        return new HapiSpecWaitUntil(stakePeriodMins, -secondsBefore);
    }

    private HapiSpecWaitUntil(final long stakePeriodMins) {
        this(stakePeriodMins, 0L);
    }

    private HapiSpecWaitUntil(final long stakePeriodMins, final long targetTimeOffsetSecs) {
        this.stakePeriodMins = stakePeriodMins;
        this.targetTimeOffsetSecs = targetTimeOffsetSecs;
        this.startOfNextStakingPeriod = true;
    }

    @Override
    protected boolean submitOp(HapiSpec spec) throws Throwable {
        final var stakePeriodMillis = stakePeriodMins * 60 * 1000L;
        final var now = Instant.now();
        if (startOfNextStakingPeriod) {
            final var currentPeriod = getPeriod(now, stakePeriodMillis);
            final var nextPeriod = currentPeriod + 1;
            timeMs = nextPeriod * stakePeriodMillis + (targetTimeOffsetSecs * 1000L);
        }
        log.info(
                "Sleeping until epoch milli {} ({} CST)",
                timeMs,
                Instant.ofEpochMilli(timeMs).atZone(ZoneId.systemDefault()));
        long currentEpocMillis = now.getEpochSecond() * 1000L;
        Thread.sleep(timeMs - currentEpocMillis);
        return false;
    }

    private long convertToEpochMillis(final String timeOfDay) throws ParseException {
        SimpleDateFormat dateMonthYear = new SimpleDateFormat("MM/dd/yyyy");
        SimpleDateFormat dateMonthYearTime = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
        Date currentDate = new Date();
        String currDateInString = dateMonthYear.format(currentDate);

        String currDateTimeInString = currDateInString + " " + timeOfDay;

        return dateMonthYearTime.parse(currDateTimeInString).getTime() * 1000L;
    }
}
