module com.hedera.node.app.service.token.impl.test {
    requires com.hedera.node.app.service.token;
    requires com.hedera.node.app.service.token.impl;
    requires org.junit.jupiter.api;
    requires com.hedera.hashgraph.protobuf.java.api;
    requires com.google.protobuf;
    requires com.hedera.node.app.service.mono;
    requires org.mockito;
    requires org.mockito.junit.jupiter;
    requires org.apache.commons.lang3;
    requires com.hedera.node.app.service.mono.testFixtures;
    requires org.hyperledger.besu.datatypes;
    requires org.assertj.core;
    requires org.hamcrest;
    requires hedera.services.hedera.node.hedera.app.spi.testFixtures;
    requires com.swirlds.common;
    requires org.bouncycastle.provider;
    requires com.hedera.node.app.service.evm;

    opens com.hedera.node.app.service.token.impl.test to
            org.junit.platform.commons;
    opens com.hedera.node.app.service.token.impl.test.entity to
            org.junit.platform.commons;
    opens com.hedera.node.app.service.token.impl.test.util to
            org.junit.platform.commons;
    opens com.hedera.node.app.service.token.impl.test.handlers to
            org.junit.platform.commons;
}
