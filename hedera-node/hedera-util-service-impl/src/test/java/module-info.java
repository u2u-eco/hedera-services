module com.hedera.node.app.service.util.impl.test {
    requires com.hedera.node.app.service.util;
    requires com.hedera.node.app.service.util.impl;
    requires org.junit.jupiter.api;

    opens com.hedera.node.app.service.util.impl.test to
            org.junit.platform.commons;
}
