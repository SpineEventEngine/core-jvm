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

package io.spine.dependency.test

/**
 * Testcontainers for Java — provides throwaway, lightweight instances of databases and other
 * services running in Docker containers.
 *
 * The modules below are versioned and released together, so a single [version] applies to all
 * of them.
 *
 * Starting with version 2.x, module artifacts are prefixed with `testcontainers-`,
 * while the core [lib] artifact keeps the plain `testcontainers` name.
 *
 * @see <a href="https://github.com/testcontainers/testcontainers-java">
 *     Testcontainers for Java at GitHub</a>
 */
@Suppress("unused", "ConstPropertyName")
object Testcontainers {
    private const val version = "2.0.5"
    private const val group = "org.testcontainers"

    /**
     * The core Testcontainers library.
     */
    const val lib = "$group:testcontainers:$version"

    /**
     * The JUnit 5 (Jupiter) integration.
     */
    const val junitJupiter = "$group:testcontainers-junit-jupiter:$version"

    /**
     * The Google Cloud (GCP) emulator container support.
     */
    const val gcloud = "$group:testcontainers-gcloud:$version"

    /**
     * The MySQL container support.
     */
    const val mySql = "$group:testcontainers-mysql:$version"

    /**
     * The PostgreSQL container support.
     */
    const val postgresql = "$group:testcontainers-postgresql:$version"
}
