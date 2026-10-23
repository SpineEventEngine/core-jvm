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
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir

/**
 * Verifies that [PublicationHandler] silences the warnings that Gradle prints when
 * it generates the POMs of modules applying `java-test-fixtures`, in a real
 * multi-module build run via Gradle TestKit.
 *
 * The fixture is built once for the whole class, and each case inspects the output
 * of the build. All the modules of the fixture apply `java-test-fixtures`.
 * The fixture has:
 *  - `library` — a module published in the standard way.
 *  - `plugin` — a module applying `java-gradle-plugin`, listed by the root project
 *    in `modulesWithCustomPublishing`. Its `pluginMaven` publication is created
 *    after the handler is applied.
 *  - `unhandled` — a module that `spinePublishing` does not publish. It declares
 *    a publication of the `java` component itself, so that the fixture shows
 *    the warnings the other modules would print.
 */
@DisplayName("`PublicationHandler` should")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class PublicationHandlerIgTest {

    private lateinit var projectDir: File
    private lateinit var output: String

    @BeforeAll
    fun buildFixture(@TempDir dir: File) {
        projectDir = dir
        writeBuild()
        output = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(
                pomTask(library, standardPublication),
                pomTask(pluginModule, pluginPublication),
                pomTask(unhandled, unhandledPublication),
                "--stacktrace",
            )
            .build()
            .output
    }

    @Test
    fun `silence the warnings about test fixtures for a standard publication`() {
        output shouldNotContain warningsHeader(standardPublication)
    }

    @Test
    fun `silence the warnings about test fixtures for a publication added by a plugin`() {
        output shouldNotContain warningsHeader(pluginPublication)
    }

    @Test
    fun `leave the warnings for a publication of a module it does not handle`() {
        val header = warningsHeader(unhandledPublication)
        output shouldContain header
        val warnings = output.substringAfter(header)
        warnings shouldContain "Variant testFixturesApiElements"
        warnings shouldContain "Variant testFixturesRuntimeElements"
    }

    private fun writeBuild() {
        write(
            "settings.gradle.kts",
            """
            rootProject.name = "test-fixtures-sample"
            include("$library", "$pluginModule", "$unhandled")
            """.trimIndent()
        )
        write(
            "build.gradle.kts",
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
                group = "io.spine.test"
                version = "1.0.0"
            }

            spinePublishing {
                modules = setOf("$library")
                modulesWithCustomPublishing = setOf("$pluginModule")
                destinations = emptySet()
            }
            """.trimIndent()
        )
        write(
            "$library/build.gradle.kts",
            """
            plugins {
                `java-library`
                `java-test-fixtures`
            }

            // `javadocJar` of `spinePublishing` is wired to Dokka, which
            // this fixture does not apply: only the POMs are under test.
            tasks.register("dokkaGeneratePublicationJavadoc")
            """.trimIndent()
        )
        write(
            "$pluginModule/build.gradle.kts",
            """
            plugins {
                `java-gradle-plugin`
                `maven-publish`
                `java-test-fixtures`
            }

            gradlePlugin {
                plugins {
                    create("sample") {
                        id = "io.spine.sample"
                        implementationClass = "io.spine.sample.SamplePlugin"
                    }
                }
            }
            """.trimIndent()
        )
        write(
            "$unhandled/build.gradle.kts",
            """
            plugins {
                `java-library`
                `maven-publish`
                `java-test-fixtures`
            }

            publishing {
                publications {
                    create<MavenPublication>("$unhandledPublication") {
                        from(components["java"])
                    }
                }
            }
            """.trimIndent()
        )
    }

    private fun file(relativePath: String): File = projectDir.resolve(relativePath)

    private fun write(relativePath: String, text: String) {
        file(relativePath).apply {
            parentFile.mkdirs()
            writeText(text)
        }
    }

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

        /** The module published in the standard way. */
        const val library = "library"

        /** The module applying `java-gradle-plugin`, published in a custom way. */
        const val pluginModule = "plugin"

        /** The module not published by `spinePublishing`. */
        const val unhandled = "unhandled"

        /*
         * The names of the publications, as given by the code that creates them.
         */
        const val standardPublication = StandardJavaPublicationHandler.PUBLICATION_NAME
        const val pluginPublication = "pluginMaven"
        const val unhandledPublication = "unhandled"

        fun pomTask(module: String, publication: String): String {
            val name = publication.replaceFirstChar { it.uppercase() }
            return ":$module:generatePomFileFor${name}Publication"
        }

        /**
         * Returns the line with which Gradle starts the POM warnings
         * for the given [publication].
         */
        fun warningsHeader(publication: String): String =
            "Maven publication '$publication' pom metadata warnings"
    }
}
