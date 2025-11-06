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

package io.spine.server.command;

import io.spine.core.AcceptsContracts;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a command assignee.
 *
 * <p>A command assignee method <em>must</em>:
 * <ul>
 *     <li>be annotated with {@link Assign @Assign};
 *     <li>return an event message derived from {@link io.spine.base.EventMessage EventMessage}
 *         if there is only one event generated;
 *         <strong>or</strong> an {@code Iterable} of event messages for two or
 *         more events;
 *     <li>accept a command message derived from {@link io.spine.base.CommandMessage CommandMessage}
 *         as the first parameter.
 * </ul>
 *
 * <p>Like other message-handling methods, command assignees are designed to be called by
 * the framework only. Therefore, it is recommended to declare them package-private
 * (or {@code internal} in Kotlin). It discourages developers from calling these methods directly
 * from anywhere. It is also acceptable to use {@code protected} if the declaring class inherits
 * the method from a superclass.
 *
 * <p>This level of access declares that a command assignee method is a part
 * of the Bounded Context-level API. See the {@link io.spine.core.BoundedContext
 * BoundedContext} description on how the packages and Bounded Contexts relate.
 *
 * <h2>Accepted Parameters</h2>
 *
 * <p>The first parameter of the method always declares a type of the handled command.
 *
 * <p>The method <strong>may</strong> accept a {@link io.spine.core.CommandContext
 * CommandContext} as the second parameter, if handling of the command requires its context.
 *
 * <pre>
 *
 * {@literal @}Assign
 *  TaskCreated handle(CreateTask command) { ... }
 *
 * {@literal @}Assign
 *  TaskCompleted handle(CompleteTask command, CommandContext context) { ... }
 * </pre>
 *
 * <p>In case a command may be rejected, a corresponding {@code Throwable} should be declared:
 *
 * <pre>
 *
 * {@literal @}Assign
 *  TaskStarted handle(StartTask command) throws TaskAlreadyInProgress { ... }
 * </pre>
 *
 * <p>If the annotation is applied to a method which doesn't satisfy any of these requirements,
 * this method is not considered a command assignee and is <strong>not</strong> registered for
 * command dispatching.
 *
 * <h2>Returning Values</h2>
 *
 * <p>As a command is an imperative, it must lead to some outcome. Typically, a command results
 * in an emission of one or more events. Each of them must derive
 * from {@link io.spine.base.EventMessage EventMessage} in order to make the code less error-prone.
 *
 * <p>A command assignee method must return either
 * <ul>
 *
 *  <li>an event message:
 *  <pre>
 *
 * {@literal @}Assign
 *  TaskReassigned handle(ReassignTask command) { ... }
 *  </pre>
 *
 *
 *  <li>an {@code Iterable} of event messages:
 *  <pre>
 *
 * {@literal @}Assign
 * {@literal Iterable<TaskCompleted>} handle(CompleteProject event) { ... }
 *  </pre>
 *
 *
 *  <li>a {@link io.spine.server.tuple.Tuple tuple} of event messages; being similar
 *  to {@code Iterable}, tuples allow to declare the exact types of returning values, including
 *  {@code Optional} values:
 *  <pre>
 *
 * {@literal @}Assign
 * {@literal Pair<ProjectCreated, ProjectAssigned>} handle(CreateProject event) { ... }
 *
 * {@literal @}Assign
 * {@literal Pair<TaskCreated, Optional<TaskAssigned>>} handle(CreateTask event) { ... }
 *  </pre>
 *
 *
 *  <li>{@linkplain io.spine.server.tuple.Either one of} particular events:
 *  <pre>
 *
 * {@literal @}Assign
 * {@literal EitherOf2<TaskRemovedFromProject, TaskDeleted>} handle(RemoveTask command) { ... }
 *  </pre>
 * </ul>
 *
 * <h2>One Handler per Command</h2>
 *
 * <p>An application must have one and only one handler per command message class.
 * This includes {@linkplain io.spine.server.command.Command the case} of transforming an incoming
 * command into one or more commands that will to be handled instead of the received one.
 *
 * <p>Declaring two methods that handle the same command class will result in run-time error.
 *
 * @see io.spine.server.tuple.Tuple Returning Two or More Event Messages
 * @see io.spine.server.tuple.Either Returning One of Event Messages
 * @see io.spine.server.command.Command Transforming Commands
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@AcceptsContracts
public @interface Assign {
}
