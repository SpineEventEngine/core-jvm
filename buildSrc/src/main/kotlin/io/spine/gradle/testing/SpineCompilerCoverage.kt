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
import io.spine.gradle.report.coverage.consumesCoverageBinaryReports
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.JavaExec
import org.gradle.kotlin.dsl.withType

/**
 * Configures the `launch*SpineCompiler` tasks of this project so that the JaCoCo
 * agent is attached to the forked JVM that runs the Spine Compiler, and the
 * resulting execution data is captured as a task output.
 *
 * The Spine Compiler executes plugin code — renderers, option generators, and
 * other compiler plugins — in a **separate JVM** spawned by the
 * `launch[<SourceSet>]SpineCompiler`
 * [JavaExec][org.gradle.api.tasks.JavaExec] tasks. Kover (and JaCoCo) instrument
 * only the `test` JVM, so this out-of-process execution is otherwise not credited
 * to coverage, even though the module's `.proto` fixtures exercise it on every build.
 *
 * For every such launch task, this method:
 *
 *  1. Resolves the standalone JaCoCo agent JAR ([Jacoco.agent]) through a dedicated
 *     [AGENT_CONFIGURATION] configuration.
 *  2. Attaches
 *     `-javaagent:<agent>=destfile=build/`[COMPILER_COVERAGE_DIR]`/<task>.exec,append=false`
 *     to the forked JVM. Each launch variant (`main`, `test`, `testFixtures`)
 *     writes its own per-task file, and `append=false` makes every run overwrite
 *     it, so a re-run never accumulates a previous run's probes.
 *  3. Declares that `.exec` file as a **task output**, which keeps the launch tasks
 *     cacheable. A `JavaExec` task blocks until the forked JVM exits, and the agent
 *     flushes the exec on that exit — so the file is already on disk when the task
 *     action returns and *can* be a declared output. Declaring it lets Gradle's
 *     build cache store and restore it, so the coverage survives both an
 *     `UP-TO-DATE` skip (the file stays on disk from the previous run) and a
 *     `FROM-CACHE` hit (the file is restored from the cache). This is the key
 *     difference from [enableTestKitCoverage], whose TestKit workers flush their
 *     exec only *after* the `Test` task completes, so the file cannot be declared
 *     as a task output — which is why that helper must instead disable caching.
 *
 * The agent is attached from a `doFirst` action rather than a `jvmArgumentProviders`
 * entry so that its *absolute path* — machine-specific, under the Gradle user home —
 * stays out of the task's input fingerprint, where it would break build-cache reuse
 * across machines. The agent *coordinate* ([Jacoco.agent]) is instead declared as an
 * `inputs.property`, so bumping the JaCoCo version invalidates the launch tasks and
 * regenerates the exec — a stale exec from an incompatible agent can never survive an
 * `UP-TO-DATE` skip or a `FROM-CACHE` hit. The exec content otherwise depends on the
 * compiler's own inputs, which already key up-to-dateness.
 *
 * The produced `.exec` files are merged into the **root** Kover report by
 * [io.spine.gradle.report.coverage.KoverConfig]. Only classes the root aggregation
 * owns — the project's own renderers and generators — are credited from them; the
 * compiler loads those classes as their original, un-relocated artifacts, so
 * JaCoCo's class IDs match. The agent emits binary execution data because Kover
 * merges binary data at the probe level — see `KoverConfig` for why binary, not XML.
 *
 * The method is idempotent — a repeated call on the same project returns early
 * instead of attaching the agent twice — and may be called on every subproject;
 * it is a no-op for modules that declare no `launch*SpineCompiler` task.
 */
fun Project.enableSpineCompilerCoverage() {
    if (configurations.findByName(AGENT_CONFIGURATION) != null) {
        return
    }
    val agent = configurations.maybeCreate(AGENT_CONFIGURATION).apply {
        isCanBeConsumed = false
        isCanBeResolved = true
    }
    dependencies.add(agent.name, Jacoco.agent)

    val agentPath = agent.elements.map { it.single().asFile.absolutePath }
    val execDir = layout.buildDirectory.dir(COMPILER_COVERAGE_DIR)

    val launchTasks = tasks.withType<JavaExec>().matching { it.isSpineCompilerLaunchTask() }

    launchTasks.configureEach {
        val taskName = name
        val execFile = execDir.map { it.file("$taskName.exec") }
        // Track the agent *coordinate*, not the resolved absolute path, so a JaCoCo
        // version bump invalidates the task and regenerates the exec while the cache
        // key stays machine-independent — see the KDoc.
        inputs.property(JACOCO_AGENT_INPUT, Jacoco.agent)
        // Captured as a task output so the build cache stores and restores it —
        // see the KDoc for why a synchronous `JavaExec` can declare its agent exec
        // while a TestKit worker cannot.
        outputs.file(execFile)
        doFirst {
            val file = execFile.get().asFile
            file.parentFile.mkdirs()
            jvmArgs(
                "-javaagent:${agentPath.get()}=destfile=${file.absolutePath},append=false"
            )
        }
    }

    // The root Kover report/verification tasks read these exec files as
    // `additionalBinaryReports`, but do not otherwise depend on the (non-Kover)
    // test modules that produce them. Make them depend on the launch tasks —
    // a hard `dependsOn`, not mere ordering, so that running a report task by
    // itself still triggers the launch tasks that write the exec files.
    rootProject.tasks
        .matching { it.consumesCoverageBinaryReports() }
        .configureEach { dependsOn(launchTasks) }
}

/**
 * Tells whether this is one of the `launch[<SourceSet>]SpineCompiler` tasks that
 * fork the Spine Compiler — matched by name to avoid a compile-time dependency on
 * the compiler Gradle plugin's task types.
 *
 * `internal` so [io.spine.gradle.report.coverage.KoverConfig] can collect the exec
 * files of the **current** launch tasks (rather than scanning the directory) and
 * thereby ignore stale execs from removed tasks.
 */
internal fun Task.isSpineCompilerLaunchTask(): Boolean =
    name.startsWith(LAUNCH_TASK_PREFIX) && name.endsWith(LAUNCH_TASK_SUFFIX)

/**
 * The name of the directory under a module's `build` directory where the coverage
 * of forked Spine Compiler JVMs is collected.
 *
 * The directory holds JaCoCo execution-data (`.exec`) files — one per launch task —
 * written by the JaCoCo agent attached to the compiler fork. `KoverConfig` picks
 * these files up and feeds them into the root Kover report as additional binary
 * reports.
 *
 * @see io.spine.gradle.report.coverage.KoverConfig
 */
internal const val COMPILER_COVERAGE_DIR: String = "jacoco-compiler"

/**
 * The name of the dedicated, resolvable configuration that holds the standalone
 * JaCoCo agent JAR ([Jacoco.agent]) attached to the forked Spine Compiler JVMs.
 *
 * The configuration is hidden and non-consumable; it exists only to resolve the
 * agent JAR.
 */
private const val AGENT_CONFIGURATION: String = "spineCompilerJacocoAgent"

/**
 * The name of the task input property that records the JaCoCo agent coordinate
 * ([Jacoco.agent]), so a version bump invalidates the `launch*SpineCompiler` tasks
 * and their cached `.exec` outputs.
 */
private const val JACOCO_AGENT_INPUT: String = "jacocoAgentCoordinate"

private const val LAUNCH_TASK_PREFIX: String = "launch"
private const val LAUNCH_TASK_SUFFIX: String = "SpineCompiler"
