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

package io.spine.gradle.publish

import io.spine.gradle.sourceSets
import java.io.File
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.FileTreeElement
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.tasks.bundling.Jar

/**
 * Tells whether there are any Proto sources in "main" source set.
 */
internal fun Project.hasProto(): Boolean {
    val protoSources = protoSources()
    val result = protoSources.any { it.exists() }
    return result
}

/**
 * Locates directories with proto sources under the "main" source sets.
 *
 * Special treatment for Proto sources is needed, because they are not Java-related, and,
 * thus, not included in `sourceSets["main"].allSource`.
 */
internal fun Project.protoSources(): Set<File> {
    val mainSourceSets = sourceSets.filter {
        ss -> ss.name.endsWith("main", ignoreCase = true)
    }

    val protoExtensions = mainSourceSets.mapNotNull {
        it.extensions.findByName("proto") as SourceDirectorySet?
    }

    val protoDirs = mutableSetOf<File>()
    protoExtensions.forEach {
        protoDirs.addAll(it.srcDirs)
    }

    return protoDirs
}

/**
 * Checks if the given file belongs to the Google `.proto` sources.
 */
internal fun FileTreeElement.isGoogleProtoSource(): Boolean {
    val pathSegments = relativePath.segments
    return pathSegments.isNotEmpty() && pathSegments[0].equals("google")
}

/**
 * The reference to the `generateProto` task of a `main` source set.
 */
internal fun Project.generateProto(): Task? = tasks.findByName("generateProto")

/**
 * Makes this [Jar] task depend on the [generateProto] task, if it exists in the same project.
 */
internal fun Jar.dependOnGenerateProto() {
    project.generateProto()?.let {
        this.dependsOn(it)
    }
}
