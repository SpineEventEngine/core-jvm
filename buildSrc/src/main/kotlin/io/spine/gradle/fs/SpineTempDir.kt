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

package io.spine.gradle.fs

import java.nio.file.Files.createDirectories
import java.nio.file.Files.createTempDirectory
import java.nio.file.Path

/**
 * A per-JVM parent directory for the temporary directories created by the build.
 *
 * The directory is created [lazily][path] under a common, attributable namespace —
 * `<java.io.tmpdir>/io.spine.gradle.fs`, named after the package of [LazyTempPath] — so
 * that leftover files are easy to attribute. Within that namespace, each JVM gets its own
 * subdirectory named after the process id, so concurrent Gradle daemons never delete one
 * another's temporary files.
 *
 * Upon creation, the per-JVM directory is scheduled for recursive removal when the JVM
 * shuts down. This is a safety net should the explicit cleanup performed by the build
 * tasks not run — for example, when a build fails before reaching it. The shared namespace
 * directory itself is intentionally left in place: deleting it on shutdown could wipe
 * directories still in use by another JVM running on the same machine.
 *
 * @see LazyTempPath
 */
internal object SpineTempDir {

    /**
     * The per-JVM directory, created on the first access and removed on JVM shutdown.
     */
    val path: Path by lazy { createPerJvmDir() }

    private fun createPerJvmDir(): Path {
        val namespace = Path.of(systemTempDir(), LazyTempPath::class.java.packageName)
        createDirectories(namespace)
        // A per-JVM directory keeps concurrent Gradle daemons from deleting one another's
        // files when their shutdown hooks fire. The PID makes a leftover directory easy
        // to attribute; `createTempDirectory` adds a random suffix so that a reused PID
        // still yields a unique directory.
        val pid = ProcessHandle.current().pid()
        val jvmDir = createTempDirectory(namespace, "$pid-")
        deleteRecursivelyOnShutdown(jvmDir)
        return jvmDir
    }

    /**
     * Obtains the value of the system property pointing to the temporary directory.
     */
    private fun systemTempDir(): String =
        checkNotNull(System.getProperty("java.io.tmpdir")) {
            "The `java.io.tmpdir` system property is not set."
        }

    /**
     * Requests the recursive removal of the given [directory] when the JVM shuts down.
     *
     * @see Runtime.addShutdownHook
     */
    private fun deleteRecursivelyOnShutdown(directory: Path) {
        val runtime = Runtime.getRuntime()
        runtime.addShutdownHook(Thread {
            val deleted = directory.toFile().deleteRecursively()
            if (!deleted) {
                System.err.println("Unable to delete the temporary directory `$directory`.")
            }
        })
    }
}
