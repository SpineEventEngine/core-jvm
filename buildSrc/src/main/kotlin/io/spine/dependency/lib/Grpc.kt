/*
 * Copyright 2025, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Redistribution and use in source and/or binary forms, with or without
 * modification, must retain the above copyright notice and the following
 * disclaimer.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.spine.dependency.lib

import io.spine.dependency.DependencyWithBom

// https://github.com/grpc/grpc-java
@Suppress("unused")
object Grpc : DependencyWithBom() {

    override val version = "1.76.0"
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
