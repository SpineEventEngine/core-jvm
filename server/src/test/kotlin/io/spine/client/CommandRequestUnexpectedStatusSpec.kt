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

import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.stub.StreamObserver
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldContain
import io.spine.client.grpc.CommandServiceGrpc
import io.spine.core.Ack
import io.spine.core.Command
import io.spine.core.Event
import io.spine.core.Status
import io.spine.protobuf.pack
import io.spine.test.client.users.command.LogInUser
import io.spine.testing.core.given.GivenUserId
import java.util.concurrent.TimeUnit.SECONDS
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Checks how `CommandRequest` handles an acknowledgement that a server must not send.
 *
 * A Spine server cannot be made to send such an acknowledgement, so the specs talk to
 * a stub of the command service.
 */
@DisplayName("`CommandRequest` should")
internal class CommandRequestUnexpectedStatusSpec {

    @Test
    // Reason: builds the deprecated `Status.rejection` until the field is removed.
    @Suppress("DEPRECATION")
    fun `fail naming the status of an acknowledgement with a rejection`() {
        val rejection = Status.newBuilder()
            .setRejection(Event.getDefaultInstance())
            .build()

        val failure = postingAnsweredWith(rejection)

        failure.message shouldContain "REJECTION"
    }

    @Test
    fun `fail naming the status of an acknowledgement without a status`() {
        val failure = postingAnsweredWith(status = null)

        failure.message shouldContain "STATUS_NOT_SET"
    }

    private fun postingAnsweredWith(status: Status?): IllegalStateException {
        val serverName = InProcessServerBuilder.generateName()
        val server = InProcessServerBuilder.forName(serverName)
            .directExecutor()
            .addService(AnsweringCommandService(status))
            .build()
            .start()
        val client = Client.inProcess(serverName)
            .shutdownTimeout(0, SECONDS)
            .build()
        try {
            return shouldThrow<IllegalStateException> {
                client.asGuest()
                    .command(logInUser())
                    .postAndForget()
            }
        } finally {
            client.shutdown()
            server.shutdownNow()
        }
    }

    private fun logInUser(): LogInUser =
        LogInUser.newBuilder()
            .setUser(GivenUserId.generated())
            .build()
}

/**
 * Answers every command with an acknowledgement carrying the given status,
 * or no status at all if it is `null`.
 */
private class AnsweringCommandService(
    private val status: Status?
) : CommandServiceGrpc.CommandServiceImplBase() {

    override fun post(request: Command, responseObserver: StreamObserver<Ack>) {
        val ack = Ack.newBuilder()
            .setMessageId(request.id.pack())
        status?.let { ack.setStatus(it) }
        responseObserver.onNext(ack.buildPartial())
        responseObserver.onCompleted()
    }
}
