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

import io.spine.core.Version

/**
 * A view on the recent history of an [AbstractEntity], read lazily from
 * a durable storage and cached for the lifetime of the entity instance.
 *
 * The history serves its reads through a loader, installed by the
 * repository managing the entity. The items obtained from the loader — and
 * the items [appended][append] by the framework as the entity produces
 * them — are kept in an instance-scoped cache, so that an entity handling
 * several signals in a batch does not re-read the same items from the
 * storage on every dispatch. The cache lives and dies with the entity
 * instance; a repeated [read] hits the storage only below the items
 * already cached.
 *
 * An entity created outside a repository has no loader installed, so its
 * reads serve only the [appended][append] items, if any.
 *
 * The instance is confined to the thread dispatching the signals of
 * the entity, as the entity itself is; the cache is not synchronized.
 *
 * @param R The type of the records persisted in the durable storage.
 * @param T The type of the history items served to the entity.
 * @param L The type of the loader serving the reads.
 * @see RecentEventHistory
 * @see RecentStateHistory
 */
internal abstract class RecentHistory<R : Any, T : Any, L : HistoryLoader<R>> {

    /**
     * If set, serves the reads from the durable storage of the entity.
     */
    private var loader: L? = null

    /**
     * The items of the history known to this instance, newest first.
     *
     * The cache is a contiguous run of the recent history: the front item
     * is the newest known one, and no items are missing in between. The
     * run always ends on a complete version group — the items sharing one
     * entity version, e.g., the events of a single dispatch, are never
     * split between the cache and the storage. This keeps the
     * [continuation reads][HistoryLoader.load] below the cache exact:
     * they resume strictly below the version of the oldest cached item.
     */
    private val cache = ArrayDeque<Cached<T>>()

    /**
     * Becomes `true` when the loader proves that the storage holds
     * nothing below the oldest cached item, so further reads do not
     * consult the storage at all.
     */
    private var exhausted = false

    /**
     * Installs the loader serving the reads from the durable storage
     * of the entity.
     *
     * The cached items are retained: a new loader serves the same
     * history. The knowledge of its bottom is [reset][exhausted], though,
     * since the new storage window is yet to be discovered.
     */
    fun useLoader(loader: L) {
        this.loader = loader
        exhausted = false
    }

    /**
     * Returns the installed loader, or `null` if the entity was created
     * outside a repository.
     */
    protected fun loader(): L? = loader

    /**
     * Appends the given record to this history as its newest item.
     *
     * See the batch overload of [append] for the contract.
     */
    fun append(record: R) {
        append(listOf(record))
    }

    /**
     * Appends the given records to this history as its newest items.
     *
     * The records must arrive in chronological order, so that the last
     * of them becomes the newest item of the history.
     *
     * The caller — the framework code persisting the outcome of
     * a dispatch — passes all the records the dispatch committed in one
     * call: a complete version group, never split between calls, with
     * versions above everything this history has seen. This keeps
     * the cached run contiguous and its reads exact.
     *
     * The contract is not enforced by failing: a history cache must never
     * fail a dispatch. A group breaking it — e.g., a catch-up replay
     * re-producing the versions already seen — instead drops the whole
     * cache and is itself discarded, so the further reads fall back to
     * the storage, the source of truth. A breaking group offered to
     * an empty cache cannot be told from a legitimate one and is accepted;
     * the mismatch is bounded by the lifetime of the entity instance.
     */
    fun append(records: Iterable<R>) {
        val group = records.map { cachedFrom(it) }
        if (group.isEmpty()) {
            return
        }
        if (extendsHistory(group)) {
            group.forEach { cache.addFirst(it) }
        } else {
            cache.clear()
            exhausted = false
        }
    }

    /**
     * Reads up to [depth] most recent items of the history, newest first.
     *
     * The items already known to this instance — cached by the previous
     * reads or [appended][append] — are served from memory. If more items
     * are requested than cached, the read continues below the cached run
     * through the installed loader, caching the traversed items on its
     * way. If no loader is [installed][useLoader] — the entity was created
     * outside a repository — only the cached items are served.
     *
     * Fewer items are returned if the history retains fewer.
     *
     * @param depth The maximum number of the most recent items to read.
     * @return An iterator over the items, newest first.
     * @throws IllegalArgumentException If the [depth] is not positive.
     */
    fun read(depth: Int): Iterator<T> {
        require(depth > 0) { "History depth must be positive. Got $depth." }
        val fromCache = cache.take(depth)
        val remaining = depth - fromCache.size
        val installed = loader
        if (remaining == 0 || installed == null || exhausted) {
            return fromCache.map { it.item }.iterator()
        }
        val tail = fromCache.lastOrNull()
        val loaded = installed.load(remaining, tail?.version)
        return continueReading(
            fromCache = fromCache,
            loaded = loaded,
            remaining = remaining,
            tail = tail
        )
    }

    /**
     * Converts the given stored record into a history item.
     */
    protected abstract fun toItem(record: R): T

    /**
     * Obtains the version of the entity the given stored record belongs to.
     */
    protected abstract fun versionOf(record: R): Version

    /**
     * Creates an iterator serving the cached items first and then
     * the items loaded from the storage, populating the cache with them.
     *
     * The loader has been invoked [eagerly][read]; only the traversal of
     * its result is lazy. Once the loader supply ends short of [remaining],
     * the storage is proven to hold nothing below, and the further reads
     * are served from the cache alone. A read satisfied with exactly
     * [remaining] items leaves its oldest version group uncached — its
     * completeness cannot be proven — so the next deeper read re-reads
     * that one group from the storage.
     */
    private fun continueReading(
        fromCache: List<Cached<T>>,
        loaded: Iterator<R>,
        remaining: Int,
        tail: Cached<T>?
    ): Iterator<T> = iterator {
        fromCache.forEach { yield(it.item) }
        val population = Population(tail)
        var consumed = 0
        for (record in loaded) {
            consumed++
            val cached = cachedFrom(record)
            population.offer(cached)
            yield(cached.item)
        }
        if (consumed < remaining && population.complete()) {
            exhausted = true
        }
    }

    /**
     * Tells if the given group can extend this history at its newest end:
     * the records share one version, strictly above everything cached.
     *
     * The "strictly above" comparison tolerates numeric gaps — a dispatch
     * may advance the entity version without producing a history record.
     */
    private fun extendsHistory(group: List<Cached<T>>): Boolean {
        val number = group.first().version.number
        val uniform = group.all { it.version.number == number }
        if (!uniform) {
            return false
        }
        val head = cache.firstOrNull()
        return head == null || number > head.version.number
    }

    private fun cachedFrom(record: R): Cached<T> =
        Cached(toItem(record), versionOf(record))

    /**
     * A history item converted for serving, together with the version
     * of the entity it belongs to.
     */
    private class Cached<T : Any>(val item: T, val version: Version)

    /**
     * Populates the cache with the items traversed by a continued [read],
     * extending the cached run at its old end.
     *
     * The items are added group by group: a version group reaches
     * the cache only when proven complete — the traversal has met the next
     * group, or the storage supply has ended. A group cut short by
     * an abandoned iterator is discarded, keeping the cached run aligned
     * to group boundaries.
     *
     * Before every extension, the current end of the cache is checked to
     * be [the one this read started below][expectedTail]. A mismatch means
     * another read has populated the cache in the meantime; this population
     * then stops for good, while the read goes on serving its items.
     */
    private inner class Population(private var expectedTail: Cached<T>?) {

        private val group = mutableListOf<Cached<T>>()
        private var populating = true

        /**
         * Accepts the next traversed item.
         */
        fun offer(cached: Cached<T>) {
            if (!populating) {
                return
            }
            val groupChanged = group.isNotEmpty() &&
                    group.last().version.number != cached.version.number
            if (groupChanged) {
                flushGroup()
            }
            if (populating) {
                group.add(cached)
            }
        }

        /**
         * Flushes the last group after the storage supply has ended,
         * telling if the cache now provably holds the whole history.
         */
        fun complete(): Boolean {
            if (!populating || cache.lastOrNull() !== expectedTail) {
                return false
            }
            cache.addAll(group)
            group.clear()
            return true
        }

        private fun flushGroup() {
            if (cache.lastOrNull() === expectedTail) {
                cache.addAll(group)
                expectedTail = cache.last()
            } else {
                populating = false
            }
            group.clear()
        }
    }
}
