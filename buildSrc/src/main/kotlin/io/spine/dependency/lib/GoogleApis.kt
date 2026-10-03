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
 * Provides dependencies on [GoogleApis projects](https://github.com/googleapis/).
 */
@Suppress("unused", "ConstPropertyName")
object GoogleApis {

    // https://github.com/googleapis/google-api-java-client
    const val client = "com.google.api-client:google-api-client:1.32.2"

    // https://github.com/googleapis/api-common-java
    const val common = "com.google.api:api-common:2.64.0"

    // https://github.com/googleapis/java-common-protos
    const val commonProtos = "com.google.api.grpc:proto-google-common-protos:2.72.0"

    // https://github.com/googleapis/gax-java
    const val gax = "com.google.api:gax:2.80.0"

    // https://github.com/googleapis/java-iam
    const val protoAim = "com.google.api.grpc:proto-google-iam-v1:1.2.0"

    // https://github.com/googleapis/google-oauth-java-client
    const val oAuthClient = "com.google.oauth-client:google-oauth-client:1.32.1"

    // https://github.com/googleapis/google-auth-library-java
    object AuthLibrary {
        const val version = "1.51.0"
        const val credentials = "com.google.auth:google-auth-library-credentials:$version"
        const val oAuth2Http = "com.google.auth:google-auth-library-oauth2-http:$version"
    }
}
