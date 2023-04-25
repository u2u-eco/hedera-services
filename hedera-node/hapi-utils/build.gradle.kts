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
plugins {
    id("com.hedera.hashgraph.conventions")
}

description = "Hedera Services API Utilities"

configurations.all {
    exclude("javax.annotation", "javax.annotation-api")

    exclude("io.grpc", "grpc-core")
    exclude("io.grpc", "grpc-context")
    exclude("io.grpc", "grpc-api")
    exclude("io.grpc", "grpc-testing")
}

dependencies {
    annotationProcessor(libs.dagger.compiler)

    api(project(":hedera-node:hedera-evm"))

    implementation(libs.jackson)
    implementation(libs.bundles.bouncycastle)
    implementation(libs.bundles.logging)
    implementation(libs.headlong)
    implementation(libs.commons.codec)
    implementation(libs.jna)
    implementation(libs.javax.inject)
    implementation(libs.dagger.api)

    testImplementation(testLibs.bundles.testing)
}
