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

import com.google.common.truth.extensions.proto.ProtoTruth
import io.kotest.matchers.shouldBe
import io.spine.base.Error
import io.spine.core.Responses.ok
import io.spine.testing.UtilityClassTest
import io.spine.validate.NonValidated
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`Responses` utility should")
internal class ResponsesTest : UtilityClassTest<Responses>(Responses::class.java) {

    @Test
    fun `return OK response`() {
        ProtoTruth.assertThat(ok())
            .isNotEqualToDefaultInstance()
    }

    @Test
    fun `recognize OK response`() {
        val ok = ok()
        ok.isOk shouldBe true
        ok.isError shouldBe false
    }

    /**
     * This test uses partially built values even though the [Status]
     * does not set validation on the [Status.error][Status.getError] field.
     * This is so because the `base` subproject is built without using the Validation SDK.
     *
     * We want to make it future-compatible, should we decide validating
     * the types provided by `base`.
     */
    @Test
    fun `recognize not OK response`() {
        val asIfError: @NonValidated Status = Status.newBuilder()
            .setError(Error.getDefaultInstance())
            .buildPartial()

        val error: @NonValidated Response = Response.newBuilder()
            .setStatus(asIfError)
            .buildPartial()

        error.isOk shouldBe false
        error.isError shouldBe true
    }
}
