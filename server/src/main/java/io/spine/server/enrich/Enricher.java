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

package io.spine.server.enrich;

import com.google.protobuf.Message;
import io.spine.core.EnrichableMessageContext;
import io.spine.core.Enrichment;
import io.spine.server.type.EnrichableMessageEnvelope;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Enriches messages <em>after</em> they are stored, and <em>before</em> they are dispatched.
 *
 * @param <M>
 *         the type of the enrichable message
 * @param <C>
 *         the type of the message context
 */
public abstract class Enricher<M extends Message, C extends EnrichableMessageContext>
        implements EnrichmentService<M, C> {

    private final Schema<M, C> schema;

    /**
     * Creates a new instance taking functions from the passed builder.
     */
    protected Enricher(EnricherBuilder<M, C, ?> builder) {
        this.schema = Schema.newInstance(builder);
    }

    /**
     * Enriches the passed message if it can be enriched. Otherwise, returns the passed instance.
     *
     * A message can be enriched if the flag {@code do_not_enrich} is not set in the
     * {@link io.spine.core.Enrichment Enrichment} instance of the context of the outer object of
     * the message.
     *
     * @param  source
     *         the envelope with the source message
     * @throws IllegalArgumentException
     *         if the passed message cannot be enriched
     */
    public <E extends EnrichableMessageEnvelope<?, ?, M, C, E>> E enrich(E source) {
        if (schema.isEmpty()) {
            return source;
        }
        var result = source.toEnriched(this);
        return  result;
    }

    @Override
    public Optional<Enrichment> createEnrichment(M message, C context) {
        @SuppressWarnings("unchecked") // correct type is ensured by a Bus which uses the Enricher.
        var cls = (Class<? extends M>) message.getClass();
        @SuppressWarnings("unchecked")  /* Expected according to the `fn` setup. */
        @Nullable SchemaFn<M, C> fn = (SchemaFn<M, C>) schema.enrichmentOf(cls);
        if (fn == null) {
            return Optional.empty();
        }

        var enrichment = fn.apply(message, context);
        return Optional.of(enrichment);
    }
}
