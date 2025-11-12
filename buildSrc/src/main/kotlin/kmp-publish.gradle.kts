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
    id("dokka-for-kotlin")
}

publishing.publications {
    named<MavenPublication>("kotlinMultiplatform") {
        // Although, the "common artifact" can't be used independently
        // of target artifacts, it is published with documentation.
        artifact(project.dokkaKotlinJar())
    }
    named<MavenPublication>("jvm") {
        // Includes Kotlin (JVM + common) and Java documentation.
        artifact(project.dokkaKotlinJar())
    }
}
