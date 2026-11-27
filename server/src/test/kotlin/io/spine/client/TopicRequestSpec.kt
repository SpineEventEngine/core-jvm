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
import io.spine.client.CompositeEntityStateFilter.either
import io.spine.client.EntityStateFilter.eq
import io.spine.client.EventFilter.eq
import io.spine.core.ErrorCase.UNSUPPORTED_REQUEST
import io.spine.core.UserId
import io.spine.grpc.StreamObservers.errorCaseOf
import io.spine.server.BoundedContextBuilder
import io.spine.server.given.organizations.Organization
import io.spine.test.client.ClientTestContext.tasks
import io.spine.test.client.board.Card
import io.spine.test.client.board.CardId
import io.spine.test.client.board.CardStatus.DONE
import io.spine.test.client.board.CardStatus.OPEN
import io.spine.test.client.board.builder as board
import io.spine.test.client.board.command.AssignCard
import io.spine.test.client.board.command.CompleteCard
import io.spine.test.client.board.command.CreateCard
import io.spine.test.client.board.command.ReopenCard
import io.spine.test.client.board.command.assignCard
import io.spine.test.client.board.command.completeCard
import io.spine.test.client.board.command.createCard
import io.spine.test.client.board.command.reopenCard
import io.spine.test.client.board.event.CardAssigned
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

    override fun contexts(): List<BoundedContextBuilder> = listOf(tasks(), users(), board())

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
        subscription.typesOfTargets() shouldContainExactly
                typeUrls(CTaskCreated::class.java, CTask::class.java, UserLoggedIn::class.java)
        client().subscriptions().contains(subscription) shouldBe true
    }

    @Test
    fun `keep the order of the calls when the block of a target adds a target`() {
        val subscription = client().asGuest().subscribe {
            event<CTaskCreated> {
                it.observe { }
                state<CTask> { task -> task.observe { } }
            }
            event<UserLoggedIn> { it.observe { } }
        }

        subscription.typesOfTargets() shouldContainExactly
                typeUrls(CTaskCreated::class.java, CTask::class.java, UserLoggedIn::class.java)
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
        val done = mutableListOf<Card>()
        val assignedToAlice = mutableListOf<Card>()

        client().asGuest().subscribe {
            state<Card> {
                it.where(eq(Card.Field.status(), DONE))
                    .observe { card -> done += card }
            }
            state<Card> {
                it.where(eq(Card.Field.assignee(), ALICE))
                    .observe { card -> assignedToAlice += card }
            }
        }
        val both = newCard()
        val onlyAssigned = newCard()
        val onlyDone = newCard()
        listOf(both, onlyAssigned, onlyDone).forEach(::post)
        post(assign(both.id, ALICE))
        post(assign(onlyAssigned.id, ALICE))
        post(complete(both.id))
        post(complete(onlyDone.id))

        done.map { it.id } shouldContainExactly listOf(both.id, onlyDone.id)
        assignedToAlice.map { it.id to it.status } shouldContainExactly listOf(
            both.id to OPEN,
            onlyAssigned.id to OPEN,
            both.id to DONE
        )
    }

    @Test
    fun `report an entity that stops matching one target while it still matches another`() {
        val noLongerDone = mutableListOf<CardId>()
        val noLongerAssignedToAlice = mutableListOf<CardId>()
        val assignedToAlice = mutableListOf<Card>()

        client().asGuest().subscribe {
            state<Card> {
                it.where(eq(Card.Field.status(), DONE))
                    .observe { }
                    .whenNoLongerMatching(CardId::class.java) { id -> noLongerDone += id }
            }
            state<Card> {
                it.where(eq(Card.Field.assignee(), ALICE))
                    .observe { card -> assignedToAlice += card }
                    .whenNoLongerMatching(CardId::class.java) { id ->
                        noLongerAssignedToAlice += id
                    }
            }
        }
        val card = newCard()
        post(card)
        post(assign(card.id, ALICE))
        post(complete(card.id))

        post(reopen(card.id))
        noLongerDone shouldContainExactly listOf(card.id)
        noLongerAssignedToAlice.shouldBeEmpty()
        assignedToAlice.last().status shouldBe OPEN

        post(assign(card.id, BOB))
        noLongerDone shouldContainExactly listOf(card.id)
        noLongerAssignedToAlice shouldContainExactly listOf(card.id)
    }

    @Test
    fun `match the states by a composite filter`() {
        val matched = mutableListOf<Card>()

        client().asGuest().subscribe {
            state<Card> {
                it.where(either(eq(Card.Field.status(), DONE), eq(Card.Field.assignee(), ALICE)))
                    .observe { card -> matched += card }
            }
        }
        val done = newCard()
        val assigned = newCard()
        val other = newCard()
        listOf(done, assigned, other).forEach(::post)
        post(complete(done.id))
        post(assign(assigned.id, ALICE))
        post(assign(other.id, BOB))

        matched.map { it.id } shouldContainExactly listOf(done.id, assigned.id)
    }

    @Test
    fun `deliver the events that match each target of one type to its own consumers`() {
        val toAlice = mutableListOf<CardAssigned>()
        val toBob = mutableListOf<CardAssigned>()

        client().asGuest().subscribe {
            event<CardAssigned> {
                it.where(eq(CardAssigned.Field.assignee(), ALICE))
                    .observe { e -> toAlice += e }
            }
            event<CardAssigned> {
                it.where(eq(CardAssigned.Field.assignee(), BOB))
                    .observe { e -> toBob += e }
            }
        }
        val card = newCard()
        post(card)
        post(assign(card.id, ALICE))
        post(assign(card.id, BOB))

        toAlice.map { it.assignee } shouldContainExactly listOf(ALICE)
        toBob.map { it.assignee } shouldContainExactly listOf(BOB)
    }

    @Test
    fun `deliver the states of the entities that each target names by ID to its consumers`() {
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
    fun `report an archived entity to the consumers of each target that it matched`() {
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

    private fun newCard(): CreateCard =
        createCard {
            id = CardId.generate()
            title = "A card created at ${System.nanoTime()}"
        }

    private fun assign(card: CardId, person: String): AssignCard =
        assignCard {
            id = card
            assignee = person
        }

    private fun complete(card: CardId): CompleteCard = completeCard { id = card }

    private fun reopen(card: CardId): ReopenCard = reopenCard { id = card }

    private companion object {

        const val ALICE = "Alice"
        const val BOB = "Bob"
    }
}
