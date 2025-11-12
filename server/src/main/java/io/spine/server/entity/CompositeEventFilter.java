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

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.spine.base.EventMessage;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * An {@link EventFilter} which composes several other filters.
 *
 * <p>The filters are applied to the input event one by one in the order of
 * {@linkplain Builder#add(EventFilter) addition}. The next filter is applied to the result of
 * the previous filter. If a filter returns an empty result, the whole composite filter returns
 * an empty result at once.
 */
public final class CompositeEventFilter implements EventFilter {

    private final ImmutableList<EventFilter> filters;

    private CompositeEventFilter(Builder builder) {
        this.filters = builder.filters.build();
    }

    @Override
    public Optional<? extends EventMessage> filter(EventMessage event) {
        Optional<? extends EventMessage> result = Optional.of(event);
        for (var filter : filters) {
            result = filter.filter(result.get());
            if (result.isEmpty()) {
                return Optional.empty();
            }
        }
        return result;
    }

    /**
     * Creates a new instance of {@code Builder} for {@code CompositeEventFilter} instances.
     *
     * @return new instance of {@code Builder}
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * A builder for the {@code CompositeEventFilter} instances.
     */
    public static final class Builder {

        private final ImmutableList.Builder<EventFilter> filters = ImmutableList.builder();

        /**
         * Prevents direct instantiation.
         */
        private Builder() {
        }

        /**
         * Adds a filter to the built composite filter.
         *
         * <p>The order of applying the filters is the order of the filters being passed to this
         * method.
         */
        @CanIgnoreReturnValue
        public Builder add(EventFilter filter) {
            checkNotNull(filter);
            filters.add(filter);
            return this;
        }

        /**
         * Creates a new instance of {@code CompositeEventFilter}.
         *
         * @return new instance of {@code CompositeEventFilter}
         */
        public CompositeEventFilter build() {
            return new CompositeEventFilter(this);
        }
    }
}
