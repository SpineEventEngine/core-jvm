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

package io.spine.server.event;

import io.spine.core.AcceptsContracts;
import io.spine.core.AcceptsExternal;
import io.spine.core.AcceptsFilters;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method of an entity as one that <em>may</em> modify the state of the entity in
 * response to some domain event.
 *
 * <p>A reacting method must be annotated {@link React @React}.
 *
 * <p>Like other message-handling methods, event reactors are designed to be called by
 * the framework only. Therefore, it is recommended to declare them
 * package-private (or {@code internal} in Kotlin).
 * It discourages developers from calling these methods directly from anywhere.
 * It is also acceptable to use {@code protected} if the declaring class inherits
 * the method from a superclass.
 *
 * <p>This level of access still declares that an event reactor method is a part
 * of the Bounded Context-level API. See the {@link io.spine.core.BoundedContext
 * BoundedContext} description on how the packages and Bounded Contexts relate.
 *
 * <h2>Accepted Parameters</h2>
 *
 * <p>Each reacting method <strong>must</strong> accept an event message derived
 * from {@link io.spine.base.EventMessage EventMessage} as the first parameter.
 * Optionally, one may pass some additional parameters and put the incoming message into
 * some perspective. Available sets of parameters are described below.
 *
 * <h3>Single event message</h3>
 * 
 * <pre>
 *
 * {@literal @}React
 *  EngineStopped on(CarStopped event) { ... }
 * </pre>
 *
 * <h3>An event message along with its {@link io.spine.core.EventContext context}</h3>
 *
 * <p>The context brings some properties related to the event, such as
 * the {@link io.spine.core.EventContext#actor actor ID} or
 * the {@link io.spine.core.EventContext#timestamp timestamp} of the event emission:
 * <pre>
 *
 * {@literal @}React
 *  ProjectOwnerAssigned on(ProjectCreated event, EventContext context) { ... }
 * </pre>
 *
 * <h3>Rejection message with an original command message</h3>
 *
 * <p>If an event is a {@linkplain io.spine.base.RejectionMessage rejection},
 * one may additionally specify <strong>the command message, which led to this rejection.</strong>
 * This will act like a filter.
 * In the example below, rejections of the {@code CannotAllocateCargo} type
 * <strong>only</strong> caused by the rejected {@code DeliverOrder} command will be dispatched:
 * <pre>
 *
 * {@literal @}React
 *  OrderDeliveryFailed on(CannotAllocateCargo event, DeliverOrder command) { ... }
 * </pre>
 *
 * <p>It is also possible to add the context of the origin command to access even more properties:
 * <pre>
 *
 * {@literal @}React
 *  ProjectRenameFailed on(ProjectAlreadyCompleted event, RenameProject command, CommandContext ctx) { ... }
 * </pre>
 *
 * <h2>Return Types</h2>
 *
 * <p>The essence of a reacting method is an emission of one or several events in a reaction to
 * the dispatched event.
 *
 * <p>As long as an entity may have a complex logic of determining which event to emit in reaction,
 * the {@code React}-marked methods allow a variety of options for the returned types.
 * A reacting method must return one of the following.
 *
 * <h3>An event message</h3>
 * 
 *  <pre>
 *
 * {@literal @}React
 *  TaskReassigned on(UserDeactivated event) { ... }
 *  </pre>
 *
 *  <h2>An {@code Optional} event message</h2>
 *
 *  <pre>
 *
 * {@literal @}React
 * {@literal Optional<PersonAllowedToBuyAlcohol>} on(PersonAgeChanged event) { ... }
 *  </pre>
 *
 *  <h2>One of particular events</h2>
 *
 *  <p>Use one of the {@link io.spine.server.tuple.Either Either} types to explicitly tell
 *  alternatives returned by the function.
 *  This way you can also return a special {@link io.spine.server.event.NoReaction NoReaction}
 *  event stating that the entity may choose not to emit an event as its reaction at all:
 *  <pre>
 *
 * {@literal @}React
 * {@literal EitherOf3<ProjectCompleted, ProjectEstimateUpdated, NoReaction>} on(TaskCompleted event) { ... }
 *  </pre>
 *
 *  <h3>An {@code Iterable} of event messages</h3>
 *  
 *  <pre>
 *
 * {@literal @}React
 * {@literal Iterable<StoryMovedToBacklog>} on(SprintCompleted event) { ... }
 *  </pre>
 *
 *
 *  <h3>A {@link io.spine.server.tuple.Tuple tuple} of event messages</h3>
 *
 *  <p>Being similar to {@code Iterable}, tuples allow declaring the exact types of returning
 *  values, including {@code Optional} values:
 *  <pre>
 *
 * {@literal @}React
 * {@literal Pair<ProjectOwnerAssigned, ProjectDueDateChanged>} on(ProjectCreated event) { ... }
 *
 * {@literal @}React
 * {@literal Pair<TaskAssigned, Optional<TaskStarted>>} on(TaskAdded event) { ... }
 *  </pre>
 *
 * <h2>Handling Incorrect Signatures</h2>
 *
 * <p>If the annotation is applied to a method which does not satisfy either of these requirements,
 * this method will not be registering for receiving events.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
@AcceptsExternal
@AcceptsFilters
@AcceptsContracts
public @interface React {
}
