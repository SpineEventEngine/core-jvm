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
import io.spine.server.command.AbstractCommander
import io.spine.server.command.Command

/**
 * A Policy is a pattern for handling an incoming event and producing
 * one or more commands in response.
 *
 * ## Implementation Guide
 *
 * To implement a reaction, override the [whenever] method to define what command messages
 * should be produced in response to the incoming event.
 *
 * For example:
 *
 * ```kotlin
 * class NotifyOwnerPolicy : Policy<ItemOutOfStock>() {
 *
 *     @Command
 *     override fun whenever(event: ItemOutOfStock): Just<NotifyOwner> {
 *         // Produce a command.
 *     }
 * }
 * ```
 *
 * ### Returning one command
 *
 * To return one message, declare `Just<MyCommand>` as the return type of the [whenever] method.
 * Use the [Just] constructor from Kotlin or [Just.just]`()` static method from Java.
 * 
 * ### Returning more than one command
 *
 * To make your return type more readable, consider using the following classes from
 * the `io.spine.server.tuple` package:
 *  [Pair][io.spine.server.tuple.Pair],
 *  [Triplet][io.spine.server.tuple.Triplet], [Quartet][io.spine.server.tuple.Quartet],
 *  [Quintet][io.spine.server.tuple.Quintet], with the corresponding number of elements declared
 *  in the return type of the [whenever] method. For example, `Pair<MyCommand, MyOtherCommand>`.
 *
 *  For returning more than five commands, use `Iterable<Message>`, as usually.
 *
 * @param E the type of the event handled by this policy.
 */
public abstract class Policy<E : EventMessage> : AbstractCommander(), Whenever<E> {

    protected lateinit var context: BoundedContext

    init {
        // Ensure there is only one event receptor defined in a derived class.
        // Doing it here allows failing faster, before the dispatching schema is built.
        checkAcceptsOneEvent()
    }

    
    /**
     * Handles an event and produces zero or more command messages in response.
     *
     * ### API NOTE
     *
     * This method uses `Iterable<Message>` as the return type to support flexible command
     * response patterns. While policy implementations typically return command messages,
     * the broader `Message` type allows use of utility classes like
     * [Either][io.spine.server.tuple.Either]:
     *
     * ```
     * // Returning alternative commands:
     * EitherOf2<CreateOrder, UpdateInventory>
     *
     * // Multiple commands:
     * Pair<NotifyCustomer, UpdateStatus>
     * ```
     *
     * The [Either][io.spine.server.tuple.Either] hierarchy and tuple classes
     * ([Pair][io.spine.server.tuple.Pair], [Triplet][io.spine.server.tuple.Triplet], etc.)
     * implement `Iterable<Message>` rather than `Iterable<CommandMessage>`.
     * `Iterable<Message>` is the common supertype for them.
     *
     * For implementation examples and return type options, see the class-level documentation.
     */
    @ContractFor(handler = Command::class)
    protected abstract fun whenever(event: E): Iterable<Message>

    final override fun registerWith(context: BoundedContext) {
        super.registerWith(context)
        this.context = context
    }
}
