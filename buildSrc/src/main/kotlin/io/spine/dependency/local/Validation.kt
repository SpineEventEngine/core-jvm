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

package io.spine.dependency.local

/**
 * Dependencies on Spine Validation SDK.
 *
 * See [`SpineEventEngine/validation`](https://github.com/SpineEventEngine/validation/).
 */
@Suppress("ConstPropertyName", "unused")
object Validation {
    /**
     * The version of the Validation library artifacts.
     */
    const val version = "2.0.0-SNAPSHOT.464"

    const val group = Spine.toolsGroup
    private const val prefix = "validation"

    const val gradlePluginModule = "$group:$prefix-gradle-plugin"
    const val gradlePluginLib = "$gradlePluginModule:$version"

    const val runtimeModule = "${Spine.group}:spine-$prefix-jvm-runtime"

    fun runtime(version: String) = "$runtimeModule:$version"
    val runtime = runtime(version)

    @Deprecated("Use `runtime` instead.", ReplaceWith("runtime"))
    const val oldRuntime = "io.spine.validation:spine-validation-java-runtime:2.0.0-SNAPSHOT.354"

    const val javaModule = "$group:$prefix-java"
    const val java = "$javaModule:$version"
    const val javaBundleModule = "$group:$prefix-java-bundle"

    /** Obtains the artifact for the `java-bundle` artifact of the given version. */
    fun javaBundle(version: String) = "$javaBundleModule:$version"

    val javaBundle = javaBundle(version)

    const val context = "$group:$prefix-context:$version"
}
