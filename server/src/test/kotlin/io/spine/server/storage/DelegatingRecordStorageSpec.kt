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

import com.google.protobuf.FieldMask
import com.google.protobuf.fieldMask
import io.kotest.matchers.shouldBe
import io.spine.server.ContextSpec
import io.spine.server.ServerEnvironment
import io.spine.server.storage.given.GivenStorageProject.messageSpec
import io.spine.server.storage.given.GivenStorageProject.newState
import io.spine.test.storage.StgProject
import io.spine.test.storage.StgProjectId
import java.util.Optional
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`DelegatingRecordStorage` should")
internal class DelegatingRecordStorageSpec {

    private val idOnly = fieldMask { paths += "id" }

    @Test
    @Suppress("DEPRECATION") // Checking that the deprecated API ignores the mask.
    fun `read a record ignoring the mask even if its delegate honors masks`() {
        val storage = storageOverMaskHonoringDelegate()
        val record = newState()
        storage.write(record.id, record)

        storage.read(record.id, idOnly) shouldBe Optional.of(record)
    }

    @Test
    @Suppress("DEPRECATION") // Checking that the deprecated API ignores the mask.
    fun `read records ignoring the mask even if its delegate honors masks`() {
        val storage = storageOverMaskHonoringDelegate()
        val record = newState()
        storage.write(record.id, record)

        storage.readAll(listOf(record.id), idOnly).asSequence().toList() shouldBe listOf(record)
    }

    /**
     * Creates a storage delegating to a storage which still honors field masks,
     * as a storage built against the previous API may do.
     */
    @Suppress("OVERRIDE_DEPRECATION") // Mimicking a storage built against the previous API.
    private fun storageOverMaskHonoringDelegate():
            DelegatingRecordStorage<StgProjectId, StgProject> {
        val context = ContextSpec.singleTenant(DelegatingRecordStorageSpec::class.java.name)
        val inMemory = ServerEnvironment.instance()
            .storageFactory()
            .createRecordStorage(context, messageSpec())
        val maskHonoring =
            object : DelegatingRecordStorage<StgProjectId, StgProject>(context, inMemory) {
                override fun read(id: StgProjectId, mask: FieldMask): Optional<StgProject> =
                    Optional.empty()

                override fun readAll(
                    ids: Iterable<StgProjectId>,
                    mask: FieldMask
                ): Iterator<StgProject> = emptyList<StgProject>().iterator()
            }
        return object : DelegatingRecordStorage<StgProjectId, StgProject>(context, maskHonoring) {}
    }
}
