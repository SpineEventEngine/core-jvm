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
 * A Java implementation of JSON Web Token (JWT) - RFC 7519.
 *
 * [Java JWT](https://github.com/auth0/java-jwt)
 */
@Suppress("unused", "ConstPropertyName")
object JavaJwt {

    /**
     * The last version in the v3.x.x series.
     *
     * There's a v4.x.x series (e.g., https://github.com/auth0/java-jwt/releases/tag/4.4.0), but
     * it introduces breaking changes. Consider upgrading to it when we're ready to migrate.
     */
    private const val version = "3.19.4"

    const val lib = "com.auth0:java-jwt:$version"
}
