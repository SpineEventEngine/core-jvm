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

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.collections.shouldNotContainAnyOf
import io.kotest.matchers.file.shouldExist
import io.kotest.matchers.file.shouldNotExist
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import java.io.File
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import javax.xml.parsers.DocumentBuilderFactory
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * Verifies the SBOMs [PublicationSbom] adds to the publications of a real
 * multi-module build, run via Gradle TestKit.
 *
 * The fixture is built once for the whole class, because a build applying
 * Kotlin Multiplatform takes long to configure, and each case only inspects
 * what that build left behind. It runs `--offline`, resolving from a local
 * `file:` repository only — which also exercises the workaround for a plugin
 * bug that fails the SBOM task for such repositories.
 *
 * The fixture has:
 *  - `api` and `impl` — standard JVM modules, `impl` depending on `api` and
 *    `bundled`, on `com.example:lib` at runtime, and on two libraries it only
 *    compiles or tests with;
 *  - `bundled` — a JVM module left unpublished, like one bundled into the
 *    artifact of another module, such as a fat JAR;
 *  - `plugin` — a Gradle plugin, whose marker publication must stay without
 *    an SBOM;
 *  - `kmp` — a Kotlin Multiplatform module with a JVM target named `desktop`, not
 *    after its platform, and an umbrella publication that must stay without an
 *    SBOM. It has no sources, so it builds
 *    without the Kotlin compiler, which an offline build cannot fetch. As
 *    modules of the `logging` repository do, it is listed with custom publishing
 *    by the root project and also opens `spinePublishing` itself, so its SBOM is
 *    registered twice;
 *  - `twin` — a JVM module with custom publishing, whose two publications
 *    publish different artifacts, so each needs an SBOM naming its own, and
 *    whose third publication is removed once the module is evaluated;
 *  - `consumer` — a JVM module depending on `kmp`, whose SBOM must name the
 *    artifact of the JVM target of `kmp`;
 *  - `thin` — a JVM module publishing a thin JAR with a hand-written POM, as
 *    a Gradle plugin shipping its code apart from its dependencies does. The POM
 *    declares `fat` and `com.example:lib`, not the rest of the runtime classpath,
 *    and the JAR packs `bundled`;
 *  - `fat` — a JVM module publishing a fat JAR with a hand-written POM. Shadow
 *    bundles `bundled` and `com.example:inner`, but neither `com.example:outer`,
 *    which the POM declares without `inner`, nor `com.example:annotations`, which
 *    the artifact leaves for its consumers to add. The JAR keeps the `all`
 *    classifier Shadow gives it, so the publication has no main artifact;
 *  - `uber` — a JVM module publishing a fat JAR of its whole runtime classpath
 *    as its main artifact, with an empty POM, as `uber-jar-module` does;
 *  - `archive` — a JVM module publishing a ZIP archive rather than a JAR, as
 *    a Kotlin/Native target publishes a klib;
 *  - `osgi` — a JVM module publishing its JAR as an OSGi bundle, whose build sets
 *    the `bundle` packaging of the POM.
 */
@DisplayName("`PublicationSbom` should")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class PublicationSbomIgTest {

    private lateinit var projectDir: File

    private lateinit var result: BuildResult

    /**
     * Builds the fixture in a directory that JUnit keeps for all cases of the class.
     */
    @BeforeAll
    fun buildFixture(@TempDir dir: File) {
        projectDir = dir
        writeRepository()
        writeBuild()
        result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(
                ":api:$publishTask",
                ":impl:$publishTask",
                ":plugin:$publishTask",
                ":impl:${PublicationChecksums.collectorTaskName}",
                ":kmp:${PublicationSbom.taskNameFor("desktop")}",
                ":kmp:${pomTaskOf("desktop")}",
                ":twin:${PublicationSbom.taskName}",
                ":consumer:${PublicationSbom.taskName}",
                ":thin:${PublicationSbom.taskName}",
                ":thin:${pomTaskOf(pluginJar)}",
                ":fat:${PublicationSbom.taskName}",
                ":fat:${pomTaskOf(fatJar)}",
                ":uber:${PublicationSbom.taskName}",
                ":uber:${pomTaskOf(fatJar)}",
                ":archive:${pomTaskOf(archive)}",
                ":osgi:${pomTaskOf(osgiBundle)}",
                "--offline",
                "--stacktrace",
            )
            .build()
    }

    @Test
    fun `publish the SBOM of a module next to its artifacts`() {
        val files = stagedFiles("io/spine/test/spine-impl/$moduleVersion")

        files shouldContainAll listOf(
            "spine-impl-$moduleVersion.jar",
            "spine-impl-$moduleVersion.spdx.json"
        )
        implSbom()["spdxVersion"].asText() shouldBe "SPDX-2.3"
    }

    @Test
    fun `list the SBOM among the subjects of the attestation`() {
        val manifest = file("impl/build/attestation/checksums.txt").readLines()

        manifest.map { it.substringAfter("  ") } shouldContain
                "spine-impl-$moduleVersion.spdx.json"
    }

    /**
     * The SBOM is published without a classifier, as the artifact itself is, and Gradle
     * derives the packaging of a POM from such artifacts. A POM leaves out the packaging
     * of a JAR, which is the default.
     */
    @Test
    fun `keep the packaging of a JAR published with an SBOM`() {
        val jarPublications = listOf(
            "impl" to "mavenJava",
            "plugin" to "pluginMaven",
            "kmp" to "desktop",
            "thin" to pluginJar,
            "uber" to fatJar,
        )

        jarPublications.forAll { (module, publication) ->
            packagingOf(module, publication).shouldBeNull()
        }
    }

    /**
     * Shadow classifies the fat JAR of `fat` as `all`, so the publication has no main
     * artifact, and its POM declares the `pom` packaging. The SBOM, having no classifier,
     * would otherwise become the main artifact, and `spdx.json` the packaging.
     */
    @Test
    fun `keep the packaging of a publication without a main artifact`() {
        packagingOf("fat", fatJar) shouldBe "pom"
    }

    /**
     * The packaging is the extension of the main artifact, rather than always `jar`.
     */
    @Test
    fun `keep the packaging of an artifact other than a JAR published with an SBOM`() {
        packagingOf("archive", archive) shouldBe "zip"
    }

    /**
     * The build of `osgi` sets the `bundle` packaging, as the POM of an OSGi bundle declares
     * for its JAR. The packaging the build sets is kept, rather than calculated anew from
     * the artifacts, which would give `jar`.
     */
    @Test
    fun `keep the packaging a build sets explicitly`() {
        packagingOf("osgi", osgiBundle) shouldBe "bundle"
    }

    @Test
    fun `describe runtime dependencies only`() {
        val names = implSbom().packageNames()

        names shouldContain "com.example:lib"
        names shouldNotContain "com.example:testlib"
        names shouldNotContain "com.example:annotations"
    }

    /**
     * The POM of `lib` declares no license and inherits one from its parent,
     * and both POMs configure the same plugins. The SPDX plugin, running on the
     * classpath of `buildSrc`, builds effective POMs that merge the configuration
     * of those plugins — on a classpath that also carries the XML libraries of the
     * Shadow plugin. Finding the license proves that the merge works there.
     */
    @Test
    fun `take the license of a dependency from its parent POM`() {
        val lib = implSbom().packageNamed("com.example:lib")

        lib[SpdxField.licenseDeclared].asText() shouldBe "MIT"
    }

    @Test
    fun `leave the download location of a dependency from a local repository unasserted`() {
        val lib = implSbom().packageNamed("com.example:lib")

        lib["downloadLocation"].asText() shouldBe "NOASSERTION"
        lib.purl() shouldBe "pkg:maven/com.example/lib@1.0"
    }

    @Test
    fun `name the module and its siblings by their published coordinates`() {
        val sbom = implSbom()

        sbom[SpdxField.name].asText() shouldBe "spine-impl"
        sbom[SpdxField.documentNamespace].asText() shouldBe
                "https://spine.io/spdxdocs/io.spine.test/spine-impl/$moduleVersion"
        sbom.packageNames() shouldNotContainAnyOf listOf("impl", "api")
        listOf("spine-impl", "spine-api").forEach { artifactId ->
            val module = sbom.packageNamed("io.spine.test:$artifactId")
            module.purl() shouldBe "pkg:maven/io.spine.test/$artifactId@$moduleVersion"
            module[SpdxField.licenseDeclared].asText() shouldBe "Apache-2.0"
        }
    }

    /**
     * An unpublished module has no coordinates to be named after, so it keeps the
     * name the plugin gives it. It is still code of this build, under its license.
     */
    @Test
    fun `describe an unpublished sibling by its project and the license of the build`() {
        val bundled = implSbom().packageNamed("bundled")

        bundled[SpdxField.versionInfo].asText() shouldBe moduleVersion
        bundled[SpdxField.licenseDeclared].asText() shouldBe "Apache-2.0"
        bundled.purl().shouldBeNull()
        result.output shouldNotContain "`:bundled`"
    }

    @Test
    fun `attach an SBOM to every publication but plugin markers and the KMP umbrella`() {
        val reported = result.output.lines()
            .filter { it.startsWith(reportPrefix) }
            .map { it.removePrefix(reportPrefix) }

        reported shouldContainAll listOf(
            ":api mavenJava 1",
            ":impl mavenJava 1",
            ":consumer mavenJava 1",
            ":plugin pluginMaven 1",
            ":plugin samplePluginMarkerMaven 0",
            ":kmp desktop 1",
            ":kmp kotlinMultiplatform 0",
            ":twin main 1",
            ":twin extra 1",
            ":thin pluginJar 1",
            ":fat fatJar 1",
            ":uber fatJar 1",
            ":archive archive 1",
            ":osgi osgiBundle 1",
        )
    }

    /**
     * `spinePublishing` prefixes the artifact ID of each publication, as it does that
     * of the project. Neither artifact is named as the project, which an SBOM falls
     * back to when it cannot find its publication.
     */
    @Test
    fun `name the SBOM of each publication after the artifact it is published with`() {
        val artifacts = mapOf("main" to "spine-twin-main", "extra" to "spine-twin-extra")
        artifacts.forEach { (publication, artifactId) ->
            val sbom = file("twin/build/sbom/$publication.spdx.json").readJson()

            sbom[SpdxField.name].asText() shouldBe artifactId
            sbom.packageNamed("io.spine.test:$artifactId").purl() shouldBe
                    "pkg:maven/io.spine.test/$artifactId@$moduleVersion"
        }
    }

    @Test
    fun `write no SBOM for a publication the build removes`() {
        file("twin/build/sbom/dropped.spdx.json").shouldNotExist()
    }

    @Test
    fun `describe the runtime of a KMP target`() {
        val sbom = file("kmp/build/sbom/desktop.spdx.json").readJson()

        sbom[SpdxField.name].asText() shouldBe "spine-kmp-desktop"
        sbom.packageNames() shouldContainAll listOf("com.example:lib", "io.spine.test:spine-api")
    }

    /**
     * Gradle resolves `consumer`'s dependency on `kmp` to the artifact of the JVM target
     * of `kmp` by its platform — the target is named `desktop`.
     */
    @Test
    fun `name a multiplatform sibling after its artifact for the same platform`() {
        val sbom = file("consumer/build/sbom/mavenJava.spdx.json").readJson()

        sbom.packageNamed("io.spine.test:spine-kmp-desktop").purl() shouldBe
                "pkg:maven/io.spine.test/spine-kmp-desktop@$moduleVersion"
    }

    @Test
    fun `describe as dependencies of an artifact what its hand-written POM declares`() {
        listOf("thin" to pluginJar, "fat" to fatJar).forEach { (module, publication) ->
            val sbom = sbomOf(module, publication)

            sbom.relatedToArtifact(DEPENDS_ON) shouldContainAll
                    pomDependenciesOf(module, publication)
        }
    }

    @Test
    fun `leave out the runtime classpath that a hand-written POM does not declare`() {
        val sbom = sbomOf("thin", pluginJar)

        sbom.relatedToArtifact(DEPENDS_ON) shouldContainExactlyInAnyOrder
                listOf("io.spine.test:spine-fat", "com.example:lib")
        sbom.packageNames() shouldNotContain "com.example:testlib"
    }

    /**
     * `fat` is published as a fat JAR, so its SBOM describes what the JAR bundles.
     * The artifact depending on it gets that content from the JAR, and not as
     * dependencies of its own.
     */
    @Test
    fun `describe a sibling an artifact depends on by the artifact of the sibling alone`() {
        val sbom = sbomOf("thin", pluginJar)
        val fat = sbom.packageNamed("io.spine.test:spine-fat")

        fat.purl() shouldBe "pkg:maven/io.spine.test/spine-fat@$moduleVersion"
        sbom.relatedTo(fat, DEPENDS_ON).shouldBeEmpty()
        sbom.packageNames() shouldNotContain "com.example:inner"
    }

    @Test
    fun `describe the modules packed into a JAR as its content`() {
        sbomOf("thin", pluginJar).relatedToArtifact(CONTAINS) shouldContainExactly
                listOf("bundled")
    }

    @Test
    fun `describe what a fat JAR bundles as its content`() {
        val sbom = sbomOf("fat", fatJar)

        sbom.relatedToArtifact(CONTAINS) shouldContainExactlyInAnyOrder
                listOf("bundled", "com.example:inner")
        sbom.relatedToArtifact(DEPENDS_ON) shouldContainExactly listOf("com.example:outer")
    }

    @Test
    fun `leave out what a fat JAR neither bundles nor declares`() {
        sbomOf("fat", fatJar).packageNames() shouldNotContain "com.example:annotations"
    }

    /**
     * The runtime classpath of `fat` resolves `inner` as a dependency of `outer`,
     * which the POM declares without it.
     */
    @Test
    fun `not describe what a fat JAR bundles as a dependency of what it declares`() {
        sbomOf("fat", fatJar).dependencyTargets() shouldNotContain "com.example:inner"
    }

    @Test
    fun `describe a fat JAR with an empty POM by its content alone`() {
        val sbom = sbomOf("uber", fatJar)

        sbom.relatedToArtifact(CONTAINS) shouldContainExactlyInAnyOrder
                listOf("bundled", "com.example:testlib")
        sbom.relatedToArtifact(DEPENDS_ON).shouldBeEmpty()
    }

    /**
     * `uber` bundles its runtime classpath, which the SPDX Gradle Plugin describes for
     * the module anyway, so the SBOM of its fat JAR needs no document of its own.
     */
    @Test
    fun `describe a fat JAR of the runtime classpath from the document of the module`() {
        file("uber/build/spdx/publication.spdx.json").shouldExist()
        file("uber/build/spdx/${fatJar}Publication.spdx.json").shouldNotExist()
    }

    @Test
    fun `keep the SBOM of a publication made from a software component as it is`() {
        val sbom = implSbom()

        sbom.relatedToArtifact(DEPENDS_ON) shouldContainExactlyInAnyOrder
                listOf("io.spine.test:spine-api", "bundled", "com.example:lib")
        sbom.relatedToArtifact(CONTAINS).shouldBeEmpty()
        file("impl/build/spdx/publication.spdx.json").shouldExist()
    }

    @Test
    fun `write SBOMs whose relationships and licenses are complete`() {
        listOf(
            implSbom(),
            sbomOf("thin", pluginJar),
            sbomOf("fat", fatJar),
            sbomOf("uber", fatJar),
        ).forEach { it.shouldBeComplete() }
    }

    private fun sbomOf(module: String, publication: String): JsonNode =
        file("$module/build/sbom/$publication.spdx.json").readJson()

    /**
     * Returns the dependencies the POM of the given [publication] declares,
     * as `group:artifactId`.
     */
    private fun pomDependenciesOf(module: String, publication: String): List<String> {
        val dependencies = pomOf(module, publication).getElementsByTagName("dependency")
        return (0 until dependencies.length)
            .map { dependencies.item(it) as Element }
            .map { "${it.childText("groupId")}:${it.childText("artifactId")}" }
            .shouldNotBeEmpty()
    }

    /**
     * Returns the packaging the POM of the given [publication] declares, or `null` if
     * it declares none, which means `jar`.
     */
    private fun packagingOf(module: String, publication: String): String? =
        pomOf(module, publication).getElementsByTagName("packaging").item(0)?.textContent

    private fun pomOf(module: String, publication: String): Document {
        val pom = file("$module/build/publications/$publication/pom-default.xml")
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pom)
    }

    private fun implSbom(): JsonNode {
        val directory = "staged/io/spine/test/spine-impl/$moduleVersion"
        return file("$directory/spine-impl-$moduleVersion.spdx.json").readJson()
    }

    private fun stagedFiles(directory: String): List<String> =
        file("staged/$directory").listFiles()?.map { it.name }.orEmpty()

    /**
     * Writes a Maven repository with the external dependencies of the fixture.
     *
     * `lib` takes its license from its parent POM, so that an SBOM naming it
     * shows that the POM was built with its parent resolved. Both POMs configure
     * the same plugins, as real POMs do, so building the effective one merges
     * plugin configuration — once through the plugin management of the parent,
     * and once through inheritance.
     *
     * `outer` depends on `inner`, so that an artifact can bundle a library it
     * resolves as a dependency of another one.
     */
    private fun writeRepository() {
        pom(
            groupId = "com.example",
            artifactId = "parent",
            version = "1.0",
            body = listOf(parentHead, parentBuild).joinToString("\n")
        )
        library(artifactId = "lib", body = listOf(libParent, libBuild).joinToString("\n"))
        library(artifactId = "testlib")
        library(artifactId = "annotations")
        library(artifactId = "inner")
        library(artifactId = "outer", body = outerDependencies)
    }

    private fun library(artifactId: String, body: String = "") {
        pom(groupId = "com.example", artifactId = artifactId, version = "1.0", body = body)
        val jar = file("repository/com/example/$artifactId/1.0/$artifactId-1.0.jar")
        JarOutputStream(jar.outputStream(), Manifest()).use { }
    }

    private fun pom(groupId: String, artifactId: String, version: String, body: String) {
        val path = "repository/${groupId.replace('.', '/')}/$artifactId/$version"
        val lines = listOf(
            """<?xml version="1.0" encoding="UTF-8"?>""",
            """<project xmlns="http://maven.apache.org/POM/4.0.0">""",
            "  <modelVersion>4.0.0</modelVersion>",
            "  <groupId>$groupId</groupId>",
            "  <artifactId>$artifactId</artifactId>",
            "  <version>$version</version>",
        ) + body.lines().filter { it.isNotBlank() }.map { "  $it" } + "</project>"
        write("$path/$artifactId-$version.pom", lines.joinToString("\n", postfix = "\n"))
    }

    private fun writeBuild() {
        write(
            "settings.gradle.kts",
            """
            rootProject.name = "sbom-sample"
            include(
                "api", "impl", "bundled", "plugin", "kmp", "twin", "consumer",
                "thin", "fat", "uber", "archive", "osgi",
            )
            """.trimIndent()
        )
        // The standard library would be resolved from Maven Central otherwise,
        // which an offline build cannot reach.
        write("gradle.properties", "kotlin.stdlib.default.dependency=false\n")
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
                version = "$moduleVersion"
                repositories {
                    maven { url = uri("${'$'}{rootDir}/repository") }
                }
            }

            subprojects {
                apply(plugin = "maven-publish")

                // `javadocJar` of `spinePublishing` is wired to Dokka, which
                // this fixture does not apply.
                tasks.register("dokkaGeneratePublicationJavadoc")
            }

            spinePublishing {
                modules = setOf("api", "impl", "consumer")
                modulesWithCustomPublishing = setOf(
                    "plugin", "kmp", "twin", "thin", "fat", "uber", "archive", "osgi",
                )
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

            // The SBOMs are added once all projects are evaluated, so report afterwards.
            gradle.taskGraph.whenReady {
                subprojects {
                    the<PublishingExtension>().publications
                        .withType<MavenPublication>()
                        .forEach { publication ->
                            val sboms = publication.artifacts.count { it.extension == "spdx.json" }
                            println("$reportPrefix${'$'}path ${'$'}{publication.name} ${'$'}sboms")
                        }
                }
            }
            """.trimIndent()
        )
        write("api/build.gradle.kts", "plugins { `java-library` }\n")
        javaSource(module = "api", packageName = "sample.api", declaration = "class Api {}")
        write(
            "impl/build.gradle.kts",
            """
            plugins { `java-library` }

            dependencies {
                implementation(project(":api"))
                implementation(project(":bundled"))
                implementation("com.example:lib:1.0")
                compileOnly("com.example:annotations:1.0")
                testImplementation("com.example:testlib:1.0")
            }
            """.trimIndent()
        )
        javaSource(module = "impl", packageName = "sample.impl", declaration = "class Impl {}")
        write("bundled/build.gradle.kts", "plugins { `java-library` }\n")
        javaSource(
            module = "bundled",
            packageName = "sample.bundled",
            declaration = "class Bundled {}"
        )
        write(
            "plugin/build.gradle.kts",
            """
            plugins { `java-gradle-plugin` }

            gradlePlugin {
                plugins {
                    create("sample") {
                        id = "io.spine.test.sample"
                        implementationClass = "sample.plugin.SamplePlugin"
                    }
                }
            }
            """.trimIndent()
        )
        javaSource(
            module = "plugin",
            packageName = "sample.plugin",
            declaration = """
                public class SamplePlugin implements org.gradle.api.Plugin<org.gradle.api.Project> {
                    @Override public void apply(org.gradle.api.Project project) {}
                }
                """.trimIndent()
        )
        write(
            "kmp/build.gradle.kts",
            """
            import io.spine.gradle.publish.spinePublishing

            plugins { kotlin("multiplatform") }

            kotlin {
                jvm("desktop")
                sourceSets.getByName("desktopMain").dependencies {
                    implementation(project(":api"))
                    implementation("com.example:lib:1.0")
                }
            }

            spinePublishing {
                customPublishing = true
                destinations = emptySet()
            }
            """.trimIndent()
        )
        write(
            "twin/build.gradle.kts",
            """
            plugins {
                `java-library`
                `maven-publish`
            }

            publishing {
                publications {
                    create<MavenPublication>("main") {
                        from(components["java"])
                        artifactId = "twin-main"
                    }
                    create<MavenPublication>("extra") {
                        from(components["java"])
                        artifactId = "twin-extra"
                    }
                    create<MavenPublication>("dropped") {
                        from(components["java"])
                        artifactId = "twin-dropped"
                    }
                }
            }

            // As builds do with the `pluginMaven` publication `java-gradle-plugin` adds.
            afterEvaluate {
                publishing.publications.removeIf { it.name == "dropped" }
            }
            """.trimIndent()
        )
        write(
            "consumer/build.gradle.kts",
            """
            plugins { `java-library` }

            dependencies {
                implementation(project(":kmp"))
            }
            """.trimIndent()
        )
        writeThinJarModule()
        writeFatJarModule()
        writeUberJarModule()
        writeArchiveModule()
        writeOsgiModule()
    }

    /**
     * Writes the `thin` module, whose JAR packs the classes of `bundled`, and whose
     * hand-written POM declares what the `pomDependencies` configuration holds.
     */
    private fun writeThinJarModule() {
        val body = """
            dependencies {
                implementation(project(":bundled"))
                implementation("com.example:testlib:1.0")
                pomDependencies(project(":fat")) { isTransitive = false }
                pomDependencies("com.example:lib:1.0")
                packed(project(":bundled"))
            }

            tasks.jar {
                from(packed.elements.map { jars -> jars.map { zipTree(it) } }) {
                    exclude("META-INF/MANIFEST.MF")
                }
            }

            publishing {
                publications {
                    create<MavenPublication>("$pluginJar") {
                        artifact(tasks.jar)
                        declareInPom(
                            listOf("io.spine.test:spine-fat:$moduleVersion", "com.example:lib:1.0")
                        )
                        sbom {
                            dependencies(pomDependencies)
                            bundled(packed)
                        }
                    }
                }
            }
            """.trimIndent()
        writeScript(
            "thin",
            "$sbomImport\n\nplugins { `java-library` }",
            pomConfiguration,
            resolvableConfiguration("packed", transitive = false),
            pomDeclaration,
            body
        )
    }

    /**
     * Writes the `fat` module, whose fat JAR bundles its runtime classpath but for
     * what its Shadow filter excludes, and whose hand-written POM declares what
     * the `pomDependencies` configuration holds.
     */
    private fun writeFatJarModule() {
        val body = """
            dependencies {
                implementation(project(":bundled"))
                implementation("com.example:outer:1.0")
                implementation("com.example:annotations:1.0")
                pomDependencies("com.example:outer:1.0") {
                    exclude(group = "com.example", module = "inner")
                }
            }

            tasks.shadowJar {
                dependencies {
                    exclude(dependency("com.example:outer"))
                    exclude(dependency("com.example:annotations"))
                }
            }

            publishing {
                publications {
                    create<MavenPublication>("$fatJar") {
                        artifact(tasks.shadowJar)
                        declareInPom(
                            listOf("com.example:outer:1.0"),
                            exclusions = mapOf("com.example:outer" to "com.example:inner")
                        )
                        sbom {
                            dependencies(pomDependencies)
                            bundled(tasks.shadowJar)
                        }
                    }
                }
            }
            """.trimIndent()
        writeScript("fat", shadowModuleHead, pomConfiguration, pomDeclaration, body)
    }

    /**
     * Writes the `uber` module, whose fat JAR bundles its whole runtime classpath,
     * and whose POM declares no dependencies.
     *
     * As in `uber-jar-module`, the fat JAR has no classifier, taking the name of the JAR
     * of the module, whose task is disabled. So the fat JAR is the main artifact.
     */
    private fun writeUberJarModule() {
        val body = """
            dependencies {
                implementation(project(":bundled"))
                implementation("com.example:testlib:1.0")
            }

            tasks.jar {
                enabled = false
            }

            tasks.shadowJar {
                archiveClassifier.set("")
            }

            publishing {
                publications {
                    create<MavenPublication>("$fatJar") {
                        artifact(tasks.shadowJar)
                        sbom { bundled(tasks.shadowJar) }
                    }
                }
            }
            """.trimIndent()
        writeScript("uber", shadowModuleHead, body)
    }

    /**
     * Writes the `archive` module, which publishes a ZIP archive rather than a JAR.
     */
    private fun writeArchiveModule() {
        val body = """
            val zip = tasks.register<Zip>("zip") {
                from("build.gradle.kts")
            }

            publishing {
                publications {
                    create<MavenPublication>("$archive") {
                        artifact(zip)
                    }
                }
            }
            """.trimIndent()
        writeScript("archive", "plugins { `java-library` }", body)
    }

    /**
     * Writes the `osgi` module, which publishes its JAR as an OSGi bundle, setting
     * the `bundle` packaging of the POM.
     */
    private fun writeOsgiModule() {
        val body = """
            publishing {
                publications {
                    create<MavenPublication>("$osgiBundle") {
                        artifact(tasks.jar)
                        pom.packaging = "bundle"
                    }
                }
            }
            """.trimIndent()
        writeScript("osgi", "plugins { `java-library` }", body)
    }

    /**
     * Writes the build script of the given [module], made of the given [parts].
     */
    private fun writeScript(module: String, vararg parts: String) {
        write("$module/build.gradle.kts", parts.joinToString("\n\n"))
    }

    private fun javaSource(module: String, packageName: String, declaration: String) {
        val className = declaration.substringAfter("class ").substringBefore(' ')
        val path = "$module/src/main/java/${packageName.replace('.', '/')}/$className.java"
        write(path, "package $packageName;\n\n$declaration\n")
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
     *
     * Each entry after the first starts a line of the generated script, so each
     * is prefixed with the indentation of the template it is inserted into, which
     * `trimIndent()` then removes along with the rest.
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

        /** The version of the modules of the fixture. */
        const val moduleVersion = "1.0.0"

        const val publishTask = "publishAllPublicationsToStageRepository"

        /** Marks the lines in which the fixture reports the SBOMs of each publication. */
        const val reportPrefix = "SBOM-REPORT "

        /** The publication of a thin JAR with a hand-written POM. */
        const val pluginJar = "pluginJar"

        /** The publication of a fat JAR. */
        const val fatJar = "fatJar"

        /** The publication of a ZIP archive. */
        const val archive = "archive"

        /** The publication of a JAR as an OSGi bundle. */
        const val osgiBundle = "osgiBundle"

        /** Returns the name of the task writing the POM of the given [publication]. */
        fun pomTaskOf(publication: String): String =
            "generatePomFileFor${publication.replaceFirstChar { it.uppercase() }}Publication"

        /** The dependency of `outer` on `inner`. */
        val outerDependencies = """
            <dependencies>
              <dependency>
                <groupId>com.example</groupId>
                <artifactId>inner</artifactId>
                <version>1.0</version>
              </dependency>
            </dependencies>
            """.trimIndent()

        /** Imports the function describing the SBOM of a publication. */
        const val sbomImport = "import io.spine.gradle.publish.sbom"

        /** The start of the build script of a module publishing a fat JAR. */
        val shadowModuleHead = """
            $sbomImport

            plugins {
                `java-library`
                id("com.gradleup.shadow")
            }
            """.trimIndent()

        /**
         * Declares the `pomDependencies` configuration in a build script, holding what
         * the POM of a publication declares.
         */
        val pomConfiguration = resolvableConfiguration("pomDependencies")

        /**
         * Declares `declareInPom` in a build script: the function writing dependencies
         * into the POM of a publication by hand.
         */
        val pomDeclaration = """
            /**
             * Declares the given [dependencies] in the POM of this publication, each as
             * `group:artifactId:version`, in the `runtime` scope. A dependency whose
             * `group:artifactId` is a key of [exclusions] excludes the module it maps to.
             */
            fun MavenPublication.declareInPom(
                dependencies: List<String>,
                exclusions: Map<String, String> = emptyMap()
            ) {
                fun groovy.util.Node.identify(module: String) {
                    val (group, name) = module.split(':')
                    appendNode("groupId", group)
                    appendNode("artifactId", name)
                }
                pom.withXml {
                    val declared = asNode().appendNode("dependencies")
                    dependencies.forEach { coordinates ->
                        val module = coordinates.substringBeforeLast(':')
                        declared.appendNode("dependency").apply {
                            identify(module)
                            appendNode("version", coordinates.substringAfterLast(':'))
                            appendNode("scope", "runtime")
                            exclusions[module]?.let {
                                appendNode("exclusions").appendNode("exclusion").identify(it)
                            }
                        }
                    }
                }
            }
            """.trimIndent()

        /**
         * Returns the declaration of a configuration with the given [name], which resolves
         * the runtime variants of what it holds, as `runtimeClasspath` does.
         */
        fun resolvableConfiguration(name: String, transitive: Boolean = true): String =
            """
            val $name = configurations.create("$name") {
                isCanBeConsumed = false
                isTransitive = $transitive
                attributes {
                    attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
                    attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
                    attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
                    attribute(
                        LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                        objects.named(LibraryElements.JAR)
                    )
                }
            }
            """.trimIndent()

        /** The packaging and the license of the parent POM of `lib`. */
        val parentHead = """
            <packaging>pom</packaging>
            <licenses>
              <license>
                <name>The MIT License</name>
                <url>http://www.opensource.org/licenses/mit-license.php</url>
              </license>
            </licenses>
            """.trimIndent()

        /** The plugin configuration of the parent POM of `lib`. */
        val parentBuild = """
            <build>
              <pluginManagement>
                <plugins>
                  <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <configuration>
                      <release>8</release>
                    </configuration>
                  </plugin>
                </plugins>
              </pluginManagement>
              <plugins>
                <plugin>
                  <groupId>org.apache.maven.plugins</groupId>
                  <artifactId>maven-jar-plugin</artifactId>
                  <configuration>
                    <skipIfEmpty>true</skipIfEmpty>
                  </configuration>
                </plugin>
              </plugins>
            </build>
            """.trimIndent()

        /** Makes `com.example:parent` the parent POM of `lib`. */
        val libParent = """
            <parent>
              <groupId>com.example</groupId>
              <artifactId>parent</artifactId>
              <version>1.0</version>
            </parent>
            """.trimIndent()

        /** The plugin configuration of `lib`, merged with that of its parent. */
        val libBuild = """
            <build>
              <plugins>
                <plugin>
                  <groupId>org.apache.maven.plugins</groupId>
                  <artifactId>maven-compiler-plugin</artifactId>
                  <configuration>
                    <parameters>true</parameters>
                  </configuration>
                </plugin>
                <plugin>
                  <groupId>org.apache.maven.plugins</groupId>
                  <artifactId>maven-jar-plugin</artifactId>
                  <configuration>
                    <forceCreation>true</forceCreation>
                  </configuration>
                </plugin>
              </plugins>
            </build>
            """.trimIndent()
    }
}

private fun File.readJson(): JsonNode = ObjectMapper().readTree(this)

private fun JsonNode.packageNames(): List<String> =
    this[SpdxField.packages].shouldNotBeNull().map { it[SpdxField.name].asText() }

private fun JsonNode.packageNamed(name: String): JsonNode =
    this[SpdxField.packages].shouldNotBeNull()
        .firstOrNull { it[SpdxField.name].asText() == name }
        .shouldNotBeNull()

private fun JsonNode.purl(): String? =
    this[SpdxField.externalRefs]
        ?.firstOrNull { it[SpdxField.referenceType].asText() == "purl" }
        ?.get(SpdxField.referenceLocator)
        ?.asText()

/** An SPDX relationship, `element` being of the given `type` to the `related` element. */
private data class Relationship(val element: String, val type: String, val related: String)

private fun JsonNode.relationships(): List<Relationship> =
    this[SpdxField.relationships].shouldNotBeNull().map {
        Relationship(
            element = it[SpdxField.spdxElementId].asText(),
            type = it[SpdxField.relationshipType].asText(),
            related = it[SpdxField.relatedSpdxElement].asText()
        )
    }

private fun JsonNode.packagesById(): Map<String, JsonNode> =
    this[SpdxField.packages].shouldNotBeNull().associateBy { it[SpdxField.spdxId].asText() }

/** Returns the SPDX ID of the package of the artifact, which the document describes. */
private fun JsonNode.describedId(): String =
    relationships().single { it.element == DOCUMENT_ID && it.type == DESCRIBES }.related

/** Returns the names of the packages to which the artifact is related as [type] says. */
private fun JsonNode.relatedToArtifact(type: String): List<String> =
    relatedTo(describedId(), type)

/** Returns the names of the packages to which [pkg] is related as [type] says. */
private fun JsonNode.relatedTo(pkg: JsonNode, type: String): List<String> =
    relatedTo(pkg[SpdxField.spdxId].asText(), type)

private fun JsonNode.relatedTo(id: String, type: String): List<String> {
    val packages = packagesById()
    return relationships()
        .filter { it.element == id && it.type == type }
        .map { packages.getValue(it.related)[SpdxField.name].asText() }
}

/** Returns the names of the packages any package of the document depends on. */
private fun JsonNode.dependencyTargets(): List<String> {
    val packages = packagesById()
    return relationships()
        .filter { it.type == DEPENDS_ON }
        .map { packages.getValue(it.related)[SpdxField.name].asText() }
}

/**
 * Asserts that each relationship of this document relates its elements, and relates
 * them once; that each license text a package refers to is in the document; and that
 * each package is found from the package of the artifact.
 */
private fun JsonNode.shouldBeComplete() {
    val packages = packagesById()
    val relationships = relationships()
    relationships.flatMap { listOf(it.element, it.related) }
        .filterNot { it == DOCUMENT_ID }
        .forEach { packages.keys shouldContain it }
    relationships.distinct() shouldContainExactly relationships
    val licenses = this[SpdxField.hasExtractedLicensingInfos]
        ?.map { it[SpdxField.licenseId].asText() }
        .orEmpty()
    packages.values
        .flatMap(::licenseReferencesOf)
        .forEach { licenses shouldContain it }
    reachableFrom(describedId(), relationships) shouldContainExactlyInAnyOrder packages.keys
}

private fun reachableFrom(id: String, relationships: List<Relationship>): Set<String> {
    val reached = mutableSetOf(id)
    val queue = ArrayDeque(listOf(id))
    while (queue.isNotEmpty()) {
        val element = queue.removeFirst()
        relationships.filter { it.element == element }
            .map { it.related }
            .filter(reached::add)
            .forEach(queue::add)
    }
    return reached
}

/** Returns the text of the child element of this one with the given [tag]. */
private fun Element.childText(tag: String): String {
    val children = childNodes
    return (0 until children.length)
        .map { children.item(it) }
        .first { it is Element && it.tagName == tag }
        .textContent
}
