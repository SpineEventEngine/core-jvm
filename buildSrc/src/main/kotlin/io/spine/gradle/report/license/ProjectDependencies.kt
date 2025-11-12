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

package io.spine.gradle.report.license

import com.github.jk1.license.ModuleData
import com.github.jk1.license.ProjectData
import io.spine.docs.MarkdownDocument

/**
 * Dependencies of some [Gradle project][ProjectData] classified by the Gradle configuration
 * (such as "runtime") to which they are bound.
 */
internal class ProjectDependencies
private constructor(
    private val runtime: Iterable<ModuleData>,
    private val compileTooling: Iterable<ModuleData>
) {

    internal companion object {

        /**
         * Creates an instance of [ProjectDependencies] by sorting the module dependencies.
         */
        fun of(data: ProjectData): ProjectDependencies {
            val runtimeDeps = mutableListOf<ModuleData>()
            val compileToolingDeps = mutableListOf<ModuleData>()
            data.configurations.forEach { config ->
                if (config.isOneOf(Configuration.runtime, Configuration.runtimeClasspath)) {
                    runtimeDeps.addAll(config.dependencies)
                } else {
                    compileToolingDeps.addAll(config.dependencies)
                }
            }
            return ProjectDependencies(runtimeDeps.toSortedSet(), compileToolingDeps.toSortedSet())
        }
    }

    /**
     * Prints the project dependencies along with the licensing information,
     * splitting them into "Runtime" and "Compile, tests, and tooling" sections.
     */
    internal fun printTo(out: MarkdownDocument) {
        out.printSection("Runtime", runtime)
            .printSection("Compile, tests, and tooling", compileTooling)
    }
}
