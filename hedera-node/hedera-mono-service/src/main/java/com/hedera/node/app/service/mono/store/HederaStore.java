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
package com.hedera.node.app.service.mono.store;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.node.app.service.mono.ledger.HederaLedger;
import com.hedera.node.app.service.mono.ledger.TransactionalLedger;
import com.hedera.node.app.service.mono.ledger.ids.EntityIdSource;
import com.hedera.node.app.service.mono.ledger.properties.AccountProperty;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

/**
 * Provides an abstract store, having common functionality related to {@link EntityIdSource}, {@link
 * HederaLedger} and {@link TransactionalLedger} for accounts.
 */
public abstract class HederaStore {
    protected final EntityIdSource ids;

    protected HederaLedger hederaLedger;
    protected TransactionalLedger<AccountID, AccountProperty, HederaAccount> accountsLedger;

    protected HederaStore(EntityIdSource ids) {
        this.ids = ids;
    }

    public void setAccountsLedger(
            TransactionalLedger<AccountID, AccountProperty, HederaAccount> accountsLedger) {
        this.accountsLedger = accountsLedger;
    }

    public void setHederaLedger(HederaLedger hederaLedger) {
        this.hederaLedger = hederaLedger;
    }

    public void rollbackCreation() {
        ids.reclaimLastId();
    }

    protected ResponseCodeEnum usableOrElse(AccountID aId, ResponseCodeEnum fallbackFailure) {
        final var validity = checkAccountUsability(aId);

        return (validity == ACCOUNT_EXPIRED_AND_PENDING_REMOVAL || validity == OK)
                ? validity
                : fallbackFailure;
    }

    protected ResponseCodeEnum checkAccountUsability(AccountID aId) {
        return hederaLedger.usabilityOf(aId);
    }
}
