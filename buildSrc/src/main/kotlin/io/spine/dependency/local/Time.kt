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

import io.spine.dependency.Dependency

/**
 * Spine Time library.
 *
 * @see <a href="https://github.com/SpineEventEngine/time">spine-time</a>
 */
@Suppress(
    "unused" /* Some subprojects do not use all Time artifacts. */,
    "ConstPropertyName" /* We use custom convention for artifact properties. */,
    "MemberVisibilityCanBePrivate" /* The properties are used directly by other subprojects. */,
)
object Time : Dependency() {
    override val group = Spine.group
    override val version = "2.0.0-SNAPSHOT.251"
    private const val infix = "spine-time"

    fun lib(version: String): String = "$group:$infix:$version"
    val lib get() = lib(version)
    const val libArtifact: String = infix

    fun javaExtensions(version: String): String = "$group:$infix-java:$version"
    val javaExtensions get() = javaExtensions(version)

    fun kotlinExtensions(version: String): String = "$group:$infix-kotlin:$version"
    val kotlinExtensions get() = kotlinExtensions(version)

    fun testLib(version: String): String = "${Spine.toolsGroup}:time-testlib:$version"
    val testLib get() = testLib(version)

    fun validation(version: String): String = "${Spine.toolsGroup}:time-validation:$version"
    val validation get() = validation(version)

    fun gradlePlugin(version: String): String = "${Spine.toolsGroup}:time-gradle-plugin:$version"
    val gradlePlugin get() = gradlePlugin(version)

    override val modules: List<String>
        get() = listOf(
            lib,
            javaExtensions,
            kotlinExtensions,
            testLib
        ).map {
            it.split(":").let { (g, artifact) -> "$g:$artifact" }
        }
}
