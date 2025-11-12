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

package io.spine.server.delivery;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A delivery action which does nothing but stores the messages which are passed to it.
 */
public final class MemoizingAction implements DeliveryAction {

    private @Nullable List<InboxMessage> passedMessages;

    /**
     * Creates an empty action, which has not yet stored anything.
     */
    static MemoizingAction empty() {
        return new MemoizingAction();
    }

    @SuppressWarnings("AssignmentOrReturnOfFieldWithMutableType")   // intentionally.
    @Override
    public DeliveryErrors executeFor(List<InboxMessage> messages) {
        passedMessages = messages;
        return DeliveryErrors.newBuilder()
                             .build();
    }

    /**
     * Returns the passed messages as-is.
     *
     * <p>Returns {@code null}, if the action has never been called.
     */
    @SuppressWarnings("AssignmentOrReturnOfFieldWithMutableType")   // intentionally.
    @Nullable List<InboxMessage> passedMessages() {
        return passedMessages;
    }
}
