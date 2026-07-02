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

package io.spine.server.aggregate

import io.spine.server.aggregate.given.Given.CommandMessage.createProject
import io.spine.server.aggregate.given.repo.FailingApplierAggregateRepository
import io.spine.server.aggregate.given.repo.ProjectAggregateRepository
import io.spine.test.aggregate.ProjectId
import io.spine.test.aggregate.event.AggProjectCreated
import io.spine.testing.logging.mute.MuteLogging
import io.spine.testing.server.blackbox.BlackBox
import io.spine.testing.server.model.ModelTests
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("An event emitted by an `Aggregate` should")
internal class AggregateEventBusSpec {

    @BeforeEach
    fun dropModels() {
        ModelTests.dropAllModels()
    }

    @Test
    @MuteLogging
    fun `not be posted to the 'EventBus' if it fails to apply`() {
        BlackBox.singleTenantWith(FailingApplierAggregateRepository()).use { context ->
            // The applier fails on purpose; tolerate it so we can assert on the `EventBus`.
            context.tolerateFailures()
                .receivesCommand(createProject(ID))

            context.assertEvents()
                .withType(AggProjectCreated::class.java)
                .isEmpty()
        }
    }

    @Test
    fun `be posted to the 'EventBus' if it is applied successfully`() {
        BlackBox.singleTenantWith(ProjectAggregateRepository()).use { context ->
            context.receivesCommand(createProject(ID))

            context.assertEvent(AggProjectCreated::class.java)
        }
    }

    private companion object {
        val ID: ProjectId = ProjectId.newBuilder()
            .setUuid("prj-eventbus-01")
            .build()
    }
}
