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

package io.spine.server

import io.kotest.matchers.shouldBe
import io.spine.server.bc.given.ProjectAggregate
import io.spine.server.bc.given.ProjectProcessManager
import io.spine.server.bc.given.ProjectReport
import io.spine.server.entity.TestEntity
import io.spine.system.server.given.entity.PersonNamePart
import io.spine.testing.server.model.ModelTests.dropAllModels
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Tests of the [DefaultRepository.of] factory and the default repositories it creates.
 */
internal class DefaultRepositorySpec {

    /**
     * Clears the model before each test.
     *
     * The fixtures used below (an aggregate and a process manager) deliberately
     * handle the same command, so modeling them in one shared model would clash.
     */
    @BeforeEach
    fun dropModels() {
        dropAllModels()
    }

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
        assertThrows<IllegalArgumentException> {
            DefaultRepository.of(TestEntity::class.java)
        }
    }
}
