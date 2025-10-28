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

@file:Suppress("UnstableApiUsage") // `configurations` block.

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import io.spine.gradle.publish.IncrementGuard
import io.spine.gradle.publish.SpinePublishing
import io.spine.gradle.publish.spinePublishing
import io.spine.gradle.report.license.LicenseReporter

plugins {
    id("module")
    `maven-publish`
    id("com.gradleup.shadow")
    id("write-manifest")
    `project-report`
    idea
}
apply<IncrementGuard>()
LicenseReporter.generateReportIn(project)

spinePublishing {
    artifactPrefix = ""
    destinations = rootProject.the<SpinePublishing>().destinations
    customPublishing = true
}

/** The ID of the far JAR artifact. */
private val projectArtifact = project.name.replace(":", "")

publishing {
    val groupName = project.group.toString()
    val versionName = project.version.toString()

    publications {
        create("fatJar", MavenPublication::class) {
            groupId = groupName
            artifactId = projectArtifact
            version = versionName
            artifact(tasks.shadowJar)
        }
    }
}

/**
 * Declare dependency explicitly to address the Gradle error.
 */
@Suppress("unused")
val publishFatJarPublicationToMavenLocal: Task by tasks.getting {
    dependsOn(tasks.shadowJar)
}

// Disable the `jar` task to free up the name of the resulting archive.
tasks.jar {
    enabled = false
}

tasks.publish {
    dependsOn(tasks.shadowJar)
}

tasks.shadowJar {
    excludeFiles()
    setZip64(true)  /* The archive has way too many items. So using the Zip64 mode. */
    archiveClassifier.set("")  /** To prevent Gradle setting something like `osx-x86_64`. */
}

/**
 * Exclude unwanted directories.
 */
@Suppress("LongMethod")
private fun ShadowJar.excludeFiles() {
    exclude(
        /*
          Exclude IntelliJ Platform images and other resources associated with IntelliJ UI.
          We do not call the UI, so they won't be used.
         */
        "actions/**",
        "chooser/**",
        "codeStyle/**",
        "codeStylePreview/**",
        "codeWithMe/**",
        "darcula/**",
        "debugger/**",
        "diff/**",
        "duplicates/**",
        "expui/**",
        "extensions/**",
        "fileTemplates/**",
        "fileTypes/**",
        "general/**",
        "graph/**",
        "gutter/**",
        "hierarchy/**",
        "icons/**",
        "ide/**",
        "idea/**",
        "inlayProviders/**",
        "inspectionDescriptions/**",
        "inspectionReport/**",
        "intentionDescriptions/**",
        "javadoc/**",
        "javaee/**",
        "json/**",
        "liveTemplates/**",
        "mac/**",
        "modules/**",
        "nodes/**",
        "objectBrowser/**",
        "plugins/**",
        "postfixTemplates/**",
        "preferences/**",
        "process/**",
        "providers/**",
        "runConfigurations/**",
        "scope/**",
        "search/**",
        "toolbar/**",
        "toolbarDecorator/**",
        "toolwindows/**",
        "vcs/**",
        "webreferences/**",
        "welcome/**",
        "windows/**",
        "xml/**",

        /*
          Exclude `https://github.com/JetBrains/pty4j`.
          We don't need the terminal.
         */
        "resources/com/pti4j/**",

        /* Exclude the IntelliJ fork of
          `http://www.sparetimelabs.com/purejavacomm/purejavacomm.php`.
           It is the part of the IDEA's terminal implementation.
         */
        "purejavacomm/**",

        /* Exclude IDEA project templates. */
        "resources/projectTemplates/**",

        /*
          Exclude dynamic libraries. Should the tool users need them,
          they would add them explicitly.
         */
        "bin/**",

        /*
          Exclude Google Protobuf definitions to avoid duplicates.
         */
        "google/**",
        "src/google/**",

        /**
         * Exclude Spine Protobuf definitions to avoid duplications.
         */
        "spine/**",

        /**
         * Exclude Kotlin runtime because it will be provided.
         */
        "kotlin/**",
        "kotlinx/**",

        /**
         * Exclude native libraries related to debugging.
         */
        "win32-x86/**",
        "win32-x86-64/**",

        /**
         * Exclude the Windows process management (WinP) libraries.
         * See: `https://github.com/jenkinsci/winp`.
         */
        "winp.dll",
        "winp.x64.dll",
    )
}
