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
package com.hedera.node.app.service.mono.state.exports;

import static com.hedera.node.app.hapi.utils.exports.FileCompressionUtils.COMPRESSION_ALGORITHM_EXTENSION;
import static com.hedera.node.app.service.mono.context.primitives.StateView.doBoundedIteration;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.LEDGER_TOTAL_TINY_BAR_FLOAT;
import static com.hedera.node.app.service.mono.ledger.HederaLedger.ACCOUNT_ID_COMPARATOR;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.readableId;

import com.hedera.node.app.service.mono.ServicesState;
import com.hedera.node.app.service.mono.context.annotations.CompositeProps;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.context.properties.PropertySource;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.state.migration.TokenRelStorageAdapter;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.MiscUtils;
import com.hedera.node.app.service.mono.utils.NonAtomicReference;
import com.hedera.node.app.service.mono.utils.SystemExits;
import com.hedera.services.stream.proto.AllAccountBalances;
import com.hedera.services.stream.proto.SingleAccountBalances;
import com.hedera.services.stream.proto.TokenUnitBalance;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.HashingOutputStream;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.system.NodeId;
import com.swirlds.merkle.map.MerkleMap;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.zip.GZIPOutputStream;
import javax.inject.Singleton;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class SignedStateBalancesExporter implements BalancesExporter {
    private static final Logger log = LogManager.getLogger(SignedStateBalancesExporter.class);

    private static final String UNKNOWN_EXPORT_DIR = "";
    private static final String BAD_EXPORT_ATTEMPT_ERROR_MSG_TPL = "Could not export to '{}'!";
    private static final String BAD_SIGNING_ATTEMPT_ERROR_MSG_TPL =
            "Could not sign balance file '{}'!";
    private static final String BAD_EXPORT_DIR_ERROR_MSG_TPL =
            "Cannot ensure existence of export dir '{}'!";
    private static final String LOW_NODE_BALANCE_WARN_MSG_TPL =
            "Node '{}' has unacceptably low balance {}!";
    private static final String GOOD_SIGNING_ATTEMPT_DEBUG_MSG_TPL =
            "Created balance signature file '{}'.";

    private static final String PROTO_FILE_EXTENSION = ".pb";

    private Instant nextExportTime = null;

    final long expectedFloat;
    private final SystemExits systemExits;
    private final Function<byte[], Signature> signer;
    private final GlobalDynamicProperties dynamicProperties;

    SigFileWriter sigFileWriter = new StandardSigFileWriter();
    FileHashReader hashReader = new Sha384HashReader();
    DirectoryAssurance directories = loc -> Files.createDirectories(Paths.get(loc));

    private String lastUsedExportDir = UNKNOWN_EXPORT_DIR;
    private BalancesSummary summary;

    private final int exportPeriod;
    private final MessageDigest accountBalanceDigest;

    static final Comparator<SingleAccountBalances> SINGLE_ACCOUNT_BALANCES_COMPARATOR =
            Comparator.comparing(SingleAccountBalances::getAccountID, ACCOUNT_ID_COMPARATOR);

    public SignedStateBalancesExporter(
            final SystemExits systemExits,
            final @CompositeProps PropertySource properties,
            final Function<byte[], Signature> signer,
            final GlobalDynamicProperties dynamicProperties)
            throws NoSuchAlgorithmException {
        this.signer = signer;
        this.systemExits = systemExits;
        this.expectedFloat = properties.getLongProperty(LEDGER_TOTAL_TINY_BAR_FLOAT);
        this.dynamicProperties = dynamicProperties;
        this.exportPeriod = dynamicProperties.balancesExportPeriodSecs();
        this.accountBalanceDigest =
                MessageDigest.getInstance(Cryptography.DEFAULT_DIGEST_TYPE.algorithmName());
    }

    private Instant getFirstExportTime(final Instant now, final int exportPeriodInSecs) {
        final long epochSeconds = now.getEpochSecond();
        final long elapsedSecs = epochSeconds % exportPeriodInSecs;
        return elapsedSecs == 0
                ? Instant.ofEpochSecond(now.getEpochSecond())
                : Instant.ofEpochSecond(
                        now.plusSeconds(exportPeriodInSecs - elapsedSecs).getEpochSecond());
    }

    Instant getNextExportTime() {
        return nextExportTime;
    }

    @Override
    public boolean isTimeToExport(final Instant now) {
        if (!dynamicProperties.shouldExportBalances()) {
            return false;
        }
        if (nextExportTime == null) {
            nextExportTime = getFirstExportTime(now, exportPeriod);
        }
        if (!now.isBefore(nextExportTime)) {
            nextExportTime = nextExportTime.plusSeconds(exportPeriod);
            return true;
        }
        return false;
    }

    @Override
    public void exportBalancesFrom(
            final ServicesState signedState, final Instant consensusTime, final NodeId nodeId) {
        if (!ensureExportDir(signedState.getAccountFromNodeId(nodeId))) {
            return;
        }
        final var watch = StopWatch.createStarted();
        summary = summarized(signedState);
        final var expected = BigInteger.valueOf(expectedFloat);
        if (expected.equals(summary.totalFloat())) {
            log.info(
                    "Took {}ms to summarize signed state balances",
                    watch.getTime(TimeUnit.MILLISECONDS));
            toProtoFile(consensusTime);
        } else {
            log.error(
                    "Signed state @ {} had total balance {} not {}; exiting",
                    consensusTime,
                    summary.totalFloat(),
                    expectedFloat);
            systemExits.fail(1);
        }
    }

    private void toProtoFile(final Instant exportTimeStamp) {
        final var watch = StopWatch.createStarted();

        final var builder = AllAccountBalances.newBuilder();
        summarizeAsProto(exportTimeStamp, builder);
        final var protoLoc =
                lastUsedExportDir
                        + exportTimeStamp.toString().replace(":", "_")
                        + "_Balances"
                        + (dynamicProperties.shouldCompressAccountBalanceFilesOnCreation()
                                ? PROTO_FILE_EXTENSION + COMPRESSION_ALGORITHM_EXTENSION
                                : PROTO_FILE_EXTENSION);
        final boolean exportSucceeded = exportBalancesProtoFile(builder, protoLoc);
        if (exportSucceeded) {
            tryToSign(protoLoc);
        }

        log.info(
                " -> Took {}ms to export and sign proto balances file at {}",
                watch.getTime(TimeUnit.MILLISECONDS),
                exportTimeStamp);
    }

    private void tryToSign(final String fileLoc) {
        try {
            final var hash = accountBalanceDigest.digest();
            final var sig = signer.apply(hash);
            final var sigFileLoc =
                    sigFileWriter.writeSigFile(fileLoc, sig.getSignatureBytes(), hash);
            if (log.isDebugEnabled()) {
                log.debug(GOOD_SIGNING_ATTEMPT_DEBUG_MSG_TPL, sigFileLoc);
            }
        } catch (final Exception e) {
            log.error(BAD_SIGNING_ATTEMPT_ERROR_MSG_TPL, fileLoc, e);
        }
    }

    private void summarizeAsProto(
            final Instant exportTimeStamp, final AllAccountBalances.Builder builder) {
        builder.setConsensusTimestamp(
                Timestamp.newBuilder()
                        .setSeconds(exportTimeStamp.getEpochSecond())
                        .setNanos(exportTimeStamp.getNano()));
        builder.addAllAllAccounts(summary.orderedBalances());
    }

    private boolean exportBalancesProtoFile(
            final AllAccountBalances.Builder allAccountsBuilder, final String protoLoc) {
        accountBalanceDigest.reset();
        try (final var outputStream =
                        dynamicProperties.shouldCompressAccountBalanceFilesOnCreation()
                                ? new GZIPOutputStream(new FileOutputStream(protoLoc))
                                : new FileOutputStream(protoLoc);
                final var hashingOutputStream =
                        new HashingOutputStream(accountBalanceDigest, outputStream)) {
            allAccountsBuilder.build().writeTo(hashingOutputStream);
            outputStream.flush();
        } catch (final IOException e) {
            log.error(BAD_EXPORT_ATTEMPT_ERROR_MSG_TPL, protoLoc, e);
            return false;
        }
        return true;
    }

    BalancesSummary summarized(final ServicesState signedState) {
        final long nodeBalanceWarnThreshold = dynamicProperties.nodeBalanceWarningThreshold();
        final var totalFloat = new NonAtomicReference<>(BigInteger.valueOf(0L));
        final List<SingleAccountBalances> accountBalances = new ArrayList<>();

        final var nodeIds = MiscUtils.getNodeAccounts(signedState.addressBook());
        final var tokens = signedState.tokens();
        final var accounts = signedState.accounts();
        final var tokenAssociations = signedState.tokenAssociations();
        accounts.forEach(
                (id, account) -> {
                    if (!account.isDeleted()) {
                        final var accountId = id.toGrpcAccountId();
                        final var balance = account.getBalance();
                        if (nodeIds.contains(accountId) && balance < nodeBalanceWarnThreshold) {
                            log.warn(LOW_NODE_BALANCE_WARN_MSG_TPL, readableId(accountId), balance);
                        }
                        totalFloat.set(
                                totalFloat.get().add(BigInteger.valueOf(account.getBalance())));
                        final SingleAccountBalances.Builder sabBuilder =
                                SingleAccountBalances.newBuilder();
                        sabBuilder.setHbarBalance(balance).setAccountID(accountId);
                        if (dynamicProperties.shouldExportTokenBalances()) {
                            addTokenBalances(account, sabBuilder, tokens, tokenAssociations);
                        }
                        accountBalances.add(sabBuilder.build());
                    }
                });
        accountBalances.sort(SINGLE_ACCOUNT_BALANCES_COMPARATOR);
        return new BalancesSummary(totalFloat.get(), accountBalances);
    }

    private void addTokenBalances(
            final HederaAccount account,
            final SingleAccountBalances.Builder sabBuilder,
            final MerkleMap<EntityNum, MerkleToken> tokens,
            final TokenRelStorageAdapter tokenAssociations) {
        doBoundedIteration(
                tokenAssociations,
                tokens,
                account,
                (token, rel) -> {
                    if (token != StateView.REMOVED_TOKEN) {
                        sabBuilder.addTokenUnitBalances(
                                unitBalanceFrom(token.grpcId(), rel.getBalance()));
                    }
                });
    }

    private TokenUnitBalance unitBalanceFrom(final TokenID id, final long balance) {
        return TokenUnitBalance.newBuilder().setTokenId(id).setBalance(balance).build();
    }

    private boolean ensureExportDir(final AccountID node) {
        final var correctDir = dynamicProperties.pathToBalancesExportDir();
        if (!lastUsedExportDir.startsWith(correctDir)) {
            final var sb = new StringBuilder(correctDir);
            if (!correctDir.endsWith(File.separator)) {
                sb.append(File.separator);
            }
            sb.append("balance").append(readableId(node)).append(File.separator);
            final var candidateDir = sb.toString();
            try {
                directories.ensureExistenceOf(candidateDir);
                lastUsedExportDir = candidateDir;
            } catch (final IOException e) {
                log.error(BAD_EXPORT_DIR_ERROR_MSG_TPL, candidateDir);
                return false;
            }
        }
        return true;
    }
}
