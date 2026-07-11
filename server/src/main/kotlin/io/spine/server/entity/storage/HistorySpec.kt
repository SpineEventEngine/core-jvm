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

/**
 * The specification of a per-entity history: how its items are stored, and
 * which of their columns carry the history semantics.
 *
 * Combines the identification of the stored items — their types and the
 * identifier extraction — with the [HistoryColumns] every history exposes.
 * [HistoryStorage] manages and queries the history through these columns.
 *
 * @param I The type of the record identifiers.
 * @param M The type of the stored history items.
 * @param idType The class of the record identifiers.
 * @param itemType The class of the stored items.
 * @property columns The columns of the history.
 * @param extractId Obtains the record identifier of an item.
 */
public class HistorySpec<I : Any, M : Message>(
    idType: Class<I>,
    itemType: Class<M>,
    public val columns: HistoryColumns<M>,
    extractId: (M) -> I
) {

    /**
     * The specification of the record storage persisting the history items.
     */
    internal val recordSpec: RecordSpec<I, M> = RecordSpec(
        idType,
        itemType,
        // The parameter is nullable only because the SAM inherits Guava's `Function`;
        // the framework never passes `null` items.
        { item -> extractId(requireNotNull(item)) },
        columns.definitions()
    )
}
