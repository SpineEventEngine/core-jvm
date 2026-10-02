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

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import java.io.File
import java.security.MessageDigest
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Verifies the `publicationChecksums` task against a real multi-module build
 * run via Gradle TestKit.
 *
 * The manifest this task writes is the sole input by which `actions/attest`
 * decides what to attest: it takes both the digest and the name from the file
 * and never reads the artifacts. A missing entry therefore narrows a signed
 * claim without any symptom, and a name that is merely close enough describes
 * something nobody publishes. Both are invisible to a unit test over the
 * derivation alone, so the fixture publishes to a local repository and compares
 * the manifest against the files that actually arrive there.
 */
@DisplayName("`publicationChecksums` task should")
internal class PublicationChecksumsIgTest {

    @TempDir
    lateinit var projectDir: File

    @BeforeEach
    fun setUpProject() {
        file("settings.gradle.kts").writeText(
            """
            rootProject.name = "checksums-sample"
            include("api", "backend")
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
                version = "$version"
            }

            subprojects {
                apply(plugin = "java-library")
                apply(plugin = "maven-publish")

                // `javadocJar` of `spinePublishing` is wired to Dokka, which
                // this fixture does not apply: the manifest is what is under
                // test, not the documentation toolchain.
                tasks.register("dokkaGeneratePublicationJavadoc")
            }

            spinePublishing {
                modules = setOf("api", "backend")
                destinations = emptySet()
            }

            subprojects {
                the<PublishingExtension>().repositories {
                    maven {
                        name = "stage"
                        url = uri("${'$'}{rootDir}/staged")
                    }
                }
            }
            """.trimIndent()
        )
        sourceFile("api")
        sourceFile("backend")
    }

    @Test
    fun `list every published artifact under its published name`() {
        val result = runGradle(aggregator, "publishAllPublicationsToStageRepository")

        result.task(":$aggregator")?.outcome shouldBe TaskOutcome.SUCCESS

        // The standard publication of a module without Proto or a test JAR: the
        // compilation output, the three documentation and source archives added
        // by `artifacts(JarFlags)`, the SBOM added by `PublicationSbom`, and the
        // two metadata files. Spelled out rather than only compared with the
        // staged files, because two empty lists also "contain exactly" each
        // other — this suite must not be able to pass by describing nothing.
        val expected = listOf("api", "backend").flatMap { module ->
            listOf(
                "spine-$module-$version.jar",
                "spine-$module-$version-sources.jar",
                "spine-$module-$version-javadoc.jar",
                "spine-$module-$version-html-docs.jar",
                "spine-$module-$version.spdx.json",
                "spine-$module-$version.pom",
                "spine-$module-$version.module",
            )
        }.sorted()

        val manifest = manifestEntries().map { it.name }.sorted()
        manifest shouldContainExactly expected
        manifest shouldContainExactly publishedFileNames()
    }

    /**
     * Each line of the manifest starts with a 64-character digest, so ordering
     * the raw lines orders them by that digest — which is to say, arbitrarily.
     * The per-project collectors sort by name, and a manifest that two runs of
     * the same build can be compared line by line has to do the same.
     */
    @Test
    fun `order the manifest by subject name`() {
        runGradle(aggregator)

        val names = manifestEntries().map { it.name }
        names shouldBe names.sorted()
    }

    @Test
    fun `record the digest of each published file`() {
        runGradle(aggregator, "publishAllPublicationsToStageRepository")

        manifestEntries().forEach { entry ->
            val published = stagedFile(entry.name)
            entry.digest shouldBe published.sha256()
        }
    }

    @Test
    fun `apply the artifact prefix to the published names`() {
        runGradle(aggregator)

        val names = manifestEntries().map { it.name }
        names.forEach { it shouldStartWith "spine-" }
        names shouldContain "spine-api-$version.jar"
        names shouldContain "spine-api-$version.pom"
        names shouldContain "spine-api-$version.module"
        names shouldContain "spine-api-$version-sources.jar"
    }

    /**
     * Maven deploys a version ending in `-SNAPSHOT` under a timestamped name
     * assigned at upload time, so the names derived from the publication belong
     * to no uploaded file. Naming files that were never published is the one
     * outcome worse than not attesting at all, so the build stops instead.
     *
     * The run below asks to publish, not merely to collect, because that is the
     * order the distributed workflow uses. Rejecting the version only once the
     * collector runs would let the upload happen first and leave behind the
     * published-but-unattested artifacts the check exists to prevent — so the
     * assertion is that nothing reached the repository at all.
     */
    @Test
    fun `refuse a Maven snapshot version before anything is published`() {
        val script = file("build.gradle.kts")
        script.writeText(
            script.readText().replace("version = \"$version\"", "version = \"1.0.0-SNAPSHOT\"")
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(
                "publishAllPublicationsToStageRepository", aggregator, "--stacktrace"
            )
            .buildAndFail()

        result.output shouldContain "1.0.0-SNAPSHOT"
        file("staged").exists() shouldBe false
    }

    /**
     * The names of the files that the build actually publishes, excluding the
     * digest sidecars and the repository-level metadata, which the repository
     * generates rather than the build.
     */
    private fun publishedFileNames(): List<String> =
        file("staged").walkTopDown()
            .filter { it.isFile }
            .map { it.name }
            .filterNot { it.startsWith("maven-metadata") }
            .filterNot { sidecars.any(it::endsWith) }
            .toList()
            .sorted()

    private fun stagedFile(name: String): File =
        file("staged").walkTopDown().firstOrNull { it.isFile && it.name == name }
            ?: error("No staged file named `$name`.")

    private fun manifestEntries(): List<ManifestEntry> =
        file("build/attestation/subject-checksums.txt")
            .readLines()
            .filter { it.isNotBlank() }
            .map { line ->
                val digest = line.substringBefore(' ')
                ManifestEntry(digest, line.substringAfter(' ').trim())
            }

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

    private fun runGradle(vararg args: String): BuildResult =
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
     *
     * The indentation below is matched to the `classpath(files(` call in the
     * generated script, which `trimIndent()` would otherwise flatten.
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

    private data class ManifestEntry(val digest: String, val name: String)

    private companion object {

        /** The task under test, named by the code that registers it. */
        val aggregator = PublicationChecksums.aggregatorTaskName

        const val version = "1.0.0"

        val sidecars = listOf(".md5", ".sha1", ".sha256", ".sha512")
    }
}

/**
 * Returns the SHA-256 digest of this file as a lowercase hexadecimal string.
 *
 * Deliberately a second implementation rather than the one the production code
 * uses: the assertion compares a digest this test computed with a digest that
 * code produced, and sharing the function would make it compare a value with
 * itself. Test files are small, so this one reads the whole file.
 */
private fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(readBytes())
    return digest.joinToString("") { "%02x".format(it) }
}
