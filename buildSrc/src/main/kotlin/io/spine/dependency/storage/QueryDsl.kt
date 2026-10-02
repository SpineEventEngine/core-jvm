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
 * QueryDSL — a framework for constructing type-safe SQL-like queries in Java.
 *
 * The JDBC storage uses the SQL module to build database queries.
 *
 * @see <a href="https://github.com/querydsl/querydsl">QueryDSL at GitHub</a>
 */
@Suppress("unused", "ConstPropertyName")
object QueryDsl {
    private const val version = "5.1.0"
    private const val group = "com.querydsl"

    /**
     * The SQL module of QueryDSL.
     */
    const val sql = "$group:querydsl-sql:$version"
}
