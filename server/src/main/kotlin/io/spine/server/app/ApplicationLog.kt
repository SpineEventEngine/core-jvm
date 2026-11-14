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

package io.spine.server.app

import io.spine.base.EventMessage
import io.spine.logging.Level

/**
 * The log of an [Application][io.spine.server.Application], for the problems that concern
 * none of its Bounded Contexts.
 *
 * The gRPC services of the application write here when they refuse a request before it
 * reaches a Bounded Context: for example, a request of a tenant that is not registered in
 * the application, or a request that no Bounded Context of the application serves.
 * Such problems are described with events, such as [RequestOfUnregisteredTenantRejected].
 * The application may write its own records too.
 *
 * The arguments other than the level are functions, so that a record of a level that is not
 * enabled costs nothing. An implementation calls them only when it writes the record.
 *
 * The services of the application write to the log concurrently, while they answer
 * requests. So an implementation must be thread-safe, and must not throw: it handles
 * a failure to write a record on its own, as Spine Logging does.
 *
 * Unless the builder of the application is given another log, the application writes
 * with Spine Logging. The message of each record starts with `[Application]`, and
 * an event is written as the name of its type, followed by its compact JSON form.
 *
 * @see io.spine.server.Application.log
 * @see io.spine.server.Application.Builder.setApplicationLog
 */
public interface ApplicationLog {

    /**
     * Writes a record with the given message.
     *
     * @param level The level of the record.
     * @param message Provides the message of the record.
     */
    public fun log(level: Level, message: () -> String)

    /**
     * Writes a record with the given event.
     *
     * @param level The level of the record.
     * @param event Provides the event that the record describes.
     */
    public fun logEvent(level: Level, event: () -> EventMessage)

    /**
     * Writes a record with the given message and event.
     *
     * @param level The level of the record.
     * @param message Provides the message of the record.
     * @param event Provides the event that the record describes.
     */
    public fun logEvent(level: Level, message: () -> String, event: () -> EventMessage)
}
