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

import io.grpc.ForwardingServerBuilder
import io.grpc.ServerBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.spine.testing.logging.mute.MuteLogging
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.MINUTES
import java.util.concurrent.TimeUnit.SECONDS
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`KeepAlive` should")
internal class KeepAliveSpec {

    @Test
    fun `ping after a minute without reads, and wait 20 seconds for the answer`() {
        val builder = RecordingServerBuilder()

        KeepAlive.applyTo(builder) shouldBeSameInstanceAs builder

        builder.keepAliveTimeNanos shouldBe MINUTES.toNanos(1)
        builder.keepAliveTimeoutNanos shouldBe SECONDS.toNanos(20)
    }

    @Test
    @MuteLogging
    fun `leave a builder whose transport does not support keepalive as it is`() {
        val builder = InProcessServerBuilder.forName(InProcessServerBuilder.generateName())

        KeepAlive.applyTo(builder) shouldBeSameInstanceAs builder

        builder.build().start().shutdownNow()
    }
}

/**
 * Records the keepalive that it is given.
 */
private class RecordingServerBuilder : ForwardingServerBuilder<RecordingServerBuilder>() {

    private val target = InProcessServerBuilder.forName(InProcessServerBuilder.generateName())

    var keepAliveTimeNanos: Long? = null
        private set

    var keepAliveTimeoutNanos: Long? = null
        private set

    override fun delegate(): ServerBuilder<*> = target

    override fun keepAliveTime(keepAliveTime: Long, timeUnit: TimeUnit): RecordingServerBuilder {
        keepAliveTimeNanos = timeUnit.toNanos(keepAliveTime)
        return this
    }

    override fun keepAliveTimeout(
        keepAliveTimeout: Long,
        timeUnit: TimeUnit
    ): RecordingServerBuilder {
        keepAliveTimeoutNanos = timeUnit.toNanos(keepAliveTimeout)
        return this
    }
}
