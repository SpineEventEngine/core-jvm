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
 * MySQL Connector/J — the official JDBC driver for MySQL.
 *
 * Used by the MySQL-based storage tests. Note the modern `com.mysql:mysql-connector-j`
 * coordinates, which superseded the legacy `mysql:mysql-connector-java` artifact.
 *
 * @see <a href="https://github.com/mysql/mysql-connector-j">MySQL Connector/J at GitHub</a>
 */
@Suppress("unused", "ConstPropertyName")
object MySql {
    private const val version = "26.7.0"
    const val connector = "com.mysql:mysql-connector-j:$version"
}
