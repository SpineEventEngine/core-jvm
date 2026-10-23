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

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.kotest.assertions.withClue
import io.kotest.matchers.file.shouldExist
import io.kotest.matchers.file.shouldNotExist
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Verifies when the credentials of a publishing destination are obtained,
 * against a real build run via Gradle TestKit.
 *
 * Obtaining the credentials may start an external process — `gcloud` for
 * [CloudArtifactRegistry]. Gradle deprecates starting one at configuration time,
 * so the credentials must be obtained only by a build that publishes, and only
 * when it is about to.
 *
 * Each [Destination] of the fixture obtains its own credentials with a function
 * that records each call in a file, and points at its own [ProbeRepository],
 * an HTTP server that accepts uploads only with credentials.
 */
@DisplayName("Credentials of a publishing destination should")
internal class PublishingCredentialsIgTest {

    @TempDir
    lateinit var projectDir: File

    private val destinations = listOf(
        Destination(name = "Probe", username = "publisher", password = "secret"),
        Destination(name = "Other", username = "deployer", password = "token"),
    )

    @BeforeEach
    fun createBuild() {
        file("settings.gradle.kts").writeText(
            """
            rootProject.name = "credentials-sample"
            include("lib")
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
            import io.spine.gradle.repo.Credentials
            import io.spine.gradle.repo.Repository

            allprojects {
                group = "io.spine.sample"
                version = "1.0.0"
            }

            subprojects {
                apply(plugin = "java-library")
                apply(plugin = "maven-publish")

                // `javadocJar` of `spinePublishing` is wired to Dokka, which
                // this fixture does not apply: the credentials are under test.
                tasks.register("dokkaGeneratePublicationJavadoc")

                // The probe repositories are served over plain HTTP.
                the<PublishingExtension>().repositories
                    .withType<MavenArtifactRepository>()
                    .configureEach { isAllowInsecureProtocol = true }
            }

            spinePublishing {
                modules = setOf("lib")
                destinations = setOf(
            ${destinations.joinToString(",\n") { it.declaration() }}
                )
            }
            """.trimIndent()
        )
        file("lib/src/main/java/io/spine/sample/Sample.java").apply {
            parentFile.mkdirs()
            writeText("package io.spine.sample;\n\npublic class Sample {}\n")
        }
    }

    @AfterEach
    fun stopRepositories() {
        destinations.forEach { it.repository.close() }
    }

    @Test
    fun `not be obtained by a build that does not publish`() {
        val stored = runGradle("help", "--configuration-cache")
        withClue("The run that stores the configuration cache entry") {
            stored.output shouldContain "Configuration cache entry stored"
        }
        val reused = runGradle("help", "--configuration-cache")
        withClue("The run that reuses the configuration cache entry") {
            reused.output shouldContain "Reusing configuration cache"
        }

        file(requestLog).shouldNotExist()
    }

    @Test
    fun `be sent with each upload to its own repository`() {
        runGradle(publishTask)

        shouldSendOwnCredentials()
    }

    /**
     * Gradle does not support explicit repository credentials with the configuration
     * cache, so a build that publishes with them falls back to running without it
     * instead of failing.
     */
    @Test
    fun `be sent with each upload when the configuration cache is requested`() {
        runGradle(publishTask, "--configuration-cache")

        shouldSendOwnCredentials()
    }

    /**
     * Gradle deprecates calling `Task.project` at execution time, which is
     * when the check obtains the credentials.
     */
    @Test
    fun `be checked by 'checkCredentials' without reaching the project through its task`() {
        val result = runGradle(checkTask, "--warning-mode", "all")

        result.output shouldNotContain "Invocation of Task.project at execution time"
        file(requestLog).shouldExist()
    }

    /**
     * Only the last destination lacks credentials, so a check that stops
     * at the first one cannot pass.
     */
    @Test
    fun `be required by 'checkCredentials' for every destination`() {
        val lacking = destinations.last().name
        val result = gradle(checkTask, "-P$withoutCredentials=$lacking").buildAndFail()

        result.output shouldContain "No valid credentials for repository `$lacking`"
    }

    /**
     * Asserts that each repository accepted uploads only with the credentials
     * of its own destination.
     *
     * Checking the log of credential requests here shows that the fixture writes it,
     * so the `help` case, which expects no log, cannot pass vacuously.
     */
    private fun shouldSendOwnCredentials() {
        file(requestLog).shouldExist()
        destinations.forEach {
            withClue(it.name) {
                it.repository.authorizations.toSet() shouldBe setOf(it.authorization)
            }
        }
    }

    private fun runGradle(vararg arguments: String): BuildResult = gradle(*arguments).build()

    private fun gradle(vararg arguments: String): GradleRunner =
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(*arguments, "--stacktrace")

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
     * A publishing destination of the fixture, served by its own [repository].
     */
    private class Destination(val name: String, val username: String, val password: String) {

        val repository = ProbeRepository()

        /**
         * The `Authorization` header that carries the credentials of this destination.
         */
        val authorization: String =
            "Basic " + Base64.getEncoder().encodeToString("$username:$password".toByteArray())

        /**
         * Declares this destination in the build script of the fixture.
         *
         * The lines are indented like those of [buildSrcClasspath], so that
         * the build script keeps its indentation once it is trimmed.
         */
        fun declaration(): String =
            """
            Repository(
                name = "$name",
                releases = "${repository.url}",
                snapshots = "${repository.url}",
                credentialValues = { project ->
                    project.rootDir.resolve("$requestLog").appendText("$name\n")
                    if (project.findProperty("$withoutCredentials") == "$name") null
                    else Credentials("$username", "$password")
                }
            )
            """.trimIndent().prependIndent("            ")
    }

    private companion object {

        /**
         * The file to which the credentials functions of the fixture append
         * a line each time they are called.
         */
        const val requestLog = "credential-requests.log"

        /**
         * The project property that names the destination whose credentials function
         * finds no credentials.
         */
        const val withoutCredentials = "withoutCredentials"

        /**
         * Publishes to every destination and checks their credentials.
         */
        const val publishTask = ":lib:publish"

        /**
         * Checks that every destination has credentials.
         */
        const val checkTask = ":lib:checkCredentials"
    }
}

/**
 * A Maven repository that accepts an upload only with credentials.
 * It is served over HTTP from the loopback interface.
 *
 * Gradle does not send the credentials until challenged, so the repository answers
 * an upload without them with `401`, asking for Basic authentication.
 * It has no artifacts to serve, so it answers any other request with `404`.
 */
private class ProbeRepository : AutoCloseable {

    private val server: HttpServer =
        HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)

    private val accepted = CopyOnWriteArrayList<String>()

    /**
     * The `Authorization` headers of the accepted uploads.
     */
    val authorizations: List<String>
        get() = accepted.toList()

    /**
     * The URL of this repository.
     *
     * The loopback address may be an IPv6 one, which [URI] encloses in square brackets.
     */
    val url: String
        get() {
            val address = server.address
            return URI("http", null, address.hostString, address.port, "/maven", null, null)
                .toString()
        }

    init {
        server.createContext("/") { exchange -> exchange.use { respond(it) } }
        server.start()
    }

    private fun respond(exchange: HttpExchange) {
        exchange.requestBody.readBytes()
        val authorization = exchange.requestHeaders.getFirst("Authorization")
        when {
            exchange.requestMethod != "PUT" -> exchange.sendResponseHeaders(404, -1)
            authorization == null -> {
                exchange.responseHeaders.add("WWW-Authenticate", "Basic realm=\"probe\"")
                exchange.sendResponseHeaders(401, -1)
            }
            else -> {
                accepted.add(authorization)
                exchange.sendResponseHeaders(201, -1)
            }
        }
    }

    override fun close() = server.stop(0)
}
