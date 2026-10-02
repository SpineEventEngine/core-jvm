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

import LicenseSettings
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathFactory
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.w3c.dom.Document

/**
 * Verifies the POMs of a module with custom publishing that applies
 * `java-gradle-plugin`, against a real multi-module build run via Gradle TestKit.
 *
 * Gradle's `java-gradle-plugin` creates its publications in an `afterEvaluate` action
 * of its own: `pluginMaven` for the plugin JAR, and a marker for each declared plugin.
 * Whether that happens before or after the handler is applied depends on how
 * the module is declared as one with custom publishing, so each test runs for
 * every [Declaration].
 *
 * The fixture also publishes a module in the standard way, so that the project-wide
 * attributes in the POM of a custom publication are compared with those of
 * a standard publication, rather than with a copy of the expected values.
 */
@DisplayName("`CustomPublicationHandler` should")
internal class CustomPublicationHandlerIgTest {

    @TempDir
    lateinit var projectDir: File

    @ParameterizedTest
    @EnumSource(Declaration::class)
    fun `publish 'pluginMaven' with the POM of a standard publication`(
        declaration: Declaration
    ) {
        build(declaration)
        val standard = pom(library, standardPublication)
        val plugin = pom(pluginModule, pluginPublication)

        plugin["/project/groupId"] shouldBe group
        plugin["/project/artifactId"] shouldBe pluginArtifact
        plugin["/project/version"] shouldBe version
        plugin["/project/description"] shouldBe moduleDescription
        plugin shouldDescribeTheProjectLike standard
    }

    /**
     * A marker is resolved by the ID of the plugin it points to, and describes
     * that plugin. Both come from the plugin declaration and must not be replaced
     * with the attributes of the module.
     */
    @ParameterizedTest
    @EnumSource(Declaration::class)
    fun `keep the identity of a plugin marker, adding the project-wide attributes`(
        declaration: Declaration
    ) {
        build(declaration)
        val standard = pom(library, standardPublication)
        val marker = pom(pluginModule, markerPublication)

        marker["/project/groupId"] shouldBe pluginId
        marker["/project/artifactId"] shouldBe "$pluginId.gradle.plugin"
        marker["/project/name"] shouldBe pluginName
        marker["/project/description"] shouldBe pluginDescription
        marker["/project/dependencies/dependency/artifactId"] shouldBe pluginArtifact
        marker shouldDescribeTheProjectLike standard
    }

    /**
     * The ways to declare a module as one with custom publishing.
     *
     * Both the handler and `java-gradle-plugin` act in `afterEvaluate` of
     * the module, which runs the actions in the order they were added.
     *
     * @property inRoot Tells if the root project lists the module
     *   in `modulesWithCustomPublishing`.
     * @property inModule Tells if the module opens `spinePublishing`
     *   with `customPublishing = true`.
     */
    enum class Declaration(val inRoot: Boolean, val inModule: Boolean) {

        /**
         * The root project lists the module in `modulesWithCustomPublishing`.
         *
         * The root project is evaluated first, so the handler is applied
         * before `java-gradle-plugin` creates its publications.
         */
        IN_ROOT(inRoot = true, inModule = false),

        /**
         * The module opens `spinePublishing` with `customPublishing = true`
         * after applying `java-gradle-plugin`, so the handler is applied
         * after the publications are created.
         */
        IN_MODULE(inRoot = false, inModule = true),

        /**
         * Both of the above, like a module that applies `uber-jar-module`
         * and is listed by the root project.
         *
         * The module configures the same handler for the second time, which
         * must not add the project-wide attributes to a publication twice.
         */
        IN_BOTH(inRoot = true, inModule = true)
    }

    private fun build(declaration: Declaration) {
        writeSettings()
        writeRootScript(declaration)
        writeLibraryScript()
        writePluginScript(declaration)
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(
                pomTask(library, standardPublication),
                pomTask(pluginModule, pluginPublication),
                pomTask(pluginModule, markerPublication),
                "--stacktrace"
            )
            .build()
    }

    private fun writeSettings() {
        file("settings.gradle.kts").writeText(
            """
            rootProject.name = "plugin-sample"
            include("$library", "$pluginModule")
            """.trimIndent()
        )
    }

    private fun writeRootScript(declaration: Declaration) {
        val customModules = if (declaration.inRoot) "setOf(\"$pluginModule\")" else "emptySet()"
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
                group = "$group"
                version = "$version"
            }

            spinePublishing {
                modules = setOf("$library")
                modulesWithCustomPublishing = $customModules
                toolArtifactPrefix = "$toolPrefix"
                destinations = emptySet()
            }
            """.trimIndent()
        )
    }

    private fun writeLibraryScript() {
        file("$library/build.gradle.kts").apply {
            parentFile.mkdirs()
            writeText(
                """
                plugins {
                    `java-library`
                }

                // `javadocJar` of `spinePublishing` is wired to Dokka, which
                // this fixture does not apply: only the POMs are under test.
                tasks.register("dokkaGeneratePublicationJavadoc")
                """.trimIndent()
            )
        }
    }

    private fun writePluginScript(declaration: Declaration) {
        val script = buildList {
            if (declaration.inModule) {
                add("import io.spine.gradle.publish.spinePublishing")
            }
            add(
                """
                plugins {
                    `java-gradle-plugin`
                    `maven-publish`
                }

                description = "$moduleDescription"

                gradlePlugin {
                    plugins {
                        create("$pluginDeclaration") {
                            id = "$pluginId"
                            implementationClass = "io.spine.sample.SamplePlugin"
                            displayName = "$pluginName"
                            description = "$pluginDescription"
                        }
                    }
                }
                """.trimIndent()
            )
            if (declaration.inModule) {
                add(
                    """
                    spinePublishing {
                        customPublishing = true
                        toolArtifactPrefix = "$toolPrefix"
                        destinations = emptySet()
                    }
                    """.trimIndent()
                )
            }
        }
        file("$pluginModule/build.gradle.kts").apply {
            parentFile.mkdirs()
            writeText(script.joinToString("\n\n"))
        }
    }

    private fun pom(module: String, publication: String): Pom =
        Pom(file("$module/build/publications/$publication/pom-default.xml"))

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
        const val version = "1.0.0"
        const val toolPrefix = "sample-"

        /**
         * The module published in the standard way, to compare the POMs with.
         */
        const val library = "library"

        /**
         * The module applying `java-gradle-plugin`, published in a custom way.
         */
        const val pluginModule = "plugin"
        const val moduleDescription = "The module publishing the sample plugin."

        /**
         * The artifact ID expected for the plugin JAR: the name of the module
         * with the prefix of a tool module.
         */
        const val pluginArtifact = "$toolPrefix$pluginModule"

        const val pluginDeclaration = "sample"
        const val pluginId = "io.spine.sample"
        const val pluginName = "Sample Plugin"
        const val pluginDescription = "Does nothing, being a sample."

        /*
         * The names of the publications, as given by the code that creates them.
         */
        const val standardPublication = StandardJavaPublicationHandler.PUBLICATION_NAME
        const val pluginPublication = "pluginMaven"
        const val markerPublication = "${pluginDeclaration}PluginMarkerMaven"

        const val licenseNamePath = "/project/licenses/license/name"

        /**
         * The POM elements that describe the project as a whole,
         * and so are the same for every publication of the project.
         *
         * The number of licenses is compared as well, so that a license
         * added twice to the same POM does not pass unnoticed.
         */
        val projectWideAttributes = listOf(
            "/project/inceptionYear",
            "count(/project/licenses/license)",
            licenseNamePath,
            "/project/licenses/license/url",
            "/project/licenses/license/distribution",
            "/project/scm/url",
            "/project/scm/connection",
            "/project/scm/developerConnection",
        )

        fun pomTask(module: String, publication: String): String {
            val name = publication.replaceFirstChar { it.uppercase() }
            return ":$module:generatePomFileFor${name}Publication"
        }
    }

    /**
     * Asserts that this POM describes the project in the same way as
     * the [standard] one does.
     *
     * The license is also checked against [LicenseSettings], since two POMs
     * with no license at all would describe the project in the same way, too.
     */
    private infix fun Pom.shouldDescribeTheProjectLike(standard: Pom) {
        standard[licenseNamePath] shouldBe LicenseSettings.name
        projectWideAttributes.forEach { path ->
            withClue(path) {
                this[path] shouldBe standard[path]
            }
        }
    }
}

/**
 * A POM file generated by the fixture, queried with XPath.
 *
 * The document is parsed without namespace awareness, so that the paths name
 * the elements without the POM namespace prefix.
 */
private class Pom(file: File) {

    private val document: Document =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)

    /**
     * Evaluates the given XPath [expression] against the POM, returning
     * an empty string if the expression selects nothing.
     */
    operator fun get(expression: String): String =
        XPathFactory.newInstance().newXPath().evaluate(expression, document).trim()
}
