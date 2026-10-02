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
 * The dependency on the Hamcrest, which is transitive for us.
 *
 * If you need assertions in Java, please use Google [Truth] instead.
 * For Kotlin, please use [Kotest].
 */
@Suppress("unused", "ConstPropertyName")
object Hamcrest {
    // https://github.com/hamcrest/JavaHamcrest/releases
    private const val version = "3.0"
    const val core = "org.hamcrest:hamcrest-core:$version"
}
