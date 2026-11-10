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
import io.spine.core.ErrorCase.INVALID_REQUEST
import io.spine.logging.LoggingFactory
import io.spine.testing.logging.LoggingTest
import io.spine.testing.logging.mute.MuteLogging
import java.util.logging.Level
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@MuteLogging
@DisplayName("`LoggingServerErrorHandler` should")
internal class LoggingServerErrorHandlerSpec :
    LoggingTest(LoggingServerErrorHandlerSpec::class.java, Level.ALL) {

    @BeforeEach
    fun startIntercepting() = interceptLogging()

    @AfterEach
    fun stopIntercepting() = restoreLogging()

    @Test
    fun `log the message and the error case as an error`() {
        val logger = LoggingFactory.loggerFor(LoggingServerErrorHandlerSpec::class)
        val handler = LoggingServerErrorHandler(logger, "Unable to post `%s`: `%s`.")

        handler.accept(StringValue.of("A refused message."), INVALID_REQUEST)

        val record = assertLog().record()
        record.isError()
        record.hasMessageThat().contains("A refused message.")
        record.hasMessageThat().contains("`INVALID_REQUEST`")
    }
}
