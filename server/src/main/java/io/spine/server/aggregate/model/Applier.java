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

package io.spine.server.aggregate.model;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import io.spine.base.EventMessage;
import io.spine.server.aggregate.Aggregate;
import io.spine.server.model.AbstractReceptor;
import io.spine.server.model.ArgumentFilter;
import io.spine.server.model.Attribute;
import io.spine.server.model.ParameterSpec;
import io.spine.server.model.VoidMethod;
import io.spine.server.type.EmptyClass;
import io.spine.server.type.EventClass;
import io.spine.server.type.EventEnvelope;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.function.Function;

/**
 * A wrapper for event applier method.
 */
@SuppressWarnings("rawtypes") // Generic params are of no importance in this context.
public final class Applier
        extends AbstractReceptor<Aggregate,
                                 EventMessage,
                                 EventClass,
                                 EventEnvelope,
                                 EmptyClass>
        implements VoidMethod<Aggregate, EventClass, EventEnvelope> {

    /**
     * Creates a new instance to wrap {@code method} on {@code target}.
     *
     * @param method
     *         the applier method
     * @param signature
     *         {@link ParameterSpec} which describes the method
     */
    Applier(Method method, ParameterSpec<EventEnvelope> signature) {
        super(checkNotFiltered(method, "applier"), signature);
    }

    /**
     * Adds {@link AllowImportAttribute} to the set of method attributes.
     */
    @Override
    protected Set<Function<Method, Attribute<?>>> attributeSuppliers() {
        return Sets.union(super.attributeSuppliers(), ImmutableSet.of(AllowImportAttribute::of));
    }

    @Override
    public EventClass messageClass() {
        return EventClass.from(rawMessageClass());
    }

    boolean allowsImport() {
        return attributes().contains(AllowImportAttribute.ALLOW);
    }

    @Override
    protected ArgumentFilter createFilter() {
        return ArgumentFilter.acceptingAll();
    }
}
