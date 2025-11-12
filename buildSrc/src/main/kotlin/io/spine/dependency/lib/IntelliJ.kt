/*
 * Copyright 2025, TeamDev. All rights reserved.
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

@file:Suppress("ConstPropertyName")

package io.spine.dependency.lib

/**
 * The components of the IntelliJ Platform.
 *
 * Make sure to add the `intellijReleases` and `jetBrainsCacheRedirector`
 * repositories to your project. See `kotlin/Repositories.kt` for details.
 */
@Suppress("unused")
object IntelliJ {

    /**
     * The version of the IntelliJ platform.
     *
     * This is the version used by Kotlin compiler `1.9.21`.
     * Advance this version with caution because it may break the setup of
     * IntelliJ platform standalone execution.
     */
    const val version = "213.7172.53"

    object Platform {
        private const val group = "com.jetbrains.intellij.platform"
        const val core = "$group:core:$version"
        const val util = "$group:util:$version"
        const val coreImpl = "$group:core-impl:$version"
        const val codeStyle = "$group:code-style:$version"
        const val codeStyleImpl = "$group:code-style-impl:$version"
        const val projectModel = "$group:project-model:$version"
        const val projectModelImpl = "$group:project-model-impl:$version"
        const val lang = "$group:lang:$version"
        const val langImpl = "$group:lang-impl:$version"
        const val ideImpl = "$group:ide-impl:$version"
        const val ideCoreImpl = "$group:ide-core-impl:$version"
        const val analysisImpl = "$group:analysis-impl:$version"
        const val indexingImpl = "$group:indexing-impl:$version"
    }

    object Jsp {
        private const val group = "com.jetbrains.intellij.jsp"
        @Suppress("MemberNameEqualsClassName")
        const val jsp = "$group:jsp:$version"
    }

    object Xml {
        private const val group = "com.jetbrains.intellij.xml"
        const val xmlPsiImpl = "$group:xml-psi-impl:$version"
    }

    object JavaPsi {
        private const val group = "com.jetbrains.intellij.java"
        const val api = "$group:java-psi:$version"
        const val impl = "$group:java-psi-impl:$version"
    }

    object Java {
        private const val group = "com.jetbrains.intellij.java"
        @Suppress("MemberNameEqualsClassName")
        const val java = "$group:java:$version"
        const val impl = "$group:java-impl:$version"
    }
}
