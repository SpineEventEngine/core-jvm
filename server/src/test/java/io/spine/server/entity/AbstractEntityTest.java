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

import com.google.common.reflect.Invokable;
import com.google.common.testing.EqualsTester;
import io.spine.base.EntityState;
import io.spine.protobuf.AnyPacker;
import io.spine.server.entity.given.entity.AnEntity;
import io.spine.server.entity.given.entity.NaturalNumberEntity;
import io.spine.server.test.shared.LongIdAggregate;
import io.spine.test.entity.Project;
import io.spine.test.entity.ProjectId;
import io.spine.test.server.number.NaturalNumber;
import io.spine.validation.ValidationError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@DisplayName("`AbstractEntity` should")
class AbstractEntityTest {

    @Nested
    @DisplayName("not allow to override method")
    class NotAllowToOverride {

        /**
         * Ensures that {@link AbstractEntity#updateState(EntityState)} is final so that
         * it's not possible to override the default behaviour.
         */
        @Test
        @DisplayName("`updateState`")
        void updateState() throws NoSuchMethodException {
            var updateState =
                    AbstractEntity.class.getDeclaredMethod("updateState", EntityState.class);
            var modifiers = updateState.getModifiers();
            assertTrue(Modifier.isFinal(modifiers));
        }

        /**
         * Ensures that the {@code AbstractEntity.validate(EntityState)} method is final so that
         * it's not possible to override the default behaviour.
         */
        @Test
        @DisplayName("`validate`")
        void validate() throws NoSuchMethodException {
            var validate =
                    AbstractEntity.class.getDeclaredMethod("validate", EntityState.class);
            var modifiers = validate.getModifiers();
            assertTrue(Modifier.isPrivate(modifiers) || Modifier.isFinal(modifiers));
        }
    }

    @Test
    @DisplayName("throw InvalidEntityStateException if state is invalid")
    void rejectInvalidState() {
        AbstractEntity<?, NaturalNumber> entity = new NaturalNumberEntity(0);
        var invalidNaturalNumber = newNaturalNumber(-1);
        try {
            // This should pass.
            entity.updateState(newNaturalNumber(1));

            // This should fail.
            entity.updateState(invalidNaturalNumber);

            fail("Exception expected.");
        } catch (InvalidEntityStateException e) {
            var details = AnyPacker.unpack(e.error().getDetails());
            assertThat(details).isInstanceOf(ValidationError.class);
            assertThat(((ValidationError)details).getConstraintViolationList())
                    .hasSize(1);
        }
    }

    @SuppressWarnings("ConstantConditions") // The goal of the test.
    @Test
    @DisplayName("not accept null to `checkEntityState`")
    void rejectNullState() {
        var entity = new AnEntity(0L);

        assertThrows(NullPointerException.class, () -> entity.checkEntityState(null));
    }

    @Test
    @DisplayName("allow valid state")
    void allowValidState() {
        var entity = new AnEntity(17L);
        var state = LongIdAggregate.newBuilder()
                .setId(entity.id())
                .build();
        assertTrue(entity.checkEntityState(state)
                         .isEmpty());
    }

    @Test
    @DisplayName("return string ID")
    void returnStringId() {
        var entity = new AnEntity(1_234_567L);

        assertEquals("1234567", entity.idAsString());
        assertSame(entity.idAsString(), entity.idAsString());
    }

    @Test
    @DisplayName("support equality")
    @SuppressWarnings("MagicNumber")
    void supportEquality() {
        var id = AvEntity.projectId("88");
        var entity = new AvEntity(id);
        var similarEntity = new AvEntity(id);
        similarEntity.updateState(entity.state(), entity.version());

        var different = new AvEntity(AvEntity.projectId("42"));
        new EqualsTester().addEqualityGroup(entity, similarEntity)
                          .addEqualityGroup(different)
                          .testEquals();
    }

    @Test
    @DisplayName("have `updateState` method visible to package only")
    void haveUpdateStatePackagePrivate() {
        var methodFound = false;

        var methods = AbstractEntity.class.getDeclaredMethods();
        for (var method : methods) {
            if ("updateState".equals(method.getName())) {
                var updateState = Invokable.from(method);
                assertTrue(updateState.isPackagePrivate());
                methodFound = true;
            }
        }
        assertTrue(methodFound,
                   "Cannot check 'updateState(...)' in " + AbstractEntity.class);
    }

    private static class AvEntity extends AbstractEntity<ProjectId, Project> {

        private AvEntity(ProjectId id) {
            super(id);
            updateState(Project.newBuilder().setId(id).build());
        }

        static ProjectId projectId(String value) {
            return ProjectId.newBuilder()
                    .setId(value)
                    .build();
        }
    }

    static NaturalNumber newNaturalNumber(int value) {
        return NaturalNumber.newBuilder()
                .setValue(value)
                .buildPartial();
    }
}
