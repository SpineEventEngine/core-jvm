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

import java.io.File
import java.net.URI
import java.nio.file.FileSystem
import java.nio.file.Files.createTempDirectory
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.WatchService

/**
 * A path to a temporary folder, which is not created until it is really used.
 *
 * After the first usage, the instances of this type delegate all calls to the internally
 * created instance of [Path] created with [createTempDirectory].
 *
 * The directory is created under the [shared base directory][SpineTempDir], which is removed
 * when the JVM — the Gradle daemon — shuts down. Build tasks delete their own directories
 * eagerly as the primary cleanup; this shutdown removal is a safety net, so a directory does
 * not outlive the daemon even when a build fails before its eager cleanup runs.
 */
@Suppress("TooManyFunctions")
class LazyTempPath(private val prefix: String) : Path {

    private val delegate: Path by lazy { createTempDirectory(SpineTempDir.path, prefix) }

    override fun compareTo(other: Path): Int = delegate.compareTo(other)

    override fun iterator(): MutableIterator<Path> = delegate.iterator()

    override fun register(
        watcher: WatchService,
        events: Array<out WatchEvent.Kind<*>>,
        vararg modifiers: WatchEvent.Modifier?
    ): WatchKey = delegate.register(watcher, events, *modifiers)

    override fun register(watcher: WatchService, vararg events: WatchEvent.Kind<*>): WatchKey =
        delegate.register(watcher, *events)

    override fun getFileSystem(): FileSystem = delegate.fileSystem

    override fun isAbsolute(): Boolean = delegate.isAbsolute

    override fun getRoot(): Path = delegate.root

    override fun getFileName(): Path = delegate.fileName

    override fun getParent(): Path = delegate.parent

    override fun getNameCount(): Int = delegate.nameCount

    override fun getName(index: Int): Path = delegate.getName(index)

    override fun subpath(beginIndex: Int, endIndex: Int): Path =
        delegate.subpath(beginIndex, endIndex)

    override fun startsWith(other: Path): Boolean = delegate.startsWith(other)

    override fun startsWith(other: String): Boolean = delegate.startsWith(other)

    override fun endsWith(other: Path): Boolean = delegate.endsWith(other)

    override fun endsWith(other: String): Boolean = delegate.endsWith(other)

    override fun normalize(): Path = delegate.normalize()

    override fun resolve(other: Path): Path = delegate.resolve(other)

    override fun resolve(other: String): Path = delegate.resolve(other)

    override fun resolveSibling(other: Path): Path = delegate.resolveSibling(other)

    override fun resolveSibling(other: String): Path = delegate.resolveSibling(other)

    override fun relativize(other: Path): Path = delegate.relativize(other)

    override fun toUri(): URI = delegate.toUri()

    override fun toAbsolutePath(): Path = delegate.toAbsolutePath()

    override fun toRealPath(vararg options: LinkOption): Path = delegate.toRealPath(*options)

    override fun toFile(): File = delegate.toFile()

    override fun toString(): String = delegate.toString()
}
