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

package io.spine.gradle

import io.spine.gradle.publish.SpinePublishing
import java.io.File
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.getByType

/**
 * Logs the result of the function using the project logger at `INFO` level.
 */
fun Project.log(message: () -> String) {
    if (logger.isInfoEnabled) {
        logger.info(message.invoke())
    }
}

/**
 * Obtains the Java plugin extension of the project.
 */
val Project.javaPluginExtension: JavaPluginExtension
    get() = extensions.getByType()

/**
 * Obtains the source set container of the Java project.
 */
val Project.sourceSets: SourceSetContainer
    get() = javaPluginExtension.sourceSets

/**
 * Applies the specified Gradle plugin to this project by the plugin [class][cls].
 */
fun Project.applyPlugin(cls: Class<out Plugin<*>>) {
    this.apply {
        plugin(cls)
    }
}

/**
 * Finds the task of type `T` in this project by the task name.
 *
 * The task must be present. Also, a caller is responsible for using the proper value of
 * the generic parameter `T`.
 */
@Suppress("UNCHECKED_CAST")     /* See the method docs. */
fun <T : Task> Project.getTask(name: String): T {
    val task = this.tasks.findByName(name)
        ?: error("Unable to find a task named `$name` in the project `${this.name}`.")
    return task as T
}

/**
 * Obtains the Maven artifact ID of this [Project].
 *
 * The property getter checks if [SpinePublishing] extension is configured upon this project.
 * If yes, it returns [SpinePublishing.artifactId] for the project.
 * Otherwise, a project's name is returned.
 */
val Project.artifactId: String
    get() {

        // Publishing of a project can be configured either from the project itself or
        // from its root project. This is why it is required to check both places.

        val spinePublishing = extensions.findByType<SpinePublishing>()
            ?: rootProject.extensions.findByType()

        val artifactId = spinePublishing?.artifactId(this)
        return artifactId ?: name
    }

/**
 * Returns project's build directory as [File].
 */
val Project.buildDirectory: File
    get() = layout.buildDirectory.get().asFile
