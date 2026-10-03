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

package io.spine.dependency.storage

/**
 * The PostgreSQL JDBC driver (pgJDBC).
 *
 * Used by the PostgreSQL-based storage tests to connect to a real PostgreSQL server.
 *
 * @see <a href="https://github.com/pgjdbc/pgjdbc">PostgreSQL JDBC Driver at GitHub</a>
 */
@Suppress("unused", "ConstPropertyName")
object PostgreSql {
    private const val version = "42.7.13"
    const val connector = "org.postgresql:postgresql:$version"
}
