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

/**
 * This package provides classes for return values from message-handling methods.
 *
 * <h3>Overview</h3>
 * 
 * <p>Although tuples are <a href="https://github.com/google/guava/wiki/IdeaGraveyard#tuples-for-n--2">
 * considered harmful</a> in general, they are useful for describing types of several messages
 * returned by a method. Consider the following example.
 *
 * <p>The return value of the below method does not say much about the number and types
 * of returned event messages. We just know that there may be more than one of them.
 * <pre>
 *    {@literal @}Assign
 *     List&lt;EventMessage&gt; on(CreateTask cmd) { ... }
 * </pre>
 *
 * The below declaration gives both the number and types of the returned events:
 * <pre>
 *    {@literal @}Assign
 *     Pair&lt;TaskCreated, Optional&lt;TaskAssigned&gt;&gt; on(CreateTask cmd) { ... }
 * </pre>
 *
 * <p>Why do we suggest returning a tuple (a {@code Pair} in the example above) instead of creating
 * a message type which would aggregate those of interest as our colleagues from the Guava team
 * <a href="https://github.com/google/guava/wiki/IdeaGraveyard#tuples-for-n--2">suggest</a>?
 *
 * <p>Messages returned from a handling method are loosely coupled. They may not
 * be created together all the time. The example above tells that {@code TaskAssigned}
 * may not happen upon the creation of the task. It's possible that a task can be assigned
 * sometime later. In this case combining returned messages does not reflect the domain language.
 * Returning a tuple tells a part of the story in terms of the domain language right in the method
 * signature. Details of this story are obtained from the method body.
 *
 * <p>It should be re-iterated that the purpose of this package is limited to the scenarios
 * of handling messages. Programmers are strongly discouraged from using this package for
 * other purposes.
 *
 * <h3>Two groups of classes</h3>
 * 
 * This package provides two groups of classes:
 * <ol>
 *     <li>{@link io.spine.server.tuple.Tuple Tuple} classes are for returning more than one message.
 *     <li>Alternatives — classes derived from {@link io.spine.server.tuple.Either Either} — are
 *     for returning only one message belonging the known set of types.
 * </ol>
 *
 * <h3>Generic Parameters</h3>
 *
 * <p>Classes provided by this package can support up to 5 generic parameters.
 * These parameters are named from {@code <A>} through {@code <E>}. Types that can be passed to
 * these parameters are described in the sections below.
 *
 * <h3>Tuples</h3>
 *
 * <p>The following tuple classes are provided:
 * <ul>
 *    <li>{@link io.spine.server.tuple.Single Single&lt;A&gt;}
 *    <li>{@link io.spine.server.tuple.Pair Pair&lt;A, B&gt;}
 *    <li>{@link io.spine.server.tuple.Triplet Triplet&lt;A, B, C&gt;}
 *    <li>{@link io.spine.server.tuple.Quartet Quartet&lt;A, B, C, D&gt;}
 *    <li>{@link io.spine.server.tuple.Quintet Quintet&lt;A, B, C, D, E&gt;}
 * </ul>
 *
 * <p>The first generic parameter {@code <A>} must always be a specific
 * {@link com.google.protobuf.Message Message} class.
 *
 * <p>Tuple classes allow {@link java.util.Optional Optional} starting from
 * the second generic argument. The example below shows the method which always returns
 * the {@code TaskCreated} event, and returns the {@code TaskAssigned} event only if
 * the {@code CreateTask} command instructs to assign the task to a person.
 * <pre>
 *    {@literal @}Assign
 *     Pair&lt;TaskCreated, Optional&lt;TaskAssigned&gt;&gt; on(CreateTask cmd) { ... }
 * </pre>
 *
 * <h3>Alternatives</h3>
 *
 * <p>In some cases it is needed to return one message from a limited set of possible options.
 * For example, the following method issues a command {@code MoveToTrash} if no work
 * was reported on the task, and {@code CancelTask} if some work has been already logged.
 * <pre>
 *    {@literal @}Command
 *     EitherOf2&lt;MoveToTrash, CancelTask&gt; handle(RemoveTask cmd) { ... }
 * </pre>
 *
 * <p>In order to define alternatively returned values, please use the following classes:
 * <ul>
 *     <li>{@link io.spine.server.tuple.EitherOf2 EitherOf2&lt;A, B&gt;}
 *     <li>{@link io.spine.server.tuple.EitherOf3 EitherOf3&lt;A, B, C&gt;}
 *     <li>{@link io.spine.server.tuple.EitherOf4 EitherOf4&lt;A, B, C, D&gt;}
 *     <li>{@link io.spine.server.tuple.EitherOf5 EitherOf5&lt;A, B, C, D, E&gt;}
 * </ul>
 *
 * <p>Generic parameters for alternatives accept only {@link com.google.protobuf.Message Message}
 * classes.
 *
 * <p>We believe that a list of alternatives longer than five is hard to understand.
 * If you face a such a need, consider splitting incoming message into two or more independent ones
 * so that their outcome is more obvious.
 *
 * <h3>Using Tuples with Alternatives</h3>
 *
 * <p>A {@link io.spine.server.tuple.Pair Pair} can be defined with the second parameter being one
 * of the {@link io.spine.server.tuple.Either Either} subclasses, and created using
 * {@link io.spine.server.tuple.Pair#withEither(com.google.protobuf.Message,
 * io.spine.server.tuple.Either) Pair.withEither()}.
 */
@CheckReturnValue
@NullMarked
package io.spine.server.tuple;

import com.google.errorprone.annotations.CheckReturnValue;

import org.jspecify.annotations.NullMarked;
