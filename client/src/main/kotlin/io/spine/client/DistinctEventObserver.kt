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
import io.spine.core.Event
import io.spine.core.EventId
import java.util.concurrent.ConcurrentHashMap

/**
 * Passes each event to the delegate at most once, telling the events apart by their IDs.
 *
 * A command rejected by a filter of the Command Bus brings its rejection to the client twice:
 * in the acknowledgement, and through a subscription to the rejection, if the subscription
 * is active by then. The client passes the rejection from both to this observer, so that
 * the consumers of the rejection receive it once, whichever comes first.
 *
 * @param delegate The observer to pass the events to.
 */
internal class DistinctEventObserver(
    private val delegate: StreamObserver<Event>
) : StreamObserver<Event> {

    private val delivered: MutableSet<EventId> = ConcurrentHashMap.newKeySet()

    override fun onNext(event: Event) {
        if (delivered.add(event.id)) {
            delegate.onNext(event)
        }
    }

    override fun onError(t: Throwable) = delegate.onError(t)

    override fun onCompleted() = delegate.onCompleted()
}
