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

package io.spine.server.storage

import com.google.protobuf.fieldMask
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.spine.server.storage.given.GivenStorageProject.newState
import io.spine.test.storage.StgProject
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@Suppress("DEPRECATION") // Checking that the deprecated API does nothing.
@DisplayName("`FieldMaskApplier` should")
internal class FieldMaskApplierSpec {

    private val applier = FieldMaskApplier<StgProject>(fieldMask { paths += "id" })

    @Test
    fun `return the passed record as-is, ignoring the mask`() {
        val record = newState()
        applier.apply(record) shouldBeSameInstanceAs record
    }

    @Test
    fun `return 'null' for 'null' input`() {
        applier.apply(null).shouldBeNull()
    }
}
