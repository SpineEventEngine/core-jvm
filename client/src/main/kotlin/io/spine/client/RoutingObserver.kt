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
import io.spine.logging.WithLogging

/**
 * Passes each update of a subscription to the consumers of the target that the update names.
 *
 * An update names its target by the position of the target in the subscription topic,
 * so the [targets] come in the order of the targets of the topic.
 *
 * A streaming error goes to the [streamingErrors] handler once for the whole subscription,
 * whatever the number of its targets. Without the handler, the observer logs the error.
 */
internal class RoutingObserver(
    private val targets: List<TargetDelivery>,
    private val streamingErrors: ErrorHandler?
) : StreamObserver<SubscriptionUpdate>, WithLogging {

    init {
        require(targets.isNotEmpty()) { "A subscription must have at least one target." }
    }

    /**
     * Passes the update to the delivery of its target.
     *
     * @throws IllegalStateException If the update names a target that the topic does not have.
     */
    override fun onNext(update: SubscriptionUpdate) {
        val index = update.targetIndex
        val delivery = targets.getOrNull(index) ?: error(
            "The update names the target at the index $index," +
                    " but the topic has ${targets.size} target(s)."
        )
        delivery.deliver(update)
    }

    /**
     * Passes the error to the handler of streaming errors, or logs it if there is no handler.
     */
    override fun onError(t: Throwable) {
        if (streamingErrors != null) {
            streamingErrors.accept(t)
            return
        }
        logger.atError().withCause(t).log {
            val types = targets.joinToString { "`${it.target.type}`" }
            "Error receiving the updates of the subscription to $types."
        }
    }

    /**
     * Does nothing.
     */
    override fun onCompleted() = Unit
}
