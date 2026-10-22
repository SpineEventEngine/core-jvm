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
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir

/**
 * Runs `publicationState` in a build that really publishes to a loopback registry.
 *
 * `api` and `backend` are the modules of `spinePublishing`. The [registry] has two remote
 * repositories: one like Artifact Registry, which serves anyone, and one like GitHub
 * Packages, which serves nothing without the token and redirects the downloads of files
 * to storage of its own. `api` publishes to both and to a local repository, `backend`
 * to GitHub Packages only, and `docs` to a local repository only. No local repository
 * may be searched.
 *
 * The build publishes once for the whole suite, after the check of the unpublished
 * version. Each test then looks at a copy of what the build published, so that it may
 * take files away from it.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 5, unit = TimeUnit.MINUTES)
@DisplayName("`publicationState` task should")
internal class PublicationStateIgTest {

    /**
     * The directory of the suite, holding the project and the repositories.
     */
    private lateinit var workDir: File

    private lateinit var registry: Registry

    /**
     * The repositories as the build published them.
     */
    private lateinit var published: File

    /**
     * The state written before anything was published, and the output of that build.
     */
    private lateinit var unpublished: Pair<String, String>

    private val copies = AtomicInteger()

    @BeforeAll
    fun publish(@TempDir dir: File) {
        workDir = dir
        registry = Registry(root = workDir.resolve("registry").apply { mkdirs() }, token = token)
        writeBuild()
        val output = runner().build().output
        unpublished = stateFile.readText().trim() to output
        runner(task = "publish").build()
        published = registry.root
    }

    @BeforeEach
    fun serveCopy() {
        val copy = workDir.resolve("served-${copies.incrementAndGet()}")
        published.copyRecursively(copy)
        registry.root = copy
        registry.leakedTokens.set(0)
    }

    @AfterAll
    fun stopRegistry() {
        if (::registry.isInitialized) {
            registry.close()
        }
    }

    @Test
    fun `report the version unpublished before anything is published`() {
        val (state, output) = unpublished

        state shouldBe "unpublished"
        output shouldContain "`$api:$version` is not published."
    }

    @Test
    fun `report the version published when its SBOMs name the commit being published`() {
        val output = runner().build().output

        stateFile.readText().trim() shouldBe "published"
        output shouldContain "`$backend:$version` is published from `$commit`."
        registry.leakedTokens.get() shouldBe 0
    }

    @Test
    fun `not look in local repositories`() {
        val local = project.resolve("local-repo")
        val aside = workDir.resolve("local-repo-aside")
        check(local.renameTo(aside))
        try {
            val output = runner().build().output

            stateFile.readText().trim() shouldBe "published"
            output shouldNotContain docs
        } finally {
            check(aside.renameTo(local))
        }
    }

    @Nested inner class
    `fail when missing from a repository` {

        @Test
        fun `a classified artifact`() {
            val jar = "spine-backend-$version-sources.jar"
            registry.root.resolve("github/sample/$backend/$version/$jar").delete()

            val output = runner().buildAndFail().output

            output shouldContain "The version is partially published"
            output shouldContain "`$jar` in `${registry.url}/github/sample`"
        }

        @Test
        fun `the artifact of the component`() {
            val jar = "spine-api-$version.jar"
            registry.root.resolve("ar/snapshots/$api/$version/$jar").delete()

            val output = runner().buildAndFail().output

            output shouldContain "`$jar` in `${registry.url}/ar/snapshots`"
        }

        @Test
        fun `the SBOM`() {
            val sbom = "spine-api-$version.spdx.json"
            registry.root.resolve("ar/snapshots/$api/$version/$sbom").delete()

            val output = runner().buildAndFail().output

            output shouldContain "`$sbom` in `${registry.url}/ar/snapshots`"
        }

        @Test
        fun `the module metadata`() {
            val module = "spine-api-$version.module"
            registry.root.resolve("ar/snapshots/$api/$version/$module").delete()

            val output = runner().buildAndFail().output

            output shouldContain "`$module` in `${registry.url}/ar/snapshots`"
            output shouldNotContain "pruned"
        }

        @Test
        fun `a whole publication, telling that GitHub Packages may have pruned it`() {
            registry.root.resolve("github/sample/$api").deleteRecursively()

            val output = runner().buildAndFail().output

            output shouldContain "The version is partially published"
            output shouldContain "`$api:$version`: the version in " +
                    "`${registry.url}/github/sample/$api/maven-metadata.xml`"
            output shouldContain "it was pruned from there"
        }
    }

    @Test
    fun `fail when the version is published from another commit`() {
        val output = runner(commit = otherCommit).buildAndFail().output

        output shouldContain "The version is published from another commit"
        output shouldContain "`$commit`"
    }

    @Test
    fun `fail when a copy of an SBOM in one repository names another commit`() {
        rewriteSbom(repository = "github/sample", path = api, revision = otherCommit)

        val output = runner().buildAndFail().output

        output shouldContain "The version is published from another commit: `$otherCommit`"
    }

    @Test
    fun `fail when a copy of an SBOM names no commit`() {
        rewriteSbom(repository = "github/sample", path = backend, revision = "unknown")

        val output = runner().buildAndFail().output

        output shouldContain "a published SBOM does not name the commit"
    }

    @Test
    fun `fail when the commit being published is unknown`() {
        val output = runner(commit = null).buildAndFail().output

        output shouldContain "this build cannot tell which commit it publishes"
        output shouldContain "`GITHUB_SHA` is not set"
    }

    @Test
    fun `fail when there is no token to read GitHub Packages with`() {
        val output = runner(token = null).buildAndFail().output

        output shouldContain "requires a token in the `GITHUB_TOKEN` environment variable"
    }

    @Test
    fun `fail when GitHub Packages refuses the token`() {
        val output = runner(token = "wrong-token").buildAndFail().output

        output shouldContain "the server answered with `401"
        registry.leakedTokens.get() shouldBe 0
    }

    @Test
    fun `look the state up again when reusing the configuration cache`() {
        val publishedCopy = registry.root
        registry.root = workDir.resolve("nothing").apply { mkdirs() }
        runner().withArguments(runner().arguments + "--configuration-cache").build()
        stateFile.readText().trim() shouldBe "unpublished"
        registry.root = publishedCopy

        val output = runner()
            .withArguments(runner().arguments + "--configuration-cache")
            .build()
            .output

        output shouldContain "Reusing configuration cache."
        stateFile.readText().trim() shouldBe "published"
    }

    /**
     * Rewrites the copy of the SBOM of the artifact at the given [path] in the given
     * [repository] of the registry, so that it names the given [revision] instead of
     * the commit the build published.
     */
    private fun rewriteSbom(repository: String, path: String, revision: String) {
        val artifactId = path.substringAfterLast('/')
        val sbom = registry.root.resolve(
            "$repository/$path/$version/$artifactId-$version.spdx.json"
        )
        val text = sbom.readText()
        check(commit in text) { "The SBOM `$sbom` does not name the commit `$commit`." }
        sbom.writeText(text.replace(commit, revision))
    }

    private val project: File
        get() = workDir.resolve("project")

    private val stateFile: File
        get() = project.resolve("build/publication/state.txt")

    /**
     * Creates a runner of the given [task], with the given [commit] as `GITHUB_SHA` and
     * the given [token] as `GITHUB_TOKEN`. A `null` value leaves the variable unset,
     * as outside GitHub Actions.
     */
    private fun runner(
        task: String = ":${PublicationState.taskName}",
        commit: String? = Companion.commit,
        token: String? = Companion.token
    ): GradleRunner {
        val environment = System.getenv() - ciVariables - "GITHUB_SHA" - "GITHUB_TOKEN" +
                listOfNotNull(
                    commit?.let { "GITHUB_SHA" to it },
                    token?.let { "GITHUB_TOKEN" to it }
                )
        return GradleRunner.create()
            .withProjectDir(project)
            .withEnvironment(environment)
            .withArguments(task, "--stacktrace")
    }

    private fun writeBuild() {
        listOf("api", "backend", "docs").forEach { module ->
            project.resolve("$module/src/main/java/sample/$module/Stub.java").apply {
                parentFile.mkdirs()
                writeText("package sample.$module;\npublic class Stub {}\n")
            }
        }
        project.resolve("settings.gradle.kts").writeText(
            """
            rootProject.name = "state-sample"
            include("api", "backend", "docs")
            """.trimIndent()
        )
        project.resolve("build.gradle.kts").writeText(
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
                $generatorStub
            }

            spinePublishing {
                modules = setOf("api", "backend")
                destinations = emptySet()
            }

            fun PublishingExtension.toRegistry() = repositories.maven {
                name = "CloudArtifactRegistry-snapshots"
                url = uri("${registry.url}/ar/snapshots")
            }

            fun PublishingExtension.toGitHub() = repositories.maven {
                name = "GitHub-Packages-snapshots"
                url = uri("${registry.url}/github/sample")
                credentials {
                    username = "spine"
                    password = "$token"
                }
            }

            fun PublishingExtension.toLocal() = repositories.maven {
                name = "local"
                url = uri("${'$'}{rootDir}/local-repo")
            }

            project("api").the<PublishingExtension>().apply {
                toRegistry()
                toGitHub()
                toLocal()
            }

            // GitHub Packages only, so that the SBOM is read from there, through a redirect.
            project("backend").the<PublishingExtension>().apply {
                toGitHub()
            }

            project("docs").the<PublishingExtension>().apply {
                publications {
                    create<MavenPublication>("docs") {
                        from(project("docs").components["java"])
                    }
                }
                toLocal()
            }
            """.trimIndent()
        )
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

        const val version = "2.0.0-SNAPSHOT.575"
        const val commit = "35054d78dfce2a0ead2ca79347ad57f74508977c"
        const val otherCommit = "2b98e1801a0b3c4d5e6f708192a3b4c5d6e7f809"
        const val token = "test-token"

        /** The artifacts of `api` and `backend`, named with the default prefix. */
        const val api = "io/spine/sample/spine-api"
        const val backend = "io/spine/sample/spine-backend"

        /** The artifact of `docs`, named after the project by `maven-publish`. */
        const val docs = "io/spine/sample/docs"

        /**
         * A task standing in for the Dokka one that `javadocJar` of `spinePublishing`
         * depends on; the fixture does not apply Dokka.
         */
        const val generatorStub = """tasks.register("dokkaGeneratePublicationJavadoc")"""
    }
}

/**
 * A loopback Maven registry storing the uploaded files under its [root].
 *
 * The files under `/ar` are served to anyone. Like GitHub Packages, the registry serves
 * the files under `/github` only to a request carrying the [token], and redirects
 * the download of any of them but `maven-metadata.xml` to storage of another origin.
 * A token sent to `/ar` or to the storage counts as a [leaked one][leakedTokens].
 */
private class Registry(@Volatile var root: File, private val token: String) : AutoCloseable {

    private val server = loopbackServer()

    /**
     * The storage that the downloads from `/github` are redirected to.
     */
    private val storage = loopbackServer()

    /**
     * The number of requests that carried a token to where it does not belong.
     */
    val leakedTokens = AtomicInteger()

    /**
     * The URL of this registry.
     */
    val url: String
        get() = server.url()

    init {
        server.createContext("/") { exchange -> exchange.use { respond(it) } }
        storage.createContext("/") { exchange ->
            exchange.use {
                it.authorization()?.let { leakedTokens.incrementAndGet() }
                serve(it, root.resolve(it.requestURI.path.removePrefix("/")))
            }
        }
        server.start()
        storage.start()
    }

    private fun respond(exchange: HttpExchange) {
        val path = exchange.requestURI.path
        val authorization = exchange.authorization()
        val file = root.resolve(path.removePrefix("/"))
        val gitHub = path.startsWith("/github/")
        if (!gitHub && authorization != null) {
            leakedTokens.incrementAndGet()
        }
        when {
            gitHub && !authorization.carriesToken() -> {
                exchange.requestBody.readBytes()
                exchange.responseHeaders.add("WWW-Authenticate", "Basic realm=\"github\"")
                exchange.sendResponseHeaders(401, -1)
            }
            exchange.requestMethod == "PUT" -> {
                file.parentFile.mkdirs()
                file.writeBytes(exchange.requestBody.readBytes())
                exchange.sendResponseHeaders(201, -1)
            }
            exchange.requestMethod == "HEAD" ->
                exchange.sendResponseHeaders(if (file.isFile) 200 else 404, -1)
            gitHub && file.isFile && file.name != MavenMetadata.FILE_NAME -> {
                exchange.responseHeaders.add("Location", "${storage.url()}$path")
                exchange.sendResponseHeaders(302, -1)
            }
            else -> serve(exchange, file)
        }
    }

    private fun serve(exchange: HttpExchange, file: File) {
        if (!file.isFile) {
            exchange.sendResponseHeaders(404, -1)
            return
        }
        val bytes = file.readBytes()
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.write(bytes)
    }

    private fun String?.carriesToken(): Boolean {
        val credentials = this?.takeIf { it.startsWith("Basic ") }?.removePrefix("Basic ")
            ?: return false
        val decoded = try {
            String(Base64.getDecoder().decode(credentials))
        } catch (_: IllegalArgumentException) {
            return false
        }
        return decoded.endsWith(":$token")
    }

    override fun close() {
        server.stop(0)
        storage.stop(0)
    }
}

private fun loopbackServer(): HttpServer =
    HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)

/**
 * Obtains the URL of this server.
 *
 * The loopback address may be an IPv6 one, which [URI] encloses in square brackets.
 */
private fun HttpServer.url(): String =
    URI("http", null, address.hostString, address.port, null, null, null).toString()

private fun HttpExchange.authorization(): String? = requestHeaders.getFirst("Authorization")
