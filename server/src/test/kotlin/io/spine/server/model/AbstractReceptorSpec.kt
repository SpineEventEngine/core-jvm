/*
 * Copyright 2024, TeamDev. All rights reserved.
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

package io.spine.server.model

import com.google.protobuf.Message
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.spine.base.EventMessage
import io.spine.base.Identifier
import io.spine.base.Mistake
import io.spine.core.Event
import io.spine.core.Subscribe
import io.spine.protobuf.AnyPacker
import io.spine.server.event.EventSubscriber
import io.spine.server.event.model.SubscriberSignature
import io.spine.server.model.AbstractReceptor.firstParamType
import io.spine.server.model.given.method.OneParamMethod
import io.spine.server.model.given.method.OneParamSignature
import io.spine.server.model.given.method.OneParamSpec
import io.spine.server.model.given.method.StubHandler
import io.spine.server.model.given.method.TwoParamMethod
import io.spine.server.model.given.method.TwoParamSpec
import io.spine.server.type.EventEnvelope
import io.spine.server.type.given.GivenEvent
import io.spine.test.model.ModProjectCreated
import io.spine.test.model.ModProjectStarted
import io.spine.test.reflect.event.RefProjectCreated
import io.spine.test.reflect.event.refProjectCreated
import io.spine.test.reflect.projectId
import io.spine.testing.TestValues
import io.spine.testing.server.TestEventFactory
import io.spine.testing.server.model.ModelTests
import java.lang.reflect.Method
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("`AbstractReceptor` should")
internal class AbstractReceptorSpec {

    private val signature = OneParamSignature()

    private lateinit var target: Any
    private lateinit var twoParamMethod: TwoParamMethod
    private lateinit var oneParamMethod: OneParamMethod

    @BeforeEach
    fun setUp() {
        target = StubHandler()
        twoParamMethod = TwoParamMethod(
            StubHandler.getTwoParameterMethod(),
            TwoParamSpec.INSTANCE
        )
        oneParamMethod = OneParamMethod(
            StubHandler.getOneParameterMethod(),
            OneParamSpec.INSTANCE
        )
    }

    @Test
    fun `not accept 'null' method`() {
        assertThrows<NullPointerException> {
            TwoParamMethod(
                TestValues.nullRef(),
                TwoParamSpec.INSTANCE
            )
        }
    }

    @Test
    fun `return method`() {
        twoParamMethod.rawMethod() shouldBe StubHandler.getTwoParameterMethod()
    }

    @Nested internal inner class
    `check if method access is` {

        @Test
        @DisplayName(AccessModifier.MODIFIER_PUBLIC)
        fun isPublic() {
            twoParamMethod.isPublic shouldBe true
        }

        @Test
        @DisplayName(AccessModifier.MODIFIER_PRIVATE)
        fun isPrivate() {
            oneParamMethod.isPrivate shouldBe true
        }
    }

    @Test
    fun `obtain first parameter type of method`() {
        firstParamType<Message>(oneParamMethod.rawMethod()) shouldBe
                ModProjectStarted::class.java
    }

    @Nested internal inner class
    `invoke method` {

        // can ignore the result in this test
        @Test
        fun `with one parameter`() {
            val eventMessage = ModProjectStarted.newBuilder()
                .setId(Identifier.newUuid())
                .build()
            val event = Event.newBuilder()
                .setId(GivenEvent.someId())
                .setMessage(AnyPacker.pack(eventMessage))
                .setContext(GivenEvent.context())
                .build()
            val envelope = EventEnvelope.of(event)

            oneParamMethod.invoke(target, envelope)

            (target as StubHandler).wasHandleInvoked() shouldBe true
        }

        @Test
        fun `with two parameters`() {
            val eventMessage = ModProjectCreated.newBuilder()
                .setId(Identifier.newUuid())
                .build()
            val event = Event.newBuilder()
                .setId(GivenEvent.someId())
                .setMessage(AnyPacker.pack(eventMessage))
                .setContext(GivenEvent.context())
                .build()
            val envelope = EventEnvelope.of(event)
            twoParamMethod.invoke(target, envelope)

            (target as StubHandler).wasOnInvoked() shouldBe true
        }
    }

    @Test
    fun `return full name in in string form`() {
        twoParamMethod.toString() shouldBe twoParamMethod.fullName
    }

    @Nested internal inner class
    `provide equality such that` {

        @Test
        fun `it equals to itself`() {
            twoParamMethod.equals(twoParamMethod) shouldBe true
        }

        @Test
        @Suppress("EqualsNullCall") // for the test.
        fun `it is not equal to 'null'`() {
            oneParamMethod.equals(null) shouldBe false
        }

        @Test
        @DisplayName("it is not equal to one of another class")
        fun `it is not equal to one of another class`() {
            oneParamMethod.equals(twoParamMethod) shouldBe false
        }

        @Test
        fun `all fields are compared`() {
            val anotherMethod: AbstractReceptor<*, *, *, *, *> =
                TwoParamMethod(
                    StubHandler.getTwoParameterMethod(),
                    TwoParamSpec.INSTANCE
                )
            anotherMethod.equals(twoParamMethod) shouldBe true
        }
    }

    @Test
    fun `provide hash code`() {
        twoParamMethod.hashCode() shouldNotBe System.identityHashCode(twoParamMethod)
    }

    @Nested internal inner class
    `not be created from method throwing` {

        @Test
        fun `checked exception`() {
            val method = signature.classify(StubHandler.getMethodWithCheckedException())
            method.isPresent shouldBe false
        }

        @Test
        fun `runtime exception`() {
            val method = signature.classify(StubHandler.getMethodWithRuntimeException())
            method.isPresent shouldBe false
        }
    }

    @Nested inner class
    `propagate instances of 'Mistake'` {

        private val signature = SubscriberSignature()

        @Test
        fun `if a target throws 'Mistake'`() {
            val receptor = signature.classify(MistakenHandler.method("throwingMistake"))
            receptor.shouldBePresent()
            val envelope = envelope(projectCreated())
            assertThrows<Mistake> {
                receptor.get().invoke(MistakenHandler(), envelope)
            }
        }

        private fun projectCreated(): RefProjectCreated =
            refProjectCreated { this.projectId = projectId { id = Identifier.newUuid() } }
    }
}

/**
 * A subscriber which throws [java.lang.Error] and [kotlin.Error] in the receptors.
 */
private class MistakenHandler : EventSubscriber {

    companion object {
        fun method(name: String): Method {
            return ModelTests.getMethod(MistakenHandler::class.java, name)
        }
    }

    @Subscribe
    @Suppress("TooGenericExceptionThrown", "UNUSED_PARAMETER")
    fun throwingMistake(e: RefProjectCreated) {
        throw StubMistake()
    }
}

@Suppress("serial") // No need for the tests.
private class StubMistake: Mistake()

private fun envelope(eventMessage: EventMessage): EventEnvelope {
    val factory = TestEventFactory.newInstance(AbstractReceptorSpec::class.java)
    val event = factory.createEvent(eventMessage)
    val envelope = EventEnvelope.of(event)
    return envelope
}
