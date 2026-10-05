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

package io.spine.server.entity

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.spine.core.TenantId
import io.spine.core.Version
import io.spine.core.tenantId
import io.spine.server.entity.given.concurrency.Gate
import io.spine.server.entity.given.concurrency.Worker
import io.spine.server.tenant.TenantAwareRunner
import io.spine.server.test.shared.StringEntity
import io.spine.testing.logging.mute.MuteLogging
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests [RepositoryCache] against a [stub of the storage][StubStorage] it reads
 * from and writes to.
 *
 * The concurrency cases hold one thread inside the storage at a [Gate], and then
 * check what another thread is able to do meanwhile.
 */
@DisplayName("`RepositoryCache` should")
internal class RepositoryCacheSpec {

    private val storage = StubStorage()
    private val cache = newCache(multitenant = false)

    @Nested inner class
    `for an entity that is not cached` {

        @Test
        fun `load it from the storage on every call`() {
            val first = cache.load(A)
            val second = cache.load(A)

            second shouldNotBeSameInstanceAs first
            storage.loadsOf(A) shouldBe 2
        }

        @Test
        fun `store it to the storage right away`() {
            val entity = StubEntity(A)

            cache.store(entity)

            storage.stored shouldContainExactly listOf(entity)
        }
    }

    @Nested inner class
    `for a cached entity` {

        @Test
        fun `load it from the storage once`() {
            cache.startCaching(A)

            val first = cache.load(A)
            val second = cache.load(A)

            second shouldBeSameInstanceAs first
            storage.loadsOf(A) shouldBe 1
        }

        @Test
        fun `store it to the storage only when the caching stops`() {
            cache.startCaching(A)
            val entity = cache.load(A)

            cache.store(entity)
            cache.store(entity)
            storage.stored.shouldBeEmpty()

            cache.stopCaching(A)
            storage.stored shouldContainExactly listOf(entity)
        }

        @Test
        fun `serve and store the instance stored last`() {
            cacheAndStore(A)
            val updated = StubEntity(A)

            cache.store(updated)

            cache.load(A) shouldBeSameInstanceAs updated
            cache.stopCaching(A)
            storage.stored shouldContainExactly listOf(updated)
        }

        @Test
        fun `go back to the storage after the caching stops`() {
            val cached = cacheAndStore(A)
            cache.stopCaching(A)

            val loaded = cache.load(A)
            cache.store(loaded)

            loaded shouldNotBeSameInstanceAs cached
            storage.stored shouldContainExactly listOf(cached, loaded)
        }
    }

    /**
     * An entity is identified by its ID together with the tenant it belongs to.
     */
    @Nested inner class
    `for the same ID in different tenants` {

        private val multitenantCache = newCache(multitenant = true)
        private val alice = tenantId { value = "Alice" }
        private val bob = tenantId { value = "Bob" }

        @Test
        fun `cache the entities apart`() {
            val ofAlice = inTenant(alice) {
                multitenantCache.startCaching(A)
                multitenantCache.load(A)
            }
            val ofBob = inTenant(bob) { multitenantCache.load(A) }

            ofBob shouldNotBeSameInstanceAs ofAlice
            inTenant(alice) { multitenantCache.load(A) } shouldBeSameInstanceAs ofAlice

            // The entity is cached for Alice only, so Bob's entity goes straight
            // to the storage.
            inTenant(alice) { multitenantCache.store(ofAlice) }
            inTenant(bob) { multitenantCache.store(ofBob) }
            storage.stored shouldContainExactly listOf(ofBob)

            inTenant(alice) { multitenantCache.stopCaching(A) }
            storage.stored shouldContainExactly listOf(ofBob, ofAlice)
        }

        @Test
        fun `serve one entity while the other one is loading`() {
            val gate = Gate()
            val firstLoading = AtomicBoolean(true)
            // Hold only the first loading, which is the one for Alice.
            storage.whenLoading(A) {
                if (firstLoading.getAndSet(false)) {
                    gate.pass()
                }
            }
            val loadingForAlice = Worker { inTenant(alice) { multitenantCache.load(A) } }
            gate.awaitReached(by = loadingForAlice)

            val ofBob = Worker { inTenant(bob) { multitenantCache.load(A) } }.result()

            loadingForAlice.isDone shouldBe false
            gate.open()
            loadingForAlice.result() shouldNotBeSameInstanceAs ofBob
        }
    }

    /**
     * The storage I/O for one entity must not delay another entity of the same repository.
     */
    @Nested inner class
    `serve an entity` {

        @Test
        fun `while another one is loading`() {
            val gate = Gate()
            storage.whenLoading(A) { gate.pass() }
            val loading = Worker { cache.load(A) }
            gate.awaitReached(by = loading)

            val served = Worker { serve(B) }.result()

            loading.isDone shouldBe false
            storage.stored shouldContainExactly served
            gate.open()
            loading.result()
        }

        /**
         * The two identifiers have the same hash code. Anything selected by the hash —
         * a lock, or a bin of a hash table — is therefore shared by the two entities,
         * and must not be held while one of them is loading.
         */
        @Test
        fun `while another one with the same hash code is loading into the cache`() {
            val busy = "Aa"
            val other = "BB"
            other.hashCode() shouldBe busy.hashCode()
            cache.startCaching(busy)
            val gate = Gate()
            storage.whenLoading(busy) { gate.pass() }
            val loading = Worker { cache.load(busy) }
            gate.awaitReached(by = loading)

            val served = Worker { serve(other) }.result()

            loading.isDone shouldBe false
            storage.stored shouldContainExactly served
            gate.open()
            loading.result()
        }

        @Test
        fun `while another one is being stored`() {
            val stored = StubEntity(A)
            val gate = Gate()
            storage.whenStoring(A) { gate.pass() }
            val storing = Worker { cache.store(stored) }
            gate.awaitReached(by = storing)

            val served = Worker { serve(B) }.result()

            storing.isDone shouldBe false
            storage.stored shouldContainExactly served
            gate.open()
            storing.result()
            storage.stored shouldContainExactly served + stored
        }

        @Test
        fun `while another one is being flushed`() {
            val flushed = cacheAndStore(A)
            val gate = Gate()
            storage.whenStoring(A) { gate.pass() }
            val flushing = Worker { cache.stopCaching(A) }
            gate.awaitReached(by = flushing)

            val served = Worker { serve(B) }.result()

            flushing.isDone shouldBe false
            storage.stored shouldContainExactly served
            gate.open()
            flushing.result()
            storage.stored shouldContainExactly served + flushed
        }

        /**
         * Calls every operation of the cache for the entity with the given ID: loads
         * and stores it directly, and then once more as a batch.
         *
         * @return The entities stored, in the order of storing.
         */
        private fun serve(id: String): List<StubEntity> {
            val direct = cache.load(id)
            cache.store(direct)

            val batched = cacheAndStore(id)
            cache.stopCaching(id)
            return listOf(direct, batched)
        }
    }

    /**
     * The operations on the same entity are mutually exclusive.
     */
    @Nested inner class
    `handle one operation on an entity at a time` {

        @Test
        fun `when two threads load it`() {
            val gate = Gate()
            storage.whenLoading(A) { gate.pass() }
            val first = Worker { cache.load(A) }
            gate.awaitReached(by = first)

            val second = Worker { cache.load(A) }
            second.awaitBlocked()

            storage.loadsOf(A) shouldBe 1
            gate.open()
            first.result()
            second.result()
            storage.loadsOf(A) shouldBe 2
        }

        @Test
        fun `when two threads load it into the cache`() {
            cache.startCaching(A)
            val gate = Gate()
            storage.whenLoading(A) { gate.pass() }
            val first = Worker { cache.load(A) }
            gate.awaitReached(by = first)

            val second = Worker { cache.load(A) }
            second.awaitBlocked()
            gate.open()

            second.result() shouldBeSameInstanceAs first.result()
            storage.loadsOf(A) shouldBe 1
        }

        @Test
        fun `when one thread flushes it and another one loads it`() {
            val flushed = cacheAndStore(A)
            val gate = Gate()
            storage.whenStoring(A) { gate.pass() }
            val flushing = Worker { cache.stopCaching(A) }
            gate.awaitReached(by = flushing)

            val loading = Worker { cache.load(A) }
            loading.awaitBlocked()

            storage.loadsOf(A) shouldBe 1
            gate.open()
            flushing.result()
            // The caching has stopped by now, so the entity comes from the storage.
            loading.result() shouldNotBeSameInstanceAs flushed
            storage.loadsOf(A) shouldBe 2
        }

        /**
         * An entity stored while its predecessor is being flushed must not land in
         * the cache, which is about to be cleared: it would never reach the storage.
         */
        @Test
        fun `when one thread flushes it and another one stores it`() {
            val flushed = cacheAndStore(A)
            val gate = Gate()
            storage.whenStoring(A) { gate.pass() }
            val flushing = Worker { cache.stopCaching(A) }
            gate.awaitReached(by = flushing)

            val updated = StubEntity(A)
            val storing = Worker { cache.store(updated) }
            storing.awaitBlocked()

            storage.stored.shouldBeEmpty()
            gate.open()
            flushing.result()
            storing.result()
            // The caching has stopped by now, so the entity goes straight to the storage.
            storage.stored shouldContainExactly listOf(flushed, updated)
        }

        @Test
        fun `when one thread flushes it and another one starts caching it`() {
            cacheAndStore(A)
            val gate = Gate()
            storage.whenStoring(A) { gate.pass() }
            val flushing = Worker { cache.stopCaching(A) }
            gate.awaitReached(by = flushing)

            val starting = Worker { cache.startCaching(A) }
            starting.awaitBlocked()
            gate.open()
            flushing.result()
            starting.result()

            // The new caching started after the previous one stopped, so it is in effect.
            cache.load(A) shouldBeSameInstanceAs cache.load(A)
        }
    }

    /**
     * Once `stopCaching()` returns or throws, the entity is no longer cached — whatever
     * happened during the batch.
     */
    @Nested inner class
    `stop caching an entity` {

        /**
         * A batch ends with nothing in the cache if every loading attempt failed —
         * e.g., because the storage was not available.
         */
        @Test
        @MuteLogging
        fun `that was never loaded`() {
            cache.startCaching(A)
            cache.stopCaching(A)
            storage.stored.shouldBeEmpty()

            val loaded = cache.load(A)
            cache.store(loaded)

            cache.load(A) shouldNotBeSameInstanceAs loaded
            storage.stored shouldContainExactly listOf(loaded)
        }

        @Test
        fun `whose flush failed`() {
            val cached = cacheAndStore(A)
            val failure = IllegalStateException("The storage is not available.")
            storage.whenStoring(A) { throw failure }

            val thrown = shouldThrow<IllegalStateException> { cache.stopCaching(A) }

            thrown shouldBeSameInstanceAs failure
            storage.whenStoring(A) { }
            val loaded = cache.load(A)
            cache.store(loaded)
            loaded shouldNotBeSameInstanceAs cached
            storage.stored shouldContainExactly listOf(loaded)
        }
    }

    /**
     * The code loading an entity may come back to the cache in the same thread —
     * e.g., when creating an entity posts an event that is dispatched right away.
     *
     * The cases run in a [Worker], so that a deadlock fails the test instead of hanging it.
     */
    @Nested inner class
    `let the load function use the cache` {

        @Test
        fun `for another entity`() {
            storage.whenLoading(A) { cache.load(B) }

            Worker { cache.load(A) }.result()

            storage.loadsOf(B) shouldBe 1
        }

        @Test
        fun `for the same entity`() {
            val nested = AtomicBoolean(false)
            storage.whenLoading(A) {
                if (nested.compareAndSet(false, true)) {
                    cache.load(A)
                }
            }

            Worker { cache.load(A) }.result()

            storage.loadsOf(A) shouldBe 2
        }
    }

    /**
     * Starts caching the entity with the given ID, then loads and stores it,
     * the way a dispatch of a signal does.
     *
     * @return The entity now waiting in the cache to be flushed.
     */
    private fun cacheAndStore(id: String): StubEntity {
        cache.startCaching(id)
        val entity = cache.load(id)
        cache.store(entity)
        return entity
    }

    /**
     * Creates a cache reading from and writing to the [storage] of this suite.
     */
    private fun newCache(multitenant: Boolean): RepositoryCache<String, StubEntity> =
        RepositoryCache(multitenant, storage::load, storage::store)

    private companion object {

        const val A = "A"
        const val B = "B"

        /**
         * Runs the given action on behalf of the given tenant.
         */
        fun <T : Any> inTenant(tenant: TenantId, action: () -> T): T =
            TenantAwareRunner.with(tenant).evaluate(action)
    }
}

/**
 * An entity served through the cache under test.
 *
 * Only the identifier and the identity of an instance matter to the tests.
 * The class does not override `equals()`, so the assertions on collections of
 * these entities compare the instances.
 *
 * @property id The identifier of the entity.
 */
private class StubEntity(private val id: String) : Entity<String, StringEntity> {

    override fun id(): String = id

    override fun state(): StringEntity = StringEntity.getDefaultInstance()

    override fun lifecycleFlagsChanged(): Boolean = false

    override fun version(): Version = Version.getDefaultInstance()

    override fun getLifecycleFlags(): LifecycleFlags = LifecycleFlags.getDefaultInstance()
}

/**
 * Stands in for the storage behind the cache under test.
 *
 * Creates a new entity on each [load], records the calls, and runs the actions
 * a test [assigns][whenLoading] to the calls for a particular entity — to hold
 * a call at a [Gate], to fail it, or to call the cache back.
 */
private class StubStorage {

    private val loaded = CopyOnWriteArrayList<String>()
    private val storedEntities = CopyOnWriteArrayList<StubEntity>()
    private val onLoad = ConcurrentHashMap<String, () -> Unit>()
    private val onStore = ConcurrentHashMap<String, () -> Unit>()

    /**
     * The entities passed to [store], in the order of the calls that have completed.
     */
    val stored: List<StubEntity>
        get() = storedEntities

    /**
     * Tells how many times the loading of the entity with the given ID has started.
     */
    fun loadsOf(id: String): Int = loaded.count { it == id }

    /**
     * Makes each loading of the entity with the given ID run the given action first.
     */
    fun whenLoading(id: String, action: () -> Unit) {
        onLoad[id] = action
    }

    /**
     * Makes each storing of the entity with the given ID run the given action first.
     */
    fun whenStoring(id: String, action: () -> Unit) {
        onStore[id] = action
    }

    fun load(id: String): StubEntity {
        loaded.add(id)
        onLoad[id]?.invoke()
        return StubEntity(id)
    }

    fun store(entity: StubEntity) {
        onStore[entity.id()]?.invoke()
        storedEntities.add(entity)
    }
}
