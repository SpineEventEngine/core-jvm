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
import io.spine.protobuf.pack
import io.spine.test.client.users.command.LogInUser
import io.spine.testing.core.given.GivenUserId
import io.spine.validation.NonValidated
import java.util.concurrent.TimeUnit.SECONDS
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Checks how `CommandRequest` handles an acknowledgement without a status.
 *
 * A Spine server cannot be made to send such an acknowledgement, so the spec talks to
 * a stub of the command service.
 */
@DisplayName("`CommandRequest` should")
internal class CommandRequestUnexpectedStatusSpec {

    @Test
    fun `fail naming the status of an acknowledgement without a status`() {
        val failure = postingAnsweredWithoutStatus()

        failure.message shouldContain "`STATUS_NOT_SET`"
    }

    private fun postingAnsweredWithoutStatus(): IllegalStateException {
        val serverName = InProcessServerBuilder.generateName()
        val server = InProcessServerBuilder.forName(serverName)
            .directExecutor()
            .addService(AnsweringWithoutStatus)
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
 * Answers every command with an acknowledgement without a status.
 */
private object AnsweringWithoutStatus : CommandServiceGrpc.CommandServiceImplBase() {

    override fun post(request: Command, responseObserver: StreamObserver<Ack>) {
        val ack: @NonValidated Ack = Ack.newBuilder()
            .setMessageId(request.id.pack())
            .buildPartial()
        responseObserver.onNext(ack)
        responseObserver.onCompleted()
    }
}
