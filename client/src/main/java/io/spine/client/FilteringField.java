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

package io.spine.client;

import com.google.protobuf.Descriptors.Descriptor;
import io.spine.base.EntityState;
import io.spine.base.Field;
import io.spine.base.FieldPath;
import io.spine.code.proto.FieldDeclaration;
import io.spine.core.Event;
import io.spine.core.EventContext;

import static io.spine.code.proto.ColumnOption.isColumn;
import static io.spine.util.Exceptions.newIllegalStateException;

/**
 * A field of a target filter.
 */
final class FilteringField {

    private final Field field;

    FilteringField(FilterOrBuilder filter) {
        var fieldPath = filter.getFieldPath();
        this.field = Field.withPath(fieldPath);
    }

    /**
     * Verifies if the field is applicable to the passed target.
     *
     * <p>If the passed target represents an entity state, the field must be a reference to
     * a column (and annotated as such).
     *
     * <p>If the passed target is an event message, the field must be present in the event message,
     * or the field must reference a field of {@code EventContext}.
     *
     * @throws IllegalStateException
     *         if the field does not apply to the passed target
     */
    void checkAppliesTo(Target target) {
        var descriptor = target.messageDescriptor();
        var targetIsEntityState = EntityState.class.isAssignableFrom(target.messageClass());
        if (targetIsEntityState) {
            checkFieldOfEntityState(descriptor);
        } else {
            checkFieldOfEvent(descriptor);
        }
    }

    private void checkFieldOfEvent(Descriptor descriptor) {
        if (refersToContext()) {
            checkPresentInEventContext();
        } else {
            checkPresentInEventMessage(descriptor);
        }
    }

    private boolean refersToContext() {
        var contextFieldName = Event.Field.context()
                                          .getField()
                                          .toString();
        var firstInPath = field.path().getFieldName(0);
        var result = contextFieldName.equals(firstInPath);
        return result;
    }

    private void checkPresentInEventContext() {
        var contextField = fieldInContext();
        var eventContext = EventContext.getDescriptor();
        if (!contextField.presentIn(eventContext)) {
            throw newIllegalStateException(
                    "The filter for event messages references a field of `%s` as `%s`." +
                    " There is no field named `%s` in the `%s` type.",
                    field.toString(),
                    eventContext.getFullName(),
                    contextField.toString(),
                    eventContext.getName()
            );
        }
    }

    private Field fieldInContext() {
        var pathInEvent = field.path();
        var fieldNames = pathInEvent.getFieldNameList();
        var pathInEventContext = FieldPath.newBuilder()
                .addAllFieldName(fieldNames.subList(1, fieldNames.size()))
                .build();
        return Field.withPath(pathInEventContext);
    }

    private boolean isTopLevel() {
        return !field.isNested();
    }

    private void checkPresentInEventMessage(Descriptor message) {
        if (!field.presentIn(message)) {
            throw newIllegalStateException(
                    "The field with path `%s` is not present in the message type `%s`.",
                    field, message.getFullName());
        }
    }

    private void checkFieldOfEntityState(Descriptor message) {
        checkFieldAtTopLevel();
        if (!isColumnIn(message)) {
            throw newIllegalStateException(
                    "The column `%s` is not found in entity state type `%s`. " +
                    "Please check the field exists and is marked with the `(column)` option.",
                    field, message.getFullName());
        }
    }

    private boolean isColumnIn(Descriptor message) {
        var fieldDescriptor = field.findDescriptor(message);
        if (fieldDescriptor.isEmpty()) {
            return false;
        }
        var declaration = new FieldDeclaration(fieldDescriptor.get());
        var result = isColumn(declaration);
        return result;
    }

    private void checkFieldAtTopLevel() {
        if (!isTopLevel()) {
            throw newIllegalStateException(
                    "The entity filter contains a nested entity column `%s`. " +
                            "Nested entity columns are currently not supported.",
                    field
            );
        }
    }
}
