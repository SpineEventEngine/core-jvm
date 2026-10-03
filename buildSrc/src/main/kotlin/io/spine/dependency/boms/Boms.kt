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

package io.spine.dependency.boms

import io.spine.dependency.DependencyWithBom
import io.spine.dependency.kotlinx.Coroutines
import io.spine.dependency.lib.JacksonV2
import io.spine.dependency.lib.Kotlin
import io.spine.dependency.lib.Grpc
import io.spine.dependency.test.JUnit

/**
 * The collection of references to BOMs applied by [BomsPlugin].
 *
 * @see <a href="https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#Bill_of_Materials_.28BOM.29_POMs">
 * Maven Bill of Materials</a>
 */
object Boms {

    /**
     * The base production BOMs.
     */
    val core: List<DependencyWithBom> = listOf(
        Kotlin,
        Coroutines
    )

    /**
     * The BOMs for testing dependencies.
     */
    val testing: List<DependencyWithBom> = listOf(
        JUnit
    )

    /**
     * Technology-based BOMs.
     */
    object Optional {
        val jackson = JacksonV2.bom
        val grpc = Grpc.bom
    }
}
