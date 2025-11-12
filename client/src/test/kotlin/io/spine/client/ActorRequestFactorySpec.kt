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
package io.spine.client

import com.google.common.collect.Sets.newHashSet
import com.google.common.reflect.TypeToken
import com.google.common.testing.NullPointerTester
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.Any
import com.google.protobuf.Message
import com.google.protobuf.Timestamp
import io.kotest.matchers.shouldBe
import io.spine.base.Identifier
import io.spine.base.Time
import io.spine.core.UserId
import io.spine.test.client.TestEntity
import io.spine.testing.DisplayNames
import io.spine.testing.core.given.GivenUserId
import io.spine.testing.setDefault
import io.spine.time.Temporals
import io.spine.time.ZoneIds
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Base tests for the [ActorRequestFactory] descendants.
 */
@DisplayName("`ActorRequestFactory` should")
internal class ActorRequestFactorySpec {

    companion object {

        val ACTOR: UserId = GivenUserId.of(Identifier.newUuid())
        val ZONE_ID: io.spine.time.ZoneId = ZoneIds.systemDefault()

        fun requestFactoryBuilder(): ActorRequestFactory.Builder {
            return ActorRequestFactory.newBuilder()
        }

        fun requestFactory(): ActorRequestFactory {
            return requestFactoryBuilder().setZoneId(ZONE_ID)
                .setActor(ACTOR)
                .build()
        }
    }

    private lateinit var factory: ActorRequestFactory

    @BeforeEach
    fun createFactory() {
        factory = requestFactory()
    }


    @Test
    @DisplayName(DisplayNames.NOT_ACCEPT_NULLS)
    fun passNullToleranceCheck() {

        @Suppress("DEPRECATION") /* Use fully-qualified names in the scope of the method
        instead of imports to localize deprecation warning and its suppression in this method. */
        fun NullPointerTester.setDeprecatedDefaultsToo() {
            setDefault<io.spine.time.ZoneOffset>(io.spine.time.ZoneOffset.getDefaultInstance())
        }

        val tester = NullPointerTester()
        tester.setDefault<Message>(TestEntity.getDefaultInstance())
            .setDefault(
                object : TypeToken<Class<out Message>>() {}.rawType,
                TestEntity::class.java
            )
            .setDefault(
                object : TypeToken<Set<Message>>() {}.rawType,
                newHashSet(Any.getDefaultInstance())
            )
            .setDefault<io.spine.time.ZoneId>(ZoneIds.systemDefault())
            .setDeprecatedDefaultsToo()

        tester.testInstanceMethods(factory, NullPointerTester.Visibility.PUBLIC)
    }

    @Test
    fun `require actor in 'Builder'`() {
        assertThrows<NullPointerException> {
            requestFactoryBuilder()
                .setZoneId(ZoneIds.systemDefault())
                .build()
        }
    }

    @Test
    fun `return values set in 'Builder'`() {
        val builder = requestFactoryBuilder().apply {
            actor = ACTOR
            setZoneId(ZONE_ID)
        }
        builder.actor shouldBe ACTOR
        builder.zoneId shouldBe ZONE_ID
    }

    @Test
    fun `be single tenant by default`() {
        assertNull(factory.tenantId())
    }

    @Test
    fun `when created, store user and timezone`() {
        factory.actor() shouldBe ACTOR
        factory.zoneId() shouldBe ZONE_ID
    }

    @Test
    fun `assign system time-zone, if not specified`() {
        val aFactory = requestFactoryBuilder()
            .setActor(ACTOR)
            .build()
        aFactory.actor() shouldBe ACTOR
        aFactory.zoneId() shouldBe ZoneIds.systemDefault()
    }

    @Test
    fun `support moving between timezones`() {
        val id = ZoneId.of("Australia/Darwin")
        val newZone = ZoneIds.of(id)
        val movedFactory = factory.switchTimeZone(newZone)
        assertThat(movedFactory.zoneId())
            .isNotEqualTo(factory.zoneId())
        movedFactory.zoneId() shouldBe newZone
    }

    @Nested
    @DisplayName("obtain time-zone from current `Time.Provider`")
    internal inner class UsingTimeProvider {

        private val provider: Time.Provider = CustomTimeProvider()

        @BeforeEach
        fun setCustomProvider() {
            Time.setProvider(provider)
        }

        @AfterEach
        fun clearProvider() {
            Time.resetProvider()
        }

        @Test
        fun `when no time-zone set directly`() {
            val factory = ActorRequestFactory.newBuilder()
                .setActor(ACTOR)
                .build()
            val expected = ZoneIds.of(CustomTimeProvider.ZONE)
            factory.zoneId() shouldBe expected
        }
    }

    private class CustomTimeProvider : Time.Provider {

        override fun currentTime(): Timestamp {
            val nowThere = ZonedDateTime.now(ZONE).toInstant()
            return Temporals.from(nowThere).toTimestamp()
        }

        override fun currentZone(): ZoneId = ZONE

        companion object {
            val ZONE: ZoneId = ZoneId.of("Pacific/Galapagos")
        }
    }
}
