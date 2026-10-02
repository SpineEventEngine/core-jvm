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
 * Google implementations of [HTTP client](https://github.com/googleapis/google-http-java-client).
 */
@Suppress("unused", "ConstPropertyName")
object HttpClient {
    // https://github.com/googleapis/google-http-java-client
    const val version  = "2.2.0"
    const val google   = "com.google.http-client:google-http-client:$version"
    const val jackson2 = "com.google.http-client:google-http-client-jackson2:$version"
    const val gson     = "com.google.http-client:google-http-client-gson:$version"
    const val apache2  = "com.google.http-client:google-http-client-apache-v2:$version"

    const val apache   = "com.google.http-client:google-http-client-apache:2.1.2"
}
