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

import io.grpc.stub.StreamObserver
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.string.shouldContain
import io.spine.base.Identifier
import io.spine.core.Event
import io.spine.core.eventId
import io.spine.protobuf.AnyPacker
import io.spine.test.client.TestEntity
import io.spine.test.client.TestEntityId
import io.spine.test.client.testEntityId
import io.spine.testing.logging.LoggingTest
import io.spine.testing.logging.mute.MuteLogging
import io.spine.validation.NonValidated
import java.util.logging.Level
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@MuteLogging
@DisplayName("`RoutingObserver` should")
internal class RoutingObserverSpec : LoggingTest(RoutingObserver::class.java, Level.ALL) {

    private val first = RecordingObserver<Event>()
    private val second = RecordingObserver<Event>()
    private val observer = RoutingObserver(
        targets = listOf(
            EventTargetObserver(target(), first),
            EventTargetObserver(target(), second)
        ),
        streamingErrors = null
    )

    @BeforeEach
    fun startIntercepting() = interceptLogging()

    @AfterEach
    fun stopIntercepting() = restoreLogging()

    @Test
    fun `pass an update to the consumers of the target that the update names`() {
        val event = event("second")

        observer.onNext(eventUpdate(targetIndex = 1, event))

        first.messages.shouldBeEmpty()
        second.messages shouldContainExactly listOf(event)
    }

    @Test
    fun `refuse an update that names a target the topic does not have`() {
        val error = shouldThrow<IllegalStateException> {
            observer.onNext(eventUpdate(targetIndex = 2, event("missing")))
        }
        error.message shouldContain "index 2"
    }

    @Test
    fun `refuse an update of another kind than its target`() {
        val update = SubscriptionUpdate.newBuilder()
            .setEntityUpdates(EntityUpdates.getDefaultInstance())
            .buildPartial()

        shouldThrow<IllegalStateException> {
            observer.onNext(update)
        }
    }

    @Test
    fun `pass a streaming error to the handler once`() {
        val errors = mutableListOf<Throwable>()
        val withHandler = RoutingObserver(
            targets = listOf(
                EventTargetObserver(target(), first),
                EventTargetObserver(target(), second)
            ),
            streamingErrors = ErrorHandler { errors += it }
        )
        val failure = IllegalStateException("The stream failed.")

        withHandler.onError(failure)

        errors shouldContainExactly listOf(failure)
    }

    @Test
    fun `go on delivering after a consumer of no longer matching entities fails`() {
        val reported = mutableListOf<TestEntityId>()
        val states = StateTargetObserver(
            target = target(),
            type = TestEntity::class.java,
            observer = RecordingObserver(),
            noLongerMatching = listOf(
                NoLongerMatchingConsumer(TestEntityId::class.java) {
                    throw AssertionError("The consumer failed.")
                },
                NoLongerMatchingConsumer(TestEntityId::class.java) { reported += it }
            )
        )
        val firstId = testEntityId { value = 1 }
        val secondId = testEntityId { value = 2 }

        RoutingObserver(listOf(states), streamingErrors = null)
            .onNext(noLongerMatching(firstId, secondId))

        reported shouldContainExactly listOf(firstId, secondId)
    }

    @Test
    fun `log a streaming error if there is no handler`() {
        observer.onError(IllegalStateException("The stream failed."))

        val record = assertLog().record()
        record.isError()
        record.hasMessageThat().contains("Error receiving the updates of the subscription")
    }

    private fun target(): @NonValidated Target =
        Target.newBuilder()
            .setType("type.spine.io/spine.test.client.Event")
            .setIncludeAll(true)
            .buildPartial()

    private fun event(id: String): @NonValidated Event =
        Event.newBuilder()
            .setId(eventId { value = id })
            .buildPartial()

    private fun eventUpdate(targetIndex: Int, event: Event): @NonValidated SubscriptionUpdate =
        SubscriptionUpdate.newBuilder()
            .setTargetIndex(targetIndex)
            .setEventUpdates(eventUpdates(event))
            .buildPartial()

    private fun noLongerMatching(vararg ids: TestEntityId): @NonValidated SubscriptionUpdate =
        SubscriptionUpdate.newBuilder()
            .setEntityUpdates(entityUpdates(ids.map(::noLongerMatchingUpdate)))
            .buildPartial()

    private fun noLongerMatchingUpdate(entity: TestEntityId): EntityStateUpdate {
        val packedId = AnyPacker.pack(entityId { id = Identifier.pack(entity) })
        return entityStateUpdate {
            id = packedId
            noLongerMatching = true
        }
    }

    private fun entityUpdates(updates: List<EntityStateUpdate>): @NonValidated EntityUpdates =
        EntityUpdates.newBuilder()
            .addAllUpdate(updates)
            .buildPartial()

    private fun eventUpdates(event: Event): @NonValidated EventUpdates =
        EventUpdates.newBuilder()
            .addEvent(event)
            .buildPartial()
}

/**
 * Remembers the messages it receives.
 */
private class RecordingObserver<T> : StreamObserver<T> {

    val messages = mutableListOf<T>()

    override fun onNext(value: T) {
        messages += value
    }

    override fun onError(t: Throwable) = Unit

    override fun onCompleted() = Unit
}
