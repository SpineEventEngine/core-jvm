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

package io.spine.server.entity

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.spine.base.Identifier
import io.spine.client.EntityId
import io.spine.server.given.groups.GroupId
import io.spine.test.entity.ProjectId
import io.spine.testdata.Sample
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`EntityIdFunction` should")
internal class EntityIdFunctionSpec {

    @Test
    fun `unpack an identifier of the expected type`() {
        val id = Sample.messageOfType(ProjectId::class.java)
        val entityId = EntityId.newBuilder()
            .setId(Identifier.pack(id))
            .build()

        val unpacked = EntityIdFunction(ProjectId::class.java).apply(entityId)

        unpacked shouldBe id
    }

    @Test
    fun `reject an identifier of an unexpected type`() {
        val alien = Sample.messageOfType(GroupId::class.java)
        val entityId = EntityId.newBuilder()
            .setId(Identifier.pack(alien))
            .build()

        shouldThrow<IllegalStateException> {
            EntityIdFunction(ProjectId::class.java).apply(entityId)
        }
    }
}
