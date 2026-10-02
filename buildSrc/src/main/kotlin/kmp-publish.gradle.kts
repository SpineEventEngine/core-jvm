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

import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.`maven-publish`
import org.gradle.kotlin.dsl.named

/**
 * Configures publications for `kmp-module`.
 *
 * As for now, [spinePublishing][io.spine.gradle.publish.spinePublishing]
 * doesn't support Kotlin Multiplatform modules. So, their publications are
 * configured by this script plugin. Other publishing-related configuration
 * is still performed by the extension.
 *
 * To publish a KMP module, one still needs to open and configure
 * `spinePublishing` extension. Make sure `spinePublishing.customPublishing`
 * property is set to `true`, and this script plugin is applied.
 *
 * For example:
 *
 * ```
 * plugins {
 *     `kmp-module`
 *     `kmp-publish`
 * }
 *
 * spinePublishing {
 *     destinations = setOf(...)
 *     customPublishing = true
 * }
 * ```
 */
@Suppress("unused")
val about = ""

plugins {
    `maven-publish`
    id("dokka-setup")
}

publishing.publications {
    named<MavenPublication>("kotlinMultiplatform") {
        // Although, the "common artifact" can't be used independently
        // of target artifacts, it is published with documentation.
        artifact(project.htmlDocsJar())
    }
    named<MavenPublication>("jvm") {
        // Includes Kotlin (JVM + common) and Java documentation.
        artifact(project.htmlDocsJar())
    }
}
