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

package io.spine.server.delivery

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`RunResult` should")
internal class RunResultSpec {

    @Test
    fun `require another run after delivering messages`() {
        RunResult(1, false, false).shouldRunAgain() shouldBe true
    }

    @Test
    fun `require another run if the catch-up jobs changed, even with nothing delivered`() {
        RunResult(0, false, true).shouldRunAgain() shouldBe true
    }

    @Test
    fun `not require another run if the monitor stopped the run`() {
        RunResult(1, true, false).shouldRunAgain() shouldBe false
    }

    @Test
    fun `not require another run if nothing was delivered and the jobs did not change`() {
        RunResult(0, false, false).shouldRunAgain() shouldBe false
    }
}
