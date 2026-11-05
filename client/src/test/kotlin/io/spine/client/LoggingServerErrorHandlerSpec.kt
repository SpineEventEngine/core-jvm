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

import com.google.protobuf.StringValue
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.spine.core.ErrorCase
import io.spine.logging.LoggingFactory
import io.spine.testing.logging.mute.MuteLogging
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`LoggingServerErrorHandler` should")
internal class LoggingServerErrorHandlerSpec {

    @Test
    @MuteLogging
    fun `log the message and the error case`() {
        val logger = LoggingFactory.loggerFor(LoggingServerErrorHandlerSpec::class)
        val handler = LoggingServerErrorHandler(logger, "Unable to post `%s`: `%s`.")

        shouldNotThrowAny {
            handler.accept(StringValue.of("A message."), ErrorCase.INVALID_REQUEST)
        }
    }
}
