/*
 * Copyright 2025, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
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

package io.spine.server.command

import io.spine.base.CommandMessage
import io.spine.base.EventMessage
import io.spine.core.ContractFor
import io.spine.server.BoundedContext

/**
 * Abstract base for [Assignee] classes that handle only one command specified by
 * the generic parameter [C]
 *
 * @param C the type of the commands a derived is going to [handle].
 */
public abstract class SingleCommandAssignee<C : CommandMessage> : AbstractAssignee() {

    /**
     * The bounded context in which this assignee works.
     */
    protected lateinit var context: BoundedContext

    init {
        // This call would check that there is only one command handler
        // defined in the derived class.
        // Doing it earlier, here, in the constructor without waiting until
        // the dispatching schema is built (thus gathering the message classes),
        // allows failing faster and avoiding delayed debugging.
        checkReceivesOneCommand()
    }

    @Assign
    @ContractFor(handler = Assign::class)
    protected abstract fun handle(command: C): Iterable<EventMessage>

    final override fun registerWith(context: BoundedContext) {
        super.registerWith(context)
        this.context = context
    }

    private fun checkReceivesOneCommand() {
        val classes = messageClasses()
        if (classes.size > 1) {
            throw IllegalStateException(
                "`${javaClass.name}` handles too many commands: [${classes.joinToString()}]." +
                        " A class extending `${SingleCommandAssignee::class.java.simpleName}`" +
                        " should handle only one."
            )
        }
    }
}
