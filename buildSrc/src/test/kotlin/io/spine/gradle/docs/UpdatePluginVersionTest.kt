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

package io.spine.gradle.docs

import io.kotest.matchers.string.shouldContain
import java.io.File
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

@DisplayName("`UpdatePluginVersion` should")
class UpdatePluginVersionTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var buildFile: File

    @BeforeEach
    fun setUp() {
        val subDir = File(tempDir, "subproject")
        subDir.mkdir()
        buildFile = File(subDir, "build.gradle.kts")
        buildFile.writeText("""
            plugins {
                id("io.spine.validation") version "1.0.0"
                id("other-plugin") version "0.1.0"
            }
        """.trimIndent())
    }

    @Test
    fun `update plugin version in build file`() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("updatePluginVersion", UpdatePluginVersion::class.java) {
            directory.set(tempDir)
            version.set("2.0.0-TEST")
            pluginId.set("io.spine.validation")
        }
        task.get().update()

        val updatedContent = buildFile.readText()
        updatedContent shouldContain """id("io.spine.validation") version "2.0.0-TEST""""
        updatedContent shouldContain """id("other-plugin") version "0.1.0""""
    }

    @Test
    fun `update 'kotlin' plugin version when 'kotlinVersion' is set`() {
        // Overwrite with a file that uses kotlin("jvm") syntax
        buildFile.writeText(
            """
            plugins {
                kotlin("jvm") version "1.9.10"
                id("io.spine.validation") version "1.0.0"
            }
            """.trimIndent()
        )

        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("updatePluginVersion", UpdatePluginVersion::class.java) {
            directory.set(tempDir)
            version.set("2.0.0-TEST")
            pluginId.set("io.spine.validation")
            kotlinVersion.set("2.2.21")
        }
        task.get().update()

        val updatedContent = buildFile.readText()
        updatedContent shouldContain """kotlin("jvm") version "2.2.21""""
        updatedContent shouldContain """id("io.spine.validation") version "2.0.0-TEST""""
    }

    @Test
    fun `handle multiple spaces between id and version`() {
        buildFile.writeText("""
            plugins {
                id("io.spine.validation")    version    "1.0.0"
            }
        """.trimIndent())

        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("updatePluginVersion", UpdatePluginVersion::class.java) {
            directory.set(tempDir)
            version.set("2.0.0-TEST")
            pluginId.set("io.spine.validation")
        }

        task.get().update()

        val updatedContent = buildFile.readText()
        updatedContent shouldContain """id("io.spine.validation") version "2.0.0-TEST""""
    }
}
