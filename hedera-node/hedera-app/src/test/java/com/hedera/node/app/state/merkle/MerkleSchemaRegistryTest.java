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
package com.hedera.node.app.state.merkle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.hedera.node.app.spi.SemanticVersionComparator;
import com.hedera.node.app.spi.fixtures.state.TestSchema;
import com.hedera.node.app.spi.state.*;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

/**
 * Tests for the {@link MerkleSchemaRegistry}. The only thing not covered here are serialization
 * tests, they are covered in {@link SerializationTest}.
 */
class MerkleSchemaRegistryTest extends MerkleTestBase {
    private MerkleSchemaRegistry schemaRegistry;
    private @TempDir Path storageDir;

    @BeforeEach
    void setUp() {
        // We don't need a real registry, and the unit tests are much
        // faster if we use a mocked one
        registry = Mockito.mock(ConstructableRegistry.class);
        schemaRegistry = new MerkleSchemaRegistry(registry, storageDir, FIRST_SERVICE);
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTest {
        @Test
        @DisplayName("A null ConstructableRegistry throws")
        void nullRegistryThrows() {
            //noinspection ConstantConditions
            assertThatThrownBy(() -> new MerkleSchemaRegistry(null, storageDir, FIRST_SERVICE))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("A null storageDir is OK")
        void nullStorageDirOK() {
            assertThat(new MerkleSchemaRegistry(registry, null, FIRST_SERVICE)).isNotNull();
        }

        @Test
        @DisplayName("A null serviceName throws")
        void nullServiceNameThrows() {
            //noinspection ConstantConditions
            assertThatThrownBy(() -> new MerkleSchemaRegistry(registry, storageDir, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("Registration Tests")
    class RegistrationTest {
        @Test
        @DisplayName("Registering with a null Schema throws NPE")
        void nullSchemaThrows() {
            //noinspection DataFlowIssue
            assertThatThrownBy(() -> schemaRegistry.register(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Registering with a schema")
        void registerOnce() {
            // Given a schema
            final var schema = Mockito.spy(new TestSchema(10));

            // When it is registered
            schemaRegistry.register(schema);

            // Then on migrateFromV9ToV10, it is called
            migrateFromV9ToV10();
            Mockito.verify(schema, Mockito.times(1)).migrate(Mockito.any(), Mockito.any());
        }

        @Test
        @DisplayName("Registering with the same schema twice")
        void registerTwice() {
            // Given a schema
            final var schema = Mockito.spy(new TestSchema(10));

            // When it is registered twice
            schemaRegistry.register(schema);
            schemaRegistry.register(schema);

            // Then on migrateFromV9ToV10, it is called only once
            migrateFromV9ToV10();
            Mockito.verify(schema, Mockito.times(1)).migrate(Mockito.any(), Mockito.any());
        }

        @Test
        @DisplayName(
                "Registering two schemas that are different but have the same version number, the"
                        + " second is used")
        void registerSameVersionDifferentInstances() {
            // Given two schemas which do different things but have the same version
            final var schema1 = Mockito.spy(new TestSchema(10));
            final var schema2 = Mockito.spy(new TestSchema(10));

            // When they are both registered
            schemaRegistry.register(schema1);
            schemaRegistry.register(schema2);

            // Then on migrateFromV9ToV10, the last one registered wins
            migrateFromV9ToV10();
            Mockito.verify(schema1, Mockito.times(0)).migrate(Mockito.any(), Mockito.any());
            Mockito.verify(schema2, Mockito.times(1)).migrate(Mockito.any(), Mockito.any());
        }

        /** Utility method that migrates from version 9 to 10 */
        void migrateFromV9ToV10() {
            schemaRegistry.migrate(
                    new MerkleHederaState(tree -> {}, (e) -> {}, (round, dualState) -> {}),
                    version(9, 0, 0),
                    version(10, 0, 0));
        }
    }

    @Nested
    @DisplayName("Migration Tests")
    class MigrationTest {
        private MerkleHederaState merkleTree;
        private SemanticVersion[] versions;

        @BeforeEach
        void setUp() {
            merkleTree = new MerkleHederaState(tree -> {}, (e) -> {}, (round, dualState) -> {});

            // Let the first version[0] be null, and all others have a number
            versions = new SemanticVersion[10];
            for (int i = 1; i < versions.length; i++) {
                versions[i] = version(0, i, 0);
            }
        }

        @Test
        @DisplayName("Calling migrate with a null hederaState throws NPE")
        void nullMerkleThrows() {
            //noinspection ConstantConditions
            assertThatThrownBy(() -> schemaRegistry.migrate(null, versions[0], versions[1]))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Calling migrate with a null currentVersion throws NPE")
        void nullCurrentVersionThrows() {
            //noinspection ConstantConditions
            assertThatThrownBy(() -> schemaRegistry.migrate(merkleTree, versions[0], null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Calling migrate with a currentVersion < previousVersion throws IAE")
        void currentVersionLessThanPreviousVersionThrows() {
            //noinspection ConstantConditions
            assertThatThrownBy(() -> schemaRegistry.migrate(merkleTree, versions[5], versions[4]))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Migration is skipped if previousVersion == currentVersion")
        void migrateIsSkippedIfVersionsAreTheSame() {
            // Given a schema
            final var schema = Mockito.spy(new TestSchema(versions[1]));

            // When it is registered twice and migrate is called
            schemaRegistry.register(schema);
            schemaRegistry.migrate(merkleTree, versions[1], versions[1]);

            // Then nothing happens
            Mockito.verify(schema, Mockito.times(0)).migrate(Mockito.any(), Mockito.any());
        }

        @ParameterizedTest(name = "From ({0}, {1}]")
        @CsvSource(
                textBlock =
                        """
                0, 1
                0, 2
                0, 3
                0, 4
                1, 2
                1, 3
                1, 4
                2, 3
                2, 4
                3, 4
                0, 9
                """)
        @DisplayName("Migration applies to all versions up to and including the currentVersion")
        void migrate(int firstVersion, int lastVersion) {
            // We will place into this list each schema as it is called
            final var called = new LinkedList<SemanticVersion>();

            // Given a schema for each version
            // versions = [null, 1, 2, 3, 4, ... ]
            // schemas = [null, 1, 2, 3, 4, ... ]
            final var schemas = new Schema[versions.length];
            for (int i = 1; i < schemas.length; i++) {
                final var ver = versions[i];
                final var schema = Mockito.spy(new TestSchema(ver, () -> called.add(ver)));
                schemas[i] = schema;
                schemaRegistry.register(schema);
            }

            // When we migrate
            schemaRegistry.migrate(merkleTree, versions[firstVersion], versions[lastVersion]);

            // Then each schema less than or equal to firstVersion are not called
            for (int i = 1; i <= firstVersion; i++) {
                Mockito.verify(schemas[i], Mockito.times(0)).migrate(Mockito.any(), Mockito.any());
            }

            // And each schema greater than firstVersion and less than or equal to lastVersion are
            // called
            for (int i = firstVersion + 1; i <= lastVersion; i++) {
                Mockito.verify(schemas[i], Mockito.times(1)).migrate(Mockito.any(), Mockito.any());
            }

            // And each schema greater than lastVersion are not called
            for (int i = lastVersion + 1; i < versions.length; i++) {
                Mockito.verify(schemas[i], Mockito.times(0)).migrate(Mockito.any(), Mockito.any());
            }

            // And each called schema was called in order
            final var itr = called.iterator();
            var prev = itr.next();
            while (itr.hasNext()) {
                final var ver = itr.next();
                assertThat(SemanticVersionComparator.INSTANCE.compare(ver, prev)).isPositive();
                prev = ver;
            }
        }

        @Test
        @DisplayName("Migration captures all appropriate schemas even when they skip versions")
        void migrateWhenSchemasSkipVersions() {
            // We will place into this list each schema as it is called
            final var called = new LinkedList<SemanticVersion>();

            // Given a schema for v1, v4, v6
            final var schemaV1 = new TestSchema(versions[1], () -> called.add(versions[1]));
            final var schemaV4 = new TestSchema(versions[4], () -> called.add(versions[4]));
            final var schemaV6 = new TestSchema(versions[6], () -> called.add(versions[6]));
            schemaRegistry.register(schemaV1);
            schemaRegistry.register(schemaV4);
            schemaRegistry.register(schemaV6);

            // When we migrate from v0 to v7
            schemaRegistry.migrate(merkleTree, null, versions[7]);

            // Then each of v1, v4, and v6 are called
            assertThat(called).hasSize(3);
            assertThat(called.removeFirst()).isSameAs(versions[1]);
            assertThat(called.removeFirst()).isSameAs(versions[4]);
            assertThat(called.removeFirst()).isSameAs(versions[6]);
        }

        /** In these tests, each migration will apply some kind of state change to the tree. */
        @Nested
        @DisplayName("Migration State Impact Tests")
        class StateImpactTest {
            @BeforeEach
            void setUp() {}

            Schema createV1Schema() {
                return new TestSchema(versions[1]) {
                    @NonNull
                    @Override
                    @SuppressWarnings("rawtypes")
                    public Set<StateDefinition> statesToCreate() {
                        final var fruitDef =
                                StateDefinition.inMemory(
                                        FRUIT_STATE_KEY, STRING_SERDES, STRING_SERDES);
                        return Set.of(fruitDef);
                    }

                    @Override
                    public void migrate(
                            @NonNull ReadableStates previousStates,
                            @NonNull WritableStates newStates) {
                        assertThat(previousStates.isEmpty()).isTrue();
                        assertThat(newStates.size()).isEqualTo(1);
                        final WritableKVState<String, String> fruit =
                                newStates.get(FRUIT_STATE_KEY);
                        fruit.put(A_KEY, APPLE);
                        fruit.put(B_KEY, BANANA);
                        fruit.put(C_KEY, CHERRY);
                    }
                };
            }

            Schema createV2Schema() {
                return new TestSchema(versions[2]) {
                    @NonNull
                    @Override
                    @SuppressWarnings("rawtypes")
                    public Set<StateDefinition> statesToCreate() {
                        final var animalDef =
                                StateDefinition.onDisk(
                                        ANIMAL_STATE_KEY, STRING_SERDES, STRING_SERDES, 100);
                        final var countryDef =
                                StateDefinition.singleton(COUNTRY_STATE_KEY, STRING_SERDES);
                        return Set.of(animalDef, countryDef);
                    }

                    @Override
                    public void migrate(
                            @NonNull ReadableStates previousStates,
                            @NonNull WritableStates newStates) {
                        // First check that the previous states only includes what was there before,
                        // and nothing new
                        assertThat(previousStates.isEmpty()).isFalse();
                        assertThat(previousStates.contains(FRUIT_STATE_KEY)).isTrue();
                        final ReadableKVState<String, String> oldFruit =
                                previousStates.get(FRUIT_STATE_KEY);
                        assertThat(oldFruit.keys()).toIterable().hasSize(3);
                        assertThat(oldFruit.get(A_KEY)).isEqualTo(APPLE);
                        assertThat(oldFruit.get(B_KEY)).isEqualTo(BANANA);
                        assertThat(oldFruit.get(C_KEY)).isEqualTo(CHERRY);

                        // Now check that the new states contains the new states
                        assertThat(newStates.size()).isEqualTo(3);
                        assertThat(newStates.contains(FRUIT_STATE_KEY)).isTrue();
                        assertThat(newStates.contains(ANIMAL_STATE_KEY)).isTrue();
                        assertThat(newStates.contains(COUNTRY_STATE_KEY)).isTrue();

                        // Add in the new animals
                        final WritableKVState<String, String> animals =
                                newStates.get(ANIMAL_STATE_KEY);
                        animals.put(A_KEY, AARDVARK);
                        animals.put(B_KEY, BEAR);

                        // Remove, update, and add fruit
                        final WritableKVState<String, String> fruit =
                                newStates.get(FRUIT_STATE_KEY);
                        fruit.remove(A_KEY);
                        fruit.put(B_KEY, BLACKBERRY);
                        fruit.put(E_KEY, EGGPLANT);

                        // Initialize the COUNTRY to be BRAZIL
                        final WritableSingletonState<String> country =
                                newStates.getSingleton(COUNTRY_STATE_KEY);
                        country.put(BRAZIL);

                        // And the old states shouldn't have a COUNTRY_STATE_KEY
                        assertThat(previousStates.contains(COUNTRY_STATE_KEY)).isFalse();

                        // Make sure old fruit hasn't been changed in any way
                        assertThat(oldFruit.keys()).toIterable().hasSize(3);
                        assertThat(oldFruit.get(A_KEY)).isEqualTo(APPLE);
                        assertThat(oldFruit.get(B_KEY)).isEqualTo(BANANA);
                        assertThat(oldFruit.get(C_KEY)).isEqualTo(CHERRY);
                    }
                };
            }

            Schema createV3Schema() {
                return new TestSchema(versions[3]) {
                    @NonNull
                    @Override
                    public Set<String> statesToRemove() {
                        return Set.of(FRUIT_STATE_KEY, COUNTRY_STATE_KEY);
                    }

                    @Override
                    public void migrate(
                            @NonNull ReadableStates previousStates,
                            @NonNull WritableStates newStates) {
                        // Verify that everything in v2 is still here
                        assertThat(previousStates.stateKeys())
                                .containsExactlyInAnyOrder(
                                        FRUIT_STATE_KEY, ANIMAL_STATE_KEY, COUNTRY_STATE_KEY);
                        final ReadableKVState<String, String> oldFruit =
                                previousStates.get(FRUIT_STATE_KEY);
                        assertThat(oldFruit.keys())
                                .toIterable()
                                .containsExactlyInAnyOrder(B_KEY, C_KEY, E_KEY);
                        assertThat(oldFruit.get(B_KEY)).isEqualTo(BLACKBERRY);
                        assertThat(oldFruit.get(C_KEY)).isEqualTo(CHERRY);
                        assertThat(oldFruit.get(E_KEY)).isEqualTo(EGGPLANT);
                        final ReadableKVState<String, String> oldAnimals =
                                previousStates.get(ANIMAL_STATE_KEY);
                        assertThat(oldAnimals.get(A_KEY)).isEqualTo(AARDVARK);
                        assertThat(oldAnimals.get(B_KEY)).isEqualTo(BEAR);

                        // Now check that the new states contains both states as well (since I am
                        // not adding any)
                        assertThat(newStates.size()).isEqualTo(1);
                        assertThat(newStates.contains(ANIMAL_STATE_KEY)).isTrue();

                        // Add in a new animal
                        final WritableKVState<String, String> animals =
                                newStates.get(ANIMAL_STATE_KEY);
                        animals.put(C_KEY, CUTTLEFISH);

                        // And I should still see the COUNTRY_STATE_KEY in the previousStates,
                        // but not in the newStates
                        final ReadableSingletonState<String> country =
                                previousStates.getSingleton(COUNTRY_STATE_KEY);
                        assertThat(country.get()).isEqualTo(BRAZIL);
                        assertThat(newStates.contains(COUNTRY_STATE_KEY)).isFalse();

                        // The newStates should not see the fruit map
                        assertThatThrownBy(() -> newStates.get(FRUIT_STATE_KEY))
                                .isInstanceOf(IllegalArgumentException.class);
                    }
                };
            }

            @Test
            @DisplayName(
                    "Migration from genesis sees nothing in oldStates but can insert into new"
                            + " states")
            void genesis() {
                // Given a schema that adds the FRUIT state with k/v for A, B, and C
                final var schemaV1 = createV1Schema();

                // When we migrate
                schemaRegistry.register(schemaV1);
                schemaRegistry.migrate(merkleTree, versions[0], versions[1]);

                // Then we see that the values for A, B, and C are available
                final var readableStates = merkleTree.createReadableStates(FIRST_SERVICE);
                assertThat(readableStates.size()).isEqualTo(1);
                final ReadableKVState<String, String> fruitV1 = readableStates.get(FRUIT_STATE_KEY);
                assertThat(fruitV1.keys())
                        .toIterable()
                        .containsExactlyInAnyOrder(A_KEY, B_KEY, C_KEY);
            }

            @Test
            @DisplayName("Migration from a former version and add a new state")
            void upgradeAndAddAState() {
                // Given a schema that adds the FRUIT state with k/v for A, B, and C
                final var schemaV1 = createV1Schema();
                final var schemaV2 = createV2Schema();

                // When we migrate
                schemaRegistry.register(schemaV1);
                schemaRegistry.register(schemaV2);
                schemaRegistry.migrate(merkleTree, versions[0], versions[2]);

                // We should see the v2 state (the delta from v2 after applied atop v1)
                final var readableStates = merkleTree.createReadableStates(FIRST_SERVICE);
                assertThat(readableStates.size()).isEqualTo(3);

                final ReadableKVState<String, String> fruitV2 = readableStates.get(FRUIT_STATE_KEY);
                assertThat(fruitV2.keys())
                        .toIterable()
                        .containsExactlyInAnyOrder(B_KEY, C_KEY, E_KEY);
                assertThat(fruitV2.get(B_KEY)).isEqualTo(BLACKBERRY);

                final ReadableKVState<String, String> animalV2 =
                        readableStates.get(ANIMAL_STATE_KEY);
                assertThat(animalV2.get(A_KEY)).isEqualTo(AARDVARK);
                assertThat(animalV2.get(B_KEY)).isEqualTo(BEAR);

                final ReadableSingletonState<String> countryV2 =
                        readableStates.getSingleton(COUNTRY_STATE_KEY);
                assertThat(countryV2.get()).isEqualTo(BRAZIL);
            }

            @Test
            @DisplayName("Migration from a former version and remove a state")
            void upgradeWithARemoveStep() {
                // Given a schema that adds the FRUIT state with k/v for A, B, and C
                final var schemaV1 = createV1Schema();
                final var schemaV2 = createV2Schema();
                final var schemaV3 = createV3Schema();

                // When we migrate
                schemaRegistry.register(schemaV1);
                schemaRegistry.register(schemaV2);
                schemaRegistry.register(schemaV3);
                schemaRegistry.migrate(merkleTree, versions[0], versions[3]);

                // We should see the v3 state (the delta from v3 after applied atop v2 and v1)
                final var readableStates = merkleTree.createReadableStates(FIRST_SERVICE);
                assertThat(readableStates.size()).isEqualTo(1);
                assertThat(readableStates.stateKeys()).containsExactlyInAnyOrder(ANIMAL_STATE_KEY);

                // This should be deleted
                assertThatThrownBy(() -> readableStates.get(FRUIT_STATE_KEY))
                        .isInstanceOf(IllegalArgumentException.class);
                assertThatThrownBy(() -> readableStates.getSingleton(COUNTRY_STATE_KEY))
                        .isInstanceOf(IllegalArgumentException.class);

                // And this should be updated
                final ReadableKVState<String, String> animalV2 =
                        readableStates.get(ANIMAL_STATE_KEY);
                assertThat(animalV2.get(A_KEY)).isEqualTo(AARDVARK);
                assertThat(animalV2.get(B_KEY)).isEqualTo(BEAR);
                assertThat(animalV2.get(C_KEY)).isEqualTo(CUTTLEFISH);
            }

            @Test
            @DisplayName("If a schema migration fails, all migrations stop")
            void badSchema() {
                // Given a bad schema followed by a good one
                final var schemaV2Called = new AtomicBoolean(false);
                final var schemaV1 =
                        new TestSchema(
                                versions[1],
                                () -> {
                                    throw new RuntimeException("Bad");
                                });
                final var schemaV2 = new TestSchema(versions[2], () -> schemaV2Called.set(true));

                // When we migrate
                schemaRegistry.register(schemaV1);
                schemaRegistry.register(schemaV2);

                // We should see that the migration failed
                assertThatThrownBy(
                                () -> schemaRegistry.migrate(merkleTree, versions[0], versions[2]))
                        .isInstanceOf(RuntimeException.class)
                        .hasMessage("Bad");

                // And we should see that schemaV2Called is false because it was never called
                assertThat(schemaV2Called).isFalse();
            }

            @Test
            @DisplayName(
                    "If something unexpected fails with the ConstructableRegistry, migration fails")
            void badRegistry() throws ConstructableRegistryException {
                // Given a bad registry
                Mockito.doThrow(new ConstructableRegistryException("Blew Up In Test"))
                        .when(registry)
                        .registerConstructable(Mockito.any());

                // When we register, then we must fail if the constructable registry fails.
                final var schemaV1 = createV1Schema();
                assertThatThrownBy(() -> schemaRegistry.register(schemaV1))
                        .isInstanceOf(RuntimeException.class)
                        .hasMessageStartingWith("Failed to register with the system")
                        .hasCauseInstanceOf(ConstructableRegistryException.class);
            }
        }
    }
}
