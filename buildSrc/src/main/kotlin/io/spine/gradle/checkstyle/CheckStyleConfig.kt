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

package io.spine.gradle.checkstyle

import io.spine.dependency.build.CheckStyle
import org.gradle.api.Project
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.plugins.quality.CheckstylePlugin
import org.gradle.kotlin.dsl.exclude
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

        // Checkstyle needs Doxia only to generate its own website, and Doxia's
        // dependencies conflict with the versions we force. See `CheckStyle.version`.
        project.configurations.named("checkstyle") {
            exclude(group = "org.apache.maven.doxia")
        }

        project.afterEvaluate {
            // Disables checking the test sources and test fixtures.
            arrayOf(
                "checkstyleTest",
                "checkstyleTestFixtures"
            ).forEach {
                task -> tasks.named { it == task }.configureEach { enabled = false }
            }
        }
    }
}
