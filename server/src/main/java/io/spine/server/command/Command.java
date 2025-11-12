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
import io.spine.core.AcceptsExternal;
import io.spine.core.AcceptsFilters;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marks a commanding method.
 *
 * <p>A commanding method <strong>must</strong>:
 * <ul>
 *     <li>be annotated with {@link Command @Command};
 *     <li>accept one of the following as the first parameter:
 *     <ul>
 *        <li>a {@linkplain io.spine.base.CommandMessage command message};
 *        <li>an {@linkplain io.spine.base.EventMessage event message};
 *        <li>a {@linkplain io.spine.base.RejectionMessage rejection message}.
 *     </ul>
 * </ul>
 *
 * <p>Like other receptors, commanding methods are designed to be called by
 * the framework only. Therefore, it is recommended to declare them package-private
 * (or {@code internal} in Kotlin). It discourages developers from calling these methods directly
 * from anywhere. It is also acceptable to use {@code protected} if the declaring class inherits
 * the method from a superclass.
 *
 * <p>This level of access declares that a command receptor is a part
 * of the Bounded Context-level API. See the {@link io.spine.core.BoundedContext
 * BoundedContext} description on how the packages and Bounded Contexts relate.
 *
 * <h2>Command Transformation</h2>
 *
 * <p>Commanding methods may be used to transform an incoming command into one or more commands.
 * In this case the first method parameter must extend {@linkplain io.spine.base.CommandMessage}.
 * The returning values must derive from the {@linkplain io.spine.base.CommandMessage} as well.
 *
 * <p>If a commanding method accepts a command type which is also handled by another method
 * of the same application, a run-time error will occur. This also means that an application
 * cannot have two command-handling methods that accept the same command type.
 *
 * <h3>Accepted Parameters</h3>
 *
 * <p>There are several combinations of accepted parameters for a command-transforming method.
 * The set of available parameters include
 *
 * <ul>
 *
 * <li>single command message:
 * <pre>
 *
 * {@literal @}Command
 *  ArchiveTask on(DeleteTask command) { ... }
 * </pre>
 *
 * <li>a command message along with its context:
 * <pre>
 *
 * {@literal @}Command
 *  MoveTaskToDone on(CompleteTask command, CommandContext context) { ... }
 * </pre>
 * </ul>
 *
 * <h3>Returning Values</h3>
 *
 * <p>A command-transforming method always returns an outcome. Depending on the design intention,
 * a type of outcome may vary.
 *
 * <p>The command-transforming method must return either
 *
 * <ul>
 *
 * <li>a command message:
 * <pre>
 *
 * {@literal @}Command
 *  AssignTask on(StartTask command) { ... }
 * </pre>
 *
 *
 * <li>{@linkplain io.spine.server.tuple.Either one of} of several command messages:
 * <pre>
 *
 * {@literal @}Command
 * {@literal EitherOf2<StopTask, PauseTask>} on(RemoveTaskFromProject command) { ... }
 * </pre>
 *
 *
 * <li>an {@code Iterable} of command messages:
 * <pre>
 *
 * {@literal @}Command
 * {@literal Iterable<PauseTask>} on(PauseProject command) { ... }
 * </pre>
 *
 *
 * <li>a {@link io.spine.server.tuple.Tuple tuple} of several command messages; being similar
 * to {@code Iterable}, tuples allow to declare the exact types of returning values, including
 * {@code Optional} values:
 *
 * <pre>
 *
 * {@literal @}Command
 * {@literal Pair<AssignTask, Optional<StartTask>>} on(AddTaskToProject command) { ... }
 * </pre>
 * </ul>
 *
 * <h3>Rejecting Commands</h3>
 *
 * <p>As a command-handling method, a command-transforming method may reject an incoming command.
 * In this case, it should declare a generated class derived from
 * {@link io.spine.base.RejectionThrowable RejectionThrowable} in {@code throws} clause:
 *
 * <pre>
 *
 * {@literal @}Command
 *  AssignProject on(CreateProject command) throws CannotCreateProject { ... }
 * </pre>
 *
 * <p>Throwing {@linkplain Throwable other types} of {@code Throwable}s is not allowed in
 * the command-transforming methods.
 *
 * <h2>Command Reaction</h2>
 *
 * <p>A commanding method may serve to emit commands in response to an incoming event. In this case
 * its first parameter must be a message of either {@link io.spine.base.EventMessage EventMessage}
 * or {@link io.spine.base.RejectionMessage RejectionMessage} type.
 *
 * <h3>Accepted Parameters</h3>
 *
 * <p>Command-reaction method must accept either
 *
 * <ul>
 *
 * <li>single event message:
 * <pre>
 *
 * {@literal @}Command
 *  ScheduleDelivery on(OrderPaid command) { ... }
 * </pre>
 *
 *
 * <li>an event message along with its context:
 * <pre>
 *
 * {@literal @}Command
 *  NotifyCustomer on(DeliveryScheduled event, EventContext context) { ... }
 * </pre>
 *
 *
 * <li>a rejection message:
 * <pre>
 *
 * {@literal @}Command
 *  CreateProject on(Rejections.CannotCreateProject rejection) {
 *      // Change the parameters and try again.
 *  }
 * </pre>
 *
 * <li>a rejection message along with the context of the command which caused it:
 * <pre>
 *
 * {@literal @}Command
 *  ReassignTask on(Rejections.CannotStartTask rejection, CommandContext) { ... }
 * </pre>
 *
 * </ul>
 *
 * <p>It is possible to receive external Events and Rejections. For that, mark the message parameter
 * with the {@link io.spine.core.External @External} annotation. External Commands do not travel
 * this way.
 *
 * <h3>Returning Values</h3>
 *
 * <p>A command-reacting method may emit one or more messages derived from
 * {@link io.spine.base.CommandMessage CommandMessage}. The possibilities for the return values
 * are flexible. They serve to describe the returning values as sharp as possible and thus
 * reduce the probability of a human mistake.
 *
 * <p>A command-reacting method must return either
 * <ul>
 *
 * <li>a command message:
 * <pre>
 *
 * {@literal @}Command
 *  ArchiveTask on(TaskCompleted event) { ... }
 * </pre>
 *
 *
 * <li>an {@code Optional} command message:
 * <pre>
 *
 * {@literal @}Command
 * {@literal Optional<StartTask>} on(TaskAdded event) { ... }
 * </pre>
 *

 * <li>{@linkplain io.spine.server.tuple.Either one of} of several command messages:
 * <pre>
 *
 * {@literal @}Command
 * {@literal EitherOf2<StopTask, PauseTask>} on(TaskReassigned event) { ... }
 * </pre>
 *
 *
 * <li>an {@code Iterable} of command messages:
 * <pre>
 *
 * {@literal @}Command
 * {@literal List<ArchiveTask>} on(ProjectCompleted event) { ... }
 * </pre>
 *
 *
 * <li>a {@link io.spine.server.tuple.Tuple tuple} of several command messages;
 * it allows to declare the exact types of returning values, including {@code Optional}s:
 *
 * <pre>
 *
 * {@literal @}Command
 * {@literal Triplet<AssignTask, UpdateTaskDueDate, Optional<StartTask>>} on(CreateTask command) { ... }
 * </pre>
 * </ul>
 *
 * <p>If the annotation is applied to a method which doesn't satisfy either of these requirements,
 * this method is not considered a commanding method and is <strong>not</strong> registered for
 * command generation.
 *
 * @see io.spine.server.tuple.Tuple Returning Two or More Command Messages
 * @see io.spine.server.tuple.Either Returning One of Command Messages
 * @see io.spine.server.command.Assign Handling Commands
 */
@Retention(RUNTIME)
@Target(METHOD)
@AcceptsExternal
@AcceptsFilters
@AcceptsContracts
public @interface Command {
}
