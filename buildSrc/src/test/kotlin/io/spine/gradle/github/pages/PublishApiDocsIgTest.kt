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

package io.spine.gradle.github.pages

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Verifies which tasks the `publishAllApiDocs` task of the root project, registered by
 * `spinePublishing { }`, brings into a build: the `publishApiDocs` tasks of the projects.
 *
 * The builds that would publish documentation run with `--dry-run`, so that nothing
 * is pushed to GitHub Pages. The builds of a snapshot version run for real, as they
 * publish nothing.
 *
 * In the multi-module build, `api` and `backend` are published and document
 * themselves, `testing` documents itself, but is not published, as a module of
 * integration tests, and `bare` is published, but does not document itself.
 *
 * No build applies Dokka. A task named like the Dokka one stands in for it, as
 * the plugin and the `javadocJar` of `spinePublishing` look the task up by name.
 */
@DisplayName("`publishAllApiDocs` task should")
internal class PublishApiDocsIgTest {

    @TempDir
    lateinit var projectDir: File

    @Test
    fun `publish the docs of each published module that documents itself`() {
        writeMultiModuleBuild(documented = setOf("api", "backend", "testing"))

        val planned = dryRun(":$aggregate")

        planned shouldContain ":$aggregate"
        planned.docsTasks() shouldContainExactlyInAnyOrder listOf(
            ":api:$taskName",
            ":backend:$taskName"
        )
        // `testing` has the task, so the aggregate left it out only for not being published.
        dryRun(":testing:$taskName").docsTasks() shouldBe listOf(":testing:$taskName")
    }

    /**
     * `lib` opens `spinePublishing { }` on its own, so the second call reaches
     * the task of the root project while `lib` is evaluated, with projects of its own.
     */
    @Test
    fun `publish the docs of a module that configures its own publishing`() {
        file("settings.gradle.kts").writeText(
            """
            rootProject.name = "own-publishing-sample"
            include("api", "lib")
            """.trimIndent()
        )
        file("build.gradle.kts").writeText(
            """
            ${buildscript()}

            import io.spine.gradle.github.pages.publishApiDocs
            import io.spine.gradle.publish.spinePublishing

            allprojects {
                group = "io.spine.sample"
                version = "1.0.0"
            }

            subprojects {
                apply(plugin = "java-library")
                $generatorStub
            }

            project("api").publishApiDocs {
                rootFolder.set(rootDir)
            }

            spinePublishing {
                modules = setOf("api")
                destinations = emptySet()
            }
            """.trimIndent()
        )
        file("lib/build.gradle.kts").apply {
            parentFile.mkdirs()
            writeText(
                """
                import io.spine.gradle.github.pages.publishApiDocs
                import io.spine.gradle.publish.spinePublishing

                publishApiDocs {
                    rootFolder.set(rootDir)
                }

                spinePublishing {
                    destinations = emptySet()
                }
                """.trimIndent()
            )
        }
        file("api").mkdirs()

        dryRun(":$aggregate").docsTasks() shouldContainExactlyInAnyOrder listOf(
            ":api:$taskName",
            ":lib:$taskName"
        )
    }

    @Test
    fun `not be a part of the publication to Maven repositories`() {
        writeMultiModuleBuild(documented = setOf("api", "backend", "testing"))

        val planned = dryRun("publish")

        planned shouldContain ":api:publish"
        planned shouldNotContain ":$aggregate"
        planned.docsTasks().shouldBeEmpty()
    }

    @Test
    fun `do nothing in a build that documents no module`() {
        writeMultiModuleBuild(documented = emptySet())

        val result = runner(":$aggregate").build()

        result.tasks.map { it.path } shouldBe listOf(":$aggregate")
        result.task(":$aggregate")?.outcome shouldBe TaskOutcome.UP_TO_DATE
    }

    @Test
    fun `publish the docs of the project in a single-project build`() {
        writeSingleProjectBuild(version = "1.0.0")

        val planned = dryRun(":$aggregate")

        planned.docsTasks() shouldBe listOf(":$taskName")
        planned shouldContain ":${TaskName.copyJavadocDocs}"
    }

    @Test
    fun `publish no docs of a snapshot version`() {
        writeMultiModuleBuild(documented = setOf("api", "backend", "testing"), version = snapshot)

        val result = runner(":$aggregate").build()

        val paths = result.tasks.map { it.path }
        paths.docsTasks() shouldContainExactlyInAnyOrder listOf(
            ":api:$taskName",
            ":backend:$taskName"
        )
        paths shouldNotContain ":api:${TaskName.copyJavadocDocs}"
        result.output shouldContain "`$snapshot` is a snapshot version"
    }

    /**
     * Only the publication to GitHub Pages skips a snapshot version. The archives of
     * its documentation, built by Dokka, are still published to Maven repositories.
     */
    @Test
    fun `leave the docs archives of a snapshot version to the Maven publication`() {
        writeMultiModuleBuild(documented = setOf("api"), version = snapshot)
        file("build.gradle.kts").appendText(
            "\n" + """
            project("api") {
                apply(plugin = "maven-publish")
                the<PublishingExtension>().repositories.maven {
                    name = "remote"
                    url = uri(rootDir.resolve("remote-repo"))
                }
            }
            """.trimIndent()
        )

        val planned = dryRun(":api:publish")

        planned shouldContain ":api:javadocJar"
        planned shouldContain ":api:dokkaGeneratePublicationJavadoc"
        planned.docsTasks().shouldBeEmpty()
    }

    @Test
    fun `publish no docs of a snapshot version in a single-project build`() {
        writeSingleProjectBuild(version = snapshot)

        val result = runner(":$aggregate").build()

        result.tasks.map { it.path } shouldContainExactlyInAnyOrder listOf(
            ":$taskName",
            ":$aggregate"
        )
        result.output shouldContain "`$snapshot` is a snapshot version"
    }

    private fun writeSingleProjectBuild(version: String) {
        file("settings.gradle.kts").writeText("""rootProject.name = "single-sample"""")
        file("build.gradle.kts").writeText(
            """
            ${buildscript()}

            import io.spine.gradle.github.pages.publishApiDocs
            import io.spine.gradle.publish.spinePublishing

            group = "io.spine.sample"
            version = "$version"

            apply(plugin = "java-library")
            $generatorStub

            publishApiDocs {
                rootFolder.set(rootDir)
            }

            spinePublishing {
                destinations = emptySet()
            }
            """.trimIndent()
        )
    }

    private fun writeMultiModuleBuild(documented: Set<String>, version: String = "1.0.0") {
        val modules = listOf("api", "backend", "bare", "testing")
        modules.forEach { file(it).mkdirs() }
        file("settings.gradle.kts").writeText(
            """
            rootProject.name = "docs-sample"
            include(${modules.joinToString { "\"$it\"" }})
            """.trimIndent()
        )
        file("build.gradle.kts").writeText(
            """
            ${buildscript()}

            import io.spine.gradle.github.pages.publishApiDocs
            import io.spine.gradle.publish.spinePublishing

            allprojects {
                group = "io.spine.sample"
                version = "$version"
            }

            subprojects {
                apply(plugin = "java-library")
                $generatorStub
            }

            listOf<String>(${documented.joinToString { "\"$it\"" }}).forEach {
                project(it).publishApiDocs {
                    rootFolder.set(rootDir)
                }
            }

            spinePublishing {
                modules = setOf("api", "backend", "bare")
                destinations = emptySet()
            }
            """.trimIndent()
        )
    }

    /**
     * Runs the build with `--dry-run` and returns the paths of the tasks
     * it would run.
     *
     * TestKit reports no tasks for a dry run, so they are read from the output,
     * which lists each of them as `<path> SKIPPED`.
     */
    private fun dryRun(task: String): List<String> =
        runner(task, "--dry-run").build().output.lines()
            .filter { it.startsWith(":") && it.endsWith(" SKIPPED") }
            .map { it.substringBefore(' ') }

    private fun runner(vararg args: String): GradleRunner =
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(*args, "--stacktrace")

    /**
     * Selects the paths of the tasks that publish documentation.
     */
    private fun List<String>.docsTasks(): List<String> =
        filter { it.substringAfterLast(':') == taskName }

    private fun file(relativePath: String): File = projectDir.resolve(relativePath)

    private fun buildscript(): String =
        """
        buildscript {
            dependencies {
                classpath(files(
        ${buildSrcClasspath()}
                ))
            }
        }
        """.trimIndent()

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

        const val taskName = TaskName.publishApiDocs

        const val aggregate = TaskName.publishAllApiDocs

        /** A version in the format the Spine SDK publishes from every merge. */
        const val snapshot = "1.0.0-SNAPSHOT.1"

        /**
         * A task standing in for the Dokka one whose output the plugin copies, and on
         * which the `javadocJar` of `spinePublishing` depends.
         */
        const val generatorStub = """tasks.register("dokkaGeneratePublicationJavadoc")"""
    }
}
