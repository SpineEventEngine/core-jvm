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

package io.spine.gradle.docs

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

/**
 * Updates the version of a Gradle plugin in `build.gradle.kts` files.
 *
 * The task searches for plugin declarations in the format
 * `id("plugin-id") version "version-number"` and replaces
 * the version number with the one found in the version script file.
 *
 * @property directory
 *         The directory to scan recursively for `build.gradle.kts` files.
 * @property version
 *         The version number to set for the plugin.
 * @property pluginId
 *         The ID of the plugin whose version should be updated.
 * @property kotlinVersion
 *         Optional. If set, updates the version of the Kotlin plugin declared with
 *         `kotlin("…") version "…"` syntax in the `plugins` block.
 *         This option works in combination with the [version] and [pluginId] properties.
 */
abstract class UpdatePluginVersion : DefaultTask() {

    @get:InputDirectory
    abstract val directory: DirectoryProperty

    @get:Input
    abstract val version: Property<String>

    @get:Input
    abstract val pluginId: Property<String>

    @get:Input
    @get:Optional
    abstract val kotlinVersion: Property<String>

    /**
     * Updates plugin versions in build files within the path in the [directory].
     */
    @TaskAction
    fun update() {
        val rootDir = directory.get().asFile

        val kotlinVersionSet = kotlinVersion.isPresent
        val kotlinVer = kotlinVersion.orNull
        val id = pluginId.get()
        val ver = version.get()

        rootDir.walkTopDown()
            .filter { it.name == "build.gradle.kts" }
            .forEach { file ->
                if (kotlinVersionSet && kotlinVer != null) {
                    updateKotlinPluginVersion(file, kotlinVer)
                }
                updatePluginVersion(file, id, ver)
            }
    }

    @Suppress("MemberNameEqualsClassName")
    private fun updatePluginVersion(file: File, id: String, version: String) {
        val content = file.readText()
        val pluginId = Regex.escape(id)
        // Regex to match: id("pluginId") version "version-number"
        val regex = """id\("$pluginId"\)\s+version\s+"([^"]+)"""".toRegex()

        if (regex.containsMatchIn(content)) {
            val updatedContent = regex.replace(content) {
                "id(\"$id\") version \"$version\""
            }
            if (content != updatedContent) {
                file.writeText(updatedContent)
                logger.info("Updated version of '$id' in `${file.absolutePath}`.")
            }
        }
    }

    private fun updateKotlinPluginVersion(file: File, kotlinVersion: String) {
        val content = file.readText()
        // Regex to match Kotlin plugin declarations like: kotlin("jvm")    version   "1.9.0"
        val regex = """kotlin\("([^"]+)"\)\s+version\s+"([^"]+)"""".toRegex()
        if (regex.containsMatchIn(content)) {
            val updatedContent = regex.replace(content) { matchResult ->
                val plugin = matchResult.groupValues[1]
                "kotlin(\"$plugin\") version \"$kotlinVersion\""
            }
            if (content != updatedContent) {
                file.writeText(updatedContent)
                logger.info("Updated Kotlin plugin version in `${file.absolutePath}`.")
            }
        }
    }
}
