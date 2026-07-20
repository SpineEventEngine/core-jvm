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

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.assertions.throwables.shouldThrow
import io.spine.base.Identifier.newUuid
import io.spine.base.Time.currentTime
import io.spine.core.Versions
import io.spine.core.userId
import io.spine.server.BoundedContextBuilder
import io.spine.server.entity.given.entity.EntityWithMessageId
import io.spine.server.entity.given.entity.TestAggregate
import io.spine.server.entity.given.entity.TestEntityWithIdInteger
import io.spine.server.entity.given.entity.TestEntityWithIdLong
import io.spine.server.entity.given.entity.TestEntityWithIdMessage
import io.spine.server.entity.given.entity.TestEntityWithIdString
import io.spine.server.entity.given.entity.UserAggregate
import io.spine.server.entity.rejection.CannotModifyArchivedEntity
import io.spine.server.entity.rejection.CannotModifyDeletedEntity
import io.spine.test.entity.Project
import io.spine.test.entity.ProjectId
import io.spine.test.entity.projectId
import io.spine.test.user.User
import io.spine.test.user.chooseDayOfBirth
import io.spine.test.user.signUpUser
import io.spine.testdata.Sample
import io.spine.testing.TestValues.nullRef
import io.spine.testing.logging.mute.MuteLogging
import io.spine.testing.server.blackbox.BlackBox
import io.spine.testing.time.TimeTests
import io.spine.time.LocalDates
import io.spine.time.Month.FEBRUARY
import io.spine.time.Month.JANUARY
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("`Entity` should")
internal class EntitySpec {

    private lateinit var state: Project
    private lateinit var entityNew: TestEntity
    private lateinit var entityWithState: TestEntity
    private lateinit var aggregateWithState: TestAggregate

    @BeforeEach
    fun setUp() {
        state = Sample.messageOfType(Project::class.java)
        entityNew = TestEntity.newInstance(newUuid())
        entityWithState = TestEntity.withState()
        aggregateWithState = TestAggregate.withState()
    }

    @Test
    fun `not accept 'null' ID`() {
        shouldThrow<NullPointerException> { EntityWithMessageId(nullRef()) }
    }

    @Nested inner class
    `return default state` {

        @Test
        fun `for single entity`() {
            entityNew.defaultState() shouldBe Project.getDefaultInstance()
        }

        @Test
        fun `for different entities`() {
            entityNew.defaultState() shouldBe Project.getDefaultInstance()

            val entityWithMessageId = EntityWithMessageId()
            entityWithMessageId.defaultState() shouldBe Project.getDefaultInstance()
        }
    }

    @Nested inner class
    `in constructor, accept ID of type` {

        @Test
        fun `'String'`() {
            val stringId = "stringId"
            val entity = TestEntityWithIdString(stringId)

            entity.id() shouldBe stringId
        }

        @Test
        fun `'Long'`() {
            val longId = 12L
            val entity = TestEntityWithIdLong(longId)

            entity.id() shouldBe longId
        }

        @Test
        fun `'Integer'`() {
            val integerId = 12
            val entity = TestEntityWithIdInteger(integerId)

            entity.id() shouldBe integerId
        }

        @Test
        fun `'Message'`() {
            val messageId = projectId { id = "messageId" }
            val entity = TestEntityWithIdMessage(messageId)

            entity.id() shouldBe messageId
        }
    }

    @Test
    fun `have default state after construction`() {
        entityNew.state() shouldBe entityNew.defaultState()
    }

    @Test
    fun `have state`() {
        val ver = Versions.newVersion(3, currentTime())

        entityNew.updateState(state, ver)

        entityNew.state() shouldBe state
        entityNew.version() shouldBe ver
    }

    @Test
    fun `throw an exception if trying to set a 'null' state`() {
        shouldThrow<NullPointerException> {
            entityNew.updateState(nullRef(), Versions.zero())
        }
    }

    @Test
    fun `update state`() {
        entityNew.incrementState(state)

        entityNew.state() shouldBe state
    }

    /**
     * Tests that an entity state transition honors the `(set_once)`
     * validation constraint.
     */
    @MuteLogging
    @Test
    fun `check '(set_once)' on state update`() {
        val context = BoundedContextBuilder
            .assumingTests()
            .add(UserAggregate::class.java)
        val id = userId { value = newUuid() }
        val signUpUser = signUpUser { this.id = id }
        val chooseInitial = chooseDayOfBirth {
            this.id = id
            dayOfBirth = LocalDates.of(2000, JANUARY, 1)
        }
        val chooseAgain = chooseDayOfBirth {
            this.id = id
            dayOfBirth = LocalDates.of(1988, FEBRUARY, 29)
        }
        val bbc = BlackBox
            .from(context)
            .receivesCommand(signUpUser)
            .receivesCommand(chooseInitial)
            .tolerateFailures()
        bbc.receivesCommand(chooseAgain)

        val expected = User.newBuilder()
            .setDateOfBirth(chooseInitial.dayOfBirth)
            .buildPartial()

        bbc.assertEntity(id, UserAggregate::class.java)
            .hasStateThat()
            .comparingExpectedFieldsOnly()
            .isEqualTo(expected)
    }

    @Test
    fun `have zero version by default`() {
        entityNew.version().number shouldBe 0
    }

    @Nested inner class
    `increment version` {

        @Test
        fun `when told to do so`() {
            val version = entityNew.incrementVersion()

            version shouldBe 1
        }

        @Test
        fun `when updating state`() {
            entityNew.incrementState(state)

            entityNew.version().number shouldBe 1
        }
    }

    @Nested inner class
    `record modification time` {

        @Test
        fun `when incrementing version`() {
            val before = TimeTests.currentTimeSeconds()
            entityNew.incrementVersion()
            val after = TimeTests.currentTimeSeconds()

            (modificationTimeSeconds() in before..after) shouldBe true
        }

        @Test
        fun `when updating state`() {
            entityNew.incrementState(state)
            val expected = TimeTests.currentTimeSeconds()

            modificationTimeSeconds() shouldBe expected
        }

        private fun modificationTimeSeconds(): Long = entityNew.whenModified().seconds
    }

    @Nested inner class
    `provide 'equals' method such that` {

        @Test
        fun `same entities are equal`() {
            val another = TestAggregate.copyOf(aggregateWithState)

            aggregateWithState shouldBe another
        }

        @Test
        fun `entity is equal to itself`() {
            entityWithState shouldBe entityWithState
        }

        @Test
        fun `entity is not equal to 'null'`() {
            entityWithState shouldNotBe nullRef()
        }

        @Test
        fun `entity is not equal to object of another class`() {
            entityWithState shouldNotBe newUuid()
        }

        @Test
        fun `entities with different IDs are not equal`() {
            val another = TestEntity.newInstance(newUuid())

            entityWithState.id() shouldNotBe another.id()
            entityWithState shouldNotBe another
        }

        @Test
        fun `entities with different states are not equal`() {
            val another = TestEntity.withStateOf(entityWithState)
            another.updateState(Sample.messageOfType(Project::class.java), another.version())

            entityWithState.state() shouldNotBe another.state()
            entityWithState shouldNotBe another
        }

        @Test
        fun `entities with different versions are not equal`() {
            val another = TestEntity.withStateOf(entityWithState)
            another.incrementVersion()

            entityWithState shouldNotBe another
        }
    }

    @Nested inner class
    `provide 'hashCode' method such that` {

        @Test
        fun `for an entity with non-empty ID and state, a non-zero hash code is generated`() {
            entityWithState.id().id.trim().isEmpty() shouldBe false

            entityWithState.hashCode() shouldNotBe 0
        }

        @Test
        fun `for same instances, the same hash code is generated`() {
            entityWithState.hashCode() shouldBe entityWithState.hashCode()
        }

        @Test
        fun `for different instances, a unique hash code is generated`() {
            val another = TestEntity.withState()

            entityWithState.hashCode() shouldNotBe another.hashCode()
        }
    }

    @Nested inner class
    `have lifecycle flags status such that` {

        @Test
        fun `entity has default status after construction`() {
            entityNew.lifecycleFlags() shouldBe LifecycleFlags.getDefaultInstance()
        }

        @Test
        fun `entity is not archived when created`() {
            entityNew.isArchived shouldBe false
        }

        @Test
        fun `entity supports archiving`() {
            entityNew.isArchived = true

            entityNew.isArchived shouldBe true
        }

        @Test
        fun `entity supports unarchiving`() {
            entityNew.isArchived = true
            entityNew.isArchived = false

            entityNew.isArchived shouldBe false
        }

        @Test
        fun `entity is not deleted when created`() {
            entityNew.isDeleted shouldBe false
        }

        @Test
        fun `entity supports deletion`() {
            entityNew.isDeleted = true

            entityNew.isDeleted shouldBe true
        }

        @Test
        fun `entity supports restoration`() {
            entityNew.isDeleted = true
            entityNew.isDeleted = false

            entityNew.isDeleted shouldBe false
        }

        @Test
        fun `entities with different status are not equal`() {
            // Create entities with the same ID and the same (default) state.
            val id = "This very same identifier"
            val oneEntity = TestEntityWithIdString(id)
            val another = TestEntityWithIdString(id)

            another.setLifecycleFlags(lifecycleFlags { archived = true })

            oneEntity shouldNotBe another
        }

        @Test
        fun `status can be assigned`() {
            val status = lifecycleFlags {
                archived = true
                deleted = false
            }

            entityNew.setLifecycleFlags(status)

            entityNew.lifecycleFlags() shouldBe status
        }

        @Test
        fun `entity can be checked for not being archived`() {
            entityNew.isArchived = true

            // This should pass.
            entityNew.checkNotDeleted()

            shouldThrow<CannotModifyArchivedEntity> { entityNew.checkNotArchived() }
        }

        @Test
        fun `entity can be checked for not being deleted`() {
            entityNew.isDeleted = true

            // This should pass.
            entityNew.checkNotArchived()

            shouldThrow<CannotModifyDeletedEntity> { entityNew.checkNotDeleted() }
        }
    }
}
