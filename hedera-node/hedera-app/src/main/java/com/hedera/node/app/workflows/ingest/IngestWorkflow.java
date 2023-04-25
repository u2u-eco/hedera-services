/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.workflows.ingest;

import com.hedera.node.app.SessionContext;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.ByteBuffer;

/**
 * The {@link IngestWorkflow} represents the workflow used when receiving a {@link Transaction} from
 * a client (currently always through gRPC). This workflow takes the transaction, checks it,
 * verifies the payer exists, signed the transaction, and has sufficient balance, checks the
 * throttles, and performs any other required tasks, and then submits the transaction to the
 * hashgraph platform for consensus.
 */
public interface IngestWorkflow {
    /**
     * Called to handle a single transaction during the ingestion flow. The call terminates in a
     * {@link TransactionResponse} being returned to the client (for both successful and
     * unsuccessful calls). There are no unhandled exceptions (even Throwable is handled).
     *
     * @param session The per-request {@link SessionContext}.
     * @param requestBuffer The raw protobuf transaction bytes. Must be a transaction object.
     * @param responseBuffer The raw protobuf response bytes.
     */
    void submitTransaction(
            @NonNull SessionContext session,
            @NonNull ByteBuffer requestBuffer,
            @NonNull ByteBuffer responseBuffer);
}
