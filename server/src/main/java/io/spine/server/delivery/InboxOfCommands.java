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

package io.spine.server.delivery;

import io.spine.server.type.CommandEnvelope;

/**
 * The part of {@link Inbox} responsible for processing incoming
 * {@link io.spine.server.type.CommandEnvelope commands}.
 *
 * @param <I>
 *         the type of identifier or inbox target entities
 */
final class InboxOfCommands<I> extends InboxPart<I, CommandEnvelope> {

    InboxOfCommands(Inbox.Builder<I> builder) {
        super(builder, builder.commandEndpoints());
    }

    @Override
    protected void setRecordPayload(CommandEnvelope envelope, InboxMessage.Builder builder) {
        var command = envelope.outerObject();
        builder.setCommand(command);
    }

    @Override
    protected String extractUuidFrom(CommandEnvelope envelope) {
        return envelope.id()
                       .getUuid();
    }

    @Override
    protected CommandEnvelope asEnvelope(InboxMessage message) {
        return CommandEnvelope.of(message.getCommand());
    }
}
