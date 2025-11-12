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

package io.spine.server.aggregate.given.part;

import com.google.protobuf.StringValue;
import io.spine.server.aggregate.AggregatePart;
import io.spine.server.aggregate.Apply;
import io.spine.server.command.Assign;
import io.spine.test.aggregate.command.AggAddComment;
import io.spine.test.aggregate.event.AggCommentAdded;
import io.spine.test.aggregate.task.AggTaskComments;
import io.spine.test.aggregate.task.AggTaskId;
import io.spine.test.aggregate.task.Comment;

/**
 * An aggregate part with {@link StringValue} state, which belongs to an aggregate
 * represented by {@link AnAggregateRoot}.
 */
public class TaskCommentsPart
        extends AggregatePart<AggTaskId, AggTaskComments, AggTaskComments.Builder, TaskRoot> {

    public TaskCommentsPart(TaskRoot root) {
        super(root);
    }

    @Assign
    AggCommentAdded handle(AggAddComment command) {
        var event = AggCommentAdded.newBuilder()
                .setTaskId(command.getTaskId())
                .setAuthor(command.getAuthor())
                .setText(command.getText())
                .build();
        return event;
    }

    @Apply
    private void apply(AggCommentAdded event) {
        var comment = Comment.newBuilder()
                .setAuthor(event.getAuthor())
                .setText(event.getText())
                .build();
        builder().setId(id())
                 .addComment(comment);
    }
}
