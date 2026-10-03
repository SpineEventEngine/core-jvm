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

import io.kotest.matchers.shouldBe
import java.io.File
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Verifies the checksum tasks against builds that configure `spinePublishing`
 * more than once.
 *
 * Repeated configuration is supported rather than exotic — the extension is
 * reused across calls, and a module with a custom publication opens it on its
 * own while still appearing in the root extension's `modulesWithCustomPublishing`.
 * Each call runs `configured()` again, so the tasks are registered against a
 * project that may already have them, with a set of published projects that may
 * have grown. Neither failure shows up in a build that configures the extension
 * once, which is every other fixture here.
 */
@DisplayName("`publicationChecksums` task should, on repeated configuration,")
internal class PublicationChecksumsReconfigurationIgTest {

    @TempDir
    lateinit var projectDir: File

    /**
     * Like `uber-jar-module.gradle.kts`, the module leaves `destinations` unset
     * and inherits them from the root extension.
     */
    @Test
    fun `tolerate the extension configured in both the root and the module`() {
        file("settings.gradle.kts").writeText(
            """
            rootProject.name = "custom-sample"
            include("uber")
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

            import io.spine.gradle.publish.spinePublishing

            allprojects {
                group = "io.spine.sample"
                version = "1.0.0"
            }

            subprojects {
                apply(plugin = "java-library")
                apply(plugin = "maven-publish")
                tasks.register("dokkaGeneratePublicationJavadoc")
            }

            spinePublishing {
                modulesWithCustomPublishing = setOf("uber")
                destinations = emptySet()
            }
            """.trimIndent()
        )
        file("uber/build.gradle.kts").apply {
            parentFile.mkdirs()
            writeText(
                """
                import io.spine.gradle.publish.spinePublishing

                spinePublishing {
                    customPublishing = true
                }
                """.trimIndent()
            )
        }
        file("uber/src/main/java/sample/Stub.java").apply {
            parentFile.mkdirs()
            writeText(
                """
                package sample;
                public class Stub {}
                """.trimIndent()
            )
        }

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("publicationChecksums", "--stacktrace")
            .build()

        result.task(":publicationChecksums")?.outcome shouldBe TaskOutcome.SUCCESS
    }

    /**
     * A second call registers a collector for the module it adds, while the
     * aggregator is already there. Unless the aggregator reads the projects it
     * merges through something that outlives one call, that module is collected
     * and then left out of the manifest — the silent narrowing this whole
     * arrangement exists to prevent.
     */
    @Test
    fun `merge the modules added by a later call`() {
        file("settings.gradle.kts").writeText(
            """
            rootProject.name = "regrown-sample"
            include("first", "second")
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

            import io.spine.gradle.publish.spinePublishing

            allprojects {
                group = "io.spine.sample"
                version = "1.0.0"
            }

            subprojects {
                apply(plugin = "java-library")
                apply(plugin = "maven-publish")
                tasks.register("dokkaGeneratePublicationJavadoc")
            }

            spinePublishing {
                modules = setOf("first")
                destinations = emptySet()
            }

            spinePublishing {
                modules = setOf("first", "second")
                destinations = emptySet()
            }
            """.trimIndent()
        )
        sourceFile("first")
        sourceFile("second")

        runGradle(PublicationChecksums.aggregatorTaskName)

        val names = manifestNames()
        names.any { it.startsWith("spine-first-") } shouldBe true
        names.any { it.startsWith("spine-second-") } shouldBe true
    }

    private fun manifestNames(): List<String> =
        file("build/attestation/subject-checksums.txt")
            .readLines()
            .filter { it.isNotBlank() }
            .map { it.substringAfter("  ").trim() }

    private fun sourceFile(module: String) {
        file("$module/src/main/java/sample/$module/Stub.java").apply {
            parentFile.mkdirs()
            writeText(
                """
                package sample.$module;
                public class Stub {}
                """.trimIndent()
            )
        }
    }

    private fun runGradle(vararg args: String) =
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(*args, "--stacktrace")
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
