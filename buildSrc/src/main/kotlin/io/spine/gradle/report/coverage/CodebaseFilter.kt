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

package io.spine.gradle.report.coverage

import com.google.errorprone.annotations.CanIgnoreReturnValue
import io.spine.gradle.report.coverage.FileFilter.generatedOnly
import java.io.File
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.SourceSetOutput

/**
 * Serves to distinguish the `.java` and `.class` files built on top of the Protobuf definitions
 * from the human-created production code.
 *
 * Works on top of the passed [source][srcDirs] and [output][outputDirs] directories, by analyzing
 * the source file names and finding the corresponding compiler output.
 */
internal class CodebaseFilter(
    private val project: Project,
    private val srcDirs: Set<File>,
    private val outputDirs: Set<SourceSetOutput>
) {

    /**
     * Returns the file tree containing the compiled `.class` files which were produced
     * from the human-written production code.
     *
     * Such filtering excludes the output obtained from the generated sources.
     */
    internal fun humanProducedCompiledFiles(): List<FileTree> {
        log("Source dirs for the code coverage calculation:")
        this.srcDirs.forEach {
            log(" - $it")
        }

        val generatedClassNames = generatedClassNames()
        val humanProducedTree = outputDirs
            .stream()
            .flatMap { it.classesDirs.files.stream() }
            .map { srcFile ->
                log("Filtering out the generated classes for ${srcFile}.")
                project.fileTree(srcFile).without(generatedClassNames)
            }.toList()
        return humanProducedTree
    }

    private fun generatedClassNames(): List<String> {
        val generatedSourceFiles = generatedOnly(srcDirs)
        val generatedNames = mutableListOf<String>()
        generatedSourceFiles
            .filter { it.exists() && it.isDirectory }
            .forEach { folder ->
                folder.walk()
                    .filter { !it.isDirectory }
                    .forEach { file ->
                        file.parseName(
                            File::asJavaClassName,
                            File::asGrpcClassName,
                            File::asSpineClassName
                        )?.let { clsName ->
                            generatedNames.add(clsName)
                        }
                    }
            }
        return generatedNames
    }

    private fun log(message: String) {
        project.logger.info(message)
    }
}

/**
 * Excludes the elements which [Java compiled file names][File.asJavaCompiledClassName]
 * are present among the passed [names].
 *
 * Returns the same instance of `ConfigurableFileTree`, for call chaining.
 */
@CanIgnoreReturnValue
private fun ConfigurableFileTree.without(names: List<String>): ConfigurableFileTree {
    this.exclude { element ->
        val className = element.file.asJavaCompiledClassName()
        names.contains(className)
    }
    return this
}
