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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.spine.base.CommandMessage
import io.spine.client.EntityStateFilter.eq
import io.spine.server.BoundedContextBuilder
import io.spine.test.client.ClientTestContext.tasks
import io.spine.test.client.ClientTestContext.users
import io.spine.test.client.board.Card
import io.spine.test.client.board.CardId
import io.spine.test.client.board.builder as board
import io.spine.test.client.board.command.assignCard
import io.spine.test.client.board.command.createCard
import io.spine.test.client.board.event.CardAssigned
import io.spine.test.client.tasks.CTask
import io.spine.test.client.tasks.CTaskId
import io.spine.test.client.tasks.command.CreateCTask
import io.spine.test.client.tasks.command.createCTask
import io.spine.test.client.tasks.event.CTaskCreated
import io.spine.test.unpublished.Locomotive
import io.spine.testing.core.given.GivenUserId
import io.spine.testing.logging.mute.MuteLogging
import io.spine.type.UnpublishedLanguageException
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@MuteLogging
@DisplayName("`CommandRequest` should")
internal class CommandRequestSpec : AbstractClientTest() {

    override fun contexts(): List<BoundedContextBuilder> = listOf(tasks(), users(), board())

    @Test
    fun `observe the states of the entities that the command changes`() {
        val tasks = mutableListOf<CTask>()
        val command = createTask()

        client().asGuest()
            .command(command)
            .observe(CTask::class.java) { it.byId(command.id).observe { task -> tasks += task } }
            .post()

        tasks.map { it.id } shouldContainExactly listOf(command.id)
    }

    @Test
    fun `observe the states of the entities that match the filters of a target`() {
        val assignedToAlice = mutableListOf<Card>()
        val assigned = mutableListOf<CardAssigned>()
        val card = createCard {
            id = CardId.generate()
            title = "A card to assign"
        }
        post(card)

        client().asGuest().command(assignCard { id = card.id; assignee = ALICE }) {
            observe<CardAssigned> { e -> assigned += e }
            observe<Card> {
                it.where(eq(Card.Field.assignee(), ALICE))
                    .observe { card -> assignedToAlice += card }
            }
        }

        assigned.map { it.assignee } shouldContainExactly listOf(ALICE)
        assignedToAlice.map { it.id } shouldContainExactly listOf(card.id)
    }

    @Test
    fun `make one subscription for the whole outcome`() {
        val created = mutableListOf<CTaskCreated>()
        val tasks = mutableListOf<CTask>()
        val command = createTask()

        val subscription = client().asGuest().command(command) {
            observe<CTaskCreated> { e -> created += e }
            observe<CTask> { it.byId(command.id).observe { task -> tasks += task } }
        }

        created.map { it.id } shouldContainExactly listOf(command.id)
        tasks.map { it.id } shouldContainExactly listOf(command.id)
        subscription.shouldNotBeNull()
        subscription.topic.targetCount shouldBe 2
        val subscriptions = client().subscriptions()
        subscriptions.cancel(subscription) shouldBe true
        subscriptions.isEmpty shouldBe true
    }

    @Test
    fun `deliver an event to each of its consumers once, through one target`() {
        val first = mutableListOf<CTaskCreated>()
        val second = mutableListOf<CTaskCreated>()
        val command = createTask()

        val subscription = client().asGuest().command(command) {
            observe<CTaskCreated> { e -> first += e }
            observe<CTaskCreated> { e, _ -> second += e }
        }

        first.map { it.id } shouldContainExactly listOf(command.id)
        second.map { it.id } shouldContainExactly listOf(command.id)
        subscription.shouldNotBeNull()
        subscription.topic.targetCount shouldBe 1
    }

    @Test
    fun `deliver only the events that originate from the command`() {
        val created = mutableListOf<CTaskCreated>()
        val command = createTask()

        client().asGuest().command(command) {
            observe<CTaskCreated> { e -> created += e }
        }
        post(createTask())

        created.map { it.id } shouldContainExactly listOf(command.id)
    }

    @Test
    fun `pass the errors of the consumers of entity states to the consuming error handler`() {
        val failure = IllegalStateException("The consumer of states failed.")
        var reported: Throwable? = null
        val command = createTask()

        client().asGuest().command(command) {
            observe<CTask> { it.byId(command.id).observe { throw failure } }
            onConsumingError { _, error -> reported = error }
        }

        reported shouldBe failure
    }

    @Nested inner class
    Refuse {

        @Test
        fun `entity states of an unpublished language`() {
            shouldThrow<UnpublishedLanguageException> {
                client().asGuest()
                    .command(createTask())
                    .observe(Locomotive::class.java) { it.observe { } }
            }
        }

        @Test
        fun `to post and forget a command whose outcome is observed`() {
            shouldThrow<IllegalStateException> {
                client().asGuest()
                    .command(createTask())
                    .observe(CTask::class.java) { it.observe { } }
                    .postAndForget()
            }
        }

        @Test
        fun `to post a command with a target of entity states without consumers`() {
            shouldThrow<IllegalStateException> {
                client().asGuest().command(createTask()) {
                    observe<CTaskCreated> { }
                    observe<CTask> { it.byId(CTaskId.generate()) }
                }
            }
            client().subscriptions().isEmpty shouldBe true
        }
    }

    private fun post(command: CommandMessage) =
        client().asGuest().command(command).postAndForget()

    private fun createTask(): CreateCTask =
        createCTask {
            id = CTaskId.generate()
            name = "A task created at ${System.nanoTime()}"
            author = GivenUserId.generated()
        }

    private companion object {

        const val ALICE = "Alice"
    }
}
