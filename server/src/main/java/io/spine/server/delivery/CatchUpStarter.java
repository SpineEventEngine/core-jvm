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

package io.spine.server.delivery;

import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.Timestamp;
import io.spine.base.Identifier;
import io.spine.server.BoundedContext;
import io.spine.server.delivery.event.CatchUpRequested;
import io.spine.server.projection.ProjectionRepository;
import io.spine.server.type.EventClass;
import io.spine.type.TypeUrl;
import org.jspecify.annotations.Nullable;

import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Streams.stream;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

/**
 * The one who starts the catch-up process.
 *
 * <p>Checks whether the catch-up is already started for the requested IDs before emitting
 * {@linkplain CatchUpRequested an event} telling that the catch-up has been requested.
 *
 * @param <I>
 *         the type of the identifiers of the catching-up entities
 */
final class CatchUpStarter<I> {

    private final BoundedContext context;
    private final TypeUrl projectionStateType;
    private final CatchUpStorage storage;
    private final ImmutableSet<EventClass> eventClasses;

    private CatchUpStarter(Builder<I> builder) {
        this.context = builder.context;
        this.storage = builder.storage;
        this.projectionStateType = builder.repository.entityStateType();
        this.eventClasses = builder.repository.messageClasses();
    }

    /**
     * Creates a new builder for the {@code CatchUpStarter}.
     *
     * @param repo
     *         the repository of the projection to catch up.
     * @param storage
     *         the storage of the catch-up processes
     * @param <I>
     *         the type of the identifiers of the catching-up entities
     * @return the new instance of the builder
     */
    static <I> Builder<I> newBuilder(ProjectionRepository<I, ?, ?> repo, CatchUpStorage storage) {
        return new Builder<>(repo, storage);
    }

    /**
     * Starts the catch-up restricting it to the set of projection instances by certain identifiers.
     *
     * @param ids
     *         the IDs of the projection instances to catch-up, or {@code null} if all entities of
     *         this kind need to catch up.
     * @param since
     *         since when the catch-up is going to read the events
     * @throws CatchUpAlreadyStartedException
     *         if the catch-up is already in progress for at least one of the requested entities
     * @return identifier of the catch-up operation
     */
    CatchUpId start(@Nullable Set<I> ids, Timestamp since) throws CatchUpAlreadyStartedException {
        checkNotActive(ids);

        var request = buildRequest(ids, since);
        var id = CatchUpId.newBuilder()
                .setUuid(Identifier.newUuid())
                .setProjectionType(projectionStateType.value())
                .build();
        var eventMessage = CatchUpRequested.newBuilder()
                .setId(id)
                .setRequest(request)
                .build();
        var eventFactory = new CatchUpEventFactory(projectionStateType, context.isMultitenant());
        var event = eventFactory.createEvent(eventMessage);
        context.eventBus()
               .post(event);
        return id;
    }

    @SuppressWarnings("MethodWithMultipleLoops")
    private CatchUp.Request buildRequest(@Nullable Set<I> ids, Timestamp since) {
        var requestBuilder = CatchUp.Request.newBuilder();
        if (ids != null) {
            for (var id : ids) {
                var packed = Identifier.pack(id);
                requestBuilder.addTarget(packed);
            }
        }

        requestBuilder.setSinceWhen(since);
        for (var eventClass : eventClasses) {
            var name = eventClass.typeName();
            requestBuilder.addEventType(name.value());
        }
        return requestBuilder.build();
    }

    private void checkNotActive(@Nullable Set<I> ids) throws CatchUpAlreadyStartedException {
        var ongoing = storage.readByType(projectionStateType);
        var active = stream(ongoing)
                .filter(catchUp -> CatchUpStatus.COMPLETED != catchUp.getStatus())
                .collect(toList());
        var alreadyCatchingUp = hasIntersections(active, ids);
        if (alreadyCatchingUp) {
            throw new CatchUpAlreadyStartedException(projectionStateType, ids);
        }
    }

    @SuppressWarnings("MethodWithMultipleLoops")
    private static boolean hasIntersections(Iterable<CatchUp> ongoing, @Nullable Set<?> ids) {
        if (ids == null) {
            return ongoing.iterator()
                          .hasNext();
        }
        if (ids.isEmpty()) {
            return false;
        }
        var packedIds = ids.stream()
                .map(Identifier::pack)
                .collect(toSet());
        for (var ongoingProcess : ongoing) {
            var targets = ongoingProcess.getRequest()
                                        .getTargetList();
            if (targets.isEmpty()) {
                return true;
            }
            for (var target : targets) {
                if (packedIds.contains(target)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * A builder for the {@link CatchUpStarter} instances.
     *
     * @param <I>
     *         the type of the identifiers of the catching-up entities
     */
    static final class Builder<I> {

        private final ProjectionRepository<I, ?, ?> repository;
        private final CatchUpStorage storage;

        private BoundedContext context;

        private Builder(ProjectionRepository<I, ?, ?> repository,
                        CatchUpStorage storage) {
            this.repository = repository;
            this.storage = storage;
        }

        /**
         * Sets the <b>initialized</b> {@code BoundedContext}, in scope of which the catch-up is
         * performed.
         */
        @CanIgnoreReturnValue
        Builder<I> withContext(BoundedContext context) {
            this.context = checkNotNull(context);
            return this;
        }

        CatchUpStarter<I> build() {
            checkNotNull(context, "The Bounded Context must be set" +
                    "in order to create an instance of `CatchUpStarter`.");
            return new CatchUpStarter<>(this);
        }
    }
}
