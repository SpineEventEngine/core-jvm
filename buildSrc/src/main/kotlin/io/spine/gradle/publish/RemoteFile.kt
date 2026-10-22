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

import java.io.FileNotFoundException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URI
import java.net.URISyntaxException
import java.net.URL
import java.net.URLConnection
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toKotlinDuration

/**
 * Reads the files of a remote Maven repository, such as `maven-metadata.xml`.
 *
 * Each request is bounded by timeouts, so that an unresponsive registry fails the build
 * instead of stalling it. A request that fails with an I/O error, a server error,
 * a [transient] client error, or a rate-limit `403 Forbidden` is retried, up to three
 * attempts in all, with the delay doubling from one second. A response with
 * a `Retry-After` header makes the next attempt wait at least as long as it asks,
 * up to a minute. A missing file is not an error, and a request the server refuses
 * for good, such as one answered with `401 Unauthorized` or a plain `403 Forbidden`,
 * is not retried.
 *
 * An `Authorization` header passed with a request is sent to the origin of the given URL
 * only. A redirect to another origin, such as the storage GitHub Packages redirects
 * a download to, is followed without it. A redirect is followed to an HTTP(S) URL only,
 * and never from HTTPS to plain HTTP.
 */
internal object RemoteFile {

    private const val attempts = 3
    private const val maxRedirects = 5
    private val connectTimeout = 10.seconds
    private val readTimeout = 30.seconds
    private val initialBackoff = 1.seconds

    /**
     * The longest wait before another attempt that a `Retry-After` header may impose.
     */
    private val maxRetryAfter = 60.seconds

    /**
     * `429 Too Many Requests`, which `HttpURLConnection` has no constant for.
     */
    private const val tooManyRequests = 429

    /**
     * The HTTP statuses of successful responses, `2xx`.
     */
    private val successes = HttpURLConnection.HTTP_OK..<HttpURLConnection.HTTP_MULT_CHOICE

    /**
     * The HTTP statuses of redirects, `3xx`.
     */
    private val redirects =
        HttpURLConnection.HTTP_MULT_CHOICE..<HttpURLConnection.HTTP_BAD_REQUEST

    /**
     * The HTTP statuses of client errors, `4xx`.
     */
    private val clientErrors =
        HttpURLConnection.HTTP_BAD_REQUEST..<HttpURLConnection.HTTP_INTERNAL_ERROR

    /**
     * The HTTP statuses that mean there is no such file.
     */
    private val missing = setOf(
        HttpURLConnection.HTTP_NOT_FOUND,
        HttpURLConnection.HTTP_GONE
    )

    /**
     * The HTTP client errors that may go away on a later attempt.
     */
    private val transient = setOf(
        HttpURLConnection.HTTP_CLIENT_TIMEOUT,
        tooManyRequests
    )

    /**
     * Reads the text of the file at the given [url].
     *
     * @param url The URL of the file.
     * @param authorization The value of the `Authorization` header to send, if any.
     *   It goes to the origin of the [url] only, never to a redirect target elsewhere.
     * @return the text of the file, or `null` if there is no such file.
     * @throws IOException if the file could not be read within the allowed attempts.
     * @throws IllegalStateException if the server refuses the request with a client error
     *   that another attempt would not change, or if it redirects the request too many
     *   times, with no `Location`, or to a URL that may not be followed.
     */
    fun readText(url: URL, authorization: String? = null): String? =
        withRetries {
            readOnce(url = url, authorization = authorization, redirectsLeft = maxRedirects)
        }

    /**
     * Tells whether there is a file at the given [url], without downloading it.
     *
     * A redirect is followed, and the answer at its end tells whether the file exists:
     * a redirect alone proves nothing, as a server may redirect every request, e.g.,
     * from HTTP to HTTPS. Neither Artifact Registry nor GitHub Packages redirects
     * a `HEAD` request: GitHub Packages redirects only the download of a file.
     *
     * @param url The URL of the file.
     * @param authorization The value of the `Authorization` header to send, if any.
     *   It goes to the origin of the [url] only, never to a redirect target elsewhere.
     * @throws IOException if the server could not be asked within the allowed attempts.
     * @throws IllegalStateException if the server refuses the request with a client error
     *   that another attempt would not change, or redirects it too many times.
     */
    fun exists(url: URL, authorization: String? = null): Boolean =
        withRetries {
            existsOnce(url = url, authorization = authorization, redirectsLeft = maxRedirects)
        }

    private fun <T> withRetries(request: () -> T): T {
        var backoff = initialBackoff
        // All attempts but the last one, whose failure propagates.
        repeat(attempts - 1) {
            try {
                return request()
            } catch (e: IOException) {
                val asked = (e as? RetryLaterException)?.delay ?: Duration.ZERO
                Thread.sleep(maxOf(backoff, asked).inWholeMilliseconds)
                backoff *= 2
            }
        }
        return request()
    }

    private fun readOnce(url: URL, authorization: String?, redirectsLeft: Int): String? {
        val connection = open(url, authorization)
        return try {
            if (connection is HttpURLConnection) {
                connection.read(authorization = authorization, redirectsLeft = redirectsLeft)
            } else {
                connection.getInputStream().bufferedReader().use { it.readText() }
            }
        } catch (_: FileNotFoundException) {
            // A `file:` URL to a missing file.
            null
        } finally {
            (connection as? HttpURLConnection)?.errorStream?.close()
        }
    }

    /**
     * Reads the response to this connection, following a redirect.
     */
    private fun HttpURLConnection.read(authorization: String?, redirectsLeft: Int): String? =
        when (status()) {
            in missing -> null
            in redirects -> {
                check(redirectsLeft > 0) { "Too many redirects reading `${url.shown()}`." }
                val next = target()
                inputStream.close()
                readOnce(
                    url = next,
                    authorization = authorization.takeIf { url.sameOrigin(next) },
                    redirectsLeft = redirectsLeft - 1
                )
            }
            else -> inputStream.bufferedReader().use { it.readText() }
        }

    private fun existsOnce(url: URL, authorization: String?, redirectsLeft: Int): Boolean {
        val connection = open(url, authorization)
        return try {
            if (connection is HttpURLConnection) {
                connection.requestMethod = "HEAD"
                connection.exists(authorization = authorization, redirectsLeft = redirectsLeft)
            } else {
                connection.getInputStream().close()
                true
            }
        } catch (_: FileNotFoundException) {
            // A `file:` URL to a missing file.
            false
        } finally {
            (connection as? HttpURLConnection)?.errorStream?.close()
        }
    }

    /**
     * Tells whether the response to this `HEAD` request finds a file, following
     * a redirect with no `Authorization` header if it leads to another origin.
     */
    private fun HttpURLConnection.exists(authorization: String?, redirectsLeft: Int): Boolean {
        val status = status()
        return when {
            status in missing -> false
            status in successes -> true
            status in redirects -> {
                check(redirectsLeft > 0) { "Too many redirects asking for `${url.shown()}`." }
                val next = target()
                existsOnce(
                    url = next,
                    authorization = authorization.takeIf { url.sameOrigin(next) },
                    redirectsLeft = redirectsLeft - 1
                )
            }
            else -> throw IOException("`${url.shown()}` answered with `$status`.")
        }
    }

    private fun open(url: URL, authorization: String?): URLConnection {
        val connection = url.openConnection()
        connection.connectTimeout = connectTimeout.inWholeMilliseconds.toInt()
        connection.readTimeout = readTimeout.inWholeMilliseconds.toInt()
        if (connection is HttpURLConnection) {
            connection.instanceFollowRedirects = false
        }
        authorization?.let { connection.setRequestProperty("Authorization", it) }
        return connection
    }

    /**
     * Obtains the status of the response if it is a success, a redirect, or a sign that
     * there is no such file.
     *
     * @throws IOException for a server error, a [transient] client error, or a rate limit,
     *   which may go away on another attempt; a [RetryLaterException] if the response
     *   tells how long to wait before it.
     * @throws IllegalStateException for any other client error.
     */
    private fun HttpURLConnection.status(): Int {
        val status = responseCode
        if (status >= HttpURLConnection.HTTP_INTERNAL_ERROR || status in transient ||
            isRateLimited(status)) {
            val message = "`${url.shown()}` answered with `$status $responseMessage`."
            val delay = retryAfter(maxRetryAfter)
            throw if (delay == null) IOException(message) else RetryLaterException(message, delay)
        }
        check(status !in clientErrors || status in missing) {
            "Cannot read `${url.shown()}`: the server answered with `$status $responseMessage`."
        }
        return status
    }

    /**
     * Obtains the URL that this connection redirects to.
     *
     * @throws IllegalStateException if there is no such URL, or it may not be followed:
     *   a URL that is not HTTP(S), or a plain HTTP one redirected to from HTTPS.
     */
    private fun HttpURLConnection.target(): URL {
        val location = checkNotNull(getHeaderField("Location")) {
            "`${url.shown()}` redirects with no `Location`."
        }
        // The cause is left out of the message: it would quote the location, whose query
        // may carry a signature.
        val target = resolve(location)
            ?: error("`${url.shown()}` redirects to an invalid location.")
        val allowed = target.protocol == url.protocol ||
                (url.protocol == "http" && target.protocol == "https")
        check(allowed) {
            "`${url.shown()}` redirects to `${target.shown()}`, which may not be followed."
        }
        return target
    }
}

/**
 * Resolves the given redirect [location] against the URL of this connection.
 *
 * @return the resolved URL, or `null` if the location is not a valid URL.
 */
private fun HttpURLConnection.resolve(location: String): URL? =
    try {
        url.toURI().resolve(URI.create(location)).toURL()
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: URISyntaxException) {
        null
    } catch (_: MalformedURLException) {
        null
    }

/**
 * An I/O failure whose response asks to wait for the given [delay] before trying again.
 */
private class RetryLaterException(message: String, val delay: Duration) : IOException(message)

/**
 * Obtains how long this response asks to wait before trying again, by its `Retry-After`
 * header in seconds or as an HTTP date, but no longer than the given [limit].
 *
 * @return the delay, or `null` if the response has no such header, or it cannot be parsed.
 */
private fun HttpURLConnection.retryAfter(limit: Duration): Duration? {
    val value = getHeaderField("Retry-After")?.trim() ?: return null
    val delay = value.toLongOrNull()?.seconds ?: untilHttpDate(value)
    return delay?.coerceIn(Duration.ZERO, limit)
}

/**
 * Obtains the time from now until the given HTTP date, such as
 * `Wed, 21 Oct 2026 07:28:00 GMT`.
 *
 * @return the time until the date, or `null` if [value] is not an HTTP date.
 */
private fun untilHttpDate(value: String): Duration? =
    try {
        val date = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
        java.time.Duration.between(Instant.now(), date.toInstant()).toKotlinDuration()
    } catch (_: DateTimeParseException) {
        null
    }

/**
 * Tells whether this response with the given [status] refuses the request for exceeding
 * a rate limit, as GitHub does with `403 Forbidden` and either a `Retry-After` header or
 * an `X-RateLimit-Remaining: 0` header.
 */
private fun HttpURLConnection.isRateLimited(status: Int): Boolean =
    status == HttpURLConnection.HTTP_FORBIDDEN &&
            (getHeaderField("Retry-After") != null ||
                    getHeaderField("X-RateLimit-Remaining") == "0")

/**
 * Tells whether the given URL has the origin of this one: the protocol, the host,
 * and the port.
 */
private fun URL.sameOrigin(other: URL): Boolean =
    protocol == other.protocol && host.equals(other.host, ignoreCase = true) &&
            effectivePort() == other.effectivePort()

private fun URL.effectivePort(): Int = if (port == -1) defaultPort else port

/**
 * Shows this URL with no query, which may carry a signature, as a redirect
 * to a storage service does.
 */
private fun URL.shown(): String = "$protocol://$authority$path"
