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

package io.spine.server.integration

import com.google.protobuf.Any
import io.spine.base.EventMessage
import io.spine.base.Time.currentTime
import io.spine.core.ActorContext
import io.spine.core.UserId
import io.spine.protobuf.AnyPacker.pack
import io.spine.server.Application
import io.spine.server.BoundedContext
import io.spine.server.BoundedContextBuilder
import io.spine.server.Closeable
import io.spine.server.event.EventFactory
import io.spine.server.type.EventEnvelope
import io.spine.util.Preconditions2.checkNotEmptyOrBlank

/**
 * An external non-Spine based upstream system.
 *
 * `ThirdPartyContext` helps to represent an external system as a Bounded Context. Events
 * that occur in the external system are converted into domain events of the user's
 * Bounded Contexts and dispatched via [IntegrationBroker].
 *
 * A `ThirdPartyContext` is [added][Application.Builder.add] to an [Application] like any other
 * Bounded Context, and can emit events once the application is built:
 * ```
 * val calendar = ThirdPartyContext.singleTenant("Calendar")
 * val application = application {
 *     serverEnvironment = environment
 *     add(BoundedContext.singleTenant("Scheduling").add(MeetingRepository()))
 *     add(calendar)
 * }
 * calendar.emittedEvent(event, actor)
 * ```
 *
 * Note that a `ThirdPartyContext` sends a request for external messages to other contexts.
 * The `ThirdPartyContext` never consumes external messages itself, but requires the other
 * Bounded Contexts to send their requests, so that the publishing channels are open.
 * Depending on the implementation of [transport][io.spine.server.transport.TransportFactory],
 * building a `ThirdPartyContext` may be an expensive operation. Thus, it is recommended that
 * the instances of this class are reused and [closed][close] when they are no longer needed.
 * The [Application] closes the context, if it is still open, when the application is closed.
 */
public class ThirdPartyContext private constructor(

    /**
     * The builder of the Bounded Context that represents the third-party system.
     *
     * The [Application] builds the context.
     */
    @get:JvmSynthetic
    internal val builder: BoundedContextBuilder
) : Closeable {

    private val producerId: Any = pack(builder.name())

    /**
     * The built context, or `null` until the application is built.
     */
    @Volatile
    private var context: BoundedContext? = null

    /**
     * Gives this third-party context the Bounded Context that the [Application] built
     * from its [builder].
     *
     * @throws IllegalStateException If the context is already given.
     */
    @JvmSynthetic
    internal fun attach(context: BoundedContext) {
        check(this.context == null) {
            "The third-party context `${builder.name().value}` is already built."
        }
        this.context = context
    }

    /**
     * Emits an event from the third-party system.
     *
     * If the event is required by another Context, posts the event into
     * the [IntegrationBroker] of the respective Context. Does nothing if the event is not
     * required by any Context.
     *
     * The caller is required to supply the tenant ID via the `ActorContext.tenant_id` if
     * this Context is multitenant.
     *
     * @param eventMessage The event.
     * @param actorContext The info about the actor, a user or a software component,
     *   who emits the event.
     * @throws IllegalStateException If the application of this context is not built yet.
     */
    public fun emittedEvent(eventMessage: EventMessage, actorContext: ActorContext) {
        val context = context()
        checkTenant(context, actorContext, eventMessage)
        val eventFactory = EventFactory.forImport(actorContext, producerId)
        val event = eventFactory.createEvent(eventMessage)
        context.internalAccess()
            .broker()
            .publish(EventEnvelope.of(event))
    }

    /**
     * Emits an event from the third-party system.
     *
     * If the event is required by another Context, posts the event into
     * the [IntegrationBroker] of the respective Context. Does nothing if the event is not
     * required by any Context.
     *
     * This overload may only be used for single-tenant third-party contexts. If this Context is
     * multitenant, this method throws an exception.
     *
     * @param eventMessage The event.
     * @param userId The ID of the actor, a user or a software component, who emits the event.
     * @throws IllegalStateException If the application of this context is not built yet.
     */
    public fun emittedEvent(eventMessage: EventMessage, userId: UserId) {
        val actorContext = ActorContext.newBuilder()
            .setActor(userId)
            .setTimestamp(currentTime())
            .build()
        emittedEvent(eventMessage, actorContext)
    }

    /**
     * Closes this Context and cleans up underlying resources.
     *
     * Attempts of emitting an event from a closed Context result in an exception.
     * Does nothing if the application of this context is not built yet.
     */
    override fun close() {
        context?.close()
    }

    /**
     * Tells if the context is built and not closed.
     */
    override fun isOpen(): Boolean = context?.isOpen ?: false

    private fun context(): BoundedContext =
        checkNotNull(context) {
            "The third-party context `${builder.name().value}` is not built yet." +
                    " Please add it to an `Application`, and build the application."
        }

    public companion object {

        /**
         * Creates a new single-tenant instance of `ThirdPartyContext` with the given name.
         *
         * The context is built when the [Application] to which it is
         * [added][Application.Builder.add] is built.
         *
         * @param name The name of the Bounded Context representing a part of
         *   a third-party system.
         */
        @JvmStatic
        public fun singleTenant(name: String): ThirdPartyContext = newContext(name, false)

        /**
         * Creates a new multitenant instance of `ThirdPartyContext` with the given name.
         *
         * The context is built when the [Application] to which it is
         * [added][Application.Builder.add] is built.
         *
         * @param name The name of the Bounded Context representing a part of
         *   a third-party system.
         */
        @JvmStatic
        public fun multitenant(name: String): ThirdPartyContext = newContext(name, true)

        private fun newContext(name: String, multitenant: Boolean): ThirdPartyContext {
            checkNotEmptyOrBlank(name)
            val builder = if (multitenant) {
                BoundedContext.multitenant(name)
            } else {
                BoundedContext.singleTenant(name)
            }
            builder.systemSettings()
                .disableCommandLog()
                .forgetEvents()
            return ThirdPartyContext(builder)
        }

        private fun checkTenant(
            context: BoundedContext,
            actorContext: ActorContext,
            event: EventMessage
        ) {
            val tenantSupplied = actorContext.hasTenantId()
            val contextName = context.name().value
            val eventType = event.javaClass.name
            if (context.isMultitenant) {
                require(tenantSupplied) {
                    "Cannot post `$eventType` into a third-party multitenant context" +
                            " `$contextName`. No tenant ID supplied."
                }
            } else {
                require(!tenantSupplied) {
                    "Cannot post `$eventType` into a third-party single-tenant context" +
                            " `$contextName`. Tenant ID must NOT be supplied."
                }
            }
        }
    }
}
