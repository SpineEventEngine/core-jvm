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

package io.spine.gradle.publish

import io.spine.gradle.sourceSets
import java.io.File
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.FileTreeElement
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.tasks.bundling.Jar

/**
 * Tells whether there are any Proto sources in the "main" source set.
 */
fun Project.hasProto(): Boolean {
    val protoSources = protoSources()
    val result = protoSources.any {
        it.exists()
                && it.isDirectory
                && it.listFiles()?.isNotEmpty() ?: false
    }
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
