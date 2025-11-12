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

package io.spine.server.entity.given.tx;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.Message;
import io.spine.server.entity.given.tx.event.TxCreated;
import io.spine.server.entity.given.tx.event.TxErrorRequested;
import io.spine.server.entity.given.tx.event.TxStateErrorRequested;
import io.spine.server.event.NoReaction;
import io.spine.server.event.React;
import io.spine.server.procman.ProcessManager;

import java.util.List;

import static com.google.common.collect.Lists.newLinkedList;

/**
 * Test environment Process Manager for {@link io.spine.server.procman.PmTransactionTest}.
 */
public class TxProcessManager extends ProcessManager<Id, PmState, PmState.Builder> {

    private final List<Message> receivedEvents = newLinkedList();

    public TxProcessManager(Id id) {
        super(id);
    }

    @React
    NoReaction event(TxCreated e) {
        receivedEvents.add(e);
        builder().setId(id())
                 .setName(e.getName());
        return noReaction();
    }

    /**
     * Always throws {@code RuntimeException} to emulate the case of an error in
     * a reacting method of a Process Manager.
     *
     * @see io.spine.server.procman.PmTransactionTest#failingInHandler()
     */
    @React
    NoReaction event(TxErrorRequested e) {
        throw new RuntimeException("that tests the tx behaviour for process manager");
    }

    @React
    NoReaction event(TxStateErrorRequested e) {
        // By convention, the first field of state is required.
        // Clearing it should fail the validation when the transaction is committed.
        builder().clearId();
        return noReaction();
    }

    public List<Message> receivedEvents() {
        return ImmutableList.copyOf(receivedEvents);
    }
}
