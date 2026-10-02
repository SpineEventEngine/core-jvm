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
 * HyperSQL DataBase (HSQLDB) — a relational database engine written in Java, used in its
 * in-memory mode for exercising the JDBC storage in tests.
 *
 * HSQLDB is hosted on SourceForge rather than GitHub.
 *
 * @see <a href="https://hsqldb.org/">HyperSQL Database site</a>
 */
@Suppress("unused", "ConstPropertyName")
object HsqlDb {
    private const val version = "2.7.4"
    const val lib = "org.hsqldb:hsqldb:$version"
}
