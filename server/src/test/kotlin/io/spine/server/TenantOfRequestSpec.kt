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

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.spine.client.Query
import io.spine.client.QueryResponse
import io.spine.client.Subscription
import io.spine.client.SubscriptionUpdate
import io.spine.client.Subscriptions
import io.spine.client.Topic
import io.spine.core.Ack
import io.spine.core.Command
import io.spine.core.CommandId
import io.spine.core.ErrorCase.INTERNAL_ERROR
import io.spine.core.ErrorCase.TENANT_ERROR
import io.spine.core.ErrorCase.UNSUPPORTED_REQUEST
import io.spine.core.Response
import io.spine.core.Subscribe
import io.spine.core.tenantId
import io.spine.grpc.MemoizingObserver
import io.spine.grpc.StreamObservers.memoizingObserver
import io.spine.logging.Level.Companion.WARNING
import io.spine.server.app.RequestOfUnregisteredTenantRejected
import io.spine.server.app.TenantCheckFailed
import io.spine.server.app.UnsupportedRequestRejected
import io.spine.server.event.AbstractEventSubscriber
import io.spine.server.given.FailingIndex
import io.spine.server.given.RecordingLog
import io.spine.server.given.RequestEventMonitor
import io.spine.server.given.errorCase
import io.spine.server.tenant.TenantIndex
import io.spine.system.server.event.CommandReceived
import io.spine.system.server.event.QueryTenantNotExpected
import io.spine.system.server.event.QueryTenantNotSpecified
import io.spine.system.server.event.SubscriptionTenantNotExpected
import io.spine.system.server.event.SubscriptionTenantNotSpecified
import io.spine.system.server.event.TopicTenantNotExpected
import io.spine.system.server.event.TopicTenantNotSpecified
import io.spine.test.aggregate.ProjectId
import io.spine.test.client.tasks.event.CTaskCreated
import io.spine.test.commandservice.CmdServDontHandle
import io.spine.test.projection.Project
import io.spine.testing.client.TestActorRequestFactory
import io.spine.testing.logging.mute.MuteLogging
import io.spine.testing.server.testApplication
import io.spine.validation.NonValidated
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Checks how the gRPC services check the tenant of a request, and what they record when
 * they refuse one.
 */
@MuteLogging
@DisplayName("The gRPC services should")
internal class TenantOfRequestSpec {

    private val log = RecordingLog()
    private val monitor = RequestEventMonitor()
    private val received = ReceivedCommands()
    private val tenant = tenantId { value = "Tenant of the request" }
    private val withTenant = TestActorRequestFactory(javaClass, tenant)
    private val withoutTenant = TestActorRequestFactory(javaClass)
    private lateinit var application: Application

    @AfterEach
    fun closeApplication() {
        if (::application.isInitialized) {
            application.close()
        }
    }

    @Nested inner class
    `refuse a command of an unregistered tenant` {

        @Test
        fun `answering the tenant error, and writing the command to the log`() {
            serve(multiTenant = true)
            val command = command()

            post(command).status.errorCase shouldBe TENANT_ERROR

            val (level, event) = log.single<RequestOfUnregisteredTenantRejected>()
            level shouldBe WARNING
            event.tenant shouldBe tenant
            event.context shouldBe context().name()
            event.request.command shouldBe command
            event.reason shouldContain "`CommandService`"
        }

        @Test
        fun `recording nothing in the context`() {
            serve(multiTenant = true)

            post(command())

            received.commands.shouldBeEmpty()
            monitor.events.shouldBeEmpty()
        }
    }

    @Test
    fun `post a command of a registered tenant`() {
        serve(multiTenant = true)
        register()
        val command = command()

        post(command).status.hasOk() shouldBe true

        received.commands shouldContainExactly listOf(command.id)
        log.records.shouldBeEmpty()
    }

    @Test
    fun `fail a command if the tenant index cannot tell if it has the tenant`() {
        serve(multiTenant = true, index = FailingIndex)
        val observer = memoizingObserver<Ack>()

        CommandService.of(application).post(command(), observer)

        observer.error.shouldNotBeNull().errorCase() shouldBe INTERNAL_ERROR
        log.single<TenantCheckFailed>()
    }

    @Test
    fun `fail a query if the tenant index cannot tell if it has the tenant`() {
        serve(multiTenant = true, index = FailingIndex)

        read(query(withTenant)).shouldNotBeNull().errorCase() shouldBe INTERNAL_ERROR

        val (_, event) = log.single<TenantCheckFailed>()
        event.context shouldBe context().name()
    }

    @Test
    fun `fail a topic that no context serves if the tenant index fails to tell`() {
        serve(multiTenant = true, index = FailingIndex)
        val topic = withTenant.topic().allOf(CTaskCreated::class.java)

        subscribe(topic).error.shouldNotBeNull().errorCase() shouldBe INTERNAL_ERROR

        val (_, event) = log.single<TenantCheckFailed>()
        event.request.topic shouldBe topic
        event.hasContext() shouldBe false
    }

    @Nested inner class
    `refuse a query` {

        @Test
        fun `of an unregistered tenant, writing it to the log`() {
            serve(multiTenant = true)
            val query = query(withTenant)

            read(query).shouldNotBeNull().errorCase() shouldBe TENANT_ERROR

            val (_, event) = log.single<RequestOfUnregisteredTenantRejected>()
            event.request.query shouldBe query
            event.tenant shouldBe tenant
            monitor.events.shouldBeEmpty()
        }

        @Test
        fun `without a tenant to a multi-tenant context, recording the query`() {
            serve(multiTenant = true)
            val query = query(withoutTenant)

            read(query).shouldNotBeNull().errorCase() shouldBe TENANT_ERROR

            monitor.single<QueryTenantNotSpecified>().id shouldBe query.id
            log.records.shouldBeEmpty()
        }

        @Test
        fun `with a tenant to a single-tenant context, recording the query`() {
            serve(multiTenant = false)
            val query = query(withTenant)

            read(query).shouldNotBeNull().errorCase() shouldBe TENANT_ERROR

            val event = monitor.single<QueryTenantNotExpected>()
            event.id shouldBe query.id
            event.tenant shouldBe tenant
            log.records.shouldBeEmpty()
        }
    }

    @Test
    fun `serve a query of a registered tenant`() {
        serve(multiTenant = true)
        register()

        read(query(withTenant)).shouldBeNull()

        log.records.shouldBeEmpty()
    }

    @Nested inner class
    `refuse to subscribe to a topic` {

        @Test
        fun `of an unregistered tenant, writing it to the log`() {
            serve(multiTenant = true)
            val topic = topic(withTenant)

            subscribe(topic).error.shouldNotBeNull().errorCase() shouldBe TENANT_ERROR

            val (_, event) = log.single<RequestOfUnregisteredTenantRejected>()
            event.request.topic shouldBe topic
            event.context shouldBe context().name()
        }

        @Test
        fun `without a tenant to a multi-tenant context, recording the topic`() {
            serve(multiTenant = true)
            val topic = topic(withoutTenant)

            subscribe(topic).error.shouldNotBeNull().errorCase() shouldBe TENANT_ERROR

            monitor.single<TopicTenantNotSpecified>().id shouldBe topic.id
        }

        @Test
        fun `with a tenant to a single-tenant context, recording the topic`() {
            serve(multiTenant = false)
            val topic = topic(withTenant)

            subscribe(topic).error.shouldNotBeNull().errorCase() shouldBe TENANT_ERROR

            monitor.single<TopicTenantNotExpected>().tenant shouldBe tenant
        }

        @Test
        fun `that no context serves, of an unregistered tenant, naming no context`() {
            serve(multiTenant = true)
            val topic = withTenant.topic().allOf(CTaskCreated::class.java)

            subscribe(topic).error.shouldNotBeNull().errorCase() shouldBe TENANT_ERROR

            val (_, event) = log.single<RequestOfUnregisteredTenantRejected>()
            event.request.topic shouldBe topic
            event.hasContext() shouldBe false
        }
    }

    @Test
    fun `subscribe to a topic of a registered tenant`() {
        serve(multiTenant = true)
        register()

        val observer = subscribe(topic(withTenant))

        observer.error.shouldBeNull()
        observer.firstResponse().shouldNotBeNull()
        log.records.shouldBeEmpty()
    }

    @Nested inner class
    `refuse a subscription` {

        @Test
        fun `to activate, of an unregistered tenant, writing it to the log`() {
            serve(multiTenant = true)
            val subscription = Subscriptions.from(topic(withTenant))
            val observer = memoizingObserver<SubscriptionUpdate>()

            SubscriptionService.of(application).activate(subscription, observer)

            observer.error.shouldNotBeNull().errorCase() shouldBe TENANT_ERROR
            val (_, event) = log.single<RequestOfUnregisteredTenantRejected>()
            event.request.subscriptionActivation shouldBe subscription
        }

        @Test
        fun `to cancel, of an unregistered tenant, writing it to the log`() {
            serve(multiTenant = true)
            val subscription = Subscriptions.from(topic(withTenant))

            cancel(subscription).error.shouldNotBeNull().errorCase() shouldBe TENANT_ERROR

            val (_, event) = log.single<RequestOfUnregisteredTenantRejected>()
            event.request.subscriptionCancellation shouldBe subscription
        }

        @Test
        fun `without a tenant in a multi-tenant context, recording the subscription`() {
            serve(multiTenant = true)
            val subscription = Subscriptions.from(topic(withoutTenant))

            cancel(subscription).error.shouldNotBeNull().errorCase() shouldBe TENANT_ERROR

            monitor.single<SubscriptionTenantNotSpecified>().id shouldBe subscription.id
        }

        @Test
        fun `with a tenant in a single-tenant context, recording the subscription`() {
            serve(multiTenant = false)
            val subscription = Subscriptions.from(topic(withTenant))

            cancel(subscription).error.shouldNotBeNull().errorCase() shouldBe TENANT_ERROR

            val event = monitor.single<SubscriptionTenantNotExpected>()
            event.id shouldBe subscription.id
            event.tenant shouldBe tenant
        }

        @Test
        fun `to a topic that no context serves, of an unregistered tenant, naming no context`() {
            serve(multiTenant = true)
            val topic = withTenant.topic().allOf(CTaskCreated::class.java)
            val subscription = Subscriptions.from(topic)

            cancel(subscription).error.shouldNotBeNull().errorCase() shouldBe TENANT_ERROR

            val (_, event) = log.single<RequestOfUnregisteredTenantRejected>()
            event.request.subscriptionCancellation shouldBe subscription
            event.hasContext() shouldBe false
        }
    }

    @Test
    fun `not check the tenant of a topic that no context serves, if no context is multi-tenant`() {
        serve(multiTenant = false)
        val topic = withTenant.topic().allOf(CTaskCreated::class.java)

        subscribe(topic).error.shouldBeNull()

        log.records.shouldBeEmpty()
    }

    @Nested inner class
    `write to the log of the application` {

        @Test
        fun `a command that no context serves`() {
            serve(multiTenant = false)
            val command = withoutTenant.createCommand(CmdServDontHandle.getDefaultInstance())

            post(command).status.errorCase shouldBe UNSUPPORTED_REQUEST

            val (level, event) = log.single<UnsupportedRequestRejected>()
            level shouldBe WARNING
            event.request.command shouldBe command
            event.reason shouldContain "No bounded context serves the type"
        }

        @Test
        fun `a query of a type unknown to the server as text, naming the type and the query`() {
            serve(multiTenant = false)
            val query = query(withoutTenant).toBuilder()
                .setTarget(
                    query(withoutTenant).target.toBuilder()
                        .setType("type.spine.io/spine.test.NoSuchType")
                        .buildPartial()
                )
                .buildPartial()

            read(query).shouldNotBeNull().errorCase() shouldBe UNSUPPORTED_REQUEST

            val record = log.records.single()
            record.level shouldBe WARNING
            record.event shouldBe null
            val message = record.message.shouldNotBeNull()
            message shouldContain "spine.test.NoSuchType"
            message shouldContain query.id.value
        }

        @Test
        fun `a topic of a type unknown to the server as text, naming the type and the topic`() {
            serve(multiTenant = false)
            val topic = withUnknownTarget(topic(withoutTenant))

            subscribe(topic).error.shouldNotBeNull().errorCase() shouldBe UNSUPPORTED_REQUEST

            val message = log.records.single().message.shouldNotBeNull()
            message shouldContain "spine.test.NoSuchType"
            message shouldContain topic.id.value
        }

        @Test
        fun `a subscription of a type unknown to the server as text, naming the subscription`() {
            serve(multiTenant = false)
            val subscription = Subscriptions.from(withUnknownTarget(topic(withoutTenant)))

            cancel(subscription).error.shouldNotBeNull().errorCase() shouldBe UNSUPPORTED_REQUEST

            val message = log.records.single().message.shouldNotBeNull()
            message shouldContain "spine.test.NoSuchType"
            message shouldContain subscription.id.value
        }
    }

    /**
     * Creates an application of one context, which serves projects, and stores the tenants
     * in the given index, or in the default one.
     */
    private fun serve(multiTenant: Boolean, index: TenantIndex? = null) {
        application = testApplication {
            applicationLog = log
            index?.let { tenantIndex = it }
            add(
                BoundedContextBuilder.assumingTests(multiTenant)
                    .add(Given.ProjectAggregateRepository())
                    .add(Given.ProjectDetailsRepository())
                    .addEventDispatcher(monitor)
                    .addEventDispatcher(received)
            )
        }
    }

    private fun context(): BoundedContext = application.contexts().single()

    private fun register() {
        application.tenantIndex().keep(tenant)
    }

    private fun command(): Command =
        withTenant.createCommand(Given.CommandMessage.createProject(ProjectId.generate()))

    private fun query(requests: TestActorRequestFactory): Query =
        requests.query().all(Project::class.java)

    private fun topic(requests: TestActorRequestFactory): Topic =
        requests.topic().allOf(Project::class.java)

    /**
     * Copies the given topic, targeting a type unknown to the server.
     */
    private fun withUnknownTarget(topic: Topic): @NonValidated Topic =
        topic.toBuilder()
            .setTarget(topic.target.toBuilder().setType("type.spine.io/spine.test.NoSuchType"))
            .buildPartial()

    private fun post(command: Command): Ack {
        val observer = memoizingObserver<Ack>()
        CommandService.of(application).post(command, observer)
        return observer.firstResponse()
    }

    /**
     * Executes the given query.
     *
     * @return The error of the call, or `null` if the query is served.
     */
    private fun read(query: Query): Throwable? {
        val observer = memoizingObserver<QueryResponse>()
        QueryService.of(application).read(query, observer)
        return observer.error
    }

    private fun subscribe(topic: Topic): MemoizingObserver<Subscription> =
        memoizingObserver<Subscription>().also {
            SubscriptionService.of(application).subscribe(topic, it)
        }

    private fun cancel(subscription: Subscription): MemoizingObserver<Response> =
        memoizingObserver<Response>().also {
            SubscriptionService.of(application).cancel(subscription, it)
        }
}

/**
 * Remembers the IDs of the commands that a context receives.
 */
internal class ReceivedCommands : AbstractEventSubscriber() {

    private val received = mutableListOf<CommandId>()

    /**
     * The IDs of the received commands, in the order of receiving.
     */
    val commands: List<CommandId>
        get() = received.toList()

    @Subscribe
    fun on(event: CommandReceived) {
        received.add(event.id)
    }
}
