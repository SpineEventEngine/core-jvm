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

package io.spine.server.command.model;

import io.spine.server.command.AbstractAssignee;
import io.spine.server.type.EventClass;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Provides message-handling information on an {@code Assignee} class.
 *
 * @param <C>
 *         the type of command assignees
 */
public final class AssigneeClass<C extends AbstractAssignee>
        extends AbstractCommandHandlingClass<C, EventClass, AssigneeReceptor> {

    /**
     * The command assignee classes of Java classes.
     *
     * @see #asCommandAssigneeClass(Class)
     */
    @SuppressWarnings("unchecked") // `asCommandAssigneeClass()` accepts only assignee classes.
    private static final ClassValue<AssigneeClass<?>> cache =
            cacheOf(cls -> new AssigneeClass<>((Class<AbstractAssignee>) cls));

    private AssigneeClass(Class<C> cls) {
        super(cls, new AssigneeSignature());
    }

    /**
     * Obtains command assignee class for the passed raw class.
     */
    public static <C extends AbstractAssignee>
    AssigneeClass<C> asCommandAssigneeClass(Class<C> cls) {
        checkNotNull(cls);
        @SuppressWarnings("unchecked") // The cache creates the model class of `cls`.
        var result = (AssigneeClass<C>) cache.get(cls);
        return result;
    }
}
