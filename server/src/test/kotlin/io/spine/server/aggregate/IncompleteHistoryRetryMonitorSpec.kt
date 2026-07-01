/*
 * Copyright 2026, TeamDev. All rights reserved.
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

package io.spine.server.aggregate

import io.kotest.matchers.shouldBe
import io.spine.base.Error
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.function.LongSupplier
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("`IncompleteHistoryRetryMonitor` should")
internal class IncompleteHistoryRetryMonitorSpec {

    private val scheduler: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor()

    @AfterEach
    fun tearDown() {
        scheduler.shutdownNow()
    }

    @Nested
    inner class `recognize as retryable` {

        @Test
        fun `an incomplete-history error`() {
            monitor().canRetry(errorOfType(incompleteHistoryType)) shouldBe true
        }

        @Test
        fun `and reject any other error type`() {
            // Even the superclass of `IncompleteHistoryException` must not match: the reported
            // error type is the exact runtime class name of the thrown exception.
            val superclassType = "java.lang.IllegalStateException"
            val unrelatedType = "io.spine.server.aggregate.SomeOtherFailure"
            monitor().canRetry(errorOfType(superclassType)) shouldBe false
            monitor().canRetry(errorOfType(unrelatedType)) shouldBe false
        }
    }

    @Nested
    inner class `apply an exponential back-off that` {

        @Test
        fun `grows by the multiplier`() {
            val monitor = monitor(initialMillis = 100, maxMillis = 10_000, multiplier = 2.0)
            monitor.backoffFor(1) shouldBe 100L
            monitor.backoffFor(2) shouldBe 200L
            monitor.backoffFor(3) shouldBe 400L
            monitor.backoffFor(4) shouldBe 800L
        }

        @Test
        fun `is capped at the maximum delay`() {
            val monitor = monitor(initialMillis = 100, maxMillis = 250, multiplier = 2.0)
            monitor.backoffFor(1) shouldBe 100L
            monitor.backoffFor(2) shouldBe 200L
            monitor.backoffFor(3) shouldBe 250L
            monitor.backoffFor(9) shouldBe 250L
        }
    }

    private fun monitor(
        initialMillis: Long = 100,
        maxMillis: Long = 2_000,
        multiplier: Double = 2.0,
    ): TestMonitor {
        val builder = IncompleteHistoryRetryMonitor.newBuilder()
            .initialDelay(Duration.ofMillis(initialMillis))
            .maxDelay(Duration.ofMillis(maxMillis))
            .multiplier(multiplier)
        return TestMonitor(builder, scheduler)
    }

    /**
     * Exposes the `protected`/package-private members of the monitor for testing.
     */
    private class TestMonitor(
        builder: IncompleteHistoryRetryMonitor.Builder,
        scheduler: ScheduledExecutorService,
    ) : IncompleteHistoryRetryMonitor(builder, scheduler, LongSupplier { 0L }) {

        fun canRetry(error: Error): Boolean = isRetryable(error)

        fun backoffFor(failure: Int): Long = backoffMillis(failure)
    }

    private companion object {

        val incompleteHistoryType: String =
            IncompleteHistoryException::class.java.canonicalName

        fun errorOfType(type: String): Error =
            Error.newBuilder()
                .setType(type)
                .buildPartial()
    }
}
