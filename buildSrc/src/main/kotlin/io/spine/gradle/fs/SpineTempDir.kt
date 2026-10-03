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
