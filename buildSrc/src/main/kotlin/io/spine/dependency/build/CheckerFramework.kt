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

package io.spine.dependency.build

// https://checkerframework.org/
@Suppress("unused", "ConstPropertyName")
object CheckerFramework {
    private const val version = "4.2.2"
    const val annotations = "org.checkerframework:checker-qual:$version"
    @Suppress("unused")
    val dataflow = listOf(
        "org.checkerframework:dataflow:$version",
        "org.checkerframework:javacutil:$version"
    )
}
