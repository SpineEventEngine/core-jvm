/*
 * Copyright 2026, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Redistribution and use in source and/or binary forms, with or without
 * modification, must retain the above copyright notice and the following
 * disclaimer.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.spine.gradle

import java.io.File
import org.gradle.api.GradleException

/**
 * Reads a `version.gradle.kts` file and resolves the `extra` properties it declares.
 *
 * [contentUnder] and [contentInBase] read the file (from the working tree or a base branch);
 * [keyForValue] and [valueForKey] resolve its `extra` properties. Each property is registered
 * either with the current `extra.set("name", …)` call or the legacy `by extra(…)` property
 * delegate that Gradle deprecated (the `bump-version` skill migrates the latter to the former);
 * both spellings are recognized, so a file is read correctly whether or not it has migrated.
 * The following value shapes are handled:
 *
 *  1. a literal:
 *     `extra.set("versionToPublish", "2.0.0-SNAPSHOT.182")`, or the legacy
 *     `val versionToPublish: String by extra("2.0.0-SNAPSHOT.182")`;
 *  2. an alias to a plain `val` (or, in the legacy spelling, to another `extra`):
 *     `extra.set("versionToPublish", compilerVersion)` paired with
 *     `val compilerVersion = "2.0.0-SNAPSHOT.043"`, or the legacy
 *     `val versionToPublish by extra(compilerVersion)` paired with
 *     `val compilerVersion: String by extra("2.0.0-SNAPSHOT.043")`.
 *
 * The publishing-version property is identified by [keyForValue] using the already-resolved
 * project version as an oracle, so the specific property name (`versionToPublish`,
 * `validationVersion`, `compilerVersion`, …) does not need to be hard-coded.
 *
 * Only a single alias hop is resolved; an alias to another alias falls through to `null`,
 * which the caller treats as "publishing version not identified" and skips the check.
 */
internal object VersionGradleFile {

    /**
     * The name of a `version.gradle.kts` file.
     */
    const val NAME = "version.gradle.kts"

    // Legacy `by extra(…)` property-delegate spellings (deprecated by Gradle).
    private val literalExtra =
        Regex("""val\s+(\w+)\s*(?::\s*String)?\s+by\s+extra\(\s*"([^"]+)"\s*\)""")
    private val aliasExtra =
        Regex("""val\s+(\w+)\s*(?::\s*String)?\s+by\s+extra\(\s*([A-Za-z_]\w*)\s*\)""")

    // Current `extra.set("name", …)` spellings.
    private val literalSet =
        Regex("""extra\.set\(\s*"(\w+)"\s*,\s*"([^"]+)"\s*\)""")
    private val aliasSet =
        Regex("""extra\.set\(\s*"(\w+)"\s*,\s*([A-Za-z_]\w*)\s*\)""")

    // A plain `val name = "value"` that an alias may reference.
    private val plainAssignment =
        Regex("""val\s+(\w+)\s*(?::\s*String)?\s*=\s*"([^"]+)"""")

    /**
     * Resolves every named `extra` (and the plain `val`s an `extra` may alias) to its
     * string value.
     */
    private fun parse(content: String): Map<String, String> {
        val literals = (literalExtra.findAll(content) + literalSet.findAll(content))
            .associate { it.groupValues[1] to it.groupValues[2] }
        val plains = plainAssignment.findAll(content)
            .associate { it.groupValues[1] to it.groupValues[2] }
        val resolved = literals.toMutableMap()
        (aliasExtra.findAll(content) + aliasSet.findAll(content)).forEach { match ->
            val name = match.groupValues[1]
            val source = match.groupValues[2]
            if (name !in resolved) {
                (literals[source] ?: plains[source])?.let { resolved[name] = it }
            }
        }
        return resolved
    }

    /**
     * The name of the property whose resolved value equals [value], or `null` if none does.
     */
    fun keyForValue(content: String, value: String): String? =
        parse(content).entries.firstOrNull { it.value == value }?.key

    /**
     * The resolved value of the property named [key], or `null` if it is absent.
     */
    fun valueForKey(content: String, key: String): String? = parse(content)[key]

    /**
     * Reads `version.gradle.kts` from [rootDir], or `null` when it is absent.
     */
    fun contentUnder(rootDir: File): String? =
        File(rootDir, NAME).takeIf { it.exists() }?.readText()

    /**
     * Reads `version.gradle.kts` from the tip of the `origin/<baseRef>` remote-tracking branch.
     *
     * Returns `null` when the file does not exist at the base — a newly introduced version file.
     * Throws a [GradleException] when the base ref cannot be resolved: the Version Guard workflow
     * is responsible for fetching it, and failing closed surfaces that misconfiguration instead
     * of silently passing the check.
     */
    fun contentInBase(rootDir: File, baseRef: String): String? {
        val result = gitShow(rootDir, "origin/$baseRef:$NAME")
        if (result.exitCode == 0) {
            return result.stdout
        }
        // `git show` reports a missing path with these phrasings; everything else
        // (e.g. an unresolvable ref) is a configuration error we must not swallow.
        val missingPath = result.stderr.contains("does not exist") ||
                result.stderr.contains("exists on disk, but not in")
        if (missingPath) {
            return null
        }
        throw GradleException(
            "Unable to read `$NAME` from base `origin/$baseRef` " +
                    "(git exit code ${result.exitCode}): ${result.stderr.trim()}.\n" +
                    "Ensure the Version Guard workflow fetches the base branch before this check."
        )
    }
}

/**
 * The outcome of a `git` invocation: its [exitCode] and the captured [stdout] and [stderr].
 *
 * @property exitCode The process exit code; `0` on success.
 * @property stdout The captured standard output stream.
 * @property stderr The captured standard error stream.
 */
private data class GitResult(val exitCode: Int, val stdout: String, val stderr: String)

private fun gitShow(rootDir: File, spec: String): GitResult {
    // Redirect to files rather than reading the process pipes sequentially: draining
    // stdout fully before stderr can deadlock if a stream fills its pipe buffer.
    val outFile = File.createTempFile("git-show", ".out")
    val errFile = File.createTempFile("git-show", ".err")
    try {
        val exitCode = ProcessBuilder("git", "show", spec)
            .directory(rootDir)
            .redirectOutput(outFile)
            .redirectError(errFile)
            .start()
            .waitFor()
        return GitResult(exitCode, outFile.readText(), errFile.readText())
    } finally {
        outFile.delete()
        errFile.delete()
    }
}
