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

package io.spine.server.async

import io.spine.core.Signal
import io.spine.logging.LoggingFactory.loggerFor
import io.spine.server.tenant.TenantAwareRunner
import io.spine.server.type.SignalEnvelope
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The handling of one signal by one asynchronous handler.
 *
 * An [AsyncExecution] gets an invocation when a signal is dispatched to an asynchronous
 * handler, and decides on which thread, and when, to call [run].
 *
 * An invocation is ongoing until [run] returns. An execution that limits the number of
 * ongoing invocations counts the calls of [run] that have not returned yet.
 */
public class AsyncInvocation internal constructor(

    /**
     * The class of the handler.
     */
    private val handlerClass: Class<*>,

    /**
     * The signal to handle.
     */
    private val envelope: SignalEnvelope<*, *, *>,

    /**
     * Passes the signal to the handler and posts the produced messages.
     */
    private val handling: Runnable
) {

    /**
     * Turns `true` when [run] is called, so that the signal is handled once.
     */
    private val started = AtomicBoolean(false)

    /**
     * Obtains the class of the handler that is going to handle the [signal].
     *
     * An execution can use it to choose how to run the invocation, for example,
     * to apply a limit of ongoing invocations.
     */
    public fun handlerClass(): Class<*> = handlerClass

    /**
     * Obtains the command or the event to handle.
     */
    public fun signal(): Signal<*, *, *> = envelope.outerObject()

    /**
     * Handles the [signal] on the calling thread and posts the messages the handler produces.
     *
     * The method returns when the handling is over.
     *
     * If the handler method fails, the failure is reported the same way as for a synchronous
     * handler, and this method returns normally. The method throws only if the handling
     * could not be completed for a reason other than the handler method, for example, if
     * the produced messages could not be posted. Such an exception is logged before it is
     * thrown. An [Error] is thrown as it is.
     *
     * The signal is handled under its tenant, whatever the tenant of the calling thread is.
     *
     * @throws IllegalStateException If the method is called more than once.
     */
    public fun run() {
        try {
            check(started.compareAndSet(false, true)) {
                "The invocation `$this` has already been run."
            }
            TenantAwareRunner.with(envelope.tenantId())
                .run(handling)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // Whatever the handling throws must reach the log: an execution may run
            // the invocation on a thread that nobody observes.
            logger.atError().withCause(e).log {
                "The invocation `$this` failed."
            }
            throw e
        }
    }

    /**
     * Returns the name of the handler class followed by the type and the ID of the signal.
     */
    override fun toString(): String {
        val signal = envelope.messageId()
        return "${handlerClass.name}(${signal.typeUrl} ${envelope.id().value()})"
    }

    private companion object {

        private val logger = loggerFor(AsyncInvocation::class)
    }
}
