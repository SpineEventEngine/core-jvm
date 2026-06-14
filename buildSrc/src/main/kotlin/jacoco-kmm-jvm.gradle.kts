/*
 * Copyright 2026, TeamDev. All rights reserved.
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

import java.io.File
import org.gradle.kotlin.dsl.getValue
import org.gradle.kotlin.dsl.getting
import org.gradle.kotlin.dsl.jacoco
import org.gradle.testing.jacoco.tasks.JacocoReport

// DEPRECATED: this script plugin distributes vanilla JaCoCo.
// New code should apply `kmp-module`, which configures Kover via
// `useJacoco(version = Jacoco.version)` and writes JaCoCo-format XML at
// `build/reports/kover/report.xml`. (Same task and path as Kotlin-JVM —
// `kmp-module` configures only Kover's `total` report, so no
// `koverXmlReport<Variant>` task is generated.) The `raise-coverage` skill
// migrates existing consumers automatically. Kept so older consumer repos
// continue to build; will be removed in a future release.
// See: .agents/skills/raise-coverage/references/migrate-to-kover.md

plugins {
    jacoco
}

logger.warn(
    "'jacoco-kmm-jvm' is deprecated; use 'kmp-module' which applies Kover. " +
        "See .agents/skills/raise-coverage/references/migrate-to-kover.md."
)

/**
 * Configures [JacocoReport] task to run in a Kotlin KMM project for `commonMain` and `jvmMain`
 * source sets.
 *
 * This script plugin must be applied using the following construct at the end of
 * a `build.gradle.kts` file of a module:
 *
 * ```kotlin
 * apply(plugin="jacoco-kmm-jvm")
 * ```
 * Please do not apply this script plugin in the `plugins {}` block because `jacocoTestReport`
 * task is not yet available at this stage.
 */
@Suppress("unused")
private val about = ""

/**
 * Configure the Jacoco task with custom input a KMM project
 * to which this convention plugin is applied.
 */
@Suppress("unused")
val jacocoTestReport: JacocoReport by tasks.getting(JacocoReport::class) {
    val buildDir = project.layout.buildDirectory.get().asFile.absolutePath
    val classFiles = File("${buildDir}/classes/kotlin/jvm/")
        .walkBottomUp()
        .toSet()
    classDirectories.setFrom(classFiles)

    val coverageSourceDirs = arrayOf(
        "src/commonMain",
        "src/jvmMain"
    )
    sourceDirectories.setFrom(files(coverageSourceDirs))

    executionData.setFrom(files("${buildDir}/jacoco/jvmTest.exec"))
}
