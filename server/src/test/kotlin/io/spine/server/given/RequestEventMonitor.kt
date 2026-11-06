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

package io.spine.server.given

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.types.shouldBeInstanceOf
import io.spine.base.EventMessage
import io.spine.core.EventContext
import io.spine.core.Subscribe
import io.spine.core.TenantId
import io.spine.server.event.AbstractEventSubscriber
import io.spine.system.server.event.CommandPostingFailed
import io.spine.system.server.event.QueryExecutionFailed
import io.spine.system.server.event.QueryValidationFailed
import io.spine.system.server.event.SubscriptionServiceFailed
import io.spine.system.server.event.SubscriptionValidationFailed
import io.spine.system.server.event.TopicValidationFailed
import io.spine.system.server.event.UnknownSubscriptionActivationRequested
import io.spine.system.server.event.UnknownSubscriptionCancellationRequested
import io.spine.system.server.event.UnsupportedQueryReceived
import io.spine.system.server.event.UnsupportedTopicReceived

/**
 * Memoizes the System events that record the problems with the requests to the services.
 */
@Suppress("TooManyFunctions") // One subscriber method per event type.
internal class RequestEventMonitor : AbstractEventSubscriber() {

    private val received = mutableListOf<Pair<EventMessage, EventContext>>()

    /**
     * The messages of the received events, in the order of receiving.
     */
    val events: List<EventMessage>
        get() = received.map { it.first }

    /**
     * Obtains the only received event, checking that it is of the type [E].
     */
    inline fun <reified E : EventMessage> single(): E {
        events shouldHaveSize 1
        return events.single().shouldBeInstanceOf<E>()
    }

    /**
     * Obtains the tenant for which the only received event was posted.
     */
    fun tenantOfSingle(): TenantId {
        received shouldHaveSize 1
        val (_, context) = received.single()
        return context.findActorContext()
            .shouldBePresent()
            .tenantId
    }

    @Subscribe
    fun on(event: CommandPostingFailed, context: EventContext) {
        remember(event, context)
    }

    @Subscribe
    fun on(event: QueryValidationFailed, context: EventContext) {
        remember(event, context)
    }

    @Subscribe
    fun on(event: UnsupportedQueryReceived, context: EventContext) {
        remember(event, context)
    }

    @Subscribe
    fun on(event: QueryExecutionFailed, context: EventContext) {
        remember(event, context)
    }

    @Subscribe
    fun on(event: TopicValidationFailed, context: EventContext) {
        remember(event, context)
    }

    @Subscribe
    fun on(event: UnsupportedTopicReceived, context: EventContext) {
        remember(event, context)
    }

    @Subscribe
    fun on(event: SubscriptionValidationFailed, context: EventContext) {
        remember(event, context)
    }

    @Subscribe
    fun on(event: UnknownSubscriptionActivationRequested, context: EventContext) {
        remember(event, context)
    }

    @Subscribe
    fun on(event: UnknownSubscriptionCancellationRequested, context: EventContext) {
        remember(event, context)
    }

    @Subscribe
    fun on(event: SubscriptionServiceFailed, context: EventContext) {
        remember(event, context)
    }

    private fun remember(event: EventMessage, context: EventContext) {
        received.add(event to context)
    }
}
