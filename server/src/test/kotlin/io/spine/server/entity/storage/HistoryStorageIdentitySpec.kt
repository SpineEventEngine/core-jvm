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

package io.spine.server.entity.storage

import com.google.protobuf.Message
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.spine.core.Event
import io.spine.server.ContextSpec
import io.spine.server.aggregate.given.aggregate.TestAggregate
import io.spine.server.delivery.given.CalcAggregate
import io.spine.server.entity.EntityRecord
import io.spine.server.storage.RecordSpec
import io.spine.server.storage.RecordStorage
import io.spine.server.storage.StorageFactory
import io.spine.server.storage.StorageGroup
import io.spine.server.storage.memory.InMemoryStorageFactory
import io.spine.server.storage.system.SystemAwareStorageFactory
import io.spine.test.aggregate.AggProject
import io.spine.test.delivery.Calc
import io.spine.type.TypeName
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Verifies the physical identity of the per-entity history storages.
 *
 * The histories reach a storage vendor at the
 * [StorageFactory.createRecordStorage] seam, where the vendor allocates
 * the physical storage — a table, a kind — by the ([group][StorageGroup],
 * [recordType][RecordSpec.recordType]) pair it receives, naming it at its
 * discretion. The group is named after the entity state, so histories of
 * different entity types stay apart, and the two histories of one entity
 * stay apart by their record type. The latest-state records of the entity
 * arrive at the same seam belonging to no group. In-memory storages are
 * isolated per instance and cannot observe a shared table, hence the
 * assertions capture the groups and specifications at the vendor seam.
 */
@DisplayName("Per-entity history storages should")
internal class HistoryStorageIdentitySpec {

    private val context = ContextSpec.singleTenant("`HistoryStorageIdentity` tests")

    @Test
    fun `give two aggregate classes two separate event journals`() {
        val factory = SpecRecordingFactory()

        factory.createAggregateStorage(context, TestAggregate::class.java)
        factory.createAggregateStorage(context, CalcAggregate::class.java)

        factory.historyGroupsOf(Event::class.java) shouldContainExactly
                listOf(groupOf(AggProject::class.java), groupOf(Calc::class.java))
    }

    @Test
    fun `give two entity classes two separate state histories`() {
        val factory = SpecRecordingFactory()

        factory.createEntityStateHistoryStorage(context, TestAggregate::class.java)
        factory.createEntityStateHistoryStorage(context, CalcAggregate::class.java)

        factory.historyGroupsOf(EntityRecord::class.java) shouldContainExactly
                listOf(groupOf(AggProject::class.java), groupOf(Calc::class.java))
    }

    @Test
    fun `reach the vendor seam through the system-aware wrapper of the framework`() {
        val vendor = SpecRecordingFactory()
        val wrapped = SystemAwareStorageFactory.wrap(vendor)

        wrapped.createEntityStateHistoryStorage(context, TestAggregate::class.java)

        // The framework always interacts with the wrapper; the group of
        // the history must reach the wrapped vendor factory intact.
        vendor.historyIdentities() shouldContainExactly listOf(
            groupOf(AggProject::class.java) to EntityRecord::class.java
        )
    }

    @Test
    fun `keep the histories of one entity class apart from each other and from its latest state`() {
        val factory = SpecRecordingFactory()

        factory.createAggregateStorage(context, TestAggregate::class.java)
        factory.createEntityStateHistoryStorage(context, TestAggregate::class.java)

        // The two histories arrive at the vendor seam with distinct identities.
        factory.historyIdentities() shouldContainExactly listOf(
            groupOf(AggProject::class.java) to Event::class.java,
            groupOf(AggProject::class.java) to EntityRecord::class.java
        )
        // The latest-state records of the same entity class arrive at the
        // same seam, but belong to no group.
        factory.latestStateSpecs() shouldHaveSize 1
    }

    /**
     * The group expected for the histories of the entities with the given
     * state class: named after the qualified Protobuf type of the state.
     */
    private fun groupOf(stateClass: Class<out Message>): StorageGroup =
        StorageGroup(TypeName.of(stateClass).value())

    /**
     * A [StorageFactory] capturing the groups and the record specifications
     * handed to the vendor seam, delegating the actual storage to
     * the in-memory factory.
     */
    private class SpecRecordingFactory : StorageFactory {

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
         * Returns the captured groups of the histories storing items of
         * the given type, in the order of storage creation.
         */
        fun historyGroupsOf(itemType: Class<out Message>): List<StorageGroup> =
            creations.filter { (_, spec) -> spec.recordType() == itemType }
                .mapNotNull { (group, _) -> group }

        /**
         * Returns the identities — the `(group, recordType)` pairs — of
         * the captured histories, in the order of storage creation.
         */
        fun historyIdentities(): List<Pair<StorageGroup, Class<*>>> =
            creations.mapNotNull { (group, spec) ->
                group?.let { it to spec.recordType() }
            }

        /**
         * Returns the captured latest-state specifications: the `EntityRecord`
         * specs which arrived at the vendor seam belonging to no group.
         */
        fun latestStateSpecs(): List<RecordSpec<*, *>> =
            creations.filter { (group, spec) ->
                group == null && spec.recordType() == EntityRecord::class.java
            }.map { (_, spec) -> spec }

        override fun isOpen(): Boolean = delegate.isOpen

        override fun close() = delegate.close()
    }
}
