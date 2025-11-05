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

package io.spine.server.event

import com.google.protobuf.Message
import io.spine.base.EventMessage
import io.spine.core.ContractFor
import io.spine.server.BoundedContext

/**
 * A reaction is a simplified pattern for event handling that converts <em>one</em> incoming event
 * directly into zero or more output events, without intermediate commands.
 *
 * ## Comparison with the Policy Pattern
 *
 * Traditionally, the Policy pattern follows this structure:
 *
 * ```markdown
 * Whenever <something happens>, then do <what you need about it>.
 * ```
 * In this approach, a Policy generates a command in response to an incoming event, and
 * handling that command generates one or more events. The flow is:
 *
 * ```markdown
 * Event → Policy → Command → (Aggregate/ProcessManager/Command Handler) → Event(s).
 * ```
 * Oftentimes, the experience of applying this pattern to various business domains shows that
 * the command merely bears the same information from the incoming event, and the handler merely
 * converts the command data into another event.
 *
 * Moreover, the presence of the "intermediate" command in relatively simple scenarios does
 * not add any business value because information of an event-sourced application is stored
 * in events, not commands.
 *
 * That's why we suggest adding the Reaction pattern which reads like:
 *
 * ```markdown
 * Whenever <something happens>, then <something else happened>.
 * ```
 * For example, a Reaction rule can be expressed as:
 *
 * ```markdown
 * Whenever a field option is discovered, a validation rule is added.
 * ```
 *
 * ## Implementation Guide
 *
 * To implement a reaction, override the [whenever] method to define what event messages
 * should be produced in response to the incoming event.
 *
 * Here's how the example above would be implemented:
 *
 * ```kotlin
 * class ValidationRuleReaction : Reaction<FieldOptionDiscovered>() {
 *
 *     @React
 *     override fun whenever(event: FieldOptionDiscovered): Just<ValidationRuleAdded> {
 *         // Produce the event.
 *     }
 * }
 * ```
 * ### Return zero events
 *
 * When you need to indicate that no events should be produced:
 * - Declare the return type as `Just<[NoReaction][io.spine.server.event.NoReaction]>`
 * - In Kotlin: return the [Just.noReaction] property
 * - In Java: call [Just.noReaction()][Just.noReaction]
 *
 * ### Returning one event
 *
 * To return one message, declare `Just<MyEvent>` as the return type of the [whenever] method.
 * Use the [Just] constructor from Kotlin or [Just.just]`()` static method from Java.
 *
 * ### Returning more than one event
 *
 * To make your return type more readable, consider using the following classes from
 * the `io.spine.server.tuple` package:
 *  [Pair][io.spine.server.tuple.Pair],
 *  [Triplet][io.spine.server.tuple.Triplet], [Quartet][io.spine.server.tuple.Quartet],
 *  [Quintet][io.spine.server.tuple.Quintet], with the corresponding number of elements declared
 *  in the return type of the [whenever] method. For example, `Pair<MyEvent, MyOtherEvent>`.
 *
 *  For returning more than five events, use `Iterable<Message>`, as usually.
 *
 * @param E the type of the event handled by this reaction.
 *
 * @see Just
 * @see [io.spine.server.tuple.Pair]
 * @see [io.spine.server.tuple.Triplet]
 * @see [io.spine.server.tuple.Quartet]
 * @see [io.spine.server.tuple.Quintet]
 * @see [io.spine.server.event.NoReaction]
 */
public abstract class Reaction<E : EventMessage> :
    AbstractEventReactor(),
    Whenever<E> {

    protected lateinit var context: BoundedContext

    init {
        // Ensure there is only one event receptor defined in a derived class.
        // Doing it here allows failing faster, before the dispatching schema is built.
        checkAcceptsOneEvent()
    }

    
    /**
     * Handles an event and produces zero or more events in response.
     *
     * ### API NOTE
     *
     * This method returns `Iterable<Message>` instead of `Iterable<EventMessage>`
     * to allow implementing classes to declare return types using classes that descend from
     * [Either][io.spine.server.tuple.Either], such as `EitherOf2<Event1, Event2>`.
     *
     * `Either` implements `Iterable<Message>`. Classes extending `Either` have two or
     * more generic parameters bounded by `Message`, not `EventMessage`.
     * Therefore, these classes will not be accepted as return types of
     * the overridden methods because `Iterable<EventMessage>` will not be
     * a super type for them.
     *
     * For implementation examples and return type options, see the class-level documentation.
     */
    @ContractFor(handler = React::class)
    abstract override fun whenever(event: E): Iterable<Message>

    final override fun registerWith(context: BoundedContext) {
        super.registerWith(context)
        this.context = context
    }
}
