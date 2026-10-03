/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package io.spine.dependency.lib

/**
 * gRPC-Kotlin/JVM.
 *
 * @see <a href="https://github.com/grpc/grpc-kotlin">GitHub project</a>
 */
@Suppress("unused")
object GrpcKotlin {
    const val version = "1.5.0"
    const val stub = "io.grpc:grpc-kotlin-stub:$version"

    object ProtocPlugin {
        const val id = "grpckt"
        // https://central.sonatype.com/artifact/io.grpc/protoc-gen-grpc-kotlin
        const val artifact = "io.grpc:protoc-gen-grpc-kotlin:$version:jdk8@jar"
    }
}
