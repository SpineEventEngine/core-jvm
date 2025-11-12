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

import io.kotest.matchers.shouldBe
import io.spine.base.EventMessage
import io.spine.protobuf.AnyPacker
import io.spine.test.core.ProjectCreated
import io.spine.test.core.TaskAssigned
import io.spine.test.core.projectCreated
import io.spine.test.core.projectId
import io.spine.validate.NonValidated
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`Signal` should")
internal class SignalTest {

    @Test
    fun `verify type of the enclosed message`() {
        val event = stubEvent()
        event.`is`(ProjectCreated::class.java) shouldBe true
        event.`is`(EventMessage::class.java) shouldBe true
        event.`is`(TaskAssigned::class.java) shouldBe false
    }

    /**
     * Creates a stub instance of `Event` with the type [ProjectCreated].
     *
     * Some of required fields of `Event` are not populated for simplicity.
     */
    private fun stubEvent(): @NonValidated Event {
        val project = projectId { id = javaClass.name }
        val message = projectCreated { projectId = project }
        return Event.newBuilder()
            .setMessage(AnyPacker.pack(message))
            .buildPartial()
    }
}
