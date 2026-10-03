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

package io.spine.dependency.kotlinx

import io.spine.dependency.Dependency

/**
 * Kotlin/Multiplatform AtomicFU library.
 *
 * https://github.com/Kotlin/kotlinx.atomicfu
 */
object AtomicFu : Dependency() {

    override val version: String = "0.33.0"

    override val group: String = KotlinX.group

    @Suppress("ConstPropertyName") // https://bit.ly/kotlin-prop-names
    const val module = "atomicfu"

    /**
     * The base artifact without platform classifier.
     */
    val std = "$group:$module"

    override val modules: List<String> = listOf(std)

    /** Convenience: full coordinates with the version for the standard artifact. */
    val lib: String get() = artifact(std)
}
