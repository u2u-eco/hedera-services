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
package com.hedera.services.bdd.spec.transactions.token;

import static com.hedera.node.app.hapi.fees.usage.token.TokenOpsUsageUtils.TOKEN_OPS_USAGE_UTILS;
import static com.hedera.services.bdd.spec.HapiPropertySource.idAsHeadlongAddress;
import static com.hedera.services.bdd.spec.transactions.TxnFactory.bannerWith;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.suFrom;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.esaulpaugh.headlong.abi.Address;
import com.google.common.base.MoreObjects;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.node.app.hapi.fees.usage.BaseTransactionMeta;
import com.hedera.node.app.hapi.fees.usage.state.UsageAccumulator;
import com.hedera.node.app.hapi.fees.usage.token.TokenOpsUsage;
import com.hedera.node.app.hapi.utils.CommonUtils;
import com.hedera.node.app.hapi.utils.fee.SigValueObj;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.fees.AdapterUtils;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HapiTokenCreate extends HapiTxnOp<HapiTokenCreate> {
    static final Logger log = LogManager.getLogger(HapiTokenCreate.class);

    public static final long WELL_KNOWN_INITIAL_SUPPLY = 123L * Integer.MAX_VALUE;
    public static final String WELL_KNOWN_NFT_SUPPLY_KEY = "wellKnownNftSupplyKey";

    private String token;

    private boolean advertiseCreation = false;
    private boolean asCallableContract = false;
    private Optional<TokenType> tokenType = Optional.empty();
    private Optional<TokenSupplyType> supplyType = Optional.empty();
    private OptionalInt decimals = OptionalInt.empty();
    private OptionalLong expiry = OptionalLong.empty();
    private OptionalLong initialSupply = OptionalLong.empty();
    private OptionalLong maxSupply = OptionalLong.empty();
    private OptionalLong autoRenewPeriod = OptionalLong.empty();
    private Optional<String> freezeKey = Optional.empty();
    private Optional<String> kycKey = Optional.empty();
    private Optional<String> wipeKey = Optional.empty();
    private Optional<String> supplyKey = Optional.empty();
    private Optional<String> feeScheduleKey = Optional.empty();
    private Optional<String> pauseKey = Optional.empty();
    private Optional<String> symbol = Optional.empty();
    private Optional<String> entityMemo = Optional.empty();
    private Optional<String> name = Optional.empty();
    private Optional<String> treasury = Optional.empty();
    private Optional<String> adminKey = Optional.empty();
    private Optional<Boolean> freezeDefault = Optional.empty();
    private Optional<String> autoRenewAccount = Optional.empty();
    private Optional<Consumer<String>> createdIdObs = Optional.empty();
    @Nullable private Consumer<Address> createdAddressObs;
    private Optional<Function<HapiSpec, String>> symbolFn = Optional.empty();
    private Optional<Function<HapiSpec, String>> nameFn = Optional.empty();
    private final List<Function<HapiSpec, CustomFee>> feeScheduleSuppliers = new ArrayList<>();

    @Override
    public HederaFunctionality type() {
        return HederaFunctionality.TokenCreate;
    }

    public HapiTokenCreate(final String token) {
        this.token = token;
    }

    public void setTokenPrefix(final String prefix) {
        token = prefix + token;
    }

    public HapiTokenCreate withCustom(final Function<HapiSpec, CustomFee> supplier) {
        feeScheduleSuppliers.add(supplier);
        return this;
    }

    public HapiTokenCreate entityMemo(final String memo) {
        this.entityMemo = Optional.of(memo);
        return this;
    }

    public HapiTokenCreate exposingCreatedIdTo(final Consumer<String> obs) {
        createdIdObs = Optional.of(obs);
        return this;
    }

    public HapiTokenCreate exposingAddressTo(final Consumer<Address> obs) {
        createdAddressObs = obs;
        return this;
    }

    public HapiTokenCreate tokenType(final TokenType tokenType) {
        this.tokenType = Optional.of(tokenType);
        return this;
    }

    public HapiTokenCreate supplyType(final TokenSupplyType supplyType) {
        this.supplyType = Optional.of(supplyType);
        return this;
    }

    public HapiTokenCreate advertisingCreation() {
        advertiseCreation = true;
        return this;
    }

    public HapiTokenCreate asCallableContract() {
        asCallableContract = true;
        return this;
    }

    public HapiTokenCreate initialSupply(final long initialSupply) {
        this.initialSupply = OptionalLong.of(initialSupply);
        return this;
    }

    public HapiTokenCreate maxSupply(final long maxSupply) {
        this.maxSupply = OptionalLong.of(maxSupply);
        return this;
    }

    public HapiTokenCreate decimals(final int decimals) {
        this.decimals = OptionalInt.of(decimals);
        return this;
    }

    public HapiTokenCreate freezeDefault(final boolean frozenByDefault) {
        freezeDefault = Optional.of(frozenByDefault);
        return this;
    }

    public HapiTokenCreate freezeKey(final String name) {
        freezeKey = Optional.of(name);
        return this;
    }

    public HapiTokenCreate expiry(final long at) {
        expiry = OptionalLong.of(at);
        return this;
    }

    public HapiTokenCreate kycKey(final String name) {
        kycKey = Optional.of(name);
        return this;
    }

    public HapiTokenCreate wipeKey(final String name) {
        wipeKey = Optional.of(name);
        return this;
    }

    public HapiTokenCreate supplyKey(final String name) {
        supplyKey = Optional.of(name);
        return this;
    }

    public HapiTokenCreate feeScheduleKey(final String name) {
        feeScheduleKey = Optional.of(name);
        return this;
    }

    public HapiTokenCreate pauseKey(final String name) {
        pauseKey = Optional.of(name);
        return this;
    }

    public HapiTokenCreate symbol(final String symbol) {
        this.symbol = Optional.of(symbol);
        return this;
    }

    public HapiTokenCreate symbol(final Function<HapiSpec, String> symbolFn) {
        this.symbolFn = Optional.of(symbolFn);
        return this;
    }

    public HapiTokenCreate name(final String name) {
        this.name = Optional.of(name);
        return this;
    }

    public HapiTokenCreate name(final Function<HapiSpec, String> nameFn) {
        this.nameFn = Optional.of(nameFn);
        return this;
    }

    public HapiTokenCreate adminKey(final String adminKeyName) {
        this.adminKey = Optional.of(adminKeyName);
        return this;
    }

    public HapiTokenCreate treasury(final String treasury) {
        this.treasury = Optional.of(treasury);
        return this;
    }

    public HapiTokenCreate autoRenewAccount(final String account) {
        this.autoRenewAccount = Optional.of(account);
        return this;
    }

    public HapiTokenCreate autoRenewPeriod(final long secs) {
        this.autoRenewPeriod = OptionalLong.of(secs);
        return this;
    }

    @Override
    protected HapiTokenCreate self() {
        return this;
    }

    @Override
    protected long feeFor(final HapiSpec spec, final Transaction txn, final int numPayerKeys)
            throws Throwable {
        final var txnSubType = getTxnSubType(CommonUtils.extractTransactionBody(txn));
        return spec.fees()
                .forActivityBasedOp(
                        HederaFunctionality.TokenCreate,
                        txnSubType,
                        this::usageEstimate,
                        txn,
                        numPayerKeys);
    }

    private SubType getTxnSubType(final TransactionBody txn) {
        final var op = txn.getTokenCreation();
        final SubType chosenType;
        final var usesCustomFees = op.hasFeeScheduleKey() || op.getCustomFeesCount() > 0;
        if (op.getTokenType() == NON_FUNGIBLE_UNIQUE) {
            chosenType =
                    usesCustomFees
                            ? TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES
                            : TOKEN_NON_FUNGIBLE_UNIQUE;
        } else {
            chosenType =
                    usesCustomFees ? TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES : TOKEN_FUNGIBLE_COMMON;
        }
        return chosenType;
    }

    private FeeData usageEstimate(final TransactionBody txn, final SigValueObj svo) {
        final var accumulator = new UsageAccumulator();
        final var tokenCreateMeta = TOKEN_OPS_USAGE_UTILS.tokenCreateUsageFrom(txn);
        final var baseTransactionMeta = new BaseTransactionMeta(txn.getMemoBytes().size(), 0);
        final TokenOpsUsage tokenOpsUsage = new TokenOpsUsage();
        tokenOpsUsage.tokenCreateUsage(
                suFrom(svo), baseTransactionMeta, tokenCreateMeta, accumulator);
        return AdapterUtils.feeDataFrom(accumulator);
    }

    @Override
    protected Consumer<TransactionBody.Builder> opBodyDef(final HapiSpec spec) throws Throwable {
        if (symbolFn.isPresent()) {
            symbol = Optional.of(symbolFn.get().apply(spec));
        }
        if (nameFn.isPresent()) {
            name = Optional.of(nameFn.get().apply(spec));
        }
        final TokenCreateTransactionBody opBody =
                spec.txns()
                        .<TokenCreateTransactionBody, TokenCreateTransactionBody.Builder>body(
                                TokenCreateTransactionBody.class,
                                b -> {
                                    tokenType.ifPresent(b::setTokenType);
                                    supplyType.ifPresent(b::setSupplyType);
                                    symbol.ifPresent(b::setSymbol);
                                    name.ifPresent(b::setName);
                                    entityMemo.ifPresent(s -> b.setMemo(s));
                                    initialSupply.ifPresent(b::setInitialSupply);
                                    maxSupply.ifPresent(b::setMaxSupply);
                                    decimals.ifPresent(b::setDecimals);
                                    freezeDefault.ifPresent(b::setFreezeDefault);
                                    adminKey.ifPresent(
                                            k -> b.setAdminKey(spec.registry().getKey(k)));
                                    freezeKey.ifPresent(
                                            k -> b.setFreezeKey(spec.registry().getKey(k)));
                                    supplyKey.ifPresent(
                                            k -> b.setSupplyKey(spec.registry().getKey(k)));
                                    feeScheduleKey.ifPresent(
                                            k -> b.setFeeScheduleKey(spec.registry().getKey(k)));
                                    pauseKey.ifPresent(
                                            k -> b.setPauseKey(spec.registry().getKey(k)));
                                    if (autoRenewAccount.isPresent()) {
                                        final var id = TxnUtils.asId(autoRenewAccount.get(), spec);
                                        b.setAutoRenewAccount(id);
                                        final long secs =
                                                autoRenewPeriod.orElse(
                                                        spec.setup()
                                                                .defaultAutoRenewPeriod()
                                                                .getSeconds());
                                        b.setAutoRenewPeriod(
                                                Duration.newBuilder().setSeconds(secs).build());
                                    }
                                    if (autoRenewPeriod.isEmpty()) {
                                        expiry.ifPresent(
                                                t ->
                                                        b.setExpiry(
                                                                Timestamp.newBuilder()
                                                                        .setSeconds(t)
                                                                        .build()));
                                    }
                                    wipeKey.ifPresent(k -> b.setWipeKey(spec.registry().getKey(k)));
                                    kycKey.ifPresent(k -> b.setKycKey(spec.registry().getKey(k)));
                                    treasury.ifPresent(
                                            a -> {
                                                final var treasuryId = TxnUtils.asId(a, spec);
                                                b.setTreasury(treasuryId);
                                            });
                                    if (!feeScheduleSuppliers.isEmpty()) {
                                        for (final var supplier : feeScheduleSuppliers) {
                                            b.addCustomFees(supplier.apply(spec));
                                        }
                                    }
                                });
        return b -> b.setTokenCreation(opBody);
    }

    @Override
    protected List<Function<HapiSpec, Key>> defaultSigners() {
        final List<Function<HapiSpec, Key>> signers =
                new ArrayList<>(
                        List.of(
                                spec -> spec.registry().getKey(effectivePayer(spec)),
                                spec ->
                                        spec.registry()
                                                .getKey(
                                                        treasury.orElseGet(
                                                                spec.setup()::defaultPayerName))));
        adminKey.ifPresent(k -> signers.add(spec -> spec.registry().getKey(k)));
        freezeKey.ifPresent(k -> signers.add(spec -> spec.registry().getKey(k)));
        autoRenewAccount.ifPresent(k -> signers.add(spec -> spec.registry().getKey(k)));
        return signers;
    }

    @Override
    protected Function<Transaction, TransactionResponse> callToUse(final HapiSpec spec) {
        return spec.clients().getTokenSvcStub(targetNodeFor(spec), useTls)::createToken;
    }

    @Override
    protected void updateStateOf(final HapiSpec spec) {
        if (actualStatus != SUCCESS) {
            return;
        }
        final var registry = spec.registry();
        symbol.ifPresent(s -> registry.saveSymbol(token, s));
        name.ifPresent(s -> registry.saveName(token, s));
        registry.saveMemo(token, memo.orElse(""));
        final TokenID tokenID = lastReceipt.getTokenID();
        registry.saveTokenId(token, tokenID);
        registry.saveTreasury(token, treasury.orElse(spec.setup().defaultPayerName()));
        createdIdObs.ifPresent(obs -> obs.accept(HapiPropertySource.asTokenString(tokenID)));
        Optional.ofNullable(createdAddressObs)
                .ifPresent(obs -> obs.accept(idAsHeadlongAddress(tokenID)));

        try {
            final var submittedBody = CommonUtils.extractTransactionBody(txnSubmitted);
            final var op = submittedBody.getTokenCreation();
            if (op.hasKycKey()) {
                registry.saveKycKey(token, op.getKycKey());
            }
            if (op.hasWipeKey()) {
                registry.saveWipeKey(token, op.getWipeKey());
            }
            if (op.hasAdminKey()) {
                registry.saveAdminKey(token, op.getAdminKey());
            }
            if (op.hasSupplyKey()) {
                registry.saveSupplyKey(token, op.getSupplyKey());
            }
            if (op.hasFreezeKey()) {
                registry.saveFreezeKey(token, op.getFreezeKey());
            }
            if (op.hasFeeScheduleKey()) {
                registry.saveFeeScheduleKey(token, op.getFeeScheduleKey());
            }
            if (op.hasPauseKey()) {
                registry.savePauseKey(token, op.getPauseKey());
            }
        } catch (final InvalidProtocolBufferException impossible) {
        }

        if (advertiseCreation) {
            final String banner =
                    "\n\n"
                            + bannerWith(
                                    String.format(
                                            "Created token '%s' with id '0.0.%d'.",
                                            token, tokenID.getTokenNum()));
            log.info(banner);
        }
        if (asCallableContract) {
            registry.saveContractId(
                    token,
                    ContractID.newBuilder()
                            .setShardNum(tokenID.getShardNum())
                            .setRealmNum(tokenID.getRealmNum())
                            .setContractNum(tokenID.getTokenNum())
                            .build());
        }
    }

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        final MoreObjects.ToStringHelper helper = super.toStringHelper().add("token", token);
        Optional.ofNullable(lastReceipt)
                .ifPresent(
                        receipt -> {
                            if (receipt.getTokenID().getTokenNum() != 0) {
                                helper.add("created", receipt.getTokenID().getTokenNum());
                            }
                        });
        return helper;
    }
}
