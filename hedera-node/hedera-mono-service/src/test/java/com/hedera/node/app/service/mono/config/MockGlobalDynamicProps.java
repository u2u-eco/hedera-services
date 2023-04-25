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
package com.hedera.node.app.service.mono.config;

import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.fees.calculation.CongestionMultipliers;
import com.hedera.node.app.service.mono.fees.calculation.EntityScaleFactors;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import java.util.Set;

public class MockGlobalDynamicProps extends GlobalDynamicProperties {
    private final CongestionMultipliers defaultMultipliers =
            CongestionMultipliers.from("90,10x,95,25x,99,100x");
    private final CongestionMultipliers differentMultipliers =
            CongestionMultipliers.from("90,11x,95,26x,99,101x");

    private int maxToTouch = 2;
    private int minCongestionPeriod = 2;
    private long gracePeriod = 604800;
    private boolean useAutoRenew = true;
    private boolean useContractAutoRenew = true;
    private boolean schedulingLongTermEnabled = false;
    private boolean exportBalances = true;
    private CongestionMultipliers currentMultipliers = defaultMultipliers;
    private EntityScaleFactors scaleFactors =
            EntityScaleFactors.from("DEFAULT(90,10:1,95,25:1,99,100:1)");
    private boolean throttleByGas;
    private boolean shouldCompressAccountFiles;

    public MockGlobalDynamicProps() {
        super(null, null);
    }

    @Override
    public void reload() {}

    @Override
    public int maxTokensRelsPerInfoQuery() {
        return 1_000;
    }

    @Override
    public int maxTokenSymbolUtf8Bytes() {
        return 100;
    }

    @Override
    public int maxTokenNameUtf8Bytes() {
        return 100;
    }

    @Override
    public int maxFileSizeKb() {
        return 1024;
    }

    @Override
    public AccountID fundingAccount() {
        return AccountID.newBuilder().setAccountNum(98L).build();
    }

    @Override
    public int cacheRecordsTtl() {
        return 180;
    }

    @Override
    public int ratesIntradayChangeLimitPercent() {
        return 5;
    }

    @Override
    public int balancesExportPeriodSecs() {
        return 600;
    }

    public void turnOffBalancesExport() {
        exportBalances = false;
    }

    @Override
    public boolean shouldExportBalances() {
        return exportBalances;
    }

    @Override
    public long nodeBalanceWarningThreshold() {
        return 123L;
    }

    @Override
    public String pathToBalancesExportDir() {
        return "src/test/resources";
    }

    @Override
    public boolean shouldExportTokenBalances() {
        return true;
    }

    @Override
    public int maxTransferListSize() {
        return 10;
    }

    @Override
    public int maxTokenTransferListSize() {
        return 10;
    }

    @Override
    public int maxMemoUtf8Bytes() {
        return 100;
    }

    @Override
    public long maxTxnDuration() {
        return 180L;
    }

    @Override
    public int minValidityBuffer() {
        return 10;
    }

    @Override
    public long maxGasPerSec() {
        return 300_000L;
    }

    @Override
    public int feesTokenTransferUsageMultiplier() {
        return 380;
    }

    @Override
    public long maxAutoRenewDuration() {
        return 8000001L;
    }

    @Override
    public long minAutoRenewDuration() {
        return 6999999L;
    }

    @Override
    public int localCallEstRetBytes() {
        return 32;
    }

    @Override
    public int scheduledTxExpiryTimeSecs() {
        return 1800;
    }

    @Override
    public int messageMaxBytesAllowed() {
        return 1024;
    }

    @Override
    public Set<HederaFunctionality> schedulingWhitelist() {
        return Set.of(HederaFunctionality.CryptoCreate, HederaFunctionality.CryptoTransfer);
    }

    @Override
    public CongestionMultipliers congestionMultipliers() {
        return currentMultipliers;
    }

    @Override
    public boolean shouldAutoRenewSomeEntityType() {
        return useAutoRenew;
    }

    public void disableAutoRenew() {
        useAutoRenew = false;
    }

    public void enableAutoRenew() {
        useAutoRenew = true;
    }

    public void enableContractAutoRenew() {
        useContractAutoRenew = true;
    }

    @Override
    public int autoRenewNumberOfEntitiesToScan() {
        return 100;
    }

    @Override
    public int autoRenewMaxNumberOfEntitiesToRenewOrDelete() {
        return maxToTouch;
    }

    public void setMaxToTouch(int maxToTouch) {
        this.maxToTouch = maxToTouch;
    }

    @Override
    public long autoRenewGracePeriod() {
        return gracePeriod;
    }

    public void useDifferentMultipliers() {
        currentMultipliers = differentMultipliers;
        minCongestionPeriod = 0;
    }

    @Override
    public int feesMinCongestionPeriod() {
        return minCongestionPeriod;
    }

    @Override
    public boolean shouldThrottleByGas() {
        return throttleByGas;
    }

    @Override
    public int changeHistorianMemorySecs() {
        return 20;
    }

    public void setThrottleByGas(boolean throttleByGas) {
        this.throttleByGas = throttleByGas;
    }

    @Override
    public boolean isAutoCreationEnabled() {
        return true;
    }

    @Override
    public int maxNumQueryableRecords() {
        return 1;
    }

    @Override
    public boolean shouldAutoRenewContracts() {
        return useContractAutoRenew;
    }

    public void disableContractAutoRenew() {
        useContractAutoRenew = false;
    }

    @Override
    public boolean shouldAutoRenewAccounts() {
        return useAutoRenew;
    }

    @Override
    public boolean schedulingLongTermEnabled() {
        return schedulingLongTermEnabled;
    }

    public void enableSchedulingLongTerm() {
        schedulingLongTermEnabled = true;
    }

    @Override
    public boolean shouldCompressAccountBalanceFilesOnCreation() {
        return shouldCompressAccountFiles;
    }

    public void setAccountBalanceCompression(final boolean isEnabled) {
        this.shouldCompressAccountFiles = isEnabled;
    }

    @Override
    public EntityScaleFactors entityScaleFactors() {
        return scaleFactors;
    }

    public void setScaleFactors(final EntityScaleFactors scaleFactors) {
        this.scaleFactors = scaleFactors;
    }
}
