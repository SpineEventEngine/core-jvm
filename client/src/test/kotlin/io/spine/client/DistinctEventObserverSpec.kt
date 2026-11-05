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

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.spine.base.Identifier.newUuid
import io.spine.core.Event
import io.spine.core.EventId
import io.spine.grpc.StreamObservers.memoizingObserver
import io.spine.validation.NonValidated
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`DistinctEventObserver` should")
internal class DistinctEventObserverSpec {

    @Test
    fun `pass each event once`() {
        val delegate = memoizingObserver<Event>()
        val observer = DistinctEventObserver(delegate)
        val first = event()
        val second = event()

        observer.onNext(first)
        observer.onNext(second)
        observer.onNext(first)

        delegate.responses() shouldContainExactly listOf(first, second)
    }

    @Test
    fun `pass errors`() {
        val delegate = memoizingObserver<Event>()
        val observer = DistinctEventObserver(delegate)
        val error = IllegalStateException("Stream failed.")

        observer.onError(error)

        delegate.error shouldBe error
    }

    @Test
    fun `pass completion`() {
        val delegate = memoizingObserver<Event>()

        DistinctEventObserver(delegate).onCompleted()

        delegate.isCompleted shouldBe true
    }

    private fun event(): @NonValidated Event = Event.newBuilder()
        .setId(EventId.newBuilder().setValue(newUuid()))
        .buildPartial()
}
