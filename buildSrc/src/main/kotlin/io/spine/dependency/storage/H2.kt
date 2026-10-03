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
 * The H2 Database Engine — a fast, in-memory/embedded SQL database used for exercising
 * the JDBC storage in tests.
 *
 * @see <a href="https://github.com/h2database/h2database">H2 Database Engine at GitHub</a>
 * @see <a href="https://h2database.com/">H2 Database Engine site</a>
 */
@Suppress("unused", "ConstPropertyName")
object H2 {
    private const val version = "2.4.240"
    const val lib = "com.h2database:h2:$version"
}
