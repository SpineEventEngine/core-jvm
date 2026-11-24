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

package io.spine.server.async.given

import com.google.protobuf.Message
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.spine.core.Subscribe
import io.spine.server.BoundedContextBuilder
import io.spine.server.async.given.RemoteService.Companion.waitSeconds
import io.spine.server.event.AbstractEventSubscriber
import io.spine.system.server.HandlerFailedUnexpectedly
import io.spine.system.server.event.CommandRejected
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Remembers the commands and the events posted to the buses of a Bounded Context, and
 * the System events that tell about a failed handler or a rejected command.
 *
 * A test [waits][await] for a message that a handler posts from another thread.
 */
internal class SignalMonitor : AbstractEventSubscriber() {

    private val lock = ReentrantLock()

    /**
     * Signalled when a message is remembered.
     */
    private val added = lock.newCondition()

    private val messages = mutableListOf<Message>()

    /**
     * Makes the given builder tell this monitor about the signals of its context.
     */
    fun watch(context: BoundedContextBuilder) {
        context.addCommandListener { remember(it.message()) }
        context.addEventListener { remember(it.message()) }
        context.addEventDispatcher(this)
    }

    @Subscribe
    internal fun on(event: HandlerFailedUnexpectedly) {
        remember(event)
    }

    @Subscribe
    internal fun on(event: CommandRejected) {
        remember(event)
    }

    private fun remember(message: Message): Unit = lock.withLock {
        messages.add(message)
        added.signalAll()
    }

    /**
     * Obtains the remembered messages of the given type, in the order they arrived.
     */
    fun <M : Message> all(type: Class<M>): List<M> = lock.withLock {
        messages.filterIsInstance(type)
    }

    /**
     * Waits for the first message of the given type, failing if it does not
     * arrive in [waitSeconds].
     */
    fun <M : Message> await(type: Class<M>): M = lock.withLock {
        var remaining = SECONDS.toNanos(waitSeconds)
        var found = messages.filterIsInstance(type).firstOrNull()
        while (found == null && remaining > 0) {
            remaining = added.awaitNanos(remaining)
            found = messages.filterIsInstance(type).firstOrNull()
        }
        withClue("A message of the type `${type.simpleName}` should have been posted") {
            found.shouldNotBeNull()
        }
    }
}

/**
 * Obtains the remembered messages of the type [M].
 */
internal inline fun <reified M : Message> SignalMonitor.all(): List<M> = all(M::class.java)

/**
 * Waits for the first message of the type [M].
 */
internal inline fun <reified M : Message> SignalMonitor.await(): M = await(M::class.java)
