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

@file:Suppress("ConstPropertyName")

package io.spine.dependency.lib

/**
 * The components of the IntelliJ Platform.
 *
 * Make sure to add the `intellijReleases` and `intellijDependencies`
 * repositories to your project. See `io/spine/gradle/repo/Repositories.kt` for details.
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
