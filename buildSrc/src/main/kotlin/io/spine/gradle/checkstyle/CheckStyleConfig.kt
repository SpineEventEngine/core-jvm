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

package io.spine.gradle.checkstyle

import io.spine.dependency.build.CheckStyle
import org.gradle.api.Project
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.plugins.quality.CheckstylePlugin
import org.gradle.kotlin.dsl.the

/**
 * Configures the Checkstyle plugin.
 *
 * Usage:
 * ```
 *      CheckStyleConfig.applyTo(project)
 * ```
 *
 * Please note, the checks of the `test` sources are disabled.
 *
 * Also, this type is named in double-camel-case to avoid re-declaration due to a clash
 * with some Gradle-provided types.
 */
@Suppress("unused")
object CheckStyleConfig {

    /**
     * Applies the configuration to the passed [project].
     */
    fun applyTo(project: Project) {
        project.apply {
            plugin(CheckstylePlugin::class.java)
        }

        val configDir = project.rootDir.resolve("buildSrc/quality/")

        with(project.the<CheckstyleExtension>()) {
            toolVersion = CheckStyle.version
            configDirectory.set(configDir)
        }

        project.afterEvaluate {
            // Disables checking the test sources and test fixtures.
            arrayOf(
                "checkstyleTest",
                "checkstyleTestFixtures"
            ).forEach {
                task -> tasks.findByName(task)?.enabled = false
            }
        }
    }
}
