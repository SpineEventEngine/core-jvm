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

package io.spine.server.commandbus.given;

import com.google.common.collect.ImmutableList;
import io.spine.core.Command;
import io.spine.server.commandbus.CommandFlowWatcher;
import io.spine.server.type.CommandEnvelope;

import java.util.List;

import static com.google.common.collect.Lists.newLinkedList;

public final class MemoizingCommandFlowWatcher implements CommandFlowWatcher {

    private final List<Command> dispatched = newLinkedList();
    private final List<Command> scheduled = newLinkedList();

    @Override
    public void onDispatchCommand(CommandEnvelope command) {
        dispatched.add(command.command());
    }

    @Override
    public void onScheduled(CommandEnvelope command) {
        scheduled.add(command.command());
    }

    public ImmutableList<Command> dispatched() {
        return ImmutableList.copyOf(dispatched);
    }

    public ImmutableList<Command> scheduled() {
        return ImmutableList.copyOf(scheduled);
    }
}
