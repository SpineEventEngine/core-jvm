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

import io.grpc.stub.StreamObserver

/**
 * A thread-safe [StreamObserver] that passes nothing to the given observer after
 * the stream terminates.
 *
 * The calls are serialized. Once [onError] or [onCompleted] is called, the later calls are
 * ignored. So when the updates of a subscription come from several Bounded Contexts, and
 * one of them fails to serve the request, an update that another context delivers at the same
 * time comes before the error, or not at all.
 *
 * @param V The type of the values in the stream.
 * @param delegate The observer to pass the values and the termination to.
 */
internal class GuardedObserver<V>(private val delegate: StreamObserver<V>) : StreamObserver<V> {

    /**
     * Tells if [onError] or [onCompleted] was called.
     */
    private var terminated = false

    @Synchronized
    override fun onNext(value: V) {
        if (!terminated) {
            delegate.onNext(value)
        }
    }

    @Synchronized
    override fun onError(t: Throwable) {
        if (terminate()) {
            delegate.onError(t)
        }
    }

    @Synchronized
    override fun onCompleted() {
        if (terminate()) {
            delegate.onCompleted()
        }
    }

    /**
     * Marks the stream terminated.
     *
     * @return `true` if the stream was not terminated before.
     */
    private fun terminate(): Boolean {
        val first = !terminated
        terminated = true
        return first
    }
}
