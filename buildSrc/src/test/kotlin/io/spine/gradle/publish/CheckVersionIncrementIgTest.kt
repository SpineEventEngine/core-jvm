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

import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Runs `checkVersionIncrement` in a real build via Gradle TestKit.
 *
 * The build publishes a module under a custom `artifactId`, as `:compiler-plugins` of
 * `core-jvm-compiler` does, and checks it against a Maven repository in a local directory.
 * The environment of a pull request to `master` makes the task run.
 *
 * Unlike the tests built with `ProjectBuilder`, this one sees the output of the build,
 * which carries the warnings of the task.
 *
 * The builds run without the [ciVariables] of the test JVM, like those of
 * [IncrementGuardIgTest], which explains why a TestKit build must not see `CI`.
 */
@DisplayName("`checkVersionIncrement` should")
internal class CheckVersionIncrementIgTest {

    @TempDir
    lateinit var projectDir: File

    @Test
    fun `fail when the version of a publication is already published`() {
        repoDir.writeMetadata(repository = "snapshots", path = artifactPath, version)
        writeBuild()

        val output = runner().buildAndFail().output

        output shouldContain "The version `$version` of `$artifactPath` is already published"
    }

    @Test
    fun `pass without a warning when the version is not published yet`() {
        repoDir.writeMetadata(repository = "snapshots", path = artifactPath, previousVersion)
        writeBuild()

        val output = runner().build().output

        output shouldNotContain "No `${MavenMetadata.FILE_NAME}` is found"
    }

    @Test
    fun `warn when no repository has the metadata of a publication`() {
        writeBuild()

        val output = runner().build().output

        output shouldContain "No `${MavenMetadata.FILE_NAME}` is found for `$artifactPath`"
        output shouldContain
                "cannot tell whether the version `$version` is already published"
    }

    /**
     * The directory holding the `snapshots` and `releases` repositories.
     */
    private val repoDir: File
        get() = projectDir.resolve("repo")

    private fun runner(): GradleRunner =
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withEnvironment(pullRequestEnvironment())
            .withArguments(IncrementGuard.taskName, "--stacktrace")

    /**
     * Returns the environment of a build of a pull request to `master`, outside
     * the `Version Guard` workflow, so that the task runs and skips the comparison
     * with the base branch.
     */
    private fun pullRequestEnvironment(): Map<String, String> =
        System.getenv() - ciVariables + mapOf(
            "GITHUB_EVENT_NAME" to "pull_request",
            "GITHUB_BASE_REF" to "master",
        )

    private fun writeBuild() {
        val repoUrl = repoDir.toURI().toString().removeSuffix("/")
        file("settings.gradle.kts").writeText(
            """
            rootProject.name = "compiler-plugins"
            """.trimIndent()
        )
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

            plugins {
                `maven-publish`
            }

            group = "$group"
            version = "$version"

            apply<IncrementGuard>()

            publishing {
                publications {
                    create<MavenPublication>("fatJar") {
                        artifactId = "$artifactId"
                    }
                }
            }

            tasks.named<CheckVersionIncrement>(IncrementGuard.taskName) {
                repository = Repository(
                    name = "local",
                    releases = "$repoUrl/releases",
                    snapshots = "$repoUrl/snapshots",
                )
            }
            """.trimIndent()
        )
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

    private companion object {

        const val group = "io.spine.tools"
        const val version = "2.0.0-SNAPSHOT.094"
        const val previousVersion = "2.0.0-SNAPSHOT.093"

        /**
         * The `artifactId` of the publication, unrelated to the name of the project.
         */
        const val artifactId = "core-jvm-plugins"

        const val artifactPath = "io/spine/tools/$artifactId"
    }
}
