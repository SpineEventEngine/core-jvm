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
 * This file contains extension methods and properties for the Gradle `Project`.
 */

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
 * Obtains source set container of the Java project.
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
 * Obtains Maven artifact ID of this [Project].
 *
 * The method checks if [SpinePublishing] extension is configured upon this project. If yes,
 * returns [SpinePublishing.artifactId] for the project. Otherwise, a project's name is returned.
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
