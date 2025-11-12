/*
 * Copyright 2023, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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

package io.spine.server.model

import com.google.common.truth.Truth.assertThat
import io.kotest.matchers.shouldBe
import io.spine.core.Subscribe
import io.spine.server.event.AbstractEventSubscriber
import io.spine.server.event.model.SubscriberSignature
import io.spine.server.given.model.signature.CoinTossed
import io.spine.server.model.MatchCriterion.ACCESS_MODIFIER
import java.lang.reflect.Method
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Method signature in Kotlin should")
class KotlinMethodSignatureSpec {

    @Test
    fun `be accepted with the 'internal' access modifier`() {
        val method = subscriberIn(TfEventSubscriber::class.java)
        assertMatches(method)
    }

    @Test
    fun `be accepted if a declaring class is 'internal'`() {
        val method = subscriberIn(TfInternalSubscriber::class.java)
        assertMatches(method)
    }

    @Test
    fun `be accepted if a declaring class is 'private'`() {
        val method = subscriberIn(TfPrivateSubscriber::class.java)
        assertMatches(method)
    }

    @Test
    fun `warn on private subscribing receptor`() {
        val method = subscriberIn(TfInternalWithPrivate::class.java)
        val mismatch = ACCESS_MODIFIER.test(method, SubscriberSignature())
        assertThat(mismatch).isPresent()
        mismatch.get().isWarning shouldBe true
    }

    private fun subscriberIn(cls: Class<*>): Method {
        val method = cls.declaredMethods.first { it.annotatedAsSubscriber() }
        return method
    }

    /**
     * Tells if [Subscribe] annotation present in this method.
     */
    private fun Method.annotatedAsSubscriber() = isAnnotationPresent(Subscribe::class.java)

    /**
     * Asserts that the given method matches the [ACCESS_MODIFIER] criterion.
     */
    private fun assertMatches(method: Method) {
        val mismatch = ACCESS_MODIFIER.test(method, SubscriberSignature())
        assertThat(mismatch).isEmpty()
    }
}

/**
 * A public class with `internal` subscribing receptor.
 */
class TfEventSubscriber : AbstractEventSubscriber() {

    @Subscribe
    internal fun on(@Suppress("UNUSED_PARAMETER") e: CoinTossed) {
        // Do nothing.
    }
}

/**
 * An internal class which declares seemingly public function.
 *
 * The function is effectively `internal` because the class which declares it
 * is `internal`.
 */
internal class TfInternalSubscriber : AbstractEventSubscriber() {

    @Subscribe
    fun on(@Suppress("UNUSED_PARAMETER") ignored: CoinTossed) {
        // Do nothing.
    }
}

/**
 * An internal class with private subscription receptor,
 * which is a warning for now.
 *
 * Later such a declaration would make the method inaccessible to the generated code.
 */
internal class TfInternalWithPrivate : AbstractEventSubscriber() {

    @Subscribe
    @Suppress("UnusedPrivateMember", "UNUSED_PARAMETER")
    private fun on(ignored: CoinTossed) {
        // Do nothing.
    }
}

/**
 * A subscriber class with a public method, which makes the method effectively `private`.
 */
private class TfPrivateSubscriber : AbstractEventSubscriber() {

    @Subscribe
    fun on(@Suppress("UNUSED_PARAMETER") ignored: CoinTossed) {
        // Do nothing.
    }
}
