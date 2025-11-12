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

package io.spine.server.entity.given;

import com.google.common.collect.ImmutableList;
import io.spine.base.Error;
import io.spine.core.Event;
import io.spine.server.entity.EntityRecord;
import io.spine.server.entity.EntityRecordChange;
import io.spine.server.entity.Phase;
import io.spine.server.entity.TransactionListener;
import io.spine.validate.NonValidated;

import java.util.List;

import static com.google.common.collect.Lists.newLinkedList;

/**
 * An implementation of {@link TransactionListener} which memoizes all arguments received in the
 * callbacks.
 *
 * @param <I>
 *         ID type of the entity under transaction
 */
public final class MemoizingTransactionListener<I> implements TransactionListener<I> {

    private final List<Phase<I>> phasesOnAfter = newLinkedList();

    private Error lastError;

    @Override
    public void onAfterPhase(Phase<I> phase) {
        phasesOnAfter.add(phase);
    }

    public ImmutableList<Phase<I>> phasesOnAfter() {
        return ImmutableList.copyOf(phasesOnAfter);
    }

    @Override
    public void onTransactionFailed(Error cause, @NonValidated EntityRecord entityRecord) {
        lastError = cause;
    }

    public Error lastError() {
        return lastError;
    }

    @Override
    public void onBeforePhase(Phase<I> phase) {
        // Do nothing.
    }

    @Override
    public void onBeforeCommit(@NonValidated EntityRecord entityRecord) {
        // Do nothing.
    }

    @Override
    public void onTransactionFailed(Event cause, @NonValidated EntityRecord entityRecord) {
        // Do nothing.
    }

    @Override
    public void onAfterCommit(EntityRecordChange change) {
        // Do nothing.
    }
}
