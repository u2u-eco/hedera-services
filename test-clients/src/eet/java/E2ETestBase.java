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
import com.hedera.services.bdd.junit.HederaContainer;
import com.hedera.services.bdd.junit.TestBase;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.props.JutilPropertySource;
import java.io.File;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Base class for end-to-end tests */
@Testcontainers
public abstract class E2ETestBase extends TestBase {
    private static final File WORKSPACE = new File(System.getProperty("networkWorkspaceDir"));

    private static final DockerImageName IMAGE = DockerImageName.parse(System.getProperty("TAG"));

    private static final Network NETWORK = Network.newNetwork();

    /**
     * Using Testcontainers, create a node container. This code currently presupposes that a docker
     * image has been created out-of-band. The docker image name is supplied as TAG.
     */
    @Container
    private static final HederaContainer NODE_0 =
            new HederaContainer(IMAGE, 0)
                    .withClasspathResourceMappingDir("network/config")
                    .withWorkspace(WORKSPACE)
                    .withNetwork(NETWORK);

    /**
     * Using Testcontainers, create a node container. This code currently presupposes that a docker
     * image has been created out-of-band. The docker image name is supplied as TAG.
     */
    @Container
    private static final HederaContainer NODE_1 =
            new HederaContainer(IMAGE, 1)
                    .withClasspathResourceMappingDir("network/config")
                    .withWorkspace(WORKSPACE)
                    .withNetwork(NETWORK);

    /**
     * Using Testcontainers, create a node container. This code currently presupposes that a docker
     * image has been created out-of-band. The docker image name is supplied as TAG.
     */
    @Container
    private static final HederaContainer NODE_2 =
            new HederaContainer(IMAGE, 2)
                    .withClasspathResourceMappingDir("network/config")
                    .withWorkspace(WORKSPACE)
                    .withNetwork(NETWORK);

    /** Before any test runs, configure HapiApiSpec to use the Testcontainer we created */
    @BeforeAll
    static void beforeAll() {
        try {
            NODE_0.waitUntilActive(Duration.ofSeconds(30));
            NODE_1.waitUntilActive(Duration.ofSeconds(30));
            NODE_2.waitUntilActive(Duration.ofSeconds(30));
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        }

        final var defaultProperties = JutilPropertySource.getDefaultInstance();
        HapiSpec.runInCiMode(
                NODE_0.getFirstMappedPort()
                        + ":"
                        + NODE_1.getFirstMappedPort()
                        + ":"
                        + NODE_2.getFirstMappedPort(),
                defaultProperties.get("default.payer"),
                defaultProperties.get("default.node").split("\\.")[2],
                defaultProperties.get("tls"),
                defaultProperties.get("txn.proto.structure"),
                defaultProperties.get("node.selector"),
                Collections.emptyMap());
    }
}
