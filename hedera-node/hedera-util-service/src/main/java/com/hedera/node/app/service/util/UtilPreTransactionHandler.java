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
package com.hedera.node.app.service.util;

import com.hedera.node.app.spi.PreTransactionHandler;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionBody;

/**
 * The pre-handler for the HAPI <a
 * href="https://github.com/hashgraph/hedera-protobufs/blob/main/services/util_service.proto">Util
 * Service</a>.
 */
public interface UtilPreTransactionHandler extends PreTransactionHandler {
    /**
     * Pre-handles a {@link com.hederahashgraph.api.proto.java.HederaFunctionality#UtilPrng}
     * transaction, returning the metadata required to, at minimum, validate the signatures of all
     * required signing keys.
     *
     * @param txn a transaction with a {@link
     *     com.hederahashgraph.api.proto.java.UtilPrngTransactionBody}
     * @return the metadata for the pseudo-random number generation
     */
    TransactionMetadata preHandlePrng(TransactionBody txn, AccountID payer);
}
