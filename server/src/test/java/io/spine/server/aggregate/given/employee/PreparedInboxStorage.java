/*
 * Copyright 2022, TeamDev. All rights reserved.
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

package io.spine.server.aggregate.given.employee;

import io.spine.base.Time;
import io.spine.client.EntityId;
import io.spine.core.Command;
import io.spine.server.delivery.InboxId;
import io.spine.server.delivery.InboxLabel;
import io.spine.server.delivery.InboxMessage;
import io.spine.server.delivery.InboxMessageMixin;
import io.spine.server.delivery.InboxMessageStatus;
import io.spine.server.delivery.InboxSignalId;
import io.spine.server.delivery.InboxStorage;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.route.CommandRouting;
import io.spine.server.storage.memory.InMemoryStorageFactory;
import io.spine.type.TypeUrl;

import static io.spine.base.Identifier.pack;

/**
 * In-memory {@code InboxStorage} which allows putting commands there directly.
 */
public final class PreparedInboxStorage extends InboxStorage {

    public PreparedInboxStorage() {
        super(InMemoryStorageFactory.newInstance(), false);
    }

    /**
     * Puts the passed commands into the storage.
     */
    public void write(ShardIndex shardIndex, Iterable<Command> commands) {
        commands.forEach(cmd -> write(shardIndex, cmd));
    }

    private void write(ShardIndex shardIndex, Command cmd) {
        var routing = CommandRouting.newInstance(EmployeeId.class);
        var target = TypeUrl.of(Employee.class);

        var inboxSignalId = InboxSignalId.newBuilder()
                .setValue(cmd.messageId().getId().getValue().toString())
                .build();
        var inboxMessage = InboxMessage.newBuilder()
                .setId(InboxMessageMixin.generateIdWith(shardIndex))
                .setSignalId(inboxSignalId)
                .setInboxId(wrap(routing.invoke(cmd.enclosedMessage(), cmd.getContext()), target))
                .setCommand(cmd)
                .setLabel(InboxLabel.HANDLE_COMMAND)
                .setWhenReceived(Time.currentTime())
                .setStatus(InboxMessageStatus.TO_DELIVER)
                .build();

        write(inboxMessage);
    }

    private static <T> InboxId wrap(T id, TypeUrl target) {
        var entityId = EntityId.newBuilder()
                .setId(pack(id))
                .build();
        var inboxId = InboxId.newBuilder()
                .setEntityId(entityId)
                .setTypeUrl(target.value())
                .build();
        return inboxId;
    }
}
