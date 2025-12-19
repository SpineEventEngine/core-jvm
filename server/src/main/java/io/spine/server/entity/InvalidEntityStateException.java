/*
 * Copyright 2025, TeamDev. All rights reserved.
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

package io.spine.server.entity;

import com.google.common.collect.ImmutableMap;
import com.google.protobuf.Message;
import com.google.protobuf.Value;
import io.spine.base.EntityState;
import io.spine.base.Error;
import io.spine.protobuf.AnyPacker;
import io.spine.server.entity.model.StateClass;
import io.spine.type.TypeName;
import io.spine.validation.ConstraintViolation;
import io.spine.validation.ExceptionFactory;
import io.spine.validation.ValidationError;
import io.spine.validation.ValidationException;
import org.jspecify.annotations.NonNull;

import java.io.Serial;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static io.spine.server.entity.EntityStateValidationError.INVALID_ENTITY_STATE;

/**
 * Signals that an entity state does not pass validation.
 */
public final class InvalidEntityStateException extends ValidationException {

    @Serial
    private static final long serialVersionUID = 0L;

    private static final String MSG_VALIDATION_ERROR =
            "Entity state does match the validation constraints";

    /**
     * The entity state which is invalid.
     */
    private final EntityState<?> state;

    /**
     * The error passed with the exception.
     */
    private final Error error;

    private InvalidEntityStateException(EntityState<?> state, Error error) {
        super(unpackViolations(error));
        this.state = state;
        this.error = error;
    }

    /**
     * Creates an exception instance for an entity state, which has fields that
     * violate validation constraint(s).
     *
     * @param state
     *         the invalid entity state
     * @param violations
     *         the constraint violations for the entity state
     */
    public static InvalidEntityStateException
    onConstraintViolations(EntityState<?> state, Iterable<ConstraintViolation> violations) {
        var factory = new Factory(state, violations);
        return factory.newException();
    }

    /**
     * Returns a related event message.
     */
    EntityState<?> entityState() {
        return state;
    }

    /**
     * Returns an error occurred.
     */
    public Error error() {
        return error;
    }

    /**
     * A helper utility aimed to create an {@code InvalidEntityStateException} to report the
     * entity state which field values violate validation constraint(s).
     */
    private static final class Factory
            extends ExceptionFactory<InvalidEntityStateException,
                                     EntityState<?>,
                                     StateClass<?>,
                                     EntityStateValidationError> {

        /**
         * The name of the attribute of the entity state type reported in an error.
         *
         * @see #getMessageTypeAttribute(Message)
         * @see Error
         */
        private static final String ATTR_ENTITY_STATE_TYPE_NAME = "entityStateType";

        private final StateClass<?> stateClass;

        private Factory(EntityState<?> state,
                        Iterable<ConstraintViolation> violations) {
            super(state, violations);
            this.stateClass = StateClass.of(state);
        }

        @Override
        protected StateClass<?> getMessageClass() {
            return stateClass;
        }

        @Override
        protected EntityStateValidationError getErrorCode() {
            return INVALID_ENTITY_STATE;
        }

        @Override
        protected String getErrorText() {
            return MSG_VALIDATION_ERROR;
        }

        /**
         * Returns a map with an entity state type attribute.
         *
         * @param entityState
         *         the entity state to get the type from
         */
        @Override
        protected Map<String, Value> getMessageTypeAttribute(Message entityState) {
            var entityStateType = TypeName.of(entityState)
                                          .value();
            var value = Value.newBuilder()
                    .setStringValue(entityStateType)
                    .build();
            return ImmutableMap.of(ATTR_ENTITY_STATE_TYPE_NAME, value);
        }

        @Override
        protected InvalidEntityStateException
        createException(String exceptionMsg, EntityState state, Error error) {
            var violations = unpackViolations(error);
            checkArgument(!violations.isEmpty(), "No constraint violations provided.");
            return new InvalidEntityStateException(state, error);
        }
    }

    @NonNull
    private static List<ConstraintViolation> unpackViolations(Error error) {
        var details = (ValidationError) AnyPacker.unpack(error.getDetails());
        var violations = details.getConstraintViolationList();
        return violations;
    }
}
