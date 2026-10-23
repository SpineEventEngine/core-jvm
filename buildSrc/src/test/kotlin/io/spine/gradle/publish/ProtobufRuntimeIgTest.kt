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
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.spine.dependency.lib.Protobuf
import java.io.File
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import javax.xml.parsers.DocumentBuilderFactory
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import org.w3c.dom.Element

/**
 * Verifies [unbundleProtobufRuntime] in a real build of fat JARs, run via Gradle TestKit.
 *
 * The fixture is built once for the whole class, and each case inspects what
 * the build left behind. The build runs with `--offline`, resolving from a local
 * `file:` repository, which holds stand-ins for the Protobuf runtime libraries.
 * `protobuf-java-util` depends on `com.example:gson`, as the genuine one depends
 * on Gson. The build also runs with the configuration cache.
 *
 * All the modules of the fixture apply Shadow and depend on `com.example:lib`.
 * The fixture has:
 *  - `fat` — a module depending on the Protobuf runtime. It publishes its fat JAR
 *    in the `cliFatJar` publication, with `artifact(...)`, as the Compiler CLI does,
 *    and its Java component, which carries the shadowed variant, in the `library`
 *    publication;
 *  - `component` — a module whose `library` publication of its Java component is
 *    given to the function, so that the POM the function adds to already has
 *    the `dependencies` element Gradle writes;
 *  - `twin` — a module depending on the Protobuf runtime. It publishes its fat JAR
 *    in two publications: `fatJar`, as `uber-jar-module` does, and `extra`, whose POM
 *    gets a `dependencies` element written by hand before the function adds to it.
 *    The function is called once for each publication.
 */
@DisplayName("`unbundleProtobufRuntime` should")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class ProtobufRuntimeIgTest {

    private lateinit var projectDir: File

    @BeforeAll
    fun buildFixture(@TempDir dir: File) {
        projectDir = dir
        writeRepository()
        writeBuild()
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(
                ":fat:shadowJar",
                ":fat:${pomTaskOf(cliFatJar)}",
                ":fat:generateMetadataFileForLibraryPublication",
                ":fat:$listIncludedTask",
                ":component:${pomTaskOf(library)}",
                ":twin:${pomTaskOf(defaultFatJar)}",
                ":twin:${pomTaskOf(extra)}",
                "--offline",
                "--configuration-cache",
                "--stacktrace",
            )
            .build()
    }

    @Test
    fun `leave the Protobuf runtime out of the fat JAR`() {
        val entries = fatJarEntries()

        entries.filter { it.startsWith("com/google/protobuf/") }.shouldBeEmpty()
        entries shouldNotContain "META-INF/protobuf-kotlin.kotlin_module"
    }

    @Test
    fun `keep bundling the other dependencies, including those of the runtime`() {
        fatJarEntries() shouldContainAll listOf(
            "com/example/Lib.class",
            "com/example/gson/Gson.class",
        )
    }

    @Test
    fun `exclude the runtime by the dependency filter of the fat JAR`() {
        val included = file("fat/build/included.txt").readLines()

        included shouldContainExactlyInAnyOrder listOf("lib-1.0.jar", "gson-1.0.jar")
    }

    @Test
    fun `not name the runtime in the manifest of the fat JAR`() {
        JarFile(fatJarFile()).use { jar ->
            val manifest = jar.manifest.shouldNotBeNull()
            manifest.mainAttributes.getValue("Class-Path").shouldBeNull()
        }
    }

    @Test
    fun `declare the runtime in the POM of the fat JAR`() {
        val dependencies = pomDependencies("fat", cliFatJar)
        val artifactIds = dependencies.map { it.childText("artifactId") }

        artifactIds shouldContainExactlyInAnyOrder runtimeLibraries
        dependencies.forAll { it.shouldBeRuntimeLibrary() }
    }

    @Test
    fun `declare the runtime in the shadowed variant`() {
        val metadata = ObjectMapper().readTree(file("fat/build/publications/library/module.json"))
        val variants = metadata["variants"].shouldNotBeNull()
            .filter { it["name"]?.asText() == shadowedVariant }
        variants.shouldBeSingleton()
        val dependencies = variants.single()["dependencies"].shouldNotBeNull().toList()
        val modules = dependencies.map { it["module"]?.asText() }

        modules shouldContainExactlyInAnyOrder runtimeLibraries
        dependencies.forAll {
            it["group"]?.asText() shouldBe Protobuf.group
            it["version"]?.get("requires")?.asText() shouldBe Protobuf.version
            it.excludes() shouldContainExactly listOf("*" to "*")
        }
    }

    @Test
    fun `add to the dependencies which Gradle writes into the POM`() {
        // The shadowed variant of the Java component adds the runtime as optional.
        val dependencies = pomDependencies("component", library)
            .filter { it.childTextOrNull("optional") == null }
        val artifactIds = dependencies.map { it.childText("artifactId") }

        artifactIds shouldContainExactly listOf("lib") + runtimeLibraries
        dependencies.drop(1).forAll { it.shouldBeRuntimeLibrary() }
    }

    @Test
    fun `add to the dependencies written into the POM by hand`() {
        val dependencies = pomDependencies("twin", extra)
        val artifactIds = dependencies.map { it.childText("artifactId") }

        artifactIds shouldContainExactly listOf("lib") + runtimeLibraries
        dependencies.drop(1).forAll { it.shouldBeRuntimeLibrary() }
    }

    @Test
    fun `declare the runtime in each publication it is called for`() {
        val dependencies = pomDependencies("twin", defaultFatJar)
        val artifactIds = dependencies.map { it.childText("artifactId") }

        artifactIds shouldContainExactlyInAnyOrder runtimeLibraries
        dependencies.forAll { it.shouldBeRuntimeLibrary() }
    }

    private fun fatJarFile(): File = file("fat/build/libs/fat-$moduleVersion-all.jar")

    private fun fatJarEntries(): List<String> =
        JarFile(fatJarFile()).use { jar -> jar.entries().toList().map { it.name } }

    /**
     * Returns the `dependency` elements of the POM of the given [publication] of
     * the given [module], ensuring the POM has a single `dependencies` element.
     */
    private fun pomDependencies(module: String, publication: String): List<Element> {
        val pom = file("$module/build/publications/$publication/pom-default.xml")
        val project = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(pom)
            .documentElement
        val dependencies = project.childElements("dependencies")
        dependencies.shouldBeSingleton()
        return dependencies.single().childElements("dependency")
    }

    /**
     * Writes the local repository with the stand-ins for the Protobuf runtime
     * and the other libraries of the fixture.
     */
    private fun writeRepository() {
        library(
            Protobuf.javaLib,
            entries = listOf("com/google/protobuf/RuntimeVersion.class"),
        )
        library(
            Protobuf.javaUtil,
            entries = listOf("com/google/protobuf/util/JsonFormat.class"),
            dependencies = listOf(Protobuf.javaLib, gson),
        )
        library(
            Protobuf.kotlin,
            entries = listOf(
                "com/google/protobuf/kotlin/DslList.class",
                "META-INF/protobuf-kotlin.kotlin_module",
            ),
            dependencies = listOf(Protobuf.javaLib),
        )
        library(lib, entries = listOf("com/example/Lib.class"))
        library(gson, entries = listOf("com/example/gson/Gson.class"))
    }

    /**
     * Writes the POM and the JAR of the library with the given `group:name:version`
     * [coordinates] into the local repository.
     *
     * @param entries The paths of the empty entries of the JAR.
     * @param dependencies The `group:name:version` coordinates of the dependencies
     *   the POM declares.
     */
    private fun library(
        coordinates: String,
        entries: List<String>,
        dependencies: List<String> = emptyList(),
    ) {
        val (groupId, artifactId, version) = coordinates.split(':')
        val path = "repository/${groupId.replace('.', '/')}/$artifactId/$version"
        val declared = dependencies.joinToString("\n") { dependency ->
            val (depGroup, depArtifact, depVersion) = dependency.split(':')
            """
            |    <dependency>
            |      <groupId>$depGroup</groupId>
            |      <artifactId>$depArtifact</artifactId>
            |      <version>$depVersion</version>
            |    </dependency>
            """.trimMargin()
        }
        val dependenciesElement = if (dependencies.isEmpty()) "" else
            "  <dependencies>\n$declared\n  </dependencies>"
        write(
            "$path/$artifactId-$version.pom",
            """
            |<?xml version="1.0" encoding="UTF-8"?>
            |<project xmlns="http://maven.apache.org/POM/4.0.0">
            |  <modelVersion>4.0.0</modelVersion>
            |  <groupId>$groupId</groupId>
            |  <artifactId>$artifactId</artifactId>
            |  <version>$version</version>
            |$dependenciesElement
            |</project>
            |""".trimMargin()
        )
        val jar = file("$path/$artifactId-$version.jar")
        JarOutputStream(jar.outputStream(), Manifest()).use { out ->
            entries.forEach {
                out.putNextEntry(ZipEntry(it))
                out.closeEntry()
            }
        }
    }

    private fun writeBuild() {
        write(
            "settings.gradle.kts",
            """
            rootProject.name = "protobuf-runtime-sample"
            include("fat", "component", "twin")
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

            allprojects {
                group = "io.spine.test"
                version = "$moduleVersion"
                repositories {
                    maven { url = uri("${'$'}{rootDir}/repository") }
                }
            }
            """.trimIndent()
        )
        writeFatModule()
        writeComponentModule()
        writeTwinModule()
    }

    private fun writeFatModule() {
        val body = """
            |publishing {
            |    publications {
            |        create<MavenPublication>("$cliFatJar") {
            |            artifactId = "fat-all"
            |            artifact(tasks.shadowJar)
            |        }
            |        create<MavenPublication>("$library") {
            |            from(components["java"])
            |        }
            |    }
            |}
            |
            |unbundleProtobufRuntime(publication = "$cliFatJar")
            |
            |// Lists the files of the dependencies the fat JAR bundles, which the SBOM
            |// of a fat JAR describes as bundled.
            |tasks.register("$listIncludedTask") {
            |    val included = files(tasks.shadowJar.map { it.includedDependencies })
            |    val listing = layout.buildDirectory.file("included.txt")
            |    inputs.files(included)
            |    outputs.file(listing)
            |    doLast {
            |        listing.get().asFile.writeText(included.files.joinToString("\n") { it.name })
            |    }
            |}
            |""".trimMargin()
        writeModule("fat", body)
    }

    private fun writeComponentModule() {
        val body = """
            |publishing {
            |    publications {
            |        create<MavenPublication>("$library") {
            |            from(components["java"])
            |        }
            |    }
            |}
            |
            |unbundleProtobufRuntime(publication = "$library")
            |""".trimMargin()
        writeModule("component", body, dependOnRuntime = false)
    }

    private fun writeTwinModule() {
        val body = """
            |publishing {
            |    publications {
            |        create<MavenPublication>("$defaultFatJar") {
            |            artifactId = "twin-all"
            |            artifact(tasks.shadowJar)
            |        }
            |        create<MavenPublication>("$extra") {
            |            artifactId = "twin-extra"
            |            artifact(tasks.shadowJar)
            |            pom.withXml {
            |                val dependency = asNode()
            |                    .appendNode("dependencies")
            |                    .appendNode("dependency")
            |                dependency.appendNode("groupId", "com.example")
            |                dependency.appendNode("artifactId", "lib")
            |                dependency.appendNode("version", "1.0")
            |            }
            |        }
            |    }
            |}
            |
            |unbundleProtobufRuntime()
            |unbundleProtobufRuntime(publication = "$extra")
            |""".trimMargin()
        writeModule("twin", body)
    }

    /**
     * Writes the build script of the given [module], which applies Shadow,
     * depends on `com.example:lib` and, unless told otherwise, on the Protobuf runtime,
     * and ends with the given [body].
     */
    private fun writeModule(module: String, body: String, dependOnRuntime: Boolean = true) {
        val libs = if (dependOnRuntime) Protobuf.libs else emptyList()
        val runtime = libs.joinToString("\n") { "|    implementation(\"$it\")" }
        val head = """
            |import io.spine.gradle.publish.unbundleProtobufRuntime
            |
            |plugins {
            |    `java-library`
            |    `maven-publish`
            |    id("com.gradleup.shadow")
            |}
            |
            |dependencies {
            |    implementation("$lib")
            $runtime
            |}
            |""".trimMargin()
        write("$module/build.gradle.kts", "$head\n$body")
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

        /** The name of a publication of a fat JAR, other than the default one. */
        const val cliFatJar = "cliFatJar"

        /** The name of the publication of a fat JAR, which the function uses by default. */
        const val defaultFatJar = "fatJar"

        /** The name of the publication of a fat JAR whose POM declares a dependency. */
        const val extra = "extra"

        /** The name of a publication of a Java component. */
        const val library = "library"

        /** The name of the variant of the Java component published by the Shadow plugin. */
        const val shadowedVariant = "shadowRuntimeElements"

        /** The name of the task listing the dependencies the fat JAR bundles. */
        const val listIncludedTask = "listIncluded"

        /** The library the modules of the fixture bundle. */
        const val lib = "com.example:lib:1.0"

        /** The library `protobuf-java-util` depends on, as the genuine one depends on Gson. */
        const val gson = "com.example:gson:1.0"

        /** The artifact IDs of the Protobuf runtime libraries. */
        val runtimeLibraries: List<String> =
            Protobuf.libs.map { it.substringBeforeLast(':').substringAfter(':') }

        /** Returns the name of the task writing the POM of the given [publication]. */
        fun pomTaskOf(publication: String): String =
            "generatePomFileFor${publication.replaceFirstChar { it.uppercase() }}Publication"
    }
}

/**
 * Asserts that this `dependency` element of a POM declares a Protobuf runtime
 * library in the `runtime` scope and without transitive dependencies.
 */
private fun Element.shouldBeRuntimeLibrary() {
    childText("groupId") shouldBe Protobuf.group
    childText("version") shouldBe Protobuf.version
    childText("scope") shouldBe "runtime"
    val exclusions = childElements("exclusions")
    exclusions.shouldBeSingleton()
    val exclusion = exclusions.single().childElements("exclusion")
    exclusion.shouldBeSingleton()
    exclusion.single().childText("groupId") shouldBe "*"
    exclusion.single().childText("artifactId") shouldBe "*"
}

/** Returns the child elements of this one with the given [tag]. */
private fun Element.childElements(tag: String): List<Element> {
    val children = childNodes
    return (0 until children.length)
        .map { children.item(it) }
        .filterIsInstance<Element>()
        .filter { it.tagName == tag }
}

/** Returns the text of the child element of this one with the given [tag]. */
private fun Element.childText(tag: String): String =
    childTextOrNull(tag).shouldNotBeNull()

/**
 * Returns the text of the child element of this one with the given [tag],
 * or `null` if there is none.
 */
private fun Element.childTextOrNull(tag: String): String? =
    childElements(tag).firstOrNull()?.textContent

/**
 * Returns the `group` and `module` of each exclusion of this dependency of
 * the Gradle module metadata.
 */
private fun JsonNode.excludes(): List<Pair<String?, String?>> =
    this["excludes"]?.map { it["group"]?.asText() to it["module"]?.asText() }.orEmpty()
