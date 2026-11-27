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

package io.spine.server

import io.kotest.matchers.collections.shouldExist
import io.spine.client.Subscription
import io.spine.client.SubscriptionUpdate
import io.spine.grpc.StreamObservers.memoizingObserver
import io.spine.grpc.StreamObservers.noOpObserver
import io.spine.server.given.CancellingObserver
import io.spine.server.given.FrozenCall
import io.spine.server.given.LogRecorder
import io.spine.server.given.SubscriptionServer
import io.spine.server.given.waitFor
import io.spine.test.client.tasks.event.CTaskCreated
import io.spine.test.client.users.event.UserLoggedIn
import io.spine.testing.logging.mute.MuteLogging
import java.util.logging.Level
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Supplements `SubscriptionServiceTest` with the debug messages about the end of
 * a subscription, which also checks that the messages are formatted without errors.
 */
@MuteLogging
@DisplayName("`SubscriptionService`, at the debug level, should")
internal class SubscriptionServiceDebugLogSpec {

    private val server = SubscriptionServer()
    private val log = LogRecorder(SubscriptionService::class.java, Level.FINE)

    @AfterEach
    fun closeServerAndLog() {
        log.close()
        server.close()
    }

    @Test
    fun `log the cancellation of a subscription whose call is cancelled`() {
        val subscription = server.subscribe(UserLoggedIn::class.java)
        val stream = server.activate(subscription)

        stream.cancel()

        waitFor("the cancellation of the subscription") { !server.holds(subscription) }
        log.messages shouldExist {
            it.contains("is cancelled") && it.contains(subscription.id.value)
        }
    }

    @Test
    fun `log a call whose cancellation handler cannot be set`() {
        val subscription = server.subscribe(UserLoggedIn::class.java)

        server.directService.activate(subscription, FrozenCall())

        log.messages shouldExist {
            it.contains("Unable to set the cancellation handler") &&
                    it.contains(subscription.id.value)
        }
    }

    @Test
    fun `log an activation that a concurrent cancellation ends`() {
        val subscription = server.subscribe(UserLoggedIn::class.java, CTaskCreated::class.java)
        server.directService.activate(subscription, cancellingInSecondContext(subscription))

        server.directService.activate(subscription, memoizingObserver<SubscriptionUpdate>())

        log.messages shouldExist {
            it.contains("is cancelled while it is activated") &&
                    it.contains(subscription.id.value)
        }
    }

    @Test
    fun `log a context that a cancellation skips`() {
        val subscription = server.subscribe(UserLoggedIn::class.java, CTaskCreated::class.java)
        server.directService.activate(subscription, cancellingInSecondContext(subscription))

        server.directService.cancel(subscription, noOpObserver())

        log.messages shouldExist {
            it.contains("Skipped the bounded context") && it.contains(subscription.id.value)
        }
    }

    private fun cancellingInSecondContext(subscription: Subscription) =
        CancellingObserver(listOf(server.tasks, server.users).sorted().last(), subscription)
}
