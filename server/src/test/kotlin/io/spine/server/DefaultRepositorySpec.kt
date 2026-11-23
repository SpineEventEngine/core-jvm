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

package io.spine.server

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.spine.server.bc.given.ProjectAggregate
import io.spine.server.bc.given.ProjectProcessManager
import io.spine.server.bc.given.ProjectReport
import io.spine.server.entity.TestEntity
import io.spine.system.server.given.entity.PersonNamePart
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Tests of the [DefaultRepository.of] factory and the default repositories it creates.
 */
internal class DefaultRepositorySpec {

    @Test
    fun `create an aggregate repository with a readable 'toString'`() {
        DefaultRepository.of(ProjectAggregate::class.java).toString() shouldBe
            "DefaultRepository.of(ProjectAggregate.class)"
    }

    @Test
    fun `create an aggregate part repository with a readable 'toString'`() {
        DefaultRepository.of(PersonNamePart::class.java).toString() shouldBe
            "DefaultRepository.of(PersonNamePart.class)"
    }

    @Test
    fun `create a process manager repository with a readable 'toString'`() {
        DefaultRepository.of(ProjectProcessManager::class.java).toString() shouldBe
            "DefaultRepository.of(ProjectProcessManager.class)"
    }

    @Test
    fun `create a projection repository with a readable 'toString'`() {
        DefaultRepository.of(ProjectReport::class.java).toString() shouldBe
            "DefaultRepository.of(ProjectReport.class)"
    }

    @Test
    fun `reject an entity class that has no default repository`() {
        val exception = assertThrows<IllegalArgumentException> {
            DefaultRepository.of(TestEntity::class.java)
        }
        exception.message.shouldNotBeNull() shouldContain
            "No default repository implementation available for the class"
    }
}
