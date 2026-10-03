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

import io.spine.dependency.DependencyWithBom

// https://github.com/grpc/grpc-java
@Suppress("unused")
object Grpc : DependencyWithBom() {

    override val version = "1.83.1"
    override val group = "io.grpc"
    override val bom = "$group:grpc-bom:$version"

    val api            = "$group:grpc-api"
    val auth           = "$group:grpc-auth"
    val core           = "$group:grpc-core"
    val context        = "$group:grpc-context"
    val inProcess      = "$group:grpc-inprocess"
    val stub           = "$group:grpc-stub"
    val okHttp         = "$group:grpc-okhttp"
    val protobuf       = "$group:grpc-protobuf"
    val protobufLite   = "$group:grpc-protobuf-lite"
    val netty          = "$group:grpc-netty"
    val nettyShaded    = "$group:grpc-netty-shaded"

    override val modules = listOf(
        api,
        auth,
        core,
        context,
        inProcess,
        stub,
        okHttp,
        protobuf,
        protobufLite,
        netty,
        nettyShaded
    )

    object ProtocPlugin {
        const val id = "grpc"
        @Deprecated(
            message = "Please use `GrpcKotlin.ProtocPlugin.artifact` instead.",
            replaceWith = ReplaceWith("GrpcKotlin.ProtocPlugin.artifact")
        )
        const val kotlinPluginVersion = GrpcKotlin.version
        val artifact = "$group:protoc-gen-grpc-java:$version"

        // https://github.com/grpc/grpc-kotlin
        // https://repo.maven.apache.org/maven2/io/grpc/protoc-gen-grpc-kotlin/
        @Deprecated(
            message = "Please use `GrpcKotlin.ProtocPlugin.artifact` instead.",
            replaceWith = ReplaceWith("GrpcKotlin.ProtocPlugin.artifact")
        )
        const val artifactKotlin = GrpcKotlin.ProtocPlugin.artifact
    }
}
