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

import org.gradle.api.Project

/**
 * The documentation settings specific to this project.
 *
 * @see <a href="https://kotlinlang.org/docs/dokka-gradle.html#source-link-configuration">
 *     Dokka source link configuration</a>
 */
@Suppress("ConstPropertyName")
object DocumentationSettings {

    /**
     * The organization infix for the Spine SDK.
     */
    private const val orgPath = "github.com/SpineEventEngine"

    /**
     * The organization URL of the Spine SDK.
     */
    private const val orgUrl = "https://$orgPath"

    /**
     * Obtains the repository URL for the given project.
     */
    fun repoUrl(project: Project) = "https://${repoPath(project)}"

    /**
     * Obtains the repository path for the given project.
     */
    private fun repoPath(project: Project) = "$orgPath/${project.rootProject.name}"

    /**
     * Obtains the connection URL for the given project.
     */
    fun connectionUrl(project: Project) = "scm:git:git://${repoPath(project)}.git"

    /**
     * Obtains the developer connection URL for the given project.
     */
    fun developerConnectionUrl(project: Project) = "scm:git:ssh://${repoPath(project)}.git"

    /**
     * Settings passed to Dokka for
     * [sourceLink][[org.jetbrains.dokka.gradle.engine.parameters.DokkaSourceLinkSpec]
     */
    object SourceLink {

        /**
         * The URL of the remote source code
         * [location][org.jetbrains.dokka.gradle.engine.parameters.DokkaSourceLinkSpec.remoteUrl].
         */
        fun url(project: Project): String {
            val root = project.rootProject.name
            val module = project.name
            return "$orgUrl/$root/tree/master/$module/src/main/kotlin"
        }

        /**
         * The suffix used to append the source code line number to the URL.
         *
         * The value depends on the online code repository and is set for GitHub (`#L`).
         *
         * @see <a href="https://kotlinlang.org/docs/dokka-gradle.html#fwor0d_534">
         *     remoteLineSuffix</a>
         */
        const val lineSuffix: String = "#L"
    }
}
