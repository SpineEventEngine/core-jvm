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

package io.spine.gradle.testing

import io.spine.dependency.test.Jacoco
import java.util.concurrent.atomic.AtomicBoolean
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType

/**
 * Configures the `test` tasks of this project so that the JaCoCo agent is
 * attached to the Gradle TestKit worker JVMs they spawn.
 *
 * `Plugin<Settings>` implementations and other plugin code exercised through
 * [`GradleRunner`][org.gradle.testkit.runner.GradleRunner] run in a separate
 * worker JVM. Kover (and JaCoCo) instrument only the test JVM, so that
 * out-of-process execution is otherwise not credited to coverage.
 *
 * This method:
 *
 *  1. Resolves the standalone JaCoCo agent JAR pinned to [Jacoco.version]
 *     through a dedicated [AGENT_CONFIGURATION] configuration.
 *  2. Passes the agent JAR path and a per-module exec directory
 *     (`build/`[TESTKIT_COVERAGE_DIR]) to the test JVM as system properties.
 *     The `plugin-testlib` harness reads these and writes a `gradle.properties`
 *     into the worker's Gradle user home that adds `-javaagent:…` to the worker JVM.
 *  3. Wipes the exec directory at most once per build invocation, from the
 *     `doFirst` of the first `Test` task that actually executes, so stale worker
 *     coverage from a previous run does not accumulate. Two failure modes are
 *     avoided deliberately:
 *      - Cleaning is **not** wired through a `dependsOn` clean task. Such a task
 *        would run even when the `Test` task is up-to-date or restored from
 *        cache, deleting the `.exec` files without regenerating them — a later
 *        `koverXmlReport`/`check` run would then drop all TestKit coverage. A
 *        `doFirst` action runs only when the task truly executes.
 *      - The wipe is guarded by a one-shot flag so that, when a module declares
 *        several TestKit `Test` tasks, only the first to run clears the
 *        directory. The workers append to a single per-module exec file, so the
 *        remaining tasks accumulate into it instead of erasing one another.
 *  4. Marks the `Test` tasks non-cacheable. The worker `.exec` data is flushed
 *     out-of-process on worker-daemon shutdown, *after* the task action
 *     completes, so it cannot be declared as a task output and captured by the
 *     build cache. Were the task left cacheable, a cache hit would skip
 *     execution and restore no exec files, leaving Kover with no TestKit
 *     coverage. An up-to-date (non-cache) run is unaffected: the previous run's
 *     files remain on disk and the guarded `doFirst` never deletes them.
 *
 * The produced `.exec` files are merged into the Kover reports by
 * [io.spine.gradle.report.coverage.KoverConfig]. The agent emits binary
 * execution data rather than an XML report because that is its only file output,
 * and because Kover merges binary data at the probe level — see `KoverConfig`
 * for why binary, not XML.
 *
 * The method is idempotent and may be called once per module that runs
 * TestKit-based tests.
 */
fun Project.enableTestKitCoverage() {
    val agent = configurations.maybeCreate(AGENT_CONFIGURATION).apply {
        isCanBeConsumed = false
        isCanBeResolved = true
    }
    dependencies.add(agent.name, "org.jacoco:org.jacoco.agent:${Jacoco.version}:runtime")

    val agentPath = agent.elements.map { it.single().asFile.absolutePath }
    val execDir = layout.buildDirectory.dir(TESTKIT_COVERAGE_DIR)

    // Wiped at most once per build invocation, by the first `Test` task that
    // actually executes — see the KDoc above for why this is a guarded `doFirst`
    // wipe rather than a `dependsOn` clean task.
    val cleaned = AtomicBoolean(false)

    tasks.withType<Test>().configureEach {
        inputs.files(agent).withPropertyName(AGENT_CONFIGURATION)
        outputs.cacheIf(
            "TestKit worker coverage is produced out-of-process and cannot be a " +
                    "declared task output; a cache hit would drop it."
        ) { false }
        doFirst {
            val dir = execDir.get().asFile
            if (cleaned.compareAndSet(false, true)) {
                dir.deleteRecursively()
            }
            dir.mkdirs()
            systemProperty(AGENT_PROPERTY, agentPath.get())
            systemProperty(EXEC_DIR_PROPERTY, dir.absolutePath)
        }
    }
}

/**
 * The name of the directory under a module's `build` directory where the
 * coverage of Gradle TestKit worker JVMs is collected.
 *
 * The directory holds JaCoCo execution-data (`.exec`) files — one per test
 * project directory — written by the JaCoCo agent attached to the TestKit
 * worker. `KoverConfig` picks these files up and feeds them into the Kover
 * reports as additional binary reports.
 *
 * @see io.spine.gradle.report.coverage.KoverConfig
 */
internal const val TESTKIT_COVERAGE_DIR: String = "jacoco-testkit"

/**
 * The name of the system property carrying the absolute path to the JaCoCo
 * agent JAR which the test harness attaches to TestKit worker JVMs.
 *
 * The value is read by `plugin-testlib` at test runtime.
 *
 * The constant is duplicated in `io.spine.tools.gradle.testing.TestKitCoverage`
 * of the `plugin-testlib` module (which cannot depend on `buildSrc`). Keep the
 * two values in sync.
 */
private const val AGENT_PROPERTY: String =
    "io.spine.tools.gradle.testkit.coverage.agent"

/**
 * The name of the system property carrying the absolute path to the directory
 * where TestKit workers write their JaCoCo execution data.
 *
 * The constant is duplicated in `io.spine.tools.gradle.testing.TestKitCoverage`
 * of the `plugin-testlib` module. Keep the two values in sync.
 */
private const val EXEC_DIR_PROPERTY: String =
    "io.spine.tools.gradle.testkit.coverage.execDir"

/**
 * The name of the dedicated, resolvable configuration that holds the standalone
 * JaCoCo agent JAR (`org.jacoco:org.jacoco.agent:<version>:runtime`) attached to
 * TestKit worker JVMs.
 *
 * The configuration is hidden and non-consumable; it exists only to resolve the
 * agent JAR and to register it as an input of the `test` tasks.
 */
private const val AGENT_CONFIGURATION: String = "testKitJacocoAgent"
