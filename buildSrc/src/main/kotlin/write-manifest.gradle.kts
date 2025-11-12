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

import io.spine.gradle.publish.SpinePublishing
import java.nio.file.Files.createDirectories
import java.nio.file.Files.createFile
import java.text.SimpleDateFormat
import java.util.*
import java.util.jar.Attributes.Name.IMPLEMENTATION_TITLE
import java.util.jar.Attributes.Name.IMPLEMENTATION_VENDOR
import java.util.jar.Attributes.Name.IMPLEMENTATION_VERSION
import java.util.jar.Attributes.Name.MANIFEST_VERSION
import java.util.jar.Manifest

plugins {
    java
}

/**
 * Obtains a string value of a [System] property with the given key.
 */
fun prop(key: String): String = System.getProperties()[key].toString()

/**
 * Obtains the current time in UTC using ISO 8601 format.
 */
fun currentTime(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(Date())

/**
 * Obtains the information on the JDK used for the build.
 */
fun buildJdk(): String =
    "${prop("java.version")} (${prop("java.vendor")} ${prop("java.vm.version")})"

/**
 * Obtains the information on the operating system used for the build.
 */
fun buildOs(): String =
    "${prop("os.name")} ${prop("os.arch")} ${prop("os.version")}"

/** The publishing settings from the root project. */
val spinePublishing = rootProject.the<SpinePublishing>()
val artifactPrefix = spinePublishing.artifactPrefix

/**
 * Obtains the implementation title for the project using project group,
 * artifact prefix from [SpinePublishing], and the name of the project to which
 * this script plugin is applied.
 */
fun implementationTitle() = "${project.group}:$artifactPrefix${project.name}"

/**
 * The name of the manifest attribute holding the timestamp of the build.
 */
val buildTimestampAttr = "Build-Timestamp"

/**
 * The attributes we put into the JAR manifest.
 *
 * This map is shared between the [exposeManifestForTests] task and the action which
 * customizes the [Jar] task below.
 */
val manifestAttributes = mapOf(
    "Built-By" to prop("user.name"),
    buildTimestampAttr to currentTime(),
    "Created-By" to "Gradle ${gradle.gradleVersion}",
    "Build-Jdk" to buildJdk(),
    "Build-OS" to buildOs(),
    IMPLEMENTATION_TITLE.toString() to implementationTitle(),
    IMPLEMENTATION_VERSION.toString() to project.version,
    IMPLEMENTATION_VENDOR.toString() to "TeamDev",
    "Bundle-License" to "https://www.apache.org/licenses/LICENSE-2.0.txt"
)

/**
 * Creates a manifest file in `resources` so that it is available for the tests.
 *
 * This task does the same what does the block which configures the `tasks.jar` below.
 * We cannot use the manifest file created by the `Jar` task because it's not visible
 * when running tests. We cannot depend on the `Jar` from `resources` because it would
 * form a circular dependency.
 */
val exposeManifestForTests by tasks.registering {

    val outputFile = layout.buildDirectory.file("resources/main/META-INF/MANIFEST.MF")
    outputs.file(outputFile).withPropertyName("manifestFile")

    fun createManifest(): Manifest {
        val manifest = Manifest()

        // The manifest version attribute is crucial for writing.
        // If it's absent, nothing would be written.
        manifest.mainAttributes[MANIFEST_VERSION] = "1.0"

        manifestAttributes.forEach { entry ->
            manifest.mainAttributes.putValue(entry.key, entry.value.toString())
        }
        return manifest
    }

    fun writeManifest(manifest: Manifest) {
        val file = outputFile.get().getAsFile()
        val path = file.toPath()
        createDirectories(path.parent)
        if (!file.exists()) {
            createFile(path)
        }
        val stream = file.outputStream()
        stream.use {
            manifest.write(stream)
        }
    }

    doLast {
        val manifest = createManifest()
        writeManifest(manifest)
    }
}

tasks.processResources {
    dependsOn(exposeManifestForTests)
}

tasks.jar {
    manifest {
        attributes(manifestAttributes)
    }
}

/**
 * Makes Gradle ignore the [buildTimestampAttr] attribute during normalization.
 *
 * See [Java META-INF normalization](https://docs.gradle.org/current/userguide/more_about_tasks.html#sec:meta_inf_normalization)
 * section of the Gradle documentation for details.
 */
normalization {
    runtimeClasspath {
        metaInf {
            ignoreAttribute(buildTimestampAttr)
        }
    }
}
