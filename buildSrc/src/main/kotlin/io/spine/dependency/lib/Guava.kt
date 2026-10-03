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

/**
 * The dependencies for Guava.
 *
 * When changing the version, also change the version used in the `build.gradle.kts`. We need
 * to synchronize the version used in `buildSrc` and in Spine modules. Otherwise, when testing
 * Gradle plugins, errors may occur due to version clashes.
 *
 * @see <a href="https://github.com/google/guava">Guava at GitHub</a>.
 */
@Suppress("unused", "ConstPropertyName")
object Guava {
    private const val version = "33.7.1-jre"
    const val group = "com.google.guava"
    const val lib     = "$group:guava:$version"
    const val testLib = "$group:guava-testlib:$version"
}
