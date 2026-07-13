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
import io.spine.server.storage.RecordSpec
import io.spine.server.storage.StorageGroup

/**
 * The specification of a [HistoryStorage].
 *
 * Folds into one value the inputs a history storage needs beyond its bounded
 * context and storage factory: the [recordSpec] persisting the history items,
 * the [historyColumns] to manage and query the history by, and the
 * [storageGroup] telling the history apart from the latest-state storage of
 * the same entity.
 *
 * The [recordSpec] must list the [historyColumns] among its columns — build it
 * with [HistoryColumns.definitions] — so that the history can be queried by them.
 *
 * @param I The type of the record identifiers.
 * @param M The type of the stored history items.
 * @property recordSpec The specification of the records persisting the history items.
 * @property historyColumns The columns to manage and query the history by.
 * @property storageGroup The group the record storage of this history belongs to.
 */
internal data class HistoryStorageSpec<I : Any, M : Message>(
    val recordSpec: RecordSpec<I, M>,
    val historyColumns: HistoryColumns<M>,
    val storageGroup: StorageGroup,
)
