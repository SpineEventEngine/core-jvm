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

import io.spine.core.BoundedContextName
import io.spine.string.Diags
import io.spine.string.joinBackticked
import io.spine.type.TypeUrl
import java.io.Serial

/**
 * An exception thrown when an [Application] is built with two Bounded Contexts that handle
 * commands of one type.
 *
 * A command has one handler in an application. The services of the application post
 * a command to the Bounded Context that handles commands of its type, so two contexts
 * handling one type would leave the command without a context to go to.
 *
 * @param commandType The type of the commands.
 * @param contexts The names of the contexts that handle the commands.
 * @see Application.Builder.build
 */
public class DuplicateCommandHandlerException internal constructor(
    commandType: TypeUrl,
    contexts: Collection<BoundedContextName>
) : RuntimeException(messageOf(commandType, contexts)) {

    private companion object {

        @Serial
        private const val serialVersionUID: Long = 0L

        private fun messageOf(
            commandType: TypeUrl,
            contexts: Collection<BoundedContextName>
        ): String {
            val type = Diags.backtick(commandType.typeName())
            val names = contexts.map { it.value }.joinBackticked()
            return "The commands of the type $type cannot be handled by more than one" +
                    " Bounded Context: $names." +
                    " Handle the commands of this type in one Bounded Context only."
        }
    }
}
