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

package io.spine.server.event.store

import com.google.protobuf.Message
import io.kotest.matchers.collections.shouldContainExactly
import io.spine.core.Event
import io.spine.server.ContextSpec
import io.spine.server.storage.RecordSpec
import io.spine.server.storage.RecordStorage
import io.spine.server.storage.StorageFactory
import io.spine.server.storage.StorageGroup
import io.spine.server.storage.memory.InMemoryStorageFactory
import io.spine.server.storage.system.SystemAwareStorageFactory
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Verifies the physical identity of the event stores of Bounded Contexts.
 *
 * The event store of each context reaches a storage vendor at the
 * [StorageFactory.createRecordStorage] seam, where the vendor allocates
 * the physical storage — a table, a kind — by the ([group][StorageGroup],
 * [recordType][RecordSpec.recordType]) pair it receives. The event stores
 * of all contexts store records of the same type, so the group, named
 * after the context, is what keeps the event log of each context in its
 * own physical storage. In-memory storages are isolated per instance and
 * cannot observe a shared table; hence, the assertions capture the
 * identities at the vendor seam.
 */
@DisplayName("Event stores of Bounded Contexts should")
internal class EventStoreIdentitySpec {

    @Test
    fun `belong to a storage group named after their context`() {
        val factory = GroupRecordingFactory()
        val context = ContextSpec.singleTenant("Billing")

        factory.createEventStore(context)

        factory.eventStoreIdentities() shouldContainExactly listOf(
            StorageGroup("Billing") to Event::class.java
        )
    }

    @Test
    fun `give two contexts two separate event logs`() {
        val factory = GroupRecordingFactory()

        factory.createEventStore(ContextSpec.singleTenant("Billing"))
        factory.createEventStore(ContextSpec.singleTenant("Shipping"))

        factory.eventStoreIdentities() shouldContainExactly listOf(
            StorageGroup("Billing") to Event::class.java,
            StorageGroup("Shipping") to Event::class.java
        )
    }

    @Test
    fun `reach the vendor seam through the system-aware wrapper of the framework`() {
        val vendor = GroupRecordingFactory()
        val wrapped = SystemAwareStorageFactory.wrap(vendor)
        val context = ContextSpec.singleTenant("Billing")

        wrapped.createEventStore(context)

        // The framework always interacts with the wrapper; the group of
        // the event store must reach the wrapped vendor factory intact.
        vendor.eventStoreIdentities() shouldContainExactly listOf(
            StorageGroup("Billing") to Event::class.java
        )
    }

    /**
     * A [StorageFactory] capturing the groups and the record specifications
     * handed to the vendor seam, delegating the actual storage to
     * the in-memory factory.
     */
    private class GroupRecordingFactory : StorageFactory {

        private val delegate = InMemoryStorageFactory.newInstance()
        private val creations = mutableListOf<Pair<StorageGroup?, RecordSpec<*, *>>>()

        override fun <I : Any, R : Message> createRecordStorage(
            context: ContextSpec,
            recordSpec: RecordSpec<I, R>,
            group: StorageGroup?
        ): RecordStorage<I, R> {
            creations.add(group to recordSpec)
            return delegate.createRecordStorage(context, recordSpec, group)
        }

        /**
         * Returns the identities — the `(group, recordType)` pairs — of
         * the created grouped storages, in the order of storage creation.
         */
        fun eventStoreIdentities(): List<Pair<StorageGroup, Class<*>>> =
            creations.mapNotNull { (group, spec) ->
                group?.let { it to spec.recordType() }
            }

        override fun isOpen(): Boolean = delegate.isOpen

        override fun close() = delegate.close()
    }
}
