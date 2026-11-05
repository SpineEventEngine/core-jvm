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

package io.spine.server

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.spine.core.Ack
import io.spine.core.Command
import io.spine.core.ErrorCase
import io.spine.core.Status
import io.spine.grpc.MemoizingObserver
import io.spine.grpc.StreamObservers.memoizingObserver
import io.spine.protobuf.isNotDefault
import io.spine.server.given.service.GivenCommandService.noOpCommandService
import io.spine.test.unpublished.command.Halt
import io.spine.testing.client.TestActorRequestFactory
import io.spine.testing.logging.mute.MuteLogging
import io.spine.type.UnpublishedLanguageException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@MuteLogging
@DisplayName("`CommandService` should prohibit using `internal_type` commands")
internal class CommandServiceUnpublishedLanguageSpec {

    private lateinit var service: CommandService
    private lateinit var observer: MemoizingObserver<Ack>

    @BeforeEach
    fun initServiceAndObserver() {
        service = noOpCommandService()
        observer = memoizingObserver()
    }

    @Test
    fun `answering 'UNSUPPORTED_REQUEST' without telling why, when such a command posted`() {
        val command = createCommand()

        service.post(command, observer)

        observer.error shouldBe null
        observer.isCompleted shouldBe true

        val response = observer.firstResponse()

        response.isNotDefault() shouldBe true

        val status = response.status

        status.statusCase shouldBe Status.StatusCase.ERROR_CASE
        status.errorCase shouldBe ErrorCase.UNSUPPORTED_REQUEST
        response.toByteString().toStringUtf8() shouldNotContain
                UnpublishedLanguageException::class.java.simpleName
    }

    private fun createCommand(): Command {
        val factory = TestActorRequestFactory(javaClass)
        val commandMessage = Halt.newBuilder().setValue(true).build()
        val command = factory.createCommand(commandMessage)
        return command
    }
}
