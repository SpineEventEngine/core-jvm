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
import io.spine.core.Event
import io.spine.server.ContextSpec
import io.spine.server.aggregate.given.aggregate.TestAggregate
import io.spine.server.delivery.given.CalcAggregate
import io.spine.server.entity.EntityRecord
import io.spine.server.storage.RecordSpec
import io.spine.server.storage.RecordStorage
import io.spine.server.storage.StorageFactory
import io.spine.server.storage.memory.InMemoryStorageFactory
import io.spine.test.aggregate.AggProject
import io.spine.test.delivery.Calc
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Verifies the physical identity of the per-entity history storages.
 *
 * Storage vendors allocate the physical storage — a table, a kind — by
 * the [source type][RecordSpec.sourceType] of the record specification
 * they receive, so the histories of two entity types must reach the
 * vendor with two different source types. In-memory storages are isolated
 * per instance and cannot observe a shared table, hence the assertions
 * capture the specifications at the vendor seam,
 * [StorageFactory.createRecordStorage].
 */
@DisplayName("Per-entity history storages should")
internal class HistoryStorageIdentitySpec {

    private val context = ContextSpec.singleTenant("`HistoryStorageIdentity` tests")

    @Test
    fun `give two aggregate classes two separate event journals`() {
        val factory = SpecRecordingFactory()

        factory.createAggregateStorage(context, TestAggregate::class.java)
        factory.createAggregateStorage(context, CalcAggregate::class.java)

        factory.sourcesOf(Event::class.java) shouldContainExactly
                listOf(AggProject::class.java, Calc::class.java)
    }

    @Test
    fun `give two entity state classes two separate state histories`() {
        val factory = SpecRecordingFactory()

        factory.createEntityStateHistoryStorage(context, AggProject::class.java)
        factory.createEntityStateHistoryStorage(context, Calc::class.java)

        factory.sourcesOf(EntityRecord::class.java) shouldContainExactly
                listOf(AggProject::class.java, Calc::class.java)
    }

    /**
     * A [StorageFactory] capturing the record specifications handed to
     * the vendor seam, delegating the actual storage to the in-memory factory.
     */
    private class SpecRecordingFactory : StorageFactory {

        private val delegate = InMemoryStorageFactory.newInstance()
        private val specs = mutableListOf<RecordSpec<*, *>>()

        override fun <I : Any, R : Message> createRecordStorage(
            context: ContextSpec,
            recordSpec: RecordSpec<I, R>
        ): RecordStorage<I, R> {
            specs.add(recordSpec)
            return delegate.createRecordStorage(context, recordSpec)
        }

        /**
         * Returns the captured source types of the specifications storing
         * records of the given type, in the order of storage creation.
         */
        fun sourcesOf(recordType: Class<out Message>): List<Class<*>> =
            specs.filter { it.recordType() == recordType }
                .map { it.sourceType() }

        override fun isOpen(): Boolean = delegate.isOpen

        override fun close() = delegate.close()
    }
}
