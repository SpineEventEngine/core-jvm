/*
 * Copyright 2023, TeamDev. All rights reserved.
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

package io.spine.server.event

import com.google.common.collect.ImmutableSet
import com.google.protobuf.Message
import io.spine.base.EventMessage
import io.spine.core.ContractFor
import io.spine.logging.WithLogging
import io.spine.server.BoundedContext
import io.spine.server.type.EventClass
import io.spine.string.joinBackticked

/**
 * A policy converts <em>one</em> event into zero to many messages (commands or events).
 *
 * Events?
 *
 * ### Spine-specific extension of the contract for Policies
 *
 * Traditionally a Policy is a business rule that reads like this:
 *
 * ```markdown
 * Whenever <something happens>, then do <what you need about it>.
 * ```
 * implying that the Policy generates a command in response to an incoming event, and
 * handling the command generates one or more events. The canonical flow looks like this:
 *
 * ```markdown
 * Event → Policy → Command → (Aggregate or ProcessManager, or Command Handler handles) → Event(s).
 * ```
 * Oftentimes, the experience of applying this pattern to various business domains shows that
 * the command merely bears the same information from the incoming event, and
 * the command handler simply converts this information into another event.
 *
 * Moreover, the presence of the "intermediate" command in relatively simple scenarios does
 * not add any business value because information of an event-sourced  application is stored
 * in events, not commands.
 *
 * That's why we suggest extending the Policy pattern to read:
 *
 * ```markdown
 * Whenever <something happens>, then <something else must happen>.
 * ```
 * For example,
 * ```markdown
 * Whenever a field option is discovered, a validation rule must be added.
 * ```
 *
 * ### Implementing a Policy
 *
 * To implement the policy, override the [whenever] method to return messages produced
 * in response to the incoming event.
 *
 * For the policy rule in the example above, the code would look like this:
 *
 * ```kotlin
 * class ValidationRulePolicy : Policy<FieldOptionDiscovered>() {
 *
 *     @React
 *     override fun whenever(event: FieldOptionDiscovered): Just<ValidationRuleAdded> {
 *         // Produce the event.
 *     }
 * }
 * ```
 * ### Returning zero messages
 * The contract of the [whenever] method requires returning an `Iterable` of messages.
 * To return no messages, declare the return type as `Just<Nothing>`, where `Nothing` is
 * the type from the `io.spine.server.model` package. Return the value of `Just.nothing` property
 * from your Kotlin method, or `Just.nothing()` from Java.
 *
 * If you need to avoid the naming collision with [kotlin.Nothing], consider using
 * the [NoReaction][io.spine.server.event.NoReaction] type alias.
 *
 * ### Returning one message
 * To return one message, declare `Just<MyEvent>` or `Just<MyCommand>` as the return type 
 * of the [whenever] method.
 * Use the [Just] constructor from Kotlin or [Just.just]`()` static method from Java.
 *
 * ### Returning more than one message
 * To make your return type more readable, consider using the following classes from
 * the `io.spine.server.tuple` package:
 *  [Pair][io.spine.server.tuple.Pair],
 *  [Triplet][io.spine.server.tuple.Triplet], [Quartet][io.spine.server.tuple.Quartet],
 *  [Quintet][io.spine.server.tuple.Quintet], with the corresponding number of elements declared
 *  in the return type of the [whenever] method. For example, `Pair<MyEvent, MyOtherEvent>`.
 *
 *  For returning more than five messages, please use `Iterable<Message>`, as usually.
 *
 * @param E the type of the event handled by this policy.
 *
 * @see Just
 * @see [io.spine.server.tuple.Pair]
 * @see [io.spine.server.tuple.Triplet]
 * @see [io.spine.server.tuple.Quartet]
 * @see [io.spine.server.tuple.Quintet]
 * @see [io.spine.server.event.NoReaction]
 */
public abstract class Policy<E : EventMessage> : AbstractEventReactor(), WithLogging {

    protected lateinit var context: BoundedContext

    init {
        // This call would check that there is only one event receptor
        // defined in the derived class.
        // Doing it earlier, here, in the constructor without waiting until
        // the dispatching schema is built (thus gathering the message classes),
        // allows failing faster and avoiding delayed debugging.
        messageClasses()
    }

    /**
     * Handles an event and produces zero or more messages in response.
     *
     * The produced messages can be either events or commands.
     *
     * ### API NOTE
     *
     * This method returns `Iterable<Message>` instead of `Iterable<EventMessage>`,
     * to allow implementing classes declare the return types using classes descending from
     * [Either][io.spine.server.tuple.Either]. For example, `EitherOf2<Event1, Event2>`.
     *
     * `Either` implements `Iterable<Message>`. Classes extending `Either` have two or
     * more generic parameters bounded by `Message`, not `EventMessage`.
     * Therefore, these classes will not be accepted as return types of
     * the overridden methods because `Iterable<EventMessage>` will not be
     * a super type for them.
     *
     * Policy authors should declare return types of the overridden methods as described
     * in the [class documentation][Policy].
     *
     * @see Policy
     */
    @ContractFor(handler = React::class)
    protected abstract fun whenever(event: E): Iterable<Message>

    final override fun registerWith(context: BoundedContext) {
        super.registerWith(context)
        this.context = context
    }

    /**
     * Ensures that there is only one event receptor defined in the derived class.
     *
     * @throws IllegalStateException
     *          if the derived class defines more than one event receptor
     */
    final override fun messageClasses(): ImmutableSet<EventClass> {
        val classes = super.messageClasses()
        checkReceptors(classes)
        return classes
    }

    private fun checkReceptors(events: Iterable<EventClass>) {
        val classes = events.toList()
        check(classes.size == 1) {
            "The policy `${javaClass.name}` should react on only one event." +
                    " Now it handles too many (${classes.size}): [${classes.joinBackticked()}]." +
                    " Please use only `whenever()` method for producing outgoing messages."
        }
    }
}
