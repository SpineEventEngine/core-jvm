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

package io.spine.dependency.lib

// https://asm.ow2.io/
@Suppress("unused", "ConstPropertyName")
object Asm {
    private const val version = "9.10.1"
    const val group = "org.ow2.asm"
    const val lib = "$group:asm:$version"

    // We use the following artifacts only to force the versions
    // of the dependencies that are transitive for us.
    //
    const val tree = "$group:asm-tree:$version"
    const val analysis = "$group:asm-analysis:$version"
    const val util = "$group:asm-util:$version"
    const val commons = "$group:asm-commons:$version"
}
