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

import io.spine.dependency.test.Jacoco
import io.spine.dependency.test.Kover
import io.spine.gradle.testing.TESTKIT_COVERAGE_DIR
import java.io.File
import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.api.Project
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

private const val GENERATED_MARKER: String = "generated"
private const val KOTLIN_SOURCE_SET_EXT_NAME: String = "kotlin"
private const val KOTLIN_MAIN_SOURCE_SET_SUFFIX: String = "Main"
private const val JAVA_SOURCE_SUFFIX: String = ".java"
private const val KOTLIN_SOURCE_SUFFIX: String = ".kt"
private const val PROTO_KOTLIN_SUFFIX: String = ".proto.kt"
private const val KOTLIN_FILE_CLASS_SUFFIX: String = "Kt"

/**
 * Configures Kover at the root of a multi-module Gradle project to aggregate
 * coverage across subprojects and exclude classes that originate from
 * `generated/` source directories.
 *
 * Apply once from the root build script, at top level:
 * ```
 * KoverConfig.applyTo(rootProject)
 * ```
 *
 * Do **not** wrap this call in `gradle.projectsEvaluated { … }`. The Kover
 * plugin registers its own `afterEvaluate` hooks at apply time; applying it
 * after the root project has been evaluated fails with `Cannot run
 * Project.afterEvaluate(Action) when the project is already evaluated`.
 *
 * Subproject wiring is deferred via `pluginManager.withPlugin(...)`:
 * the per-subproject `useJacoco(...)`, aggregation dependency, and exclude
 * filter are registered the moment a subproject applies the Kover plugin —
 * either immediately (if the plugin is already applied) or later in the
 * same configuration phase. Both branches run **before** Kover's own
 * `afterEvaluate` finalization, so the engine pin and the aggregation
 * dependency are visible when Kover builds its task graph.
 *
 * Generated-class FQN discovery is resolved lazily through a [Provider]
 * passed to `classes(...)`. The directory walk happens at task-graph time
 * (not at configuration time), so `protoc`-generated sources created by
 * upstream tasks are picked up correctly on a clean build.
 *
 * The configuration:
 *
 *  - Applies the Kover plugin to the root project.
 *  - Pins the coverage engine to the JaCoCo version declared in
 *    [io.spine.dependency.test.Jacoco] via `useJacoco(...)` on the root **and
 *    on every eligible subproject**. The `jvm-module` / `kmp-module` script
 *    plugins already pin the same version, so the per-subproject call is
 *    idempotent for those modules; it matters for subprojects that apply
 *    Kover directly without the convention plugin.
 *  - For every subproject that applies Kover, adds a `kover(project(...))`
 *    dependency so the subproject's coverage flows into the root rollup,
 *    and pushes the subproject's generated-class FQNs into its own
 *    `kover { reports { filters { excludes { classes(...) } } } }`.
 *  - Configures the root `koverXmlReport` task with `onCheck = true` and
 *    excludes the union of generated-class FQNs across all subprojects.
 *  - Feeds the JaCoCo execution data produced by Gradle TestKit worker JVMs
 *    (see [io.spine.gradle.testing.enableTestKitCoverage]) into the `total`
 *    reports as `additionalBinaryReports`, both per subproject and at the root.
 *    Without this, plugin code exercised out-of-process through `GradleRunner`
 *    — most notably `Plugin<Settings>` implementations, which cannot be
 *    unit-tested in-process — is not credited to coverage.
 *
 *    The worker data is merged as binary `.exec` rather than as a generated
 *    XML report on purpose. `additionalBinaryReports` is the only merge hook
 *    Kover offers (there is no XML-merge equivalent), and binary data merges at
 *    the probe level against each module's actual bytecode: a line hit both
 *    in-process and out-of-process is counted once. Summing pre-aggregated XML
 *    reports instead would double-count such lines.
 *
 * This is the Kover-based successor to the deprecated JaCoCo-based
 * coverage aggregation pipeline. The behaviour mirrors what
 * the former JaCoCo-based pipeline provided, but is wired through Kover
 * (`koverXmlReport`) instead of vanilla `jacocoRootReport`.
 */
@Suppress("unused")
class KoverConfig private constructor(
    private val rootProject: Project,
) {

    companion object {

        /**
         * Configures Kover aggregation and generated-code exclusion at the
         * root of a multi-module Gradle project.
         *
         * Must be called with the root project; throws an
         * [IllegalArgumentException] if called with a non-root project, and
         * an [IllegalStateException] if [project] has no subprojects —
         * a single-module Gradle project does not need root aggregation,
         * so apply the `jvm-module` / `kmp-module` script plugin (or the
         * Kover plugin) directly to that module instead.
         *
         * Eligibility is determined per subproject: only subprojects that
         * apply the Kover plugin (directly or through `jvm-module` /
         * `kmp-module`) are wired into the rollup. Subprojects that apply
         * Kover after `applyTo` returns are still picked up — wiring runs
         * inside a `pluginManager.withPlugin(...)` callback that fires
         * the moment the plugin is applied.
         */
        fun applyTo(project: Project) {
            require(project == project.rootProject) {
                "`KoverConfig.applyTo` must be called with the root project. " +
                        "Received ${project.path}."
            }
            check(project.subprojects.isNotEmpty()) {
                "In a single-module Gradle project, `KoverConfig` is NOT needed. " +
                        "Apply the Kover plugin directly to the module instead."
            }
            project.pluginManager.apply(Kover.id)
            KoverConfig(project).configure()
        }
    }

    private fun configure() {
        configureRoot()
        rootProject.subprojects.forEach { sub ->
            sub.pluginManager.withPlugin(Kover.id) {
                addAggregationDependency(sub)
                configureSubproject(sub)
            }
        }
    }

    private fun addAggregationDependency(sub: Project) {
        rootProject.dependencies.add("kover", rootProject.project(sub.path))
    }

    /**
     * Pins the coverage engine to the JaCoCo version declared in
     * [io.spine.dependency.test.Jacoco] on [sub] and registers a lazy
     * exclude filter that resolves [sub]'s generated-class FQNs at
     * task-graph time, after upstream code-generation tasks have run.
     *
     * Calling `useJacoco(...)` is idempotent: the `jvm-module` and
     * `kmp-module` script plugins already pin the same version; the call
     * here matters for subprojects that apply Kover directly.
     */
    private fun configureSubproject(sub: Project) {
        sub.extensions.configure(KoverProjectExtension::class.java) {
            useJacoco(Jacoco.version)
            reports {
                total {
                    additionalBinaryReports.addAll(testKitExecFilesProvider(sub))
                }
                filters {
                    excludes {
                        classes(perSubprojectExcludePatternsProvider(sub))
                    }
                }
            }
        }
    }

    private fun configureRoot() {
        rootProject.extensions.configure(KoverProjectExtension::class.java) {
            useJacoco(Jacoco.version)
            reports {
                total {
                    xml {
                        onCheck.set(true)
                    }
                    additionalBinaryReports.addAll(rootTestKitExecFilesProvider())
                }
                filters {
                    excludes {
                        classes(generatedExcludePatternsProvider())
                    }
                }
            }
        }
    }

    /**
     * Lazy `Provider` of the union of generated-class FQN exclusion patterns
     * across every subproject that applies the Kover plugin.
     *
     * Resolved at task-graph time; the per-subproject FQN walk runs **after**
     * `protoc` (and other code-generation tasks) have populated each
     * subproject's `generated/` directories on a clean build.
     */
    private fun generatedExcludePatternsProvider(): Provider<Iterable<String>> =
        rootProject.provider {
            rootProject.subprojects.asSequence()
                .filter { it.pluginManager.hasPlugin(Kover.id) }
                .flatMap { generatedClassFqns(it).asSequence() }
                .toSortedSet()
                .toExclusionPatterns()
        }

    /**
     * Lazy `Provider` of the JaCoCo execution-data files produced by the Gradle
     * TestKit workers of every subproject that applies the Kover plugin.
     *
     * These files credit out-of-process plugin execution (settings plugins and
     * other code run through `GradleRunner`) to the root coverage rollup.
     * Resolved at task-graph time, after the `test` tasks have written them.
     *
     * @see io.spine.gradle.testing.enableTestKitCoverage
     */
    private fun rootTestKitExecFilesProvider(): Provider<Iterable<File>> =
        rootProject.provider {
            rootProject.subprojects.asSequence()
                .filter { it.pluginManager.hasPlugin(Kover.id) }
                .flatMap { testKitExecFiles(it).asSequence() }
                .toList()
        }

    /**
     * Lazy `Provider` of the JaCoCo execution-data files produced by the Gradle
     * TestKit workers of [sub]. See [rootTestKitExecFilesProvider] for timing notes.
     */
    private fun testKitExecFilesProvider(sub: Project): Provider<Iterable<File>> =
        sub.provider { testKitExecFiles(sub) }

    /**
     * Lazy `Provider` of the generated-class FQN exclusion patterns
     * for [sub]. See [generatedExcludePatternsProvider] for timing notes.
     */
    private fun perSubprojectExcludePatternsProvider(
        sub: Project,
    ): Provider<Iterable<String>> =
        sub.provider {
            generatedClassFqns(sub).toSortedSet().toExclusionPatterns()
        }

    /**
     * Returns the fully-qualified names of all classes that originate from
     * `generated/` source directories of the [project]'s production source sets.
     *
     * Java/Kotlin-JVM projects expose these dirs through the `main` source set.
     * Kotlin Multiplatform projects expose them through source sets such as
     * `commonMain` and `jvmMain`.
     */
    private fun generatedClassFqns(project: Project): List<String> {
        return generatedSrcDirs(project)
            .asSequence()
            .filter { it.exists() && it.isDirectory }
            .flatMap { root ->
                root.walk()
                    .filter { !it.isDirectory }
                    .flatMap { it.classNamesIn(root).asSequence() }
            }
            .distinct()
            .toList()
    }
}

/**
 * Returns the JaCoCo execution-data (`.exec`) files written by the Gradle TestKit
 * workers of the [project], or an empty list if the module produced none.
 *
 * The files reside under `build/`[TESTKIT_COVERAGE_DIR] and are created by the
 * `plugin-testlib` test harness when a module opts in via
 * [io.spine.gradle.testing.enableTestKitCoverage].
 */
private fun testKitExecFiles(project: Project): List<File> {
    val dir = project.layout.buildDirectory.dir(TESTKIT_COVERAGE_DIR).get().asFile
    if (!dir.isDirectory) {
        return emptyList()
    }
    return dir.listFiles { file -> file.isFile && file.extension == "exec" }
        ?.sorted()
        ?: emptyList()
}

private fun generatedSrcDirs(project: Project): Set<File> {
    val javaDirs = javaMainSourceSet(project)
        ?.let(::generatedSrcDirs)
        ?: emptySet()
    val kotlinMultiplatformDirs = kotlinMultiplatformMainSourceSets(project)
        .asSequence()
        .flatMap { generatedSrcDirs(it).asSequence() }
        .toSet()
    return javaDirs + kotlinMultiplatformDirs
}

private fun javaMainSourceSet(project: Project): SourceSet? =
    project.extensions.findByType(JavaPluginExtension::class.java)
        ?.sourceSets
        ?.findByName(SourceSet.MAIN_SOURCE_SET_NAME)

private fun kotlinMultiplatformMainSourceSets(project: Project): List<KotlinSourceSet> =
    project.extensions.findByType(KotlinMultiplatformExtension::class.java)
        ?.sourceSets
        ?.filter { it.isMainSourceSet() }
        ?: emptyList()

private fun generatedSrcDirs(main: SourceSet): Set<File> {
    val javaDirs = main.allJava.srcDirs
    val kotlinDirs =
        (main.extensions.findByName(KOTLIN_SOURCE_SET_EXT_NAME) as? SourceDirectorySet)
            ?.srcDirs
            ?: emptySet()
    return (javaDirs + kotlinDirs).filter { it.absolutePath.contains(GENERATED_MARKER) }
        .toSet()
}

@OptIn(ExperimentalKotlinGradlePluginApi::class)
private fun generatedSrcDirs(sourceSet: KotlinSourceSet): Set<File> {
    val kotlinDirs = sourceSet.kotlin.srcDirs
    val generatedKotlinDirs = sourceSet.generatedKotlin.srcDirs
    return (kotlinDirs + generatedKotlinDirs)
        .filter { it.absolutePath.contains(GENERATED_MARKER) }
        .toSet()
}

private fun KotlinSourceSet.isMainSourceSet(): Boolean =
    name == KotlinSourceSet.COMMON_MAIN_SOURCE_SET_NAME ||
            name.endsWith(KOTLIN_MAIN_SOURCE_SET_SUFFIX)

/**
 * Derives one or more class FQNs from this source file's path relative
 * to [root].
 *
 *  - `.java` — one FQN.
 *  - `.kt` — the declared class plus the Kotlin file-class synthetic
 *    (`<Name>Kt`).
 *  - `.proto.kt` — `protoc-gen-kotlin` convention; the two-part suffix
 *    is stripped, otherwise treated as a `.kt` file.
 *  - any other extension — an empty list.
 *
 * Returns an empty list if this file is not under [root].
 */
internal fun File.classNamesIn(root: File): List<String> {
    if (!startsWith(root)) {
        return emptyList()
    }
    val relative = toRelativeString(root)
    return when {
        relative.endsWith(PROTO_KOTLIN_SUFFIX) -> {
            val base = relative.removeSuffix(PROTO_KOTLIN_SUFFIX).toFqn()
            listOf(base, base + KOTLIN_FILE_CLASS_SUFFIX)
        }
        relative.endsWith(KOTLIN_SOURCE_SUFFIX) -> {
            val base = relative.removeSuffix(KOTLIN_SOURCE_SUFFIX).toFqn()
            listOf(base, base + KOTLIN_FILE_CLASS_SUFFIX)
        }
        relative.endsWith(JAVA_SOURCE_SUFFIX) ->
            listOf(relative.removeSuffix(JAVA_SOURCE_SUFFIX).toFqn())
        else -> emptyList()
    }
}

/**
 * Expands each fully-qualified class name into two Kover exclusion
 * patterns: the class itself, and `<FQN>$*` for any nested or anonymous
 * classes the compiler emits alongside it.
 */
private fun Collection<String>.toExclusionPatterns(): List<String> =
    flatMap { listOf(it, "$it\$*") }

private fun String.toFqn(): String = replace(File.separatorChar, '.')
