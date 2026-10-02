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

import com.google.common.collect.ImmutableList
import com.google.common.testing.NullPointerTester
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.spine.server.BoundedContextBuilder
import io.spine.test.client.ClientTestContext
import io.spine.test.client.users.UserAccount
import io.spine.testing.logging.mute.MuteLogging
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@MuteLogging
@DisplayName("`FilteringRequest` should")
internal class FilteringRequestSpec : AbstractClientTest() {

    private lateinit var request: SubscriptionRequest<UserAccount>

    override fun contexts(): List<BoundedContextBuilder> =
        ImmutableList.of(ClientTestContext.users())

    @BeforeEach
    fun createRequest() {
        request = client().asGuest().subscribeTo(UserAccount::class.java)
    }

    @Test
    @Suppress("DEPRECATION") // Reason: verifies the deprecated no-op; delete with the API.
    fun `return itself, ignoring the field mask`() {
        request.withMask("id") shouldBeSameInstanceAs request
        request.withMask(listOf("id")) shouldBeSameInstanceAs request
    }

    @Test
    fun `reject 'null' field names when given a field mask`() {
        val type = FilteringRequest::class.java
        val withIterable = type.getMethod("withMask", Iterable::class.java)
        val withVarargs = type.getMethod("withMask", Array<String>::class.java)
        val tester = NullPointerTester()
        tester.testMethodParameter(request, withIterable, 0)
        tester.testMethodParameter(request, withVarargs, 0)
    }
}
