/*
 * Copyright 2022, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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

package io.spine.server.entity;

import com.google.common.collect.Range;
import com.google.common.truth.LongSubject;
import io.spine.core.UserId;
import io.spine.core.Versions;
import io.spine.server.BoundedContextBuilder;
import io.spine.server.entity.given.entity.EntityWithMessageId;
import io.spine.server.entity.given.entity.TestAggregate;
import io.spine.server.entity.given.entity.TestEntityWithIdInteger;
import io.spine.server.entity.given.entity.TestEntityWithIdLong;
import io.spine.server.entity.given.entity.TestEntityWithIdMessage;
import io.spine.server.entity.given.entity.TestEntityWithIdString;
import io.spine.server.entity.given.entity.UserAggregate;
import io.spine.server.entity.rejection.CannotModifyArchivedEntity;
import io.spine.server.entity.rejection.CannotModifyDeletedEntity;
import io.spine.test.entity.Project;
import io.spine.test.entity.ProjectId;
import io.spine.test.user.ChooseDayOfBirth;
import io.spine.test.user.SignUpUser;
import io.spine.test.user.User;
import io.spine.testdata.Sample;
import io.spine.testing.logging.mute.MuteLogging;
import io.spine.testing.server.blackbox.BlackBox;
import io.spine.time.LocalDates;
import io.spine.time.testing.TimeTests;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.base.Identifier.newUuid;
import static io.spine.base.Time.currentTime;
import static io.spine.testing.TestValues.nullRef;
import static io.spine.time.Month.FEBRUARY;
import static io.spine.time.Month.JANUARY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("`Entity` should")
class EntityTest {

    private Project state = Sample.messageOfType(Project.class);
    private TestEntity entityNew;
    private TestEntity entityWithState;
    private TestAggregate aggregateWithState;

    @BeforeEach
    void setUp() {
        state = Sample.messageOfType(Project.class);
        entityNew = TestEntity.newInstance(newUuid());
        entityWithState = TestEntity.withState();
        aggregateWithState = TestAggregate.withState();
    }

    @Test
    @DisplayName("not accept `null` ID")
    @SuppressWarnings("ResultOfObjectAllocationIgnored") // Because we expect the exception.
    void notAcceptNullId() {
        assertThrows(NullPointerException.class, () -> new EntityWithMessageId(nullRef()));
    }

    @Nested
    @DisplayName("return default state")
    class ReturnDefaultState {

        @Test
        @DisplayName("for single entity")
        void forSingleEntity() {
            var state = entityNew.defaultState();
            assertEquals(Project.getDefaultInstance(), state);
        }

        @Test
        @DisplayName("for different entities")
        void forDifferentEntities() {
            assertEquals(Project.getDefaultInstance(), entityNew.defaultState());

            var entityWithMessageId = new EntityWithMessageId();
            var expected = Project.getDefaultInstance();
            assertEquals(expected, entityWithMessageId.defaultState());
        }
    }

    @Nested
    @DisplayName("in constructor, accept ID of type")
    class AcceptId {

        @Test
        @DisplayName("`String`")
        void ofStringType() {
            var stringId = "stringId";
            var entityWithStringId = new TestEntityWithIdString(stringId);

            assertEquals(stringId, entityWithStringId.id());
        }

        @Test
        @DisplayName("`Long`")
        void ofLongType() {
            Long longId = 12L;
            var entityWithLongId = new TestEntityWithIdLong(longId);

            assertEquals(longId, entityWithLongId.id());
        }

        @Test
        @DisplayName("`Integer`")
        void ofIntegerType() {
            Integer integerId = 12;
            var entityWithIntegerId = new TestEntityWithIdInteger(integerId);

            assertEquals(integerId, entityWithIntegerId.id());
        }

        @Test
        @DisplayName("`Message`")
        void ofMessageType() {
            var messageId = ProjectId.newBuilder()
                    .setId("messageId")
                    .build();
            var entityWithMessageID = new TestEntityWithIdMessage(messageId);

            assertEquals(messageId, entityWithMessageID.id());
        }
    }

    @Test
    @DisplayName("have default state after construction")
    void defaultState() {
        assertEquals(entityNew.defaultState(), entityNew.state());
    }

    @Test
    @DisplayName("have state")
    void haveState() {
        var ver = Versions.newVersion(3, currentTime());

        entityNew.updateState(state, ver);

        assertEquals(state, entityNew.state());
        assertEquals(ver, entityNew.version());
    }

    @Test
    @DisplayName("throw exception if try to set null state")
    void throwOnSettingNullState() {
        assertThrows(NullPointerException.class,
                     () -> entityNew.updateState(nullRef(), Versions.zero()));
    }

    @Test
    @DisplayName("update state")
    void updateState() {
        entityNew.incrementState(state);

        assertEquals(state, entityNew.state());
    }

    /**
     * Tests that an entity state transition honors the {@code (set_once)}
     * validation constraint.
     */
    @MuteLogging
    @Test
    @DisplayName("check `(set_once)` on state update")
    void setOnce() {
        var context = BoundedContextBuilder
                .assumingTests()
                .add(UserAggregate.class);
        var id = UserId.newBuilder()
                .setValue(newUuid())
                .build();
        var signUpUser = SignUpUser.newBuilder()
                .setId(id)
                .build();
        var chooseInitial = ChooseDayOfBirth.newBuilder()
                .setId(id)
                .setDayOfBirth(LocalDates.of(2000, JANUARY, 1))
                .build();
        var chooseAgain = ChooseDayOfBirth.newBuilder()
                .setId(id)
                .setDayOfBirth(LocalDates.of(1988, FEBRUARY, 29))
                .build();
        var bbc = BlackBox
                .from(context)
                .receivesCommand(signUpUser)
                .receivesCommand(chooseInitial)
                .tolerateFailures();
        bbc.receivesCommand(chooseAgain);

        var expected = User.newBuilder()
                .setDateOfBirth(chooseInitial.getDayOfBirth())
                .buildPartial();

        bbc.assertEntity(id, UserAggregate.class)
           .hasStateThat()
           .comparingExpectedFieldsOnly()
           .isEqualTo(expected);
    }

    @Test
    @DisplayName("have zero version by default")
    void haveZeroVersionByDefault() {
        assertEquals(0, entityNew.version().getNumber());
    }

    @Nested
    @DisplayName("increment version")
    class IncrementVersion {

        @Test
        @DisplayName("when told to do so")
        void whenAsked() {
            var version = entityNew.incrementVersion();
            assertEquals(1, version);
        }

        @Test
        @DisplayName("when updating state")
        void whenUpdatingState() {
            entityNew.incrementState(state);

            assertEquals(1, entityNew.version()
                                     .getNumber());
        }
    }

    @Nested
    @DisplayName("record modification time")
    class RecordModificationTime {

        @SuppressWarnings("CheckReturnValue")
        // New entity version number can be ignored in this test.
        @Test
        @DisplayName("when incrementing version")
        void onVersionIncrement() {
            var timeBeforeIncrement = TimeTests.currentTimeSeconds();
            entityNew.incrementVersion();
            var timeAfterIncrement = TimeTests.currentTimeSeconds();

            assertModificationTime()
                    .isIn(Range.closed(timeBeforeIncrement, timeAfterIncrement));
        }

        @Test
        @DisplayName("when updating state")
        void onStateUpdate() {
            entityNew.incrementState(state);
            var expectedTimeSec = TimeTests.currentTimeSeconds();
            assertModificationTime()
                    .isEqualTo(expectedTimeSec);
        }

        private LongSubject assertModificationTime() {
            return assertThat(entityNew.whenModified()
                                       .getSeconds());
        }
    }

    @Nested
    @DisplayName("provide `equals` method such that")
    class ProvideEqualsSuchThat {

        @Test
        @DisplayName("same entities are equal")
        void equalToSame() {
            var another = TestAggregate.copyOf(aggregateWithState);

            assertEquals(aggregateWithState, another);
        }

        @Test
        @DisplayName("entity is equal to itself")
        @SuppressWarnings("EqualsWithItself") /* is the purpose of the test.
            We should probably rewrite these tests using `EqualsTester` from Guava. */
        void equalToItself() {
            assertEquals(entityWithState, entityWithState);
        }

        @Test
        @DisplayName("entity is not equal to `null`")
        void notEqualToNull() {
            assertNotEquals(entityWithState, nullRef());
        }

        @Test
        @DisplayName("entity is not equal to object of another class")
        @SuppressWarnings("AssertBetweenInconvertibleTypes") /* That's the point. */
        void notEqualToOtherClass() {
            assertNotEquals(entityWithState, newUuid());
        }

        @Test
        @DisplayName("entities with different IDs are not equal")
        void notEqualToDifferentId() {
            var another = TestEntity.newInstance(newUuid());

            assertNotEquals(entityWithState.id(), another.id());
            assertNotEquals(entityWithState, another);
        }

        @Test
        @DisplayName("entities with different states are not equal")
        void notEqualToDifferentState() {
            var another = TestEntity.withStateOf(entityWithState);
            another.updateState(Sample.messageOfType(Project.class), another.version());

            assertNotEquals(entityWithState.state(), another.state());
            assertNotEquals(entityWithState, another);
        }

        @Test
        @DisplayName("entities with different versions are not equal")
        @SuppressWarnings("CheckReturnValue") // The entity version can be ignored in this test.
        void notEqualToDifferentVersion() {
            var another = TestEntity.withStateOf(entityWithState);
            another.incrementVersion();

            assertNotEquals(entityWithState, another);
        }
    }

    @Nested
    @DisplayName("provide `hashCode` method such that")
    class ProvideHashCode {

        @Test
        @DisplayName("for entity with non-empty ID and state, non-zero hash code is generated")
        void nonZeroForNonEmptyEntity() {
            assertFalse(entityWithState.id()
                                       .getId()
                                       .trim()
                                       .isEmpty());

            var hashCode = entityWithState.hashCode();

            assertTrue(hashCode != 0);
        }

        @Test
        @DisplayName("for same instances, same hash code is generated")
        void sameForSameInstances() {
            assertEquals(entityWithState.hashCode(), entityWithState.hashCode());
        }

        @Test
        @DisplayName("for different instances, unique hash code is generated")
        void uniqueForDifferentInstances() {
            var another = TestEntity.withState();

            assertNotEquals(entityWithState.hashCode(), another.hashCode());
        }
    }

    @Nested
    @DisplayName("have lifecycle flags status such that")
    class HaveLifecycleFlagsStatus {

        @Test
        @DisplayName("entity has default status after construction")
        void defaultOnCreation() {
            assertEquals(LifecycleFlags.getDefaultInstance(), entityNew.lifecycleFlags());
        }

        @Test
        @DisplayName("entity is not archived when created")
        void notArchivedOnCreation() {
            assertFalse(entityNew.isArchived());
        }

        @Test
        @DisplayName("entity supports archiving")
        void supportArchiving() {
            entityNew.setArchived(true);

            assertTrue(entityNew.isArchived());
        }

        @Test
        @DisplayName("entity supports unarchiving")
        void supportUnarchiving() {
            entityNew.setArchived(true);
            entityNew.setArchived(false);

            assertFalse(entityNew.isArchived());
        }

        @Test
        @DisplayName("entity is not deleted when created")
        void notDeletedOnCreation() {
            assertFalse(entityNew.isDeleted());
        }

        @Test
        @DisplayName("entity supports deletion")
        void supportDeletion() {
            entityNew.setDeleted(true);

            assertTrue(entityNew.isDeleted());
        }

        @Test
        @DisplayName("entity supports restoration")
        void supportRestoration() {
            entityNew.setDeleted(true);
            entityNew.setDeleted(false);
            assertFalse(entityNew.isDeleted());
        }

        @Test
        @DisplayName("entities with different status are not equal")
        void consideredForEquality() {
            // Create entities with the same ID and the same (default) state.
            var id = "This very same identifier";
            AbstractEntity<?, ?> oneEntity = new TestEntityWithIdString(id);
            AbstractEntity<?, ?> another = new TestEntityWithIdString(id);

            another.setArchived(true);

            assertNotEquals(oneEntity, another);
        }

        @Test
        @DisplayName("status can be assigned")
        void supportAssignment() {
            var status = LifecycleFlags.newBuilder()
                                                  .setArchived(true)
                                                  .setDeleted(false)
                                                  .build();
            entityNew.setLifecycleFlags(status);
            assertEquals(status, entityNew.lifecycleFlags());
        }

        @Test
        @DisplayName("entity can be checked for not being archived")
        void supportNotArchivedCheck() throws Throwable {
            entityNew.setArchived(true);

            // This should pass.
            entityNew.checkNotDeleted();

            assertThrows(CannotModifyArchivedEntity.class, () -> entityNew.checkNotArchived());
        }

        @Test
        @DisplayName("entity can be checked for not being deleted")
        void supportNotDeletedCheck() throws Throwable {
            entityNew.setDeleted(true);

            // This should pass.
            entityNew.checkNotArchived();

            assertThrows(CannotModifyDeletedEntity.class, () -> entityNew.checkNotDeleted());
        }
    }
}
