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

package io.spine.client;

import io.spine.base.EntityState;
import io.spine.client.CompositeFilter.CompositeOperator;

import java.io.Serial;
import java.util.Collection;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Lists.asList;
import static io.spine.client.CompositeFilter.CompositeOperator.ALL;
import static io.spine.client.CompositeFilter.CompositeOperator.EITHER;

/**
 * A composite query filter which targets one or more entity
 * {@link io.spine.query.EntityColumn columns}.
 *
 * @deprecated This type is deprecated. Use {@linkplain io.spine.query.EntityQuery entity queries}
 *         instead. See {@code io.spine.query} package documentation for more details on usage.
 */
@Deprecated
public final class CompositeQueryFilter extends TypedCompositeFilter<EntityState<?>> {

    @Serial
    private static final long serialVersionUID = 0L;

    private CompositeQueryFilter(Collection<QueryFilter> filters, CompositeOperator operator) {
        super(filters, operator);
    }

    /**
     * Creates a new conjunction composite filter.
     *
     * <p>A record is considered matching this filter if and only if it matches all of the
     * passed filters.
     */
    public static CompositeQueryFilter all(QueryFilter first, QueryFilter... rest) {
        checkNotNull(first);
        checkNotNull(rest);
        return new CompositeQueryFilter(asList(first, rest), ALL);
    }

    /**
     * Creates a new disjunction composite filter.
     *
     * <p>A record is considered matching this filter if it matches at least one of the passed
     * filters.
     */
    public static CompositeQueryFilter either(QueryFilter first, QueryFilter... rest) {
        checkNotNull(first);
        checkNotNull(rest);
        return new CompositeQueryFilter(asList(first, rest), EITHER);
    }
}
