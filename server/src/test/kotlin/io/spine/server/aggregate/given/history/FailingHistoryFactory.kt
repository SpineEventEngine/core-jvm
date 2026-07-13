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

package io.spine.server.aggregate.given.history

import com.google.protobuf.Message
import io.spine.server.ContextSpec
import io.spine.server.entity.EntityRecord
import io.spine.server.storage.RecordSpec
import io.spine.server.storage.StorageGroup
import io.spine.server.storage.RecordStorage
import io.spine.server.storage.RecordStorageDelegate
import io.spine.server.storage.RecordWithColumns
import io.spine.server.storage.StorageFactory
import io.spine.server.storage.memory.InMemoryStorageFactory

/**
 * A storage factory whose *state* history storages fail to write.
 *
 * The event journals — and every other storage — work normally, so
 * a dispatch proceeds until the state history append. Serves the spec
 * cases pinning the fail-loud contract of the append: a failure to
 * record the history fails the dispatch.
 */
internal class FailingHistoryFactory : StorageFactory {

    private val delegate = InMemoryStorageFactory.newInstance()

    override fun <I : Any, R : Message> createRecordStorage(
        context: ContextSpec,
        group: StorageGroup?,
        recordSpec: RecordSpec<I, R>
    ): RecordStorage<I, R> {
        val actual = delegate.createRecordStorage(context, group, recordSpec)
        // Fail only the state-history writes: a grouped storage of `EntityRecord`s.
        // The event journal (of `Event`s) and the latest-state storage (belonging
        // to no group) keep working, so a dispatch proceeds until the append.
        return if (group != null && recordSpec.recordType() == EntityRecord::class.java) {
            FailingWrites(context, actual)
        } else {
            actual
        }
    }

    override fun isOpen(): Boolean = delegate.isOpen

    override fun close() = delegate.close()
}

/**
 * A record storage failing each write.
 */
private class FailingWrites<I : Any, R : Message>(
    context: ContextSpec,
    delegate: RecordStorage<I, R>
) : RecordStorageDelegate<I, R>(context, delegate) {

    override fun write(record: RecordWithColumns<I, R>): Unit =
        error("The history storage always fails to write.")
}
