/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package io.spine.client;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Message;
import io.spine.annotation.GeneratedMixin;
import io.spine.base.EntityState;
import io.spine.base.EventMessage;
import io.spine.type.TypeUrl;

import java.util.Collection;

import static io.spine.util.Exceptions.newIllegalStateException;

/**
 * Extends the {@link Target} with validation routines.
 */
@GeneratedMixin
interface TargetMixin extends TargetOrBuilder {

    /**
     * Returns the URL of the target type.
     */
    default TypeUrl type() {
        var type = getType();
        return TypeUrl.parse(type);
    }

    /**
     * Obtains the class of the target.
     */
    default Class<Message> messageClass() {
        var result = type().getMessageClass();
        return result;
    }

    /**
     * Tells if the target type is an event message type.
     */
    default boolean ofEvent() {
        var result = EventMessage.class.isAssignableFrom(messageClass());
        return result;
    }

    /**
     * Obtains the descriptor of the target type.
     */
    default Descriptor messageDescriptor() {
        var result = type().typeName()
                           .messageDescriptor();
        return result;
    }

    /**
     * Verifies that the target type is a valid type for querying.
     *
     * @throws IllegalArgumentException
     *         if the target type is not a valid type for querying
     */
    default void checkTypeValid() {
        var targetClass = messageClass();
        var isEntityState = EntityState.class.isAssignableFrom(targetClass);
        var isEventMessage = EventMessage.class.isAssignableFrom(targetClass);
        if (!isEntityState && !isEventMessage) {
            throw newIllegalStateException(
                    "The queried type should represent either an entity state or an event " +
                            "message. Got type `%s` instead.", targetClass.getCanonicalName());
        }
    }

    /**
     * Verifies that the target has valid type and filters.
     *
     * @throws IllegalStateException
     *         if either the target type is not a valid type for querying or the filters
     *         are invalid
     * @see FilterMixin#checkCanApplyTo(Target)
     */
    @SuppressWarnings("ClassReferencesSubclass") // OK for a proto mixin.
    default void checkValid() {
        checkTypeValid();
        var thisAsTarget = (Target) this;
        getFilters()
                .getFilterList()
                .stream()
                .map(CompositeFilter::getFilterList)
                .flatMap(Collection::stream)
                .forEach(filter -> filter.checkCanApplyTo(thisAsTarget));
    }
}
