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

package io.spine.client

import io.grpc.StatusRuntimeException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.spine.base.CommandMessage
import io.spine.client.EventFilter.eq
import io.spine.core.ErrorCase.UNSUPPORTED_REQUEST
import io.spine.core.UserId
import io.spine.grpc.StreamObservers.errorCaseOf
import io.spine.server.BoundedContextBuilder
import io.spine.server.given.organizations.Organization
import io.spine.test.client.ClientTestContext.tasks
import io.spine.test.client.ClientTestContext.users
import io.spine.test.client.tasks.CTask
import io.spine.test.client.tasks.CTaskId
import io.spine.test.client.tasks.command.CreateCTask
import io.spine.test.client.tasks.command.archiveCTask
import io.spine.test.client.tasks.command.createCTask
import io.spine.test.client.tasks.command.unarchiveCTask
import io.spine.test.client.tasks.event.CTaskCreated
import io.spine.test.client.users.command.logInUser
import io.spine.test.client.users.event.UserLoggedIn
import io.spine.test.unpublished.Locomotive
import io.spine.test.unpublished.event.WheelsKnocked
import io.spine.testing.core.given.GivenUserId
import io.spine.testing.logging.mute.MuteLogging
import io.spine.type.UnpublishedLanguageException
import java.util.Optional
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@MuteLogging
@DisplayName("`TopicRequest` should")
internal class TopicRequestSpec : AbstractClientTest() {

    private val author = GivenUserId.generated()

    override fun contexts(): List<BoundedContextBuilder> = listOf(tasks(), users())

    @Test
    fun `deliver events and entity states of several contexts in one subscription`() {
        val created = mutableListOf<CTaskCreated>()
        val tasks = mutableListOf<CTask>()
        val loggedIn = mutableListOf<UserLoggedIn>()
        val user = GivenUserId.generated()

        val subscription = client().asGuest().subscribe {
            event<CTaskCreated> { it.observe { e -> created += e } }
            state<CTask> { it.observe { task -> tasks += task } }
            event<UserLoggedIn> {
                it.where(eq(UserLoggedIn.Field.user(), user))
                    .observe { e -> loggedIn += e }
            }
        }
        val createTask = createTask("Alpha", author)
        post(createTask)
        post(logInUser { this.user = user })

        created.map { it.id } shouldContainExactly listOf(createTask.id)
        tasks.map { it.id } shouldContainExactly listOf(createTask.id)
        loggedIn.map { it.user } shouldContainExactly listOf(user)
        client().subscriptions().contains(subscription) shouldBe true
    }

    @Test
    fun `accept the types of the targets as classes`() {
        val created = mutableListOf<CTaskCreated>()
        val tasks = mutableListOf<CTask>()

        client().asGuest()
            .subscribe()
            .event(CTaskCreated::class.java) { it.observe { e, _ -> created += e } }
            .state(CTask::class.java) { it.observe { task -> tasks += task } }
            .post()
        val createTask = createTask("Alpha", author)
        post(createTask)

        created.map { it.id } shouldContainExactly listOf(createTask.id)
        tasks.map { it.id } shouldContainExactly listOf(createTask.id)
    }

    @Test
    fun `deliver the states that match each target of one type to its own consumers`() {
        val first = mutableListOf<CTask>()
        val second = mutableListOf<CTask>()
        val onlyFirst = createTask("Alpha", author)
        val both = createTask("Beta", author)
        val onlySecond = createTask("Gamma", author)

        client().asGuest().subscribe {
            state<CTask> {
                it.byId(onlyFirst.id, both.id)
                    .observe { task -> first += task }
            }
            state<CTask> {
                it.byId(both.id, onlySecond.id)
                    .observe { task -> second += task }
            }
        }
        listOf(onlyFirst, both, onlySecond).forEach(::post)

        first.map { it.id } shouldContainExactly listOf(onlyFirst.id, both.id)
        second.map { it.id } shouldContainExactly listOf(both.id, onlySecond.id)
    }

    @Test
    fun `report the entities that no longer match a target to the consumers of that target`() {
        val noLongerFirst = mutableListOf<CTaskId>()
        val noLongerSecond = mutableListOf<CTaskId>()
        val onlyFirst = createTask("Alpha", author)
        val both = createTask("Beta", author)

        client().asGuest().subscribe {
            state<CTask> {
                it.byId(onlyFirst.id, both.id)
                    .observe { }
                    .whenNoLongerMatching(CTaskId::class.java) { id -> noLongerFirst += id }
            }
            state<CTask> {
                it.byId(both.id)
                    .observe { }
                    .whenNoLongerMatching(CTaskId::class.java) { id -> noLongerSecond += id }
            }
        }
        listOf(onlyFirst, both).forEach(::post)
        post(archiveCTask { id = onlyFirst.id })
        post(archiveCTask { id = both.id })

        noLongerFirst shouldContainExactly listOf(onlyFirst.id, both.id)
        noLongerSecond shouldContainExactly listOf(both.id)
    }

    @Test
    fun `stop the delivery to all the targets with one cancellation`() {
        val created = mutableListOf<CTaskCreated>()
        val tasks = mutableListOf<CTask>()
        val subscription = client().asGuest().subscribe {
            event<CTaskCreated> { it.observe { e -> created += e } }
            state<CTask> { it.observe { task -> tasks += task } }
        }
        val subscriptions = client().subscriptions()

        subscriptions.cancel(subscription) shouldBe true
        subscriptions.isEmpty shouldBe true

        post(createTask("Alpha", author))
        created.shouldBeEmpty()
        tasks.shouldBeEmpty()
    }

    @Test
    fun `pass the errors of the consumers of all the targets to the consuming error handler`() {
        val eventFailure = IllegalStateException("The consumer of events failed.")
        val stateFailure = IllegalStateException("The consumer of states failed.")
        val reported = mutableListOf<Throwable>()

        client().asGuest().subscribe {
            event<CTaskCreated> { it.observe { throw eventFailure } }
            state<CTask> { it.observe { throw stateFailure } }
            onConsumingError { _, error -> reported += error }
        }
        post(createTask("Alpha", author))

        reported shouldContainExactlyInAnyOrder listOf(eventFailure, stateFailure)
    }

    @Test
    fun `keep the subscription when a consumer of no longer matching entities fails`() {
        val tasks = mutableListOf<CTask>()
        val noLongerMatching = mutableListOf<CTaskId>()
        val createTask = createTask("Alpha", author)
        val id = createTask.id

        client().asGuest().subscribe {
            state<CTask> {
                it.byId(id)
                    .observe { task -> tasks += task }
                    .whenNoLongerMatching(CTaskId::class.java) {
                        error("The consumer of no longer matching entities failed.")
                    }
            }
            state<CTask> {
                it.byId(id)
                    .observe { }
                    .whenNoLongerMatching(CTaskId::class.java) { gone -> noLongerMatching += gone }
            }
        }
        post(createTask)
        post(archiveCTask { this.id = id })
        post(unarchiveCTask { this.id = id })

        noLongerMatching shouldContainExactly listOf(id)
        tasks.map { it.id } shouldContainExactly listOf(id, id)
    }

    @Nested inner class
    Refuse {

        @Test
        fun `a type of an unpublished language`() {
            shouldThrow<UnpublishedLanguageException> {
                client().asGuest().subscribe().state(Locomotive::class.java) { }
            }
            shouldThrow<UnpublishedLanguageException> {
                client().asGuest().subscribe().event(WheelsKnocked::class.java) { }
            }
        }

        @Test
        fun `a request without targets`() {
            shouldThrow<IllegalStateException> {
                client().asGuest().subscribe().post()
            }
        }

        @Test
        fun `a target without consumers`() {
            shouldThrow<IllegalStateException> {
                client().asGuest().subscribe {
                    event<CTaskCreated> { it.observe { } }
                    state<CTask> { it.byId(CTaskId.generate()) }
                }
            }
            client().subscriptions().isEmpty shouldBe true
        }

        @Test
        fun `a topic that the server refuses, throwing the error of the server`() {
            val error = shouldThrow<StatusRuntimeException> {
                client().asGuest().subscribe {
                    event<CTaskCreated> { it.observe { } }
                    state<Organization> { it.observe { } }
                }
            }
            errorCaseOf(error) shouldBe Optional.of(UNSUPPORTED_REQUEST)
            client().subscriptions().isEmpty shouldBe true
        }
    }

    private fun post(command: CommandMessage) =
        client().asGuest().command(command).postAndForget()

    private fun createTask(name: String, author: UserId): CreateCTask =
        createCTask {
            id = CTaskId.generate()
            this.name = name
            this.author = author
        }
}
