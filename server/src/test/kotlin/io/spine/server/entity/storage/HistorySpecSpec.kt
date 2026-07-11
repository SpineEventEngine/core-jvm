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

import io.kotest.matchers.shouldBe
import io.spine.server.entity.EntityRecord
import io.spine.server.entity.EntityStateId
import io.spine.test.storage.StgProject
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`HistorySpec` should")
internal class HistorySpecSpec {

    @Test
    fun `identify the physical storage by the given source type`() {
        val spec = HistorySpec(
            EntityStateId::class.java,
            EntityRecord::class.java,
            StgProject::class.java,
            EntityStateHistoryColumns
        ) { EntityStateId.getDefaultInstance() }

        spec.recordSpec.sourceType() shouldBe StgProject::class.java
    }

    @Test
    fun `identify the physical storage by the item type unless told otherwise`() {
        val spec = HistorySpec(
            EntityStateId::class.java,
            EntityRecord::class.java,
            EntityStateHistoryColumns
        ) { EntityStateId.getDefaultInstance() }

        spec.recordSpec.sourceType() shouldBe EntityRecord::class.java
    }
}
