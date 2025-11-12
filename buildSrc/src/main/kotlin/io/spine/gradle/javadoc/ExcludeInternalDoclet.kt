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

package io.spine.gradle.javadoc

import io.spine.dependency.local.ToolBase
import io.spine.gradle.javadoc.ExcludeInternalDoclet.Companion.taskName
import io.spine.gradle.sourceSets
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.StandardJavadocDocletOptions

/**
 * The doclet which removes Javadoc for `@Internal` things in the Java code.
 */
@Suppress("ConstPropertyName")
class ExcludeInternalDoclet {

    private val dependency = ToolBase.JavadocFilter.artifact

    companion object {

        /**
         * The name of the custom configuration in scope of which the exclusion of
         * `@Internal` types is performed.
         */
        private const val configurationName = "excludeInternalDoclet"

        /**
         * The fully-qualified class name of the doclet.
         */
        const val className = "io.spine.tools.javadoc.ExcludeInternalDoclet"

        /**
         * The name of the helper task which configures the Javadoc processing
         * to exclude `@Internal` types.
         */
        const val taskName = "noInternalJavadoc"

        private fun createConfiguration(project: Project): Configuration {
            return project.configurations.create(configurationName)
        }
    }

    /**
     * Creates a custom Javadoc task for the [project] which excludes the types
     * annotated as `@Internal`.
     *
     * The task is registered under [taskName].
     */
    fun registerTaskIn(project: Project) {
        val configuration = addTo(project)
        project.appendCustomJavadocTask(configuration)
    }

    /**
     * Creates a configuration for the doclet in the given project and adds it to its dependencies.
     *
     * @return added configuration
     */
    private fun addTo(project: Project): Configuration {
        val configuration = createConfiguration(project)
        project.dependencies.add(configuration.name, dependency)
        return configuration
    }
}

private fun Project.appendCustomJavadocTask(excludeInternalDoclet: Configuration) {
    val javadocTask = tasks.javadocTask()
    tasks.register(taskName, Javadoc::class.java) {

        source = sourceSets.getByName("main").allJava.filter {
            !it.absolutePath.contains("generated")
        }.asFileTree

        classpath = javadocTask.classpath

        options {
            encoding = JavadocConfig.encoding.name

            // Doclet fully qualified name.
            doclet = ExcludeInternalDoclet.className

            // Path to the JAR containing the doclet.
            docletpath = excludeInternalDoclet.files.toList()
        }

        val docletOptions = options as StandardJavadocDocletOptions
        JavadocConfig.registerCustomTags(docletOptions)
    }
}
