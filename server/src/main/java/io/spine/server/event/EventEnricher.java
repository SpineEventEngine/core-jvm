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

package io.spine.server.event;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.Message;
import io.spine.base.EventMessage;
import io.spine.core.EventContext;
import io.spine.server.enrich.Enricher;
import io.spine.server.enrich.EnricherBuilder;
import io.spine.server.enrich.EventEnrichmentFn;

/**
 * Enriches events <em>after</em> they are stored, and <em>before</em> they are dispatched.
 *
 * <p>Enrichment schema is constructed like this:
 * <pre>{@code
 *   Enricher enricher = Enricher.newBuilder()
 *       .add(MyEvent.class, MyEnrichment.class,
 *            new EventEnrichmentFn<MyEvent, EventContext, MyEnrichment> { ... } )
 *       ...
 *       .build();
 * }</pre>
 */
public final class EventEnricher extends Enricher<EventMessage, EventContext> {

    private EventEnricher(Builder builder) {
        super(builder);
    }

    /**
     * Creates a new builder.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * The builder for {@link EventEnricher}.
     */
    public static final class Builder
            extends EnricherBuilder<EventMessage, EventContext, Builder> {

        /**
         * Adds event enrichment function to the builder.
         *
         * @param <M>
         *         the type of the event message
         * @param <R>
         *         the type of the enrichment message produced by the function
         * @param eventClassOrInterface
         *         the class or common interface of the events the passed function enriches
         * @param enrichmentClass
         *         the class of the enrichments the passed function produces
         * @param func
         *         the enrichment function
         * @return {@code this} builder
         * @throws IllegalArgumentException
         *         if the builder already contains a function which produces instances of
         *         {@code enrichmentClass} for the passed class or interface of events, or for
         *         its super-interface, or a sub-interface, or a sub-class
         * @see #remove(Class, Class)
         */
        @CanIgnoreReturnValue
        public <M extends EventMessage, R extends Message>
        Builder add(Class<M> eventClassOrInterface, Class<R> enrichmentClass,
                    EventEnrichmentFn<M, R> func) {
            return doAdd(eventClassOrInterface, enrichmentClass, func);
        }

        @Override
        public EventEnricher build() {
            return new EventEnricher(this);
        }
    }
}
