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

/**
 * Provides dependencies on [GoogleApis projects](https://github.com/googleapis/).
 */
@Suppress("unused", "ConstPropertyName")
object GoogleApis {

    // https://github.com/googleapis/google-api-java-client
    const val client = "com.google.api-client:google-api-client:1.32.2"

    // https://github.com/googleapis/api-common-java
    const val common = "com.google.api:api-common:2.1.1"

    // https://github.com/googleapis/java-common-protos
    const val commonProtos = "com.google.api.grpc:proto-google-common-protos:2.7.0"

    // https://github.com/googleapis/gax-java
    const val gax = "com.google.api:gax:2.7.1"

    // https://github.com/googleapis/java-iam
    const val protoAim = "com.google.api.grpc:proto-google-iam-v1:1.2.0"

    // https://github.com/googleapis/google-oauth-java-client
    const val oAuthClient = "com.google.oauth-client:google-oauth-client:1.32.1"

    // https://github.com/googleapis/google-auth-library-java
    object AuthLibrary {
        const val version = "1.3.0"
        const val credentials = "com.google.auth:google-auth-library-credentials:$version"
        const val oAuth2Http = "com.google.auth:google-auth-library-oauth2-http:$version"
    }
}
