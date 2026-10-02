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

@file:Suppress("UnstableApiUsage") // `configurations` block.

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import io.spine.gradle.publish.IncrementGuard
import io.spine.gradle.publish.sbom
import io.spine.gradle.publish.setup
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
    // This prefix does not apply to the modules of this project because they all belong
    // to the `io.spine.tools` group, and therefore `toolArtifactPrefix` applies instead.
    artifactPrefix = ""
    toolArtifactPrefix = "NONE"
    customPublishing = true
}

/** The ID of the fat JAR artifact. */
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
            sbom { bundled(tasks.shadowJar) }
        }
    }
}

/**
 * Declare dependency explicitly to address the Gradle error.
 */
tasks.named("publishFatJarPublicationToMavenLocal") {
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
    setup()
    excludeFiles()
    isZip64 = true  /* The archive has way too many items. So using the Zip64 mode. */
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
