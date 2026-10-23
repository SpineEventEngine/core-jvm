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
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

@DisplayName("`RemoteFile` should")
internal class RemoteFileSpec {

    private val server = Server()

    /**
     * Another origin, such as the storage that GitHub Packages redirects a download to.
     */
    private val storage = Server()

    @AfterEach
    fun stopServers() {
        server.close()
        storage.close()
    }

    @Nested inner class
    `read a file` {

        @Test
        fun `answered with its text`() {
            server.answer(200)

            RemoteFile.readText(server.url("file.txt")) shouldBe body
        }

        @Test
        fun `telling at once that there is no file`() {
            server.answer(404)

            RemoteFile.readText(server.url("file.txt")).shouldBeNull()
            server.requests.size shouldBe 1
        }

        @Test
        fun `retrying after a server error`() {
            server.answer(503, 200)

            RemoteFile.readText(server.url("file.txt")) shouldBe body
            server.requests.size shouldBe 2
        }

        @Test
        fun `retrying after throttling`() {
            server.answer(429, 200)

            RemoteFile.readText(server.url("file.txt")) shouldBe body
            server.requests.size shouldBe 2
        }

        @Test
        fun `retrying after a rate limit`() {
            server.rateLimit(then = 200)

            RemoteFile.readText(server.url("file.txt")) shouldBe body
            server.requests.size shouldBe 2
        }

        @Test
        fun `waiting before a retry as long as the server asks`() {
            server.throttle(retryAfter = "2", then = 200)

            val elapsed = measureTime {
                RemoteFile.readText(server.url("file.txt")) shouldBe body
            }

            elapsed shouldBeGreaterThanOrEqualTo 2.seconds
            server.requests.size shouldBe 2
        }

        @Test
        fun `failing after the last attempt`() {
            server.answer(500)

            shouldThrow<IOException> { RemoteFile.readText(server.url("file.txt")) }
            server.requests.size shouldBe 3
        }

        @Test
        fun `not retrying a request the server refuses`() {
            server.answer(403)

            val error = shouldThrow<IllegalStateException> {
                RemoteFile.readText(server.url("file.txt"))
            }

            error.message shouldContain "403"
            server.requests.size shouldBe 1
        }

        @Test
        fun `on disk, telling that there is no such file`(@TempDir dir: File) {
            val url = dir.resolve("missing.xml").toURI().toURL()

            RemoteFile.readText(url).shouldBeNull()
        }
    }

    @Nested inner class
    `follow a redirect` {

        @Test
        fun `to another origin, without the authorization`() {
            server.redirect(storage.url("blob/file.txt").toString())
            storage.answer(200)

            RemoteFile.readText(server.url("file.txt"), authorization) shouldBe body

            server.requests.map { it.authorization } shouldContainExactly listOf(authorization)
            storage.requests.map { it.authorization } shouldContainExactly listOf(null)
        }

        @Test
        fun `within the origin, with the authorization`() {
            server.redirect("/moved/file.txt", then = 200)

            RemoteFile.readText(server.url("file.txt"), authorization) shouldBe body

            server.requests.map { it.path } shouldContainExactly
                    listOf("/file.txt", "/moved/file.txt")
            server.requests.map { it.authorization } shouldContainExactly
                    listOf(authorization, authorization)
        }

        @Test
        fun `only a few times`() {
            server.redirect("/file.txt")

            val error = shouldThrow<IllegalStateException> {
                RemoteFile.readText(server.url("file.txt"))
            }

            error.message shouldContain "Too many redirects"
        }

        @Test
        fun `only to a location given`() {
            server.redirect(location = null)

            val error = shouldThrow<IllegalStateException> {
                RemoteFile.readText(server.url("file.txt"))
            }

            error.message shouldContain "no `Location`"
        }

        @Test
        fun `only to an HTTP location`() {
            server.redirect("file:///etc/hosts")

            val error = shouldThrow<IllegalStateException> {
                RemoteFile.readText(server.url("file.txt"))
            }

            error.message shouldContain "may not be followed"
        }

        @Test
        fun `showing no query in a message`() {
            server.redirect("${storage.url("blob/file.txt")}?signature=secret")
            storage.answer(403)

            val error = shouldThrow<IllegalStateException> {
                RemoteFile.readText(server.url("file.txt"))
            }

            error.message shouldNotContain "secret"
        }
    }

    @Nested inner class
    `tell whether there is a file` {

        @Test
        fun `answered with a success`() {
            server.answer(200)

            RemoteFile.exists(server.url("file.txt")) shouldBe true
            server.requests.map { it.method } shouldContainExactly listOf("HEAD")
        }

        @Test
        fun `answered with no such file`() {
            server.answer(404)

            RemoteFile.exists(server.url("file.txt")) shouldBe false
        }

        @Test
        fun `answered with a gone file`() {
            server.answer(410)

            RemoteFile.exists(server.url("file.txt")) shouldBe false
        }

        @Test
        fun `redirected to another origin, following without the authorization`() {
            server.redirect(storage.url("blob/file.txt").toString())
            storage.answer(200)

            RemoteFile.exists(server.url("file.txt"), authorization) shouldBe true
            storage.requests shouldContainExactly
                    listOf(Request(method = "HEAD", path = "/blob/file.txt", authorization = null))
        }

        @Test
        fun `redirected to another origin that has no such file`() {
            server.redirect(storage.url("blob/file.txt").toString())
            storage.answer(404)

            RemoteFile.exists(server.url("file.txt")) shouldBe false
        }

        @Test
        fun `redirected within the origin, following`() {
            server.redirect("/moved/file.txt", then = 404)

            RemoteFile.exists(server.url("file.txt")) shouldBe false
            server.requests.size shouldBe 2
        }

        @Test
        fun `retrying after a request timeout`() {
            server.answer(408, 200)

            RemoteFile.exists(server.url("file.txt")) shouldBe true
            server.requests.size shouldBe 2
        }

        @Test
        fun `not retrying a request the server refuses`() {
            server.answer(401)

            shouldThrow<IllegalStateException> { RemoteFile.exists(server.url("file.txt")) }
            server.requests.size shouldBe 1
        }

        @Test
        fun `on disk`(@TempDir dir: File) {
            val file = dir.resolve("file.txt").apply { writeText(body) }

            RemoteFile.exists(file.toURI().toURL()) shouldBe true
            RemoteFile.exists(dir.resolve("missing.txt").toURI().toURL()) shouldBe false
        }
    }

    private companion object {
        const val body = "<metadata/>"
        const val authorization = "Bearer test-token"
    }
}

/**
 * A request received by a [Server].
 */
private data class Request(val method: String, val path: String, val authorization: String?)

/**
 * A loopback HTTP server, which answers each request as told, and records the requests.
 */
private class Server : AutoCloseable {

    private val server: HttpServer =
        HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)

    /**
     * The requests received so far.
     */
    val requests = CopyOnWriteArrayList<Request>()

    @Volatile
    private var respond: (HttpExchange, Int) -> Unit = { exchange, _ -> exchange.reply(404) }

    init {
        server.createContext("/") { exchange ->
            exchange.use {
                requests.add(
                    Request(
                        method = it.requestMethod,
                        path = it.requestURI.path,
                        authorization = it.requestHeaders.getFirst("Authorization")
                    )
                )
                respond(it, requests.size - 1)
            }
        }
        server.start()
    }

    /**
     * Obtains the URL of the given [path] at this server.
     *
     * The loopback address may be an IPv6 one, which [URI] encloses in square brackets.
     */
    fun url(path: String): URL {
        val address = server.address
        return URI("http", null, address.hostString, address.port, "/$path", null, null)
            .toURL()
    }

    /**
     * Answers each request with the next of the given [statuses], repeating the last one.
     */
    fun answer(vararg statuses: Int) {
        respond = { exchange, index -> exchange.reply(statuses[minOf(index, statuses.lastIndex)]) }
    }

    /**
     * Redirects the first request to the given [location], or with no `Location` if it is
     * `null`, and answers the next ones with the given status, or with the same redirect.
     */
    fun redirect(location: String?, then: Int? = null) {
        respond = { exchange, index ->
            if (index == 0 || then == null) {
                location?.let { exchange.responseHeaders.add("Location", it) }
                exchange.sendResponseHeaders(302, -1)
            } else {
                exchange.reply(then)
            }
        }
    }

    /**
     * Refuses the first request for exceeding a rate limit, as GitHub does with
     * `403 Forbidden` and a `Retry-After` header, and answers the next ones with
     * the given status.
     */
    fun rateLimit(then: Int) {
        respond = { exchange, index ->
            if (index == 0) {
                exchange.responseHeaders.add("Retry-After", "1")
                exchange.sendResponseHeaders(403, -1)
            } else {
                exchange.reply(then)
            }
        }
    }

    /**
     * Answers the first request with `429 Too Many Requests` and the given `Retry-After`
     * header, and the next ones with the given status.
     */
    fun throttle(retryAfter: String, then: Int) {
        respond = { exchange, index ->
            if (index == 0) {
                exchange.responseHeaders.add("Retry-After", retryAfter)
                exchange.sendResponseHeaders(429, -1)
            } else {
                exchange.reply(then)
            }
        }
    }

    override fun close() = server.stop(0)

    private fun HttpExchange.reply(status: Int) {
        val bytes = if (status == 200 && requestMethod != "HEAD") {
            "<metadata/>".toByteArray()
        } else {
            byteArrayOf()
        }
        sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
        responseBody.write(bytes)
    }
}
