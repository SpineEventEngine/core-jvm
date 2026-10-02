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

package io.spine.gradle.report.pom

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Verifies the `generatePom` task against a real multi-project build, run via
 * Gradle TestKit with parallel execution on, as in the SDK repositories.
 *
 * The fixture reproduces the situations that a `ProjectBuilder`-based test
 * cannot: an artifact whose version conflict is settled by a `force(...)`
 * directive, an artifact genuinely resolving to different versions across
 * modules, and a configuration failing to resolve as a whole. Reporting the
 * declared versions instead of the resolved ones — as happened when a
 * standalone `generatePom` run failed to resolve the configurations of
 * subprojects — makes the forced artifact look conflicting and the generated
 * file differ between invocations.
 */
@DisplayName("`generatePom` task should")
internal class PomGeneratorIgTest {

    @TempDir
    lateinit var projectDir: File

    @BeforeEach
    fun setUpProject() {
        file("settings.gradle.kts").writeText(
            """
            rootProject.name = "pom-sample"
            include("api", "backend")
            """.trimIndent()
        )
        file("gradle.properties").writeText("org.gradle.parallel=true")
        file("build.gradle.kts").writeText(
            """
            buildscript {
                dependencies {
                    classpath(files(
            ${buildSrcClasspath()}
                    ))
                }
            }

            group = "io.spine.sample"
            version = "1.0.0"

            subprojects {
                configurations.all {
                    resolutionStrategy.force("$FORCED_LIB:$FORCED_VERSION")
                }
            }

            io.spine.gradle.report.pom.PomGenerator.applyTo(project)
            """.trimIndent()
        )
        subproject(
            name = "api",
            forcedLibVersion = "2.0.1",
            conflictingLibVersion = "3.0.1",
            withUnresolvableConfiguration = true
        )
        subproject(
            name = "backend",
            forcedLibVersion = "1.5.1",
            conflictingLibVersion = "4.0.1"
        )
        publishPom(FORCED_LIB, "1.0.1")
        publishPom(FORCED_LIB, "1.5.1")
        publishPom(FORCED_LIB, "2.0.1")
        publishPom(CONFLICTING_LIB, "3.0.1")
        publishPom(CONFLICTING_LIB, "4.0.1")
        publishPom(STRICT_LIB, "5.0.1")
        publishPom(STRICT_LIB, "6.0.1")
    }

    /**
     * A standalone `generatePom` run resolves nothing before the report, which
     * is exactly the case that used to degrade to the declared versions: the
     * forced artifact was reported as conflicting, with versions `2.0.1` and
     * `1.5.1` never present on any classpath.
     */
    @Test
    fun `report the versions selected by dependency resolution`() {
        val result = runGradle("generatePom")

        result.task(":generatePom")?.outcome shouldBe TaskOutcome.SUCCESS
        result.task(":api:$COLLECTOR")?.outcome shouldBe TaskOutcome.SUCCESS
        result.task(":backend:$COLLECTOR")?.outcome shouldBe TaskOutcome.SUCCESS

        val pom = pomFile().readText()
        pom shouldContain "<artifactId>forced-lib</artifactId>"
        pom shouldContain "<version>$FORCED_VERSION</version>"
        pom shouldNotContain "2.0.1"
        pom shouldNotContain "1.5.1"

        // Of a genuine cross-module conflict, the newest version is retained.
        pom shouldContain "<artifactId>conflicting-lib</artifactId>"
        pom shouldContain "<version>4.0.1</version>"
        pom shouldNotContain "3.0.1"

        // A module failing to resolve — reading a resolution graph is lenient,
        // so the failing configuration cannot break the build — falls back
        // to the newest declared version.
        pom shouldContain "<artifactId>strict-lib</artifactId>"
        pom shouldContain "<version>6.0.1</version>"
        pom shouldNotContain "5.0.1"
    }

    @Test
    fun `warn only about a genuinely unreconciled artifact`() {
        val result = runGradle("generatePom")

        result.output shouldContain
                "The project uses several versions of `$CONFLICTING_LIB` dependency."
        // A module that fails to resolve is genuinely unreconciled, too:
        // both declared versions fall back into the report, and the conflict
        // between them is legitimately warned about.
        result.output shouldContain
                "The project uses several versions of `$STRICT_LIB` dependency."
        result.output shouldNotContain "several versions of `$FORCED_LIB`"
    }

    @Test
    fun `write the same file for a standalone run and for a full build`() {
        runGradle("generatePom")
        val standalone = pomFile().readText()

        val fullBuild = runGradle("clean", "build")

        fullBuild.task(":generatePom")?.outcome shouldBe TaskOutcome.SUCCESS
        pomFile().readText() shouldBe standalone
    }

    private fun runGradle(vararg args: String): BuildResult =
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(*args, "--stacktrace")
            .build()

    private fun pomFile(): File = file("docs/dependencies/pom.xml")

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

    /**
     * Writes the build script of a subproject declaring the fixture dependencies.
     *
     * With [withUnresolvableConfiguration], the subproject also declares
     * an `unresolvable` configuration requesting two conflicting versions
     * of [STRICT_LIB] under `failOnVersionConflict()`, so resolving that
     * configuration as a whole fails.
     */
    private fun subproject(
        name: String,
        forcedLibVersion: String,
        conflictingLibVersion: String,
        withUnresolvableConfiguration: Boolean = false
    ) {
        val header = """
            plugins {
                `java-library`
            }

            repositories {
                maven {
                    url = uri(rootDir.resolve("repo"))
                }
            }
        """.trimIndent()
        val unresolvable = """
            // `isCanBeResolved` is stated rather than inherited: the legacy
            // role it would otherwise get is a deprecation candidate, and if
            // the default changes this configuration would be skipped by the
            // `isCanBeResolved` filter in `resolvedVersions()` — the fixture
            // would stop covering the failing-configuration case while the
            // test still passed.
            val unresolvable =
                configurations.create("unresolvable") { isCanBeResolved = true }
            unresolvable.resolutionStrategy.failOnVersionConflict()
        """.trimIndent()
        val dependencies = buildString {
            appendLine("dependencies {")
            appendLine("    implementation(\"$FORCED_LIB:$forcedLibVersion\")")
            appendLine("    implementation(\"$CONFLICTING_LIB:$conflictingLibVersion\")")
            if (withUnresolvableConfiguration) {
                appendLine("    \"unresolvable\"(\"$STRICT_LIB:5.0.1\")")
                appendLine("    \"unresolvable\"(\"$STRICT_LIB:6.0.1\")")
            }
            append("}")
        }
        val script = file("$name/build.gradle.kts")
        script.parentFile.mkdirs()
        val sections = listOfNotNull(
            header,
            unresolvable.takeIf { withUnresolvableConfiguration },
            dependencies
        )
        script.writeText(sections.joinToString(separator = "\n\n", postfix = "\n"))
    }

    /** Writes a metadata-only Maven POM for the module under the local repository. */
    private fun publishPom(module: String, version: String) {
        val (group, name) = module.split(':')
        val dir = file("repo/${group.replace('.', '/')}/$name/$version")
        dir.mkdirs()
        File(dir, "$name-$version.pom").writeText(
            """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <groupId>$group</groupId>
              <artifactId>$name</artifactId>
              <version>$version</version>
            </project>
            """.trimIndent()
        )
    }

    private companion object {

        /** The `"group:name"` of the artifact pinned by a `force(...)` directive. */
        const val FORCED_LIB = "io.test:forced-lib"

        /** The version [FORCED_LIB] is forced to, older than any declared one. */
        const val FORCED_VERSION = "1.0.1"

        /** The `"group:name"` of the artifact resolving differently across modules. */
        const val CONFLICTING_LIB = "io.test:conflicting-lib"

        /**
         * The `"group:name"` of the artifact declared in two conflicting versions
         * under `failOnVersionConflict()`, failing its configuration as a whole.
         */
        const val STRICT_LIB = "io.test:strict-lib"

        /** The name of the per-project version-collecting task. */
        const val COLLECTOR = ResolvedVersions.taskName
    }
}
