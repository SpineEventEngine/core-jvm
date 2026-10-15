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

package io.spine.gradle.docs

import io.kotest.assertions.withClue
import io.kotest.matchers.file.shouldExist
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
 * The path of the probe task that the build under test declares.
 */
private const val PROBE_PATH = ":dokka-sample:dokkaGeneratePublicationProbe"

/**
 * Verifies when the `dokka-setup` plugin lets Dokka tasks run in a real build
 * that uses the configuration cache.
 *
 * Every case runs the build twice: the first run stores the configuration cache entry,
 * and the second one reuses it. The task must reach the same outcome both times, so the
 * decision whether to run cannot rely on configuration that a reused entry skips.
 *
 * Dokka's own tasks resolve the Dokka engine from Maven Central, which an offline
 * build cannot reach. So the build declares a probe task that extends
 * [DokkaBaseTask][org.jetbrains.dokka.gradle.tasks.DokkaBaseTask] and does nothing.
 * The plugin gates it like any other Dokka task. The probe is named like
 * `dokkaGeneratePublicationJavadoc`: the Dokka 2.x tasks that generate documentation
 * have `dokkaGenerate` in their names, so a gate that relied on task names would never
 * skip them.
 *
 * The build publishes a `javadocJar` that depends on the probe, just as the `javadocJar`
 * that `spinePublishing` creates depends on `dokkaGeneratePublicationJavadoc`. The build
 * publishes to a file-based repository and to its own Maven Local repository, so it never
 * touches `~/.m2`. The `updateGitHubPages` task of the build stands in for the one that
 * copies the Dokka output to GitHub Pages.
 *
 * The project with the probe is named `dokka-sample`, like the modules of `dokka-tools`,
 * so that the tests cover `dokka` in the path of a requested task, which must not count
 * as a Dokka request.
 */
@DisplayName("`dokka-setup` should, with the configuration cache,")
internal class DokkaSetupIgTest {

    @TempDir
    lateinit var projectDir: File

    @BeforeEach
    fun createBuild() {
        writeBuild(projectDir, "documented-sample")
    }

    @Test
    fun `skip a Dokka task when the build publishes to Maven Local only`() {
        runStoringThenReusing(":dokka-sample:publishToMavenLocal") {
            it.probeOutcome shouldBe SKIPPED
            // The javadoc JAR is still published, which is enough for integration tests.
            mavenLocal.resolve("io/spine/sample/dokka-sample/1.0.0/dokka-sample-1.0.0-javadoc.jar")
                .shouldExist()
        }
    }

    @Test
    fun `run a Dokka task when the build publishes to a remote repository`() {
        runStoringThenReusing(":dokka-sample:publish") {
            it.probeOutcome shouldBe SUCCESS
        }
    }

    @Test
    fun `run a Dokka task when the build publishes to both Maven Local and a remote repository`() {
        runStoringThenReusing(":dokka-sample:publishToMavenLocal", ":dokka-sample:publish") {
            it.probeOutcome shouldBe SUCCESS
        }
    }

    @Test
    fun `run a Dokka task when a repository publication task runs along with Maven Local`() {
        runStoringThenReusing(
            ":dokka-sample:publishToMavenLocal",
            ":dokka-sample:publishDocsPublicationToRemoteRepository"
        ) {
            it.probeOutcome shouldBe SUCCESS
        }
    }

    @Test
    fun `run a Dokka task when the build updates GitHub Pages and publishes to Maven Local`() {
        runStoringThenReusing(
            ":dokka-sample:publishToMavenLocal",
            ":dokka-sample:updateGitHubPages"
        ) {
            it.probeOutcome shouldBe SUCCESS
        }
    }

    @Test
    fun `run a Dokka task when it is requested along with publishing to Maven Local`() {
        runStoringThenReusing(":dokka-sample:publishToMavenLocal", PROBE_PATH) {
            it.probeOutcome shouldBe SUCCESS
        }
    }

    @Test
    fun `run a Dokka task requested by an abbreviation along with publishing to Maven Local`() {
        // Gradle selects `dokkaGeneratePublicationProbe` by this camel-case abbreviation.
        runStoringThenReusing(":dokka-sample:publishToMavenLocal", ":dokka-sample:dGPP") {
            it.probeOutcome shouldBe SUCCESS
        }
    }

    @Test
    fun `run a Dokka task when a docs JAR is requested along with publishing to Maven Local`() {
        runStoringThenReusing(":dokka-sample:publishToMavenLocal", ":dokka-sample:javadocJar") {
            it.probeOutcome shouldBe SUCCESS
        }
    }

    @Test
    fun `run a Dokka task requested from an included build along with Maven Local`() {
        // Gradle passes an included build no task names, so it cannot see the request.
        val included = "included"
        writeBuild(file(included), included)
        file("settings.gradle.kts").appendText("\nincludeBuild(\"$included\")\n")
        val probe = ":$included$PROBE_PATH"
        runStoringThenReusing(":$included:dokka-sample:publishToMavenLocal", probe) {
            it.task(probe)?.outcome shouldBe SUCCESS
        }
    }

    @Test
    fun `run a Dokka task when the build does not publish`() {
        runStoringThenReusing(":dokka-sample:javadocJar") {
            it.probeOutcome shouldBe SUCCESS
        }
    }

    @Test
    fun `run a Dokka task when the build generates documentation`() {
        runStoringThenReusing(PROBE_PATH) {
            it.probeOutcome shouldBe SUCCESS
        }
    }

    /**
     * Writes a build named [name] into [dir], with the probe in its `dokka-sample` project.
     */
    private fun writeBuild(dir: File, name: String) {
        dir.mkdirs()
        dir.resolve("settings.gradle.kts").writeText(
            """
            rootProject.name = "$name"
            include("dokka-sample")
            """.trimIndent()
        )
        // The standard library would be resolved from Maven Central otherwise,
        // which an offline build cannot reach.
        dir.resolve("gradle.properties").writeText("kotlin.stdlib.default.dependency=false\n")
        dir.resolve("build.gradle.kts").writeText(
            """
            buildscript {
                dependencies {
                    classpath(files(
            ${buildSrcClasspath()}
                    ))
                }
            }
            """.trimIndent()
        )
        dir.resolve("dokka-sample").mkdirs()
        dir.resolve("dokka-sample/build.gradle.kts").writeText(
            """
            import org.jetbrains.dokka.gradle.internal.InternalDokkaGradlePluginApi
            import org.jetbrains.dokka.gradle.tasks.DokkaBaseTask

            plugins {
                kotlin("jvm")
                `maven-publish`
                id("dokka-setup")
            }

            @OptIn(InternalDokkaGradlePluginApi::class)
            abstract class DocsProbe : DokkaBaseTask() {

                @TaskAction
                fun probe() = Unit
            }

            group = "io.spine.sample"
            version = "1.0.0"

            val docsProbe = tasks.register<DocsProbe>("dokkaGeneratePublicationProbe")

            val javadocJar = tasks.register<Jar>("javadocJar") {
                archiveClassifier.set("javadoc")
                from(layout.buildDirectory.dir("docs-probe"))
                dependsOn(docsProbe)
            }

            publishing {
                publications.create<MavenPublication>("docs") {
                    artifact(javadocJar)
                }
                repositories.maven {
                    name = "remote"
                    url = uri(rootDir.resolve("remote-repo"))
                }
            }

            tasks.register("updateGitHubPages") {
                dependsOn(docsProbe)
            }
            """.trimIndent()
        )
    }

    private val BuildResult.probeOutcome
        get() = task(PROBE_PATH)?.outcome

    /**
     * The Maven Local repository of the build, which replaces `~/.m2/repository`.
     */
    private val mavenLocal: File
        get() = file("maven-local")

    /**
     * Runs the given [tasks] twice with the configuration cache, and checks the result of
     * each run with [verify].
     *
     * The first run stores the cache entry, and the second one reuses it.
     */
    private fun runStoringThenReusing(vararg tasks: String, verify: (BuildResult) -> Unit) {
        val stored = runGradle(tasks)
        withClue("The run that stores the configuration cache entry") {
            stored.output shouldContain "Configuration cache entry stored"
            verify(stored)
        }
        val reused = runGradle(tasks)
        withClue("The run that reuses the configuration cache entry") {
            reused.output shouldContain "Reusing configuration cache"
            verify(reused)
        }
    }

    private fun runGradle(tasks: Array<out String>): BuildResult =
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(
                *tasks,
                "--configuration-cache",
                "--offline",
                "--stacktrace",
                "-Dmaven.repo.local=${mavenLocal.absolutePath}"
            )
            .build()

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
