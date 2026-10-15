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

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome.SKIPPED
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Verifies when `checkVersionIncrement` runs in a real build that uses the
 * configuration cache.
 *
 * Every case runs the build twice: the first run stores the configuration cache entry,
 * and the second one reuses it. The task must reach the same outcome both times, so the
 * decision whether to run cannot rely on configuration that a reused entry skips.
 *
 * The builds run without the `CI` and GitHub Actions variables of the test JVM, so the
 * outcome is the same on a workstation and on CI. The Maven repository the task queries
 * is a missing local directory, so the task finds no published versions and passes
 * without network access.
 *
 * [io.spine.gradle.Build.ci] is read once per class loader, and a TestKit daemon may reuse
 * the build script class loader across builds. Therefore, any TestKit build in this test
 * run that reads `Build.ci` with `CI` set — e.g., by running `checkVersionIncrement`
 * without removing `CI` from its environment — would leak that value into these builds.
 */
@DisplayName("`checkVersionIncrement` should, with the configuration cache,")
internal class IncrementGuardIgTest {

    @TempDir
    lateinit var projectDir: File

    @BeforeEach
    fun createBuild() {
        file("settings.gradle.kts").writeText(
            """
            rootProject.name = "guarded-sample"
            include("lib", "app")
            """.trimIndent()
        )
        file("lib").mkdirs()
        file("app").mkdirs()
        file("build.gradle.kts").writeText(
            """
            buildscript {
                dependencies {
                    classpath(files(
            ${buildSrcClasspath()}
                    ))
                }
            }

            import io.spine.gradle.publish.CheckVersionIncrement
            import io.spine.gradle.publish.IncrementGuard
            import io.spine.gradle.repo.Repository

            allprojects {
                group = "io.spine.sample"
                version = "1.0.0"
            }

            subprojects {
                apply(plugin = "maven-publish")
                apply<IncrementGuard>()
                configure<PublishingExtension> {
                    publications {
                        create<MavenPublication>("maven")
                    }
                }
                val unpublished = rootDir.resolve("repository").toURI().toString()
                tasks.withType<CheckVersionIncrement>().configureEach {
                    repository = Repository(
                        name = "unpublished",
                        releases = unpublished,
                        snapshots = unpublished
                    )
                }
            }
            """.trimIndent()
        )
    }

    @Test
    fun `run when its project publishes to Maven Local`() {
        runStoringThenReusing(":lib:publishToMavenLocal") {
            it.task(":lib:checkVersionIncrement")?.outcome shouldBe SUCCESS
        }
    }

    @Test
    fun `skip when only a sibling project publishes to Maven Local`() {
        runStoringThenReusing(":lib:checkVersionIncrement", ":app:publishToMavenLocal") {
            it.task(":lib:checkVersionIncrement")?.outcome shouldBe SKIPPED
            it.task(":app:checkVersionIncrement")?.outcome shouldBe SUCCESS
        }
    }

    @Test
    fun `skip on a local build that does not publish`() {
        runStoringThenReusing(":lib:checkVersionIncrement") {
            it.task(":lib:checkVersionIncrement")?.outcome shouldBe SKIPPED
        }
    }

    @Test
    fun `run on a pull request to a protected branch`() {
        val pullRequest = mapOf(
            "GITHUB_EVENT_NAME" to "pull_request",
            "GITHUB_BASE_REF" to "master"
        )

        runStoringThenReusing(":lib:checkVersionIncrement", env = pullRequest) {
            it.task(":lib:checkVersionIncrement")?.outcome shouldBe SUCCESS
        }
    }

    /**
     * Runs the given [tasks] twice with the configuration cache, and checks the result of
     * each run with [verify].
     *
     * The first run stores the cache entry, and the second one reuses it. The [env]
     * variables are added to the environment of the test JVM, from which the [ciVariables]
     * are removed.
     */
    private fun runStoringThenReusing(
        vararg tasks: String,
        env: Map<String, String> = emptyMap(),
        verify: (BuildResult) -> Unit
    ) {
        val stored = runGradle(tasks, env)
        withClue("The run that stores the configuration cache entry") {
            stored.output shouldContain "Configuration cache entry stored"
            verify(stored)
        }
        val reused = runGradle(tasks, env)
        withClue("The run that reuses the configuration cache entry") {
            reused.output shouldContain "Reusing configuration cache"
            verify(reused)
        }
    }

    private fun runGradle(tasks: Array<out String>, env: Map<String, String>): BuildResult {
        val environment = System.getenv() - ciVariables + env
        return GradleRunner.create()
            .withProjectDir(projectDir)
            .withEnvironment(environment)
            .withArguments(
                *tasks,
                "--configuration-cache",
                "-Dmaven.repo.local=${file("m2").absolutePath}",
                "--stacktrace"
            )
            .build()
    }

    private fun file(relativePath: String): File = projectDir.resolve(relativePath)

    /**
     * Renders the classpath with the production classes of `buildSrc` as
     * arguments of `files(...)`, for injection into the build script classpath
     * of the generated build.
     *
     * The classpath comes from the `test` task in `buildSrc/build.gradle.kts`.
     */
    private fun buildSrcClasspath(): String {
        val classpath = requireNotNull(System.getProperty("buildSrc.classpath")) {
            "The `buildSrc.classpath` system property is not set." +
                    " It is supplied by the `test` task in `buildSrc/build.gradle.kts`."
        }
        return classpath.split(File.pathSeparator).joinToString(",\n") {
            "            \"${File(it).invariantSeparatorsPath}\""
        }
    }
}

/**
 * The environment variables that select the CI behavior of [IncrementGuard]
 * and [CheckVersionIncrement].
 */
internal val ciVariables = setOf("CI", "GITHUB_EVENT_NAME", "GITHUB_BASE_REF", "VERSION_GUARD")
