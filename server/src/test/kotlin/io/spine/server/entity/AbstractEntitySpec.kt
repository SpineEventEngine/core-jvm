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

import com.google.common.testing.EqualsTester
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.spine.protobuf.AnyPacker
import io.spine.server.entity.given.entity.AnEntity
import io.spine.server.entity.given.entity.NaturalNumberEntity
import io.spine.server.test.shared.LongIdAggregate
import io.spine.server.test.shared.longIdAggregate
import io.spine.test.entity.Project
import io.spine.test.entity.ProjectId
import io.spine.test.entity.project
import io.spine.test.entity.projectId
import io.spine.test.server.number.NaturalNumber
import io.spine.validation.NonValidated
import io.spine.validation.ValidationError
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`AbstractEntity` should")
internal class AbstractEntitySpec {

    @Test
    fun `throw 'InvalidEntityStateException' if state is invalid`() {
        val entity: AbstractEntity<*, NaturalNumber> = NaturalNumberEntity(0)
        val invalidNaturalNumber = newNaturalNumber(-1)

        // This should pass.
        entity.updateState(newNaturalNumber(1))

        // This should fail.
        val exception = shouldThrow<InvalidEntityStateException> {
            entity.updateState(invalidNaturalNumber)
        }
        val details = AnyPacker.unpack(exception.error().details)
        details.shouldBeInstanceOf<ValidationError>()
        details.constraintViolationList shouldHaveSize 1
    }

    @Test
    fun `allow valid state`() {
        val entity = CheckingEntity(17L)
        val state = longIdAggregate { id = entity.id }
        entity.check(state).shouldBeEmpty()
    }

    @Test
    fun `return string ID`() {
        val entity = AnEntity(1_234_567L)

        entity.idAsString() shouldBe "1234567"
        entity.idAsString() shouldBeSameInstanceAs entity.idAsString()
    }

    @Test
    fun `support equality`() {
        val id = avProjectId("88")
        val entity = AvEntity(id)
        val similarEntity = AvEntity(id)
        similarEntity.updateState(entity.state(), entity.version())

        val different = AvEntity(avProjectId("42"))
        EqualsTester().addEqualityGroup(entity, similarEntity)
            .addEqualityGroup(different)
            .testEquals()
    }

    /**
     * An entity re-exposing the `protected` state check for the assertions.
     */
    private class CheckingEntity(id: Long) : AbstractEntity<Long, LongIdAggregate>(id) {

        fun check(state: LongIdAggregate) = checkEntityState(state)
    }

    private class AvEntity(id: ProjectId) : AbstractEntity<ProjectId, Project>(id) {
        init {
            val projectId = id
            updateState(project { this.id = projectId })
        }
    }

    private fun avProjectId(value: String): ProjectId = projectId { id = value }

    private fun newNaturalNumber(value: Int): @NonValidated NaturalNumber =
        NaturalNumber.newBuilder()
            .setValue(value)
            .buildPartial()
}
