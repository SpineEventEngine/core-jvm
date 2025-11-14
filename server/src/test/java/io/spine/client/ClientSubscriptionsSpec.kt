/*
 * Copyright 2025, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Redistribution and use in source and/or binary forms, with or without
 * modification, must retain the above copyright notice and the following
 * disclaimer.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.spine.client

import com.google.common.truth.extensions.proto.ProtoTruth.assertThat
import com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.spine.base.CommandMessage
import io.spine.base.EventMessage
import io.spine.client.given.ClientTasksTestEnv.archiveCTask
import io.spine.client.given.ClientTasksTestEnv.createCTask
import io.spine.client.given.ClientTasksTestEnv.deleteCTask
import io.spine.client.given.ClientTasksTestEnv.restoreCTask
import io.spine.client.given.ClientTasksTestEnv.stateAfter
import io.spine.client.given.ClientTasksTestEnv.unarchiveCTask
import io.spine.server.BoundedContextBuilder
import io.spine.test.client.ClientTestContext.tasks
import io.spine.test.client.ClientTestContext.users
import io.spine.test.client.tasks.CTask
import io.spine.test.client.tasks.CTaskId
import io.spine.test.client.users.command.changePassword
import io.spine.test.client.users.event.UserAuthenticationRequired
import io.spine.testing.core.given.GivenUserId
import io.spine.testing.logging.mute.MuteLogging
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@MuteLogging
@DisplayName("Subscription API for `Client` should allow subscribing to ")
internal class ClientSubscriptionsSpec : AbstractClientTest() {

    override fun contexts(): List<BoundedContextBuilder> {
        return listOf(tasks(), users())
    }

    @Nested
    @DisplayName("changes")
    internal inner class Changes {

        @Test
        fun `in state of an 'Aggregate'`() {
            val updateReceived = AtomicBoolean(false)
            val client = client()

            val createTask = createCTask("My task")
            val expectedState = stateAfter(createTask)
            client.asGuest()
                .subscribeTo(CTask::class.java)
                .observe { update: CTask ->
                    updateReceived.set(true)
                    assertThat(update)
                        .comparingExpectedFieldsOnly()
                        .isEqualTo(expectedState)
                }
                .post()
            client.postAndForget(createTask)
            sleepUninterruptibly(Duration.ofSeconds(1))

            updateReceived.get() shouldBe true
        }
    }

    @Nested
    @DisplayName("deletion and restoration")
    internal inner class Deletion {

        @Test
        fun `of an 'Aggregate'`() {
            val updates = ArrayList<CTask>()
            val noLongerMatchingIds = ArrayList<CTaskId>()

            val client = client()
            val createTask = createCTask("Soon to be deleted and restored")
            val id = createTask.id
            val deleteTask = deleteCTask(id)
            val restoreTask = restoreCTask(id)
            client.asGuest()
                .subscribeTo(CTask::class.java)
                .observe { t: CTask -> updates.add(t) }
                .whenNoLongerMatching(CTaskId::class.java) { id: CTaskId ->
                    noLongerMatchingIds.add(id)
                }
                .post()

            noLongerMatchingIds.shouldBeEmpty()

            client.assertNumOfUpdates(createTask, 1, updates)

            // Marking as deleted does not count as an update. So, we still have one.
            client.assertNumOfUpdates(deleteTask, 1, updates)

            // `CTask` became deleted. It should stop matching the subscription criteria.
            noLongerMatchingIds.shouldContainExactly(id)

            // Restoring "shows" the task back. So, it is an update.
            client.assertNumOfUpdates(restoreTask, 2, updates)

            // This is the task which was once marked as deleted.
            noLongerMatchingIds.shouldContainExactly(id)
        }
    }

    @Nested
    @DisplayName("archiving and un-archiving")
    internal inner class Archiving {

        @Test
        fun `of an 'Aggregate'`() {
            val updates = ArrayList<CTask>()
            val noLongerMatchingIds = ArrayList<CTaskId>()

            val client = client()
            val createTask = createCTask("Soon to be archived and un-archived")
            val id = createTask.id
            val archiveTask = archiveCTask(id)
            val unarchiveCTask = unarchiveCTask(id)
            client.asGuest()
                .subscribeTo(CTask::class.java)
                .observe { t: CTask -> updates.add(t) }
                .whenNoLongerMatching(
                    CTaskId::class.java
                ) { id: CTaskId -> noLongerMatchingIds.add(id) }
                .post()

            noLongerMatchingIds.shouldBeEmpty()
            client.assertNumOfUpdates(createTask, 1, updates)

            // Archiving does not count as an update. We still have one.
            client.assertNumOfUpdates(archiveTask, 1, updates)

            // `CTask` became archived. It should stop matching the subscription criteria.
            noLongerMatchingIds.shouldContainExactly(id)

            client.assertNumOfUpdates(unarchiveCTask, 2, updates)
            noLongerMatchingIds.shouldContainExactly(id)
        }
    }

    @Test
    fun `subscribe to events produced by 'EventReactor'`() {
        val events = ArrayList<EventMessage>()
        val client = client()
        client.asGuest()
            .subscribeToEvent(UserAuthenticationRequired::class.java)
            .observe {
                e: EventMessage -> events.add(e)
            }
            .post()
        val userId = GivenUserId.generated()
        val command = changePassword {
            user = userId
            previousPassword = "test"
            newPassword = "new-test"
        }
        client.asGuest()
            .command(command)
            .postAndForget()

        events.size shouldBe 1
    }
}

private fun Client.postAndForget(command: CommandMessage) =
    asGuest().command(command).postAndForget()

private fun Client.assertNumOfUpdates(
    cmd: CommandMessage,
    expectedCount: Int,
    updates: List<CTask>
) {
    postAndForget(cmd)
    sleepUninterruptibly(Duration.ofSeconds(1))
    updates.size shouldBe expectedCount
}
