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
package com.hedera.node.app.service.mono.grpc.marshalling;

import static com.hedera.node.app.service.mono.grpc.marshalling.AdjustmentUtils.adjustForAssessedHbar;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.node.app.service.mono.state.submerkle.FcCustomFee;
import com.hedera.node.app.service.mono.store.models.Id;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class HbarFeeAssessor {
    @Inject
    public HbarFeeAssessor() {
        // Default constructor
    }

    public ResponseCodeEnum assess(
            final Id payer,
            final FcCustomFee hbarFee,
            final BalanceChangeManager changeManager,
            final List<AssessedCustomFeeWrapper> accumulator,
            boolean isFallbackFee) {
        final var collector = hbarFee.getFeeCollectorAsId();
        final var fixedSpec = hbarFee.getFixedFeeSpec();
        final var amount = fixedSpec.getUnitsToCollect();
        adjustForAssessedHbar(payer, collector, amount, changeManager, isFallbackFee);
        final var effPayerAccountNums = new AccountID[] {payer.asGrpcAccount()};
        final var assessed =
                new AssessedCustomFeeWrapper(collector.asEntityId(), amount, effPayerAccountNums);
        accumulator.add(assessed);
        return OK;
    }
}
