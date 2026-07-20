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

package io.spine.server.entity

/**
 * A view on the recent history of a [TransactionalEntity], read lazily from
 * a durable storage.
 *
 * The history serves its reads through a loader, installed by the
 * repository managing the entity. The items are not cached on the entity
 * side: an entity serves its signals and leaves the memory; caching, if
 * any, belongs to the storage side.
 *
 * An entity created outside a repository has no loader installed, so its
 * reads return no items.
 *
 * The kinds of recent histories are fixed by the framework:
 * the constructor is `internal`.
 *
 * @param T The type of the history items.
 * @param L The type of the loader serving the reads.
 * @see RecentEventHistory
 * @see RecentStateHistory
 */
public abstract class RecentHistory<T : Any, L : HistoryLoader<*>> internal constructor() {

    /**
     * If set, serves the reads from the durable storage of the entity.
     */
    private var loader: L? = null

    /**
     * Installs the loader serving the reads from the durable storage
     * of the entity.
     */
    internal fun useLoader(loader: L) {
        this.loader = loader
    }

    /**
     * Returns the installed loader, or `null` if the entity was created
     * outside a repository.
     */
    protected fun loader(): L? = loader

    /**
     * Reads up to [depth] most recent items of the history, newest first.
     *
     * Fewer items are returned if the history retains fewer. If no loader
     * is [installed][useLoader] — the entity was created outside
     * a repository — no items are returned.
     *
     * @param depth The maximum number of the most recent items to read.
     * @return An iterator over the items, newest first.
     * @throws IllegalArgumentException If the [depth] is not positive.
     */
    public fun read(depth: Int): Iterator<T> {
        require(depth > 0) { "History depth must be positive. Got $depth." }
        val installed = loader ?: return emptyList<T>().iterator()
        return load(installed, depth)
    }

    /**
     * Loads up to [depth] most recent items using the installed [loader].
     *
     * @param loader The installed loader.
     * @param depth The maximum number of the most recent items to load; positive.
     */
    protected abstract fun load(loader: L, depth: Int): Iterator<T>
}
