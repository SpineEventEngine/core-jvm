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

package io.spine.server.tuple

import com.google.common.testing.EqualsTester
import com.google.common.testing.NullPointerTester
import com.google.protobuf.Empty
import com.google.protobuf.Message
import io.spine.testing.TestValues
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertEquals
import com.google.common.truth.Truth.assertThat
import com.google.common.testing.SerializableTester.reserializeAndAssert
import com.google.protobuf.StringValue
import org.junit.jupiter.api.DisplayName

@DisplayName("`Single` should")
class SingleSpec {

    @Test
    fun `not accept nulls`() {
        NullPointerTester()
            .setDefault(Message::class.java, TestValues.newUuidValue())
            .testAllPublicStaticMethods(Single::class.java)
    }

    @Test
    fun `support equality`() {
        val v1 = TestValues.newUuidValue()
        val v2 = TestValues.newUuidValue()

        val s1 = Single.of(v1)
        val s1a = Single.of(v1)
        val s2 = Single.of(v2)

        EqualsTester()
            .addEqualityGroup(s1, s1a)
            .addEqualityGroup(s2)
            .testEquals()
    }

    @Test
    fun `prohibit default value for A`() {
        assertThrows(IllegalArgumentException::class.java) {
            Single.of(StringValue.getDefaultInstance())
        }
    }

    @Test
    fun `prohibit 'Empty' A`() {
        assertThrows(IllegalArgumentException::class.java) {
            Single.of(Empty.getDefaultInstance())
        }
    }

    @Test
    fun `return value and report presence`() {
        val a = TestValues.newUuidValue()
        val single = Single.of(a)

        assertEquals(a, single.a)
        assertThat(single.hasA()).isTrue()
    }

    @Test
    fun `be iterable and return the only element`() {
        val a = TestValues.newUuidValue()
        val single = Single.of(a)

        val it = single.iterator()
        assertThat(it.hasNext()).isTrue()
        assertEquals(a, it.next())
        assertThat(it.hasNext()).isFalse()
    }

    @Test
    fun `be serializable`() {
        val a = TestValues.newUuidValue()
        reserializeAndAssert(Single.of(a))
    }
}
