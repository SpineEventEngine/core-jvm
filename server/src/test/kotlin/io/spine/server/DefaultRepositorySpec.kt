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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Tests of the [DefaultRepository.of] factory and the default repositories it creates.
 */
internal class DefaultRepositorySpec {

    @Test
    fun `create a default repository for every kind of entity, with a readable 'toString'`() {
        DefaultRepository.of(ProjectAggregate::class.java).toString() shouldBe
            "DefaultRepository.of(ProjectAggregate.class)"
        DefaultRepository.of(PersonNamePart::class.java).toString() shouldBe
            "DefaultRepository.of(PersonNamePart.class)"
        DefaultRepository.of(ProjectProcessManager::class.java).toString() shouldBe
            "DefaultRepository.of(ProjectProcessManager.class)"
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
