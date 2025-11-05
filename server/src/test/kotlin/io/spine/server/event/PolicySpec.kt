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

package io.spine.server.event

import io.kotest.matchers.shouldBe
import io.spine.server.command.Command
import io.spine.server.command.DoNothing
import io.spine.server.tuple.EitherOf2
import io.spine.test.shared.event.SomethingHappened
import io.spine.testing.logging.mute.MuteLogging
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("`Policy` should")
internal class PolicySpec {

    @Test
    fun `do not allow adding more command methods`() {
        assertThrows<IllegalStateException> {
            GreedyPolicy()
        }
    }

    @Test
    @MuteLogging(WHY_MUTE)
    fun `allow returning a single command as Iterable`() {
        val doNothing = DoNothing.getDefaultInstance()
        val policy = object : Policy<SomethingHappened>() {
            @Command
            public override fun whenever(event: SomethingHappened): List<DoNothing> =
                listOf(doNothing)
        }
        policy.whenever(somethingHappened) shouldBe listOf(doNothing)
    }

    @Test
    @MuteLogging(WHY_MUTE)
    fun `allow using 'Either' in return value`() {
        val doNothing = DoNothing.getDefaultInstance()
        object : Policy<SomethingHappened>() {
            @Command
            public override fun whenever(
                event: SomethingHappened
            ): EitherOf2<DoNothing, DoNothing> =
                EitherOf2.withA(doNothing)
        }.let {
            it.whenever(somethingHappened) shouldBe EitherOf2.withA(doNothing)
        }
    }

    companion object {
        const val WHY_MUTE = """
            The method `whenever()` `public`.
            A `public` reacting method causes a warning because of unnecessary exposure.
        """
        val somethingHappened: SomethingHappened = SomethingHappened.getDefaultInstance()
    }
}

/**
 * A policy which attempts to define two `@Command` receptors to handle more than one
 * event type, which is not allowed by the `Policy` contract.
 */
private class GreedyPolicy : Policy<SomethingHappened>() {

    @Command
    override fun whenever(event: SomethingHappened): List<DoNothing> =
        listOf(DoNothing.getDefaultInstance())

    /**
     * In reality `NoReaction` is never dispatched, but that's OK for this stub receptor.
     */
    @Command
    fun on(@Suppress("UNUSED_PARAMETER") e: NoReaction): List<DoNothing> =
        listOf(DoNothing.getDefaultInstance())
}
