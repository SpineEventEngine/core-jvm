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
 * Spine used to log with SLF4J. Now we use Flogger. Whenever a choice comes up, we recommend
 * using the latter.
 *
 * The primary purpose of having this dependency object is working in combination with
 * [Flogger.Runtime.slf4JBackend].
 *
 * Some third-party libraries may clash with different versions of the library.
 * Thus, we specify this version and force it via [forceVersions].
 * Please see `DependencyResolution.kt` for details.
 */
// https://search.maven.org/artifact/org.slf4j/slf4j-api
@Suppress("unused", "ConstPropertyName")
object Slf4J {
    private const val version = "2.0.18"
    const val lib = "org.slf4j:slf4j-api:$version"
    const val jdk14 = "org.slf4j:slf4j-jdk14:$version"
    const val reload4j = "org.slf4j:slf4j-reload4j:$version"
    const val simple = "org.slf4j:slf4j-simple:$version"
}
