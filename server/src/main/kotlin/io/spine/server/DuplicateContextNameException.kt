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
import java.io.Serial

/**
 * An exception thrown when a Bounded Context is built with the name of another open
 * Bounded Context of the JVM.
 *
 * The framework tells Bounded Contexts apart by name in several places. For example,
 * the integration transport takes a message from a context with the same name for one of its
 * own, and the event store keeps the events of a context under its name. So two open contexts
 * with one name would be taken for one another.
 *
 * @param name The name of the context.
 * @see BoundedContextBuilder.build
 */
public class DuplicateContextNameException internal constructor(
    name: BoundedContextName
) : RuntimeException(messageOf(name)) {

    private companion object {

        @Serial
        private const val serialVersionUID: Long = 0L

        private fun messageOf(name: BoundedContextName): String {
            val quoted = Diags.backtick(name.value)
            return "A Bounded Context named $quoted is already open, or is being built." +
                    " Close it, or give the new context another name."
        }
    }
}
