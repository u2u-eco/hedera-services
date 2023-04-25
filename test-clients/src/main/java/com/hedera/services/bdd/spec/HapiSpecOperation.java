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
package com.hedera.services.bdd.spec;

import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.extractTxnId;
import static java.util.Collections.EMPTY_LIST;
import static java.util.stream.Collectors.toList;

import com.google.common.base.MoreObjects;
import com.google.protobuf.ByteString;
import com.google.protobuf.UnknownFieldSet;
import com.hedera.node.app.hapi.fees.usage.consensus.ConsensusOpsUsage;
import com.hedera.node.app.hapi.fees.usage.crypto.CryptoOpsUsage;
import com.hedera.node.app.hapi.fees.usage.file.FileOpsUsage;
import com.hedera.node.app.hapi.fees.usage.schedule.ScheduleOpsUsage;
import com.hedera.node.app.hapi.utils.CommonUtils;
import com.hedera.node.app.hapi.utils.fee.CryptoFeeBuilder;
import com.hedera.node.app.hapi.utils.fee.FeeBuilder;
import com.hedera.node.app.hapi.utils.fee.FileFeeBuilder;
import com.hedera.node.app.hapi.utils.fee.SmartContractFeeBuilder;
import com.hedera.services.bdd.spec.keys.ControlForKey;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.spec.keys.SigMapGenerator;
import com.hedera.services.bdd.spec.props.NodeConnectInfo;
import com.hedera.services.bdd.spec.queries.meta.HapiGetTxnRecord;
import com.hedera.services.bdd.spec.stats.OpObs;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class HapiSpecOperation {
    private static final Logger log = LogManager.getLogger(HapiSpecOperation.class);

    protected static final FileOpsUsage fileOpsUsage = new FileOpsUsage();
    protected static final CryptoOpsUsage cryptoOpsUsage = new CryptoOpsUsage();
    protected static final ScheduleOpsUsage scheduleOpsUsage = new ScheduleOpsUsage();
    protected static final ConsensusOpsUsage consensusOpsUsage = new ConsensusOpsUsage();

    private final Random r = new Random();

    /* Note that an op may _be_ a txn; or just a query that submits a txn as payment. */
    protected String txnName = UUID.randomUUID().toString().substring(0, 8);
    protected Transaction txnSubmitted;
    protected TransactionRecord recordOfSubmission;

    protected FeeBuilder fees = new FeeBuilder();
    protected FileFeeBuilder fileFees = new FileFeeBuilder();
    protected CryptoFeeBuilder cryptoFees = new CryptoFeeBuilder();
    protected SmartContractFeeBuilder scFees = new SmartContractFeeBuilder();

    protected boolean omitTxnId = false;
    protected boolean loggingOff = false;
    protected boolean yahcliLogger = false;
    protected boolean suppressStats = false;
    protected boolean omitNodeAccount = false;
    protected boolean verboseLoggingOn = false;
    protected boolean shouldRegisterTxn = false;
    protected boolean useDefaultTxnAsCostAnswerPayment = false;
    protected boolean useDefaultTxnAsAnswerOnlyPayment = false;
    protected boolean usePresetTimestamp = false;
    protected boolean asTxnWithOnlySigMap = false;
    @Nullable protected HapiSpecSetup.TxnProtoStructure explicitProtoStructure = null;
    protected boolean asTxnWithSignedTxnBytesAndSigMap = false;
    protected boolean asTxnWithSignedTxnBytesAndBodyBytes = false;

    protected boolean useTls = false;
    protected HapiSpecSetup.TxnProtoStructure txnProtoStructure =
            HapiSpecSetup.TxnProtoStructure.ALTERNATE;
    protected boolean useRandomNode = false;
    protected boolean unavailableNode = false;
    protected Optional<String> expectedLedgerId = Optional.empty();
    protected Optional<Integer> hardcodedNumPayerKeys = Optional.empty();
    protected Optional<SigMapGenerator> sigMapGen = Optional.empty();
    protected Optional<List<Function<HapiSpec, Key>>> signers = Optional.empty();
    protected Optional<ControlForKey[]> controlOverrides = Optional.empty();
    protected Map<Key, SigControl> overrides = Collections.EMPTY_MAP;

    protected Optional<Long> fee = Optional.empty();
    protected Optional<Long> submitDelay = Optional.empty();
    protected Optional<Long> validDurationSecs = Optional.empty();
    protected Optional<String> customTxnId = Optional.empty();
    protected Optional<String> memo = Optional.empty();
    protected Optional<String> payer = Optional.empty();
    protected Optional<Boolean> genRecord = Optional.empty();
    protected Optional<AccountID> node = Optional.empty();
    protected Optional<Supplier<AccountID>> nodeSupplier = Optional.empty();
    protected OptionalDouble usdFee = OptionalDouble.empty();
    protected Optional<Integer> retryLimits = Optional.empty();

    @Nullable protected UnknownFieldLocation unknownFieldLocation = null;

    public enum UnknownFieldLocation {
        TRANSACTION,
        SIGNED_TRANSACTION,
        TRANSACTION_BODY,
        OP_BODY
    }

    protected abstract long feeFor(HapiSpec spec, Transaction txn, int numPayerKeys)
            throws Throwable;

    protected abstract boolean submitOp(HapiSpec spec) throws Throwable;

    protected Key lookupKey(final HapiSpec spec, final String name) {
        return spec.registry().getKey(name);
    }

    protected void updateStateOf(final HapiSpec spec) throws Throwable {
        /* no-op. */
    }

    protected void assertExpectationsGiven(final HapiSpec spec) throws Throwable {
        /* no-op. */
    }

    public HederaFunctionality type() {
        return HederaFunctionality.UNRECOGNIZED;
    }

    public void finalizeExecFor(final HapiSpec spec) throws Throwable {
        /* no-op. */
    }

    public boolean requiresFinalization(final HapiSpec spec) {
        return false;
    }

    protected AccountID targetNodeFor(final HapiSpec spec) {
        fixNodeFor(spec);
        return node.get();
    }

    protected void configureTlsFor(final HapiSpec spec) {
        useTls = spec.setup().getConfigTLS();
    }

    private void configureProtoStructureFor(final HapiSpec spec) {
        txnProtoStructure =
                (explicitProtoStructure != null)
                        ? explicitProtoStructure
                        : spec.setup().txnProtoStructure();
    }

    protected void fixNodeFor(final HapiSpec spec) {
        if (node.isPresent()) {
            return;
        }
        if (nodeSupplier.isPresent()) {
            node = Optional.of(nodeSupplier.get().get());
        } else {
            if (useRandomNode
                    || spec.setup().nodeSelector() == HapiSpecSetup.NodeSelection.RANDOM) {
                node = Optional.of(randomNodeFrom(spec));
            } else {
                node = Optional.of(spec.setup().defaultNode());
            }
        }
    }

    private AccountID randomNodeFrom(final HapiSpec spec) {
        final List<NodeConnectInfo> nodes = spec.setup().nodes();
        return nodes.get(r.nextInt(nodes.size())).getAccount();
    }

    public Optional<Throwable> execFor(final HapiSpec spec) {
        pauseIfRequested();
        configureProtoStructureFor(spec);
        try {
            final boolean hasCompleteLifecycle = submitOp(spec);

            if (!(this instanceof UtilOp)) {
                spec.incrementNumLedgerOps();
            }

            if (shouldRegisterTxn) {
                registerTxnSubmitted(spec);
            }

            if (hasCompleteLifecycle) {
                assertExpectationsGiven(spec);
                updateStateOf(spec);
            }
        } catch (final Throwable t) {
            t.printStackTrace();
            if (unavailableNode && t.getMessage().startsWith("UNAVAILABLE")) {
                log.info(
                        "Node {} is unavailable as expected!",
                        HapiPropertySource.asAccountString(node.get()));
                return Optional.empty();
            }
            if (verboseLoggingOn) {
                log.warn(spec.logPrefix() + this + " failed - {}", t);
            } else if (!loggingOff) {
                log.warn(spec.logPrefix() + this + " failed {}!", t.getMessage());
            }
            return Optional.of(t);
        }

        if (unavailableNode) {
            final String message =
                    String.format(
                            "Node %s is NOT unavailable as expected!!!",
                            HapiPropertySource.asAccountString(node.get()));
            log.error(message);
            return Optional.of(new RuntimeException(message));
        }
        return Optional.empty();
    }

    private void pauseIfRequested() {
        submitDelay.ifPresent(
                l -> {
                    try {
                        Thread.sleep(l);
                    } catch (final InterruptedException ignore) {
                    }
                });
    }

    private void registerTxnSubmitted(final HapiSpec spec) throws Throwable {
        if (txnSubmitted != Transaction.getDefaultInstance()) {
            spec.registry().saveBytes(txnName, txnSubmitted.toByteString());
            final TransactionID txnId = extractTxnId(txnSubmitted);
            spec.registry().saveTxnId(txnName, txnId);
        }
    }

    protected Consumer<TransactionBody.Builder> bodyDef(final HapiSpec spec) {
        return builder -> {
            if (omitTxnId) {
                builder.clearTransactionID();
            } else {
                payer.ifPresent(
                        payerId -> {
                            final var id = TxnUtils.asId(payerId, spec);
                            final TransactionID txnId =
                                    builder.getTransactionID().toBuilder().setAccountID(id).build();
                            builder.setTransactionID(txnId);
                        });
                if (usePresetTimestamp) {
                    final TransactionID txnId =
                            builder.getTransactionID().toBuilder()
                                    .setTransactionValidStart(spec.registry().getTimestamp(txnName))
                                    .build();
                    builder.setTransactionID(txnId);
                }
                customTxnId.ifPresent(
                        name -> {
                            final TransactionID id = spec.registry().getTxnId(name);
                            builder.setTransactionID(id);
                        });
            }

            if (omitNodeAccount) {
                builder.clearNodeAccountID();
            } else {
                node.ifPresent(builder::setNodeAccountID);
            }
            validDurationSecs.ifPresent(
                    s -> {
                        builder.setTransactionValidDuration(
                                Duration.newBuilder().setSeconds(s).build());
                    });
            genRecord.ifPresent(builder::setGenerateRecord);
            memo.ifPresent(builder::setMemo);
        };
    }

    protected Transaction finalizedTxn(
            final HapiSpec spec, final Consumer<TransactionBody.Builder> opDef) throws Throwable {
        return finalizedTxn(spec, opDef, false);
    }

    protected Transaction finalizedTxn(
            final HapiSpec spec,
            final Consumer<TransactionBody.Builder> opDef,
            final boolean forCost)
            throws Throwable {
        if ((forCost && useDefaultTxnAsCostAnswerPayment)
                || (!forCost && useDefaultTxnAsAnswerOnlyPayment)) {
            return Transaction.getDefaultInstance();
        }

        final Transaction txn;
        final Consumer<TransactionBody.Builder> minDef = bodyDef(spec);
        if (usdFee.isPresent()) {
            final double centsFee = usdFee.getAsDouble() * 100.0;
            final double tinybarFee =
                    centsFee
                            / spec.ratesProvider().rates().getCentEquiv()
                            * spec.ratesProvider().rates().getHbarEquiv()
                            * HapiSuite.ONE_HBAR;
            fee = Optional.of((long) tinybarFee);
        }
        Consumer<TransactionBody.Builder> netDef =
                fee.map(amount -> minDef.andThen(b -> b.setTransactionFee(amount)))
                        .orElse(minDef)
                        .andThen(opDef);

        setKeyControlOverrides(spec);
        final List<Key> keys = signersToUseFor(spec);
        final Transaction.Builder builder = spec.txns().getReadyToSign(netDef);
        final Transaction provisional = getSigned(spec, builder, keys);
        if (fee.isPresent()) {
            txn = provisional;
        } else {
            final Key payerKey =
                    spec.registry().getKey(payer.orElse(spec.setup().defaultPayerName()));
            final int numPayerKeys =
                    hardcodedNumPayerKeys.orElse(
                            spec.keys().controlledKeyCount(payerKey, overrides));
            final long customFee = feeFor(spec, provisional, numPayerKeys);
            netDef = netDef.andThen(b -> b.setTransactionFee(customFee));
            txn = getSigned(spec, spec.txns().getReadyToSign(netDef), keys);
        }

        return finalizedTxnFromTxnWithBodyBytesAndSigMap(txn);
    }

    protected static UnknownFieldSet nonEmptyUnknownFields() {
        return UnknownFieldSet.newBuilder()
                .addField(666, UnknownFieldSet.Field.newBuilder().addFixed32(42).build())
                .build();
    }

    private Transaction finalizedTxnFromTxnWithBodyBytesAndSigMap(
            Transaction txnWithBodyBytesAndSigMap) throws Throwable {
        if (asTxnWithOnlySigMap) {
            return txnWithBodyBytesAndSigMap.toBuilder().clearBodyBytes().build();
        }
        if (explicitProtoStructure == HapiSpecSetup.TxnProtoStructure.OLD) {
            return txnWithBodyBytesAndSigMap;
        }
        ByteString bodyByteString =
                CommonUtils.extractTransactionBodyByteString(txnWithBodyBytesAndSigMap);
        if (unknownFieldLocation == UnknownFieldLocation.TRANSACTION_BODY) {
            bodyByteString =
                    TransactionBody.parseFrom(bodyByteString).toBuilder()
                            .setUnknownFields(nonEmptyUnknownFields())
                            .build()
                            .toByteString();
        }
        final SignatureMap sigMap = CommonUtils.extractSignatureMap(txnWithBodyBytesAndSigMap);
        final var wrapper =
                SignedTransaction.newBuilder().setBodyBytes(bodyByteString).setSigMap(sigMap);
        if (unknownFieldLocation == UnknownFieldLocation.SIGNED_TRANSACTION) {
            wrapper.setUnknownFields(nonEmptyUnknownFields());
        }
        final var signedTransaction = wrapper.build();
        final Transaction.Builder txnWithSignedTxnBytesBuilder =
                Transaction.newBuilder()
                        .setSignedTransactionBytes(signedTransaction.toByteString());
        if (asTxnWithSignedTxnBytesAndSigMap) {
            return txnWithSignedTxnBytesBuilder.setSigMap(sigMap).build();
        }
        if (asTxnWithSignedTxnBytesAndBodyBytes) {
            return txnWithSignedTxnBytesBuilder.setBodyBytes(bodyByteString).build();
        }
        if (txnProtoStructure == HapiSpecSetup.TxnProtoStructure.OLD) {
            if (unknownFieldLocation == UnknownFieldLocation.TRANSACTION) {
                txnWithBodyBytesAndSigMap =
                        txnWithBodyBytesAndSigMap.toBuilder()
                                .setUnknownFields(nonEmptyUnknownFields())
                                .build();
            }
            return txnWithBodyBytesAndSigMap;
        }

        if (unknownFieldLocation == UnknownFieldLocation.TRANSACTION) {
            txnWithSignedTxnBytesBuilder.setUnknownFields(nonEmptyUnknownFields());
        }
        return txnWithSignedTxnBytesBuilder.build();
    }

    private Transaction getSigned(
            final HapiSpec spec, final Transaction.Builder builder, final List<Key> keys)
            throws Throwable {
        return sigMapGen.isPresent()
                ? spec.keys().sign(spec, builder, keys, overrides, sigMapGen.get())
                : spec.keys().sign(spec, builder, keys, overrides);
    }

    private void setKeyControlOverrides(final HapiSpec spec) {
        if (controlOverrides.isPresent()) {
            overrides = new HashMap<>();
            Stream.of(controlOverrides.get())
                    .forEach(
                            c -> overrides.put(lookupKey(spec, c.getKeyName()), c.getController()));
        }
    }

    private List<Key> signersToUseFor(final HapiSpec spec) {
        final List<Key> active =
                signers.orElse(defaultSigners()).stream()
                        .map(f -> f.apply(spec))
                        .filter(k -> k != Key.getDefaultInstance())
                        .collect(toList());
        if (!signers.isPresent()) {
            active.addAll(variableDefaultSigners().apply(spec));
        }
        return active;
    }

    protected boolean isWithInRetryLimit(final int retryCount) {
        return retryLimits.map(integer -> retryCount < integer).orElse(true);
    }

    protected List<Function<HapiSpec, Key>> defaultSigners() {
        return Arrays.asList(spec -> spec.registry().getKey(effectivePayer(spec)));
    }

    protected Function<HapiSpec, List<Key>> variableDefaultSigners() {
        return spec -> EMPTY_LIST;
    }

    protected String effectivePayer(final HapiSpec spec) {
        return payer.orElse(spec.setup().defaultPayerName());
    }

    protected void lookupSubmissionRecord(final HapiSpec spec) throws Throwable {
        final HapiGetTxnRecord subOp =
                getTxnRecord(extractTxnId(txnSubmitted))
                        .noLogging()
                        .assertingNothing()
                        .suppressStats(true)
                        .nodePayment(spec.setup().defaultNodePaymentTinyBars());
        final Optional<Throwable> error = subOp.execFor(spec);
        if (error.isPresent()) {
            throw error.get();
        }
        recordOfSubmission = subOp.getResponse().getTransactionGetRecord().getTransactionRecord();
    }

    @Override
    public String toString() {
        return toStringHelper().toString();
    }

    protected MoreObjects.ToStringHelper toStringHelper() {
        final MoreObjects.ToStringHelper helper = MoreObjects.toStringHelper(this);
        if (txnSubmitted != null) {
            helper.add("sigs", FeeBuilder.getSignatureCount(txnSubmitted));
        }
        payer.ifPresent(a -> helper.add("payer", a));
        node.ifPresent(id -> helper.add("node", HapiPropertySource.asAccountString(id)));
        return helper;
    }

    public Optional<String> getPayer() {
        return payer;
    }

    protected void considerRecording(final HapiSpec spec, final OpObs obs) {
        if (!suppressStats) {
            spec.registry().record(obs);
        }
    }

    protected ByteString rationalize(final String expectedLedgerId) {
        final var hex = expectedLedgerId.substring(2);
        final var bytes = HexFormat.of().parseHex(hex);
        return ByteString.copyFrom(bytes);
    }
}
