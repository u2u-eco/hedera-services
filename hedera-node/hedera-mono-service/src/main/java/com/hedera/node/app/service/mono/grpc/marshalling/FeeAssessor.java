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
package com.hedera.node.app.service.mono.grpc.marshalling;

import static com.hedera.node.app.service.mono.grpc.marshalling.FixedFeeResult.ASSESSMENT_FAILED_WITH_TOO_MANY_ADJUSTMENTS_REQUIRED;
import static com.hedera.node.app.service.mono.grpc.marshalling.FixedFeeResult.ASSESSMENT_FINISHED;
import static com.hedera.node.app.service.mono.grpc.marshalling.FixedFeeResult.FRACTIONAL_FEE_ASSESSMENT_PENDING;
import static com.hedera.node.app.service.mono.grpc.marshalling.FixedFeeResult.ROYALTY_FEE_ASSESSMENT_PENDING;
import static com.hedera.node.app.service.mono.state.submerkle.FcCustomFee.FeeType.FIXED_FEE;
import static com.hedera.node.app.service.mono.state.submerkle.FcCustomFee.FeeType.FRACTIONAL_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_CHARGING_EXCEEDED_MAX_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_CHARGING_EXCEEDED_MAX_RECURSION_DEPTH;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.node.app.service.mono.ledger.BalanceChange;
import com.hedera.node.app.service.mono.store.models.Id;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class FeeAssessor {
    private final FixedFeeAssessor fixedFeeAssessor;
    private final RoyaltyFeeAssessor royaltyFeeAssessor;
    private final FractionalFeeAssessor fractionalFeeAssessor;

    public static final boolean IS_NOT_FALLBACK_FEE = false;
    public static final boolean IS_FALLBACK_FEE = true;

    @Inject
    public FeeAssessor(
            FixedFeeAssessor fixedFeeAssessor,
            RoyaltyFeeAssessor royaltyFeeAssessor,
            FractionalFeeAssessor fractionalFeeAssessor) {
        this.fixedFeeAssessor = fixedFeeAssessor;
        this.royaltyFeeAssessor = royaltyFeeAssessor;
        this.fractionalFeeAssessor = fractionalFeeAssessor;
    }

    public ResponseCodeEnum assess(
            BalanceChange change,
            CustomSchedulesManager customSchedulesManager,
            BalanceChangeManager changeManager,
            List<AssessedCustomFeeWrapper> accumulator,
            ImpliedTransfersMeta.ValidationProps props) {
        if (changeManager.getLevelNo() > props.maxNestedCustomFees()) {
            return CUSTOM_FEE_CHARGING_EXCEEDED_MAX_RECURSION_DEPTH;
        }
        final var chargingToken = change.getToken();

        final var feeMeta = customSchedulesManager.managedSchedulesFor(chargingToken);
        final var payer = change.getAccount();
        final var fees = feeMeta.customFees();
        /* Token treasuries are exempt from all custom fees */
        if (fees.isEmpty() || feeMeta.treasuryId().equals(payer)) {
            return OK;
        }

        final var maxBalanceChanges = props.maxXferBalanceChanges();
        final var fixedFeeResult =
                assessFixedFees(feeMeta, payer, changeManager, accumulator, maxBalanceChanges);
        if (fixedFeeResult == ASSESSMENT_FAILED_WITH_TOO_MANY_ADJUSTMENTS_REQUIRED) {
            return CUSTOM_FEE_CHARGING_EXCEEDED_MAX_ACCOUNT_AMOUNTS;
        }

        /* A COMMON_FUNGIBLE token can have fractional fees but not royalty fees;
        and a NONFUNGIBLE_UNIQUE token can have royalty fees but not fractional fees.
        So these two if clauses are mutually exclusive. */
        if (fixedFeeResult == FRACTIONAL_FEE_ASSESSMENT_PENDING) {
            final var fractionalValidity =
                    fractionalFeeAssessor.assessAllFractional(
                            change, feeMeta, changeManager, accumulator);
            if (fractionalValidity != OK) {
                return fractionalValidity;
            }
        } else if (fixedFeeResult == ROYALTY_FEE_ASSESSMENT_PENDING) {
            final var royaltyValidity =
                    royaltyFeeAssessor.assessAllRoyalties(
                            change, feeMeta, changeManager, accumulator);
            if (royaltyValidity != OK) {
                return royaltyValidity;
            }
        }

        return (changeManager.numChangesSoFar() > maxBalanceChanges)
                ? CUSTOM_FEE_CHARGING_EXCEEDED_MAX_ACCOUNT_AMOUNTS
                : OK;
    }

    private FixedFeeResult assessFixedFees(
            CustomFeeMeta feeMeta,
            Id payer,
            BalanceChangeManager balanceChangeManager,
            List<AssessedCustomFeeWrapper> accumulator,
            int maxBalanceChanges) {
        var result = ASSESSMENT_FINISHED;
        for (var fee : feeMeta.customFees()) {
            final var collector = fee.getFeeCollectorAsId();
            if (payer.equals(collector)) {
                continue;
            }
            if (fee.getFeeType() == FIXED_FEE) {
                // This is a top-level fixed fee, not a fallback royalty fee
                fixedFeeAssessor.assess(
                        payer,
                        feeMeta,
                        fee,
                        balanceChangeManager,
                        accumulator,
                        IS_NOT_FALLBACK_FEE);
                if (balanceChangeManager.numChangesSoFar() > maxBalanceChanges) {
                    return ASSESSMENT_FAILED_WITH_TOO_MANY_ADJUSTMENTS_REQUIRED;
                }
            } else {
                if (fee.getFeeType() == FRACTIONAL_FEE) {
                    result = FRACTIONAL_FEE_ASSESSMENT_PENDING;
                } else {
                    result = ROYALTY_FEE_ASSESSMENT_PENDING;
                }
            }
        }
        return result;
    }
}
