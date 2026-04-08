/*
 * Copyright 2026, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Redistribution and use in source and/or binary forms, with or without
 * modification, must retain the above copyright notice and the following
 * disclaimer.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
    override val version = "2.0.0-SNAPSHOT.235"
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
