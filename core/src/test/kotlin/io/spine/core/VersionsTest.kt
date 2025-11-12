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
package io.spine.core

import com.google.common.testing.NullPointerTester
import io.kotest.matchers.shouldBe
import io.spine.core.Versions.checkIsIncrement
import io.spine.core.Versions.increment
import io.spine.testing.UtilityClassTest
import io.spine.testing.core.given.GivenVersion.withNumber
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("`Versions` utility should")
internal class VersionsTest : UtilityClassTest<Versions>(Versions::class.java) {

    override fun configure(tester: NullPointerTester) {
        super.configure(tester)
        tester.setDefault(Version::class.java, Version.getDefaultInstance())
    }

    @Test
    fun `check 'Version' increment`() {
        assertThrows<IllegalArgumentException> {
            checkIsIncrement(
                withNumber(2),
                withNumber(1)
            )
        }
    }

    @Test
    fun `increment 'Version'`() {
        val v1 = withNumber(1)
        increment(v1).number shouldBe v1.number + 1
    }
}
