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
package com.hedera.node.app.service.mono.queries.answering;

import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.records.RecordCache;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.migration.QueryableRecords;
import com.hedera.node.app.service.mono.state.submerkle.ExpirableTxnRecord;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hederahashgraph.api.proto.java.CryptoGetAccountRecordsQuery;
import com.hederahashgraph.api.proto.java.TransactionGetRecordQuery;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AnswerFunctions {
    private final GlobalDynamicProperties dynamicProperties;

    @Inject
    public AnswerFunctions(GlobalDynamicProperties dynamicProperties) {
        this.dynamicProperties = dynamicProperties;
    }

    /**
     * Returns the most recent payer records available for an account in the given {@link
     * StateView}.
     *
     * <p>Note that at <b>most</b> {@link GlobalDynamicProperties#maxNumQueryableRecords()} records
     * will be available, even if the given account has paid for more than this number of
     * transactions in the last 180 seconds.
     *
     * @param view the view of the world state to get payer records from
     * @param op the query with the target payer account
     * @return the most recent available records for the given payer
     */
    public List<TransactionRecord> mostRecentRecords(
            final StateView view, final CryptoGetAccountRecordsQuery op) {
        final var payerNum = EntityNum.fromAccountId(op.getAccountID());
        final var queryableRecords = view.payerRecords().getReadOnlyPayerRecords(payerNum);
        return mostRecentFrom(queryableRecords, dynamicProperties.maxNumQueryableRecords());
    }

    /**
     * Returns the record of the requested transaction from the given {@link RecordCache}, if
     * available.
     *
     * @param recordCache the cache to get the record from
     * @param op the query with the target transaction id
     * @return the transaction record if available
     */
    public Optional<TransactionRecord> txnRecord(
            final RecordCache recordCache, final TransactionGetRecordQuery op) {
        final var txnId = op.getTransactionID();
        final var expirableTxnRecord = recordCache.getPriorityRecord(txnId);
        return Optional.ofNullable(expirableTxnRecord).map(ExpirableTxnRecord::asGrpc);
    }

    /* --- Internal helpers --- */
    /**
     * Returns up to the last {@code m}-of-{@code n} payer records from an account in gRPC form.
     *
     * <p>Since records are added FIFO to the payer account, the last records are the most recent;
     * and presumably the most interesting.
     *
     * <p>If the given {@link MerkleAccount} is from the working state (as in release 0.22), then
     * this method acts on a best-effort basis, and returns only the relevant records it could
     * iterate over before hitting a {@link ConcurrentModificationException} or {@link
     * NoSuchElementException}.
     *
     * @param payerRecords queryable records from the account of interest
     * @param m the maximum number of records to return
     * @return the available records
     */
    private List<TransactionRecord> mostRecentFrom(
            final QueryableRecords payerRecords, final int m) {
        final List<TransactionRecord> ans = new ArrayList<>();
        final var n = payerRecords.expectedSize();
        final Iterator<ExpirableTxnRecord> iter = payerRecords.iterator();
        try {
            for (int i = 0, cutoff = n - m; i < n; i++) {
                final var nextRecord = iter.next();
                if (i >= cutoff) {
                    ans.add(nextRecord.asGrpc());
                }
            }
        } catch (ConcurrentModificationException | NoSuchElementException ignore) {
            /* Records expired while we were iterating the list, return only what we could find. */
        }
        return ans;
    }
}
