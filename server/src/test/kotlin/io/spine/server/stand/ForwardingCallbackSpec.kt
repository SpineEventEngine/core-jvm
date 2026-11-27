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

package io.spine.server.stand

import io.grpc.stub.StreamObserver
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.spine.client.SubscriptionUpdate
import io.spine.validation.NonValidated
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`ForwardingCallback` should")
internal class ForwardingCallbackSpec {

    private val observer = CountingObserver()
    private val callback = SubscriptionCallback.forwardingTo(observer)

    @Test
    fun `pass the updates to the observer`() {
        val first = update(0)
        val second = update(1)

        callback.accept(first)
        callback.accept(second)

        observer.updates shouldContainExactly listOf(first, second)
        observer.completions shouldBe 0
    }

    @Test
    fun `complete the observer once, however many contexts tell it about the cancellation`() {
        callback.onCancelled()
        callback.onCancelled()

        observer.completions shouldBe 1
    }

    @Test
    fun `pass no updates after the cancellation`() {
        callback.onCancelled()

        callback.accept(update(0))

        observer.updates.shouldBeEmpty()
    }
}

/**
 * Remembers the updates and counts the completions.
 */
private class CountingObserver : StreamObserver<SubscriptionUpdate> {

    private val received = mutableListOf<SubscriptionUpdate>()

    val updates: List<SubscriptionUpdate> = received

    var completions = 0
        private set

    override fun onNext(value: SubscriptionUpdate) {
        received.add(value)
    }

    override fun onError(t: Throwable) = Unit

    override fun onCompleted() {
        completions++
    }
}

/**
 * Creates an update of the target with the given index, with no other fields set.
 */
private fun update(targetIndex: Int): @NonValidated SubscriptionUpdate =
    SubscriptionUpdate.newBuilder()
        .setTargetIndex(targetIndex)
        .buildPartial()
