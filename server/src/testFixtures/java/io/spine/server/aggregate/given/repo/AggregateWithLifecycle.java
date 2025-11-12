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

package io.spine.server.aggregate.given.repo;

import io.spine.client.ArchivedColumn;
import io.spine.client.DeletedColumn;
import io.spine.client.EntityLifecycleColumn;
import io.spine.server.aggregate.Aggregate;
import io.spine.server.aggregate.Apply;
import io.spine.server.command.Assign;
import io.spine.server.test.shared.LongIdAggregate;
import io.spine.test.aggregate.cli.Evaluate;
import io.spine.test.aggregate.cli.Evaluated;

/**
 * The aggregate that can handle status flags.
 */
public class AggregateWithLifecycle
        extends Aggregate<Long, LongIdAggregate, LongIdAggregate.Builder> {

    @Assign
    Evaluated handle(Evaluate commandMessage) {
        var command = commandMessage.getCmd();
        var result = command + 'd';
        return Evaluated
                .newBuilder()
                .setCmd(result)
                .build();
    }

    @Apply
    private void on(Evaluated eventMessage) {
        var msg = RepoOfAggregateWithLifecycle.getMessage(eventMessage);
        if (namedAfterColumn(msg, ArchivedColumn.instance())) {
            setArchived(true);
        }
        if (namedAfterColumn(msg, DeletedColumn.instance())) {
            setDeleted(true);
        }
        builder().setValue(eventMessage.hashCode());
    }

    private static boolean namedAfterColumn(String msg, EntityLifecycleColumn<?> column) {
        return msg.equalsIgnoreCase(column.toString());
    }
}
