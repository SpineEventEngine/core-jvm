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

package io.spine.server.entity;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import io.spine.annotation.Internal;
import io.spine.base.Error;
import io.spine.core.Event;
import io.spine.core.MessageId;
import io.spine.core.Signal;
import io.spine.protobuf.AnyPacker;
import io.spine.validate.NonValidated;
import io.spine.validate.ValidationError;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

import java.util.List;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * An implementation of {@link TransactionListener} which monitors the transaction flow and
 * triggers the {@link EntityLifecycle}.
 *
 * <p>On a successful {@link Phase}, memoizes the ID of the applied message. After a successful
 * transaction commit, passes the memoized IDs to the {@link EntityLifecycle} of the entity under
 * transaction, along with the information about the mutations performed under the transaction - an
 * {@link EntityRecordChange}.
 *
 * <p>An instance of this {@link TransactionListener} is meant to serve for a single
 * {@link Transaction}. When trying to reuse a listener for multiple transactions,
 * an {@link IllegalStateException} is thrown.
 *
 * @param <I>
 *         ID type of the entity under transaction
 */
@Internal
public final class EntityLifecycleMonitor<I> implements TransactionListener<I> {

    private final Repository<I, ?> repository;
    private final List<MessageId> acknowledgedMessages;
    private final I entityId;
    private @MonotonicNonNull Signal<?, ?, ?> lastMessage;

    private EntityLifecycleMonitor(Repository<I, ?> repository, I id) {
        this.repository = repository;
        this.acknowledgedMessages = Lists.newArrayList();
        this.entityId = id;
    }

    /**
     * Creates a new instance of {@code EntityLifecycleMonitor}.
     *
     * @param repository
     *         the repository of the entity under transaction
     * @param id
     *         the entity ID
     */
    public static <I> EntityLifecycleMonitor<I> newInstance(Repository<I, ?> repository, I id) {
        checkNotNull(repository);
        checkNotNull(id);
        return new EntityLifecycleMonitor<>(repository, id);
    }

    /**
     * Creates a new instance with a single {@linkplain #acknowledgedMessages acknowledged} message.
     *
     * @param repository
     *         the repository of the entity under transaction
     * @param id
     *         the entity ID
     * @param message
     *         the artificially injected acknowledged message
     */
    static <I> EntityLifecycleMonitor<I>
    withAcknowledgedMessage(Repository<I, ?> repository, I id, Signal<?, ?, ?> message) {
        var monitor = newInstance(repository, id);
        monitor.lastMessage = message;
        monitor.acknowledgedMessages.add(message.messageId());
        return monitor;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Memoizes the dispatched message for diagnostics in case of a failure.
     */
    @Override
    public void onBeforePhase(Phase<I> phase) {
        lastMessage = phase.signal();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Memoizes the ID of the event applied by the given phase. The received event IDs will be
     * reported to the {@link EntityLifecycle} after a successful commit.
     */
    @Override
    public void onAfterPhase(Phase<I> phase) {
        checkSameEntity(phase.entityId());
        var messageId = phase.signal().messageId();
        acknowledgedMessages.add(messageId);
    }

    @Override
    public void onBeforeCommit(@NonValidated EntityRecord entityRecord) {
        // NOP.
    }

    /**
     * {@inheritDoc}
     *
     * <p>Notifies the {@link EntityLifecycle} of the entity state change.
     */
    @Override
    public void onAfterCommit(EntityRecordChange change) {
        Set<MessageId> signalIds = ImmutableSet.copyOf(acknowledgedMessages);
        if (lastMessage != null) {
            // If lastMessage is null, then no messages have been applied, hence no changes
            // performed.
            repository.lifecycleOf(entityId)
                      .onStateChanged(change, signalIds, lastMessage.asMessageOrigin());
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Notifies the {@link EntityLifecycle} of the failure.
     */
    @Override
    public void onTransactionFailed(Error cause, EntityRecord entityRecord) {
        if (!cause.hasDetails()) {
            return;
        }
        var details = AnyPacker.unpack(cause.getDetails());
        if (details instanceof ValidationError) {
            var error = (ValidationError) details;
            checkState(lastMessage != null, "Transaction failed but no messages were propagated.");
            var causeMessage = lastMessage.messageId();
            var rootMessage = lastMessage.rootMessage();
            repository.lifecycleOf(entityId)
                      .onInvalidEntity(causeMessage,
                                       rootMessage,
                                       error,
                                       entityRecord.getVersion());
        }
    }

    @Override
    public void onTransactionFailed(Event cause, @NonValidated EntityRecord entityRecord) {
        // NOP.
    }

    /**
     * Ensures that the given entity is the same that was given the previous time.
     *
     * <p>An instance of {@code EntityLifecycleMonitor} should not be reused for multiple entity
     * transactions. Thus, if the given entity is not the same as seen before,
     * an {@link IllegalStateException} is thrown
     *
     * @param entityId
     *         the ID of the entity to check
     * @throws IllegalStateException
     *         if the check fails
     */
    private void checkSameEntity(I entityId) throws IllegalStateException {
        checkState(this.entityId.equals(entityId),
                   "Tried to reuse an instance of %s for multiple transactions.",
                   EntityLifecycleMonitor.class.getSimpleName());
    }
}
