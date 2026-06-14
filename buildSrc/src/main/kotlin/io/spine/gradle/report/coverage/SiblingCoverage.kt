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

package io.spine.gradle.report.coverage

import java.io.File
import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskCollection
import org.gradle.api.tasks.testing.Test

/**
 * Credits the test coverage produced by the [contributor] module for the classes
 * of this project to this project's own Kover report.
 *
 * Some modules' production classes are exercised only by the tests of a sibling
 * module — for example, the language-neutral `psi` classes are tested through
 * the Java-PSI fixtures that live in `psi-java`. Kover's per-module report sees
 * only this module's own test execution data, so that cross-module coverage is
 * otherwise missing from the per-module report (which is what Codecov consumes),
 * even though the root aggregated report already accounts for it.
 *
 * This function adds the [contributor]'s JaCoCo execution data to this project's
 * `total` report as additional binary reports. Only this project's classes are
 * credited from them — coverage of unrelated classes in the same execution data
 * is ignored, because a Kover report is scoped to the owning project's classes.
 * The report tasks are wired to run after the contributor's JVM test tasks so the
 * data is present when a report is generated.
 *
 * The contributor's JVM test tasks are discovered by type rather than by name, so
 * the helper works regardless of the module convention: a `jvm-module` contributes
 * through its `test` task, a `kmp-module` through `jvmTest`, and any additional
 * JVM test tasks are picked up as well. Non-JVM Kotlin test tasks (`*Native`,
 * `*Js`, …) are not of type [Test] and are correctly ignored — Kover instruments
 * only JVM test tasks.
 *
 * Requires the Kover plugin to be applied to this project.
 * A cross-project **task** dependency is used, not a project dependency,
 * so it does not introduce a dependency cycle even when the [contributor]
 * already depends on this project.
 */
fun Project.creditTestCoverageFrom(contributor: Project) {
    val contributorTests = contributor.tasks.withType(Test::class.java)
    extensions.configure(KoverProjectExtension::class.java) {
        reports {
            total {
                additionalBinaryReports.addAll(contributor.execFilesOf(contributorTests))
            }
        }
    }
    tasks.matching { it.consumesCoverageBinaryReports() }.configureEach {
        dependsOn(contributorTests)
    }
}

/**
 * Lazy `Provider` of the JaCoCo execution-data files produced by [testTasks]
 * of this project.
 *
 * When the coverage engine is pinned to JaCoCo via `useJacoco(...)`, Kover writes
 * one binary report per instrumented JVM test task at `build/`[BIN_REPORTS_DIR]
 * `/<taskName>.exec`, so the file name follows the task name. Resolved at
 * task-graph time, after the contributor's test tasks have been registered.
 */
private fun Project.execFilesOf(testTasks: TaskCollection<Test>): Provider<Iterable<File>> {
    val binReports = layout.buildDirectory.dir(BIN_REPORTS_DIR)
    return provider {
        testTasks.map { binReports.get().file("${it.name}.exec").asFile }
    }
}

/**
 * The directory under a module's `build/` where Kover writes the per-test-task
 * binary execution-data files.
 */
private const val BIN_REPORTS_DIR: String = "kover/bin-reports"

/**
 * Tells whether this is a Kover task that reads the binary reports and therefore
 * must run only after the [contributor's][creditTestCoverageFrom] test data exists.
 *
 * This matches both the report tasks (`koverXmlReport`, `koverHtmlReport`,
 * `koverBinaryReport`) and the verification tasks (`koverVerify` and its
 * cacheable companion `koverCachedVerify`) — the suffix test covers the
 * `Cached*` variants Kover registers, which are the ones that actually consume
 * the binary reports.
 */
private fun Task.consumesCoverageBinaryReports(): Boolean =
    name.startsWith("kover") && (name.endsWith("Report") || name.endsWith("Verify"))
