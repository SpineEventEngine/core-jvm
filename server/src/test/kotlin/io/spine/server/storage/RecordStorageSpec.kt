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
import io.kotest.matchers.shouldBe
import io.spine.server.ContextSpec
import io.spine.server.ServerEnvironment
import io.spine.server.storage.given.GivenStorageProject.messageSpec
import io.spine.server.storage.given.GivenStorageProject.newId
import io.spine.server.storage.given.GivenStorageProject.newState
import io.spine.test.storage.StgProject
import io.spine.test.storage.StgProjectId
import java.util.Optional
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`RecordStorage` should")
internal class RecordStorageSpec {

    private val idOnly = fieldMask { paths += "id" }

    private val storage: RecordStorage<StgProjectId, StgProject> =
        ServerEnvironment.instance()
            .storageFactory()
            .createRecordStorage(
                ContextSpec.singleTenant(RecordStorageSpec::class.java.name),
                messageSpec()
            )

    @Test
    @Suppress("DEPRECATION") // Reason: verifies the deprecated no-op; delete with the API.
    fun `read a record ignoring the mask`() {
        val record = newState()
        storage.write(record.id, record)

        storage.read(record.id, idOnly) shouldBe Optional.of(record)
    }

    @Test
    @Suppress("DEPRECATION") // Reason: verifies the deprecated no-op; delete with the API.
    fun `read records ignoring the mask`() {
        val record = newState()
        storage.write(record.id, record)

        storage.readAll(listOf(record.id), idOnly).asSequence().toList() shouldBe listOf(record)
    }

    @Test
    @Suppress("DEPRECATION") // Reason: verifies the deprecated no-op; delete with the API.
    fun `create queries ignoring the mask`() {
        val id = newId()

        storage.toQuery(id, idOnly) shouldBe storage.toQuery(id)
        storage.toQuery(listOf(id), idOnly) shouldBe storage.toQuery(listOf(id))
    }
}
