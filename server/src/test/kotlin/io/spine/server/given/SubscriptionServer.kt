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

import com.google.protobuf.Message
import io.grpc.ManagedChannel
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.stub.ClientCallStreamObserver
import io.grpc.stub.ClientResponseObserver
import io.kotest.matchers.shouldBe
import io.spine.base.CommandMessage
import io.spine.client.Subscription
import io.spine.client.SubscriptionUpdate
import io.spine.client.Targets.allOf
import io.spine.client.grpc.SubscriptionServiceGrpc
import io.spine.core.Ack
import io.spine.core.Response
import io.spine.grpc.StreamObservers.memoizingObserver
import io.spine.server.Application
import io.spine.server.BoundedContext
import io.spine.server.Server
import io.spine.server.SubscriptionService
import io.spine.test.client.ClientTestContext
import io.spine.test.client.users.NAME as USERS
import io.spine.test.client.users.command.logInUser
import io.spine.testing.client.TestActorRequestFactory
import io.spine.testing.core.given.GivenUserId
import io.spine.testing.server.testApplication
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit.SECONDS
import org.junit.jupiter.api.fail

/**
 * An in-process gRPC server of an application with the Bounded Contexts of users and tasks,
 * and a channel to it.
 *
 * The context of [users] emits
 * [UserLoggedIn][io.spine.test.client.users.event.UserLoggedIn] events, and the context of
 * [tasks] emits [CTaskCreated][io.spine.test.client.tasks.event.CTaskCreated] events.
 * Each context records the problems with the requests to the services in its monitor.
 *
 * The server and the channel use direct executors. So the server serves the calls in
 * the order the spec makes them, which decides the latest of several activations of
 * a subscription. The cancellation of a call usually reaches the server before
 * [UpdateStream.cancel] returns.
 */
internal class SubscriptionServer : AutoCloseable {

    private val requests = TestActorRequestFactory(SubscriptionServer::class.java)

    /**
     * Records the problems with the requests in the context of users.
     */
    val usersMonitor = RequestEventMonitor()

    /**
     * Records the problems with the requests in the context of tasks.
     */
    val tasksMonitor = RequestEventMonitor()

    private val application: Application = testApplication {
        add(ClientTestContext.users().addEventDispatcher(usersMonitor))
        add(ClientTestContext.tasks().addEventDispatcher(tasksMonitor))
    }

    /**
     * The context of users.
     */
    val users: BoundedContext = application.contexts().single { it.name().value == USERS }

    /**
     * The context of tasks.
     */
    val tasks: BoundedContext = application.contexts().single { it != users }

    /**
     * The subscription service of the application, for the specs that call it directly
     * rather than over gRPC.
     */
    val directService: SubscriptionService = SubscriptionService.of(application)

    private val serverName = InProcessServerBuilder.generateName()
    private val server: Server = Server.inProcess(serverName)
        .setApplication(application)
        .build()
        .also { it.start() }

    /**
     * The channel through which the client calls the server.
     */
    val channel: ManagedChannel = InProcessChannelBuilder.forName(serverName)
        .directExecutor()
        .build()

    private val blockingService = SubscriptionServiceGrpc.newBlockingStub(channel)
    private val service = SubscriptionServiceGrpc.newStub(channel)

    /**
     * Subscribes to all the messages of each of the given types.
     */
    fun subscribe(vararg types: Class<out Message>): Subscription {
        val topic = requests.topic().forTargets(types.map { allOf(it) })
        return blockingService.subscribe(topic)
    }

    /**
     * Activates the subscription in a call of its own.
     */
    fun activate(subscription: Subscription): UpdateStream {
        val stream = UpdateStream()
        service.activate(subscription, stream)
        return stream
    }

    /**
     * Cancels the subscription, expecting the server to answer.
     */
    fun cancel(subscription: Subscription): Response = blockingService.cancel(subscription)

    /**
     * Makes the context of users emit
     * a [UserLoggedIn][io.spine.test.client.users.event.UserLoggedIn] event.
     */
    fun logIn() {
        post(users, logInUser { user = GivenUserId.generated() })
    }

    private fun post(context: BoundedContext, message: CommandMessage) {
        val ack = memoizingObserver<Ack>()
        context.commandBus().post(requests.createCommand(message), ack)
        ack.firstResponse().status.hasOk() shouldBe true
    }

    /**
     * Tells whether any context of the application has the subscription.
     */
    fun holds(subscription: Subscription): Boolean =
        application.contexts().any { it.stand().hasSubscription(subscription) }

    /**
     * Drops the channel without cancelling the subscriptions, and then shuts down
     * the server and closes the application.
     */
    override fun close() {
        try {
            channel.shutdownNow().awaitTermination(TIMEOUT_SECONDS, SECONDS)
            server.shutdownAndWait()
        } finally {
            application.close()
        }
    }
}

/**
 * The client side of the call that activates a subscription.
 */
internal class UpdateStream : ClientResponseObserver<Subscription, SubscriptionUpdate> {

    private lateinit var call: ClientCallStreamObserver<Subscription>
    private val received = CopyOnWriteArrayList<SubscriptionUpdate>()

    /**
     * The received updates, in the order of receiving.
     */
    val updates: List<SubscriptionUpdate> = received

    /**
     * Tells whether the server completed the stream.
     */
    @Volatile
    var completed: Boolean = false
        private set

    /**
     * The error that ended the stream, if any.
     */
    @Volatile
    var error: Throwable? = null
        private set

    override fun beforeStart(requestStream: ClientCallStreamObserver<Subscription>) {
        call = requestStream
    }

    override fun onNext(value: SubscriptionUpdate) {
        received.add(value)
    }

    override fun onError(t: Throwable) {
        error = t
    }

    override fun onCompleted() {
        completed = true
    }

    /**
     * Cancels the call, as a client does when it no longer needs the updates.
     */
    fun cancel() {
        call.cancel("The client does not need the updates.", null)
    }
}

/**
 * Waits until the condition holds, failing the test if it does not hold within a few seconds.
 *
 * Guards the specs against the end of a gRPC call, or an update, reaching the other side
 * after the call that causes it returns.
 *
 * @param what The description of the awaited state, for the failure message.
 * @param condition Tells whether the awaited state is reached.
 */
internal fun waitFor(what: String, condition: () -> Boolean) {
    val deadline = System.nanoTime() + SECONDS.toNanos(TIMEOUT_SECONDS)
    while (!condition()) {
        if (System.nanoTime() >= deadline) {
            fail("Waited for $what for $TIMEOUT_SECONDS seconds.")
        }
        Thread.sleep(POLL_MILLIS)
    }
}

private const val TIMEOUT_SECONDS = 5L
private const val POLL_MILLIS = 10L
