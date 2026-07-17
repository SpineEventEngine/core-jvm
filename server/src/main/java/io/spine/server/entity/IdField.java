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

package io.spine.server.entity;

import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.Descriptors.FieldDescriptor;
import io.spine.base.EntityState;
import io.spine.code.proto.FieldDeclaration;
import io.spine.code.proto.FieldOption;
import io.spine.code.proto.Option;
import io.spine.option.OptionsProto;
import io.spine.server.entity.model.EntityClass;
import io.spine.validation.ValidatingBuilder;
import org.jspecify.annotations.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Helps to initialize an ID field of an entity state if such
 * a field is declared and does not have the {@code (required)}
 * option set to {@code false}.
 */
@Immutable
final class IdField {

    private static final Option<Boolean, FieldDescriptor> requiredOption = new Required();
    private final @Nullable FieldDeclaration declaration;

    static IdField of(EntityClass<?> entityClass) {
        var defaultState = entityClass.defaultState();
        var fields = defaultState.getDescriptorForType().getFields();
        if (fields.isEmpty()) {
            /* The entity state is an empty message.
               This is weird for production purposes but can be used for test stubs. */
            return new IdField(null);
        } else {
            var firstField = fields.get(0);
            var fieldClass = defaultState.getField(firstField)
                                         .getClass();
            var declaration =
                    fieldClass.equals(entityClass.idClass())
                    ? new FieldDeclaration(firstField)
                    : null;
            return new IdField(declaration);
        }
    }

    private IdField(@Nullable FieldDeclaration declaration) {
        this.declaration = declaration;
    }

    /**
     * Returns {@code true} if the entity state type declares a field that is
     * the ID of the entity, {@code false} otherwise.
     */
    private boolean declared() {
        return declaration != null;
    }

    /**
     * Initializes the given builder with the value of the entity ID,
     * <em>iff</em> the field's {@code (required)} option is not set
     * to {@code false} explicitly.
     *
     * <p>Conventionally, an entity state ID is assumed a required field.
     * So, if there is no option set, we assume that the field is required.
     */
    <I, S extends EntityState<I>, B extends ValidatingBuilder<S>>
    void initBuilder(B builder, I id) {
        checkNotNull(builder);
        checkNotNull(id);
        if (!declared()) {
            return;
        }
        var idField = declaration.descriptor();
        boolean isRequired = requiredOption.valueFrom(idField)
                                           .orElse(true);
        if (isRequired) {
            builder.setField(idField, id);
        }
    }

    /**
     * Provides access to the value of the {@link OptionsProto#required (required)}
     * field option.
     */
    @Immutable
    private static final class Required extends FieldOption<Boolean> {

        private Required() {
            super(OptionsProto.required);
        }
    }
}
