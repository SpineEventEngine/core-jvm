/*
 * Copyright 2026, TeamDev. All rights reserved.
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

package io.spine.core;

import com.google.protobuf.Message;
import io.spine.core.Enrichment.Container;
import io.spine.core.Enrichment.ModeCase;
import io.spine.type.TypeName;

import java.util.Deque;
import java.util.Optional;

import static com.google.common.collect.Queues.newArrayDeque;
import static io.spine.protobuf.AnyPacker.unpack;
import static io.spine.util.Exceptions.newIllegalStateException;

/**
 * Utility class for working with event enrichments.
 */
final class Enrichments {

    /** Prevents instantiation of this utility class. */
    private Enrichments() {
    }

    /**
     * Obtains the container of enrichments from the passed enclosing instance,
     * if its {@link ModeCase} allows for having enrichments.
     *
     * <p>Otherwise, empty {@code Optional} is returned.
     */
    static Optional<Container> containerIn(EnrichableMessageContext context) {
        var enrichment = context.getEnrichment();
        if (enrichment.getModeCase() == ModeCase.CONTAINER) {
            return Optional.of(enrichment.getContainer());
        }
        return Optional.empty();
    }

    /**
     * Obtains enrichment from the passed container.
     */
    static <E extends Message>
    Optional<E> find(Class<E> enrichmentClass, Container container) {
        var typeName = TypeName.of(enrichmentClass);
        var result = findType(typeName, enrichmentClass, container);
        return result;
    }

    /**
     * Obtains enrichment of the passed class from the container.
     *
     * @throws IllegalStateException if there is no enrichment of this class in the passed container
     */
    static <E extends Message> E get(Class<E> enrichmentClass, Container container) {
        var typeName = TypeName.of(enrichmentClass);
        var result = findType(typeName, enrichmentClass, container)
                .orElseThrow(() -> newIllegalStateException(
                        "Unable to get enrichment of the type `%s` from the container `%s`.",
                        typeName, container));
        return result;
    }

    private static <E extends Message> Optional<E>
    findType(TypeName typeName, Class<E> enrichmentClass, Container container) {
        var any = container.getItemsMap()
                           .get(typeName.value());
        var result = Optional.ofNullable(any)
                             .map(packed -> unpack(packed, enrichmentClass));
        return result;
    }

    /**
     * Clears the enrichments from the {@code event} and its origin.
     */
    @SuppressWarnings("deprecation") // Uses the deprecated field to be sure to clean up old data.
    static Event clear(Event event) {
        var context = event.getContext();
        var originCase = context.getOriginCase();
        var resultContext = context.toBuilder().clearEnrichment();
        if (originCase == EventContext.OriginCase.EVENT_CONTEXT) {
            resultContext.setEventContext(context.getEventContext()
                                                  .toBuilder()
                                                  .clearEnrichment()
                                                  .build());
        }
        var result = event.toBuilder()
                            .setContext(resultContext.build())
                            .build();
        return result;
    }

    /**
     * Clears the enrichments from the {@code event} and all of its parent contexts.
     */
    @SuppressWarnings({
            "deprecation" /* Uses the deprecated field to be sure to clean up old data. */,
            "ConstantConditions" /* Checked logically. */})
    static Event clearAll(Event event) {
        var eventContext = event.getContext()
                .toBuilder()
                .clearEnrichment();
        var contexts = eventContextHierarchy(eventContext);

        var context = contexts.pollLast();
        var next = contexts.pollLast();
        while (next != null) {
            context = next.setEventContext(context);
            next = contexts.pollLast();
        }
        var result = event.toBuilder()
                            .setContext(context.build())
                            .build();
        return result;
    }

    /**
     * Traverses the event parent context hierarchy until non-{@link EventContext} origin is found.
     *
     * <p>All of the {@link EventContext}-kind origins are collected and returned as
     * {@code Deque<EventContext.Builder>}, where the deepest origin resides last.
     */
    @SuppressWarnings("deprecation") // Uses the deprecated field to be sure to clean up old data.
    private static Deque<EventContext.Builder>
    eventContextHierarchy(EventContext.Builder eventContext) {

        var child = eventContext;
        var originCase = child.getOriginCase();
        Deque<EventContext.Builder> contexts = newArrayDeque();
        contexts.add(child);
        while (originCase == EventContext.OriginCase.EVENT_CONTEXT) {
            var origin = child.getEventContext()
                    .toBuilder()
                    .clearEnrichment();
            contexts.add(origin);
            child = origin;
            originCase = child.getOriginCase();
        }
        return contexts;
    }
}
