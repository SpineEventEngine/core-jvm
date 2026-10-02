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

import io.spine.dependency.Dependency

/**
 * Palantir Java Format.
 *
 * @see <a href="https://github.com/palantir/palantir-java-format">GitHub Repo</a>
 */
object PalantirJavaFormat : Dependency() {

    override val group = "com.palantir.javaformat"
    override val version = "2.97.0"
    override val modules: List<String> = listOf("$group:palantir-java-format")

    val lib = artifact(modules[0])
}
