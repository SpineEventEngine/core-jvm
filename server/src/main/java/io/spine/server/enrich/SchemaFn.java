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
import io.spine.core.Enrichment.Container;
import io.spine.protobuf.AnyPacker;
import io.spine.type.TypeName;
import org.jspecify.annotations.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Abstract base for functions that produce {@link Enrichment} by applying aggregated enrichment
 * functions.
 *
 * @param <M>
 *         the type of the enrichable message
 * @param <C>
 *         the type of the message context
 */
abstract class SchemaFn<M extends Message, C extends EnrichableMessageContext>
        implements EnrichmentFn<M, C, Enrichment> {

    @Override
    public Enrichment apply(M m, C c) {
        checkNotNull(m);
        checkNotNull(c);
        var container = Container.newBuilder();
        applyAndPut(container, m, c);
        var result = Enrichment.newBuilder()
                .setContainer(container)
                .build();
        return result;
    }

    /**
     * Creates enrichment messages and puts them into the passed container.
     */
    abstract void applyAndPut(Container.Builder container, M m, C c);

    /**
     * Puts a single enrichment message into the container.
     */
    @SuppressWarnings("CheckReturnValue") // calling builder method.
    static void put(Container.Builder container, Message output) {
        var typeName = TypeName.of(output).value();
        container.putItems(typeName, AnyPacker.pack(output));
    }

    /**
     * Ensures that the created enrichment message is non-null.
     * Otherwise, throws {@code NullPointerException}.
     */
    void checkResult(@Nullable Message output,
                     Message sourceMessage,
                     EnrichableMessageContext context,
                     EnrichmentFn<?, ?, ?> function) {
        requireNonNull(
                output,
                format("`%s` produced `null` for the source message `%s` (context: `%s`).",
                       function, sourceMessage, context)
        );
    }
}
