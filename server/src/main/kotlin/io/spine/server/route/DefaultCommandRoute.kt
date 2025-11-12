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

package io.spine.server.route

import io.spine.base.CommandMessage
import io.spine.core.CommandContext

/**
 * Obtains an ID of a command target entity from the first field of the command message.
 *
 * @param I The type of target entity IDs.
 */
public class DefaultCommandRoute<I : Any> private constructor(cls: Class<I>) :
    CommandRoute<I, CommandMessage> {

    private val firstFieldOf = ByFirstField<I, CommandMessage, CommandContext>(cls)

    override fun invoke(message: CommandMessage, context: CommandContext): I {
        val result = firstFieldOf(message, context)
        return result
    }

    public companion object {

        /**
         * Creates a new instance.
         *
         * @param idClass The class of identifiers used for the routing.
         */
        @JvmStatic
        public fun <I : Any> newInstance(idClass: Class<I>): DefaultCommandRoute<I> =
            DefaultCommandRoute(idClass)

        /**
         * Verifies of the passed command message potentially has a field with an entity ID.
         */
        @JvmStatic
        public fun exists(commandMessage: CommandMessage): Boolean {
            val hasAtLeastOneField = commandMessage.descriptorForType.fields.isNotEmpty()
            return hasAtLeastOneField
        }
    }
}
