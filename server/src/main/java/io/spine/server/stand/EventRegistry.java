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

package io.spine.server.stand;

import com.google.common.collect.ImmutableSet;
import io.spine.server.Closeable;
import io.spine.server.EventProducer;
import io.spine.server.entity.Repository;
import io.spine.server.type.EventClass;
import io.spine.type.TypeUrl;

/**
 * Manages the event types exposed by the associated instance of {@link Stand}.
 */
interface EventRegistry extends Closeable {

    /**
     * Registers the repository as an event producer.
     */
    void register(Repository<?, ?> repository);

    /**
     * Registers the event producer.
     */
    void register(EventProducer producer);

    /**
     * Retrieves all event {@linkplain TypeUrl types} stored in the registry.
     */
    ImmutableSet<TypeUrl> typeSet();

    /**
     * Checks if this registry instance contains the specified event type.
     */
    boolean contains(TypeUrl type);

    /**
     * Retrieves all stored event types as {@link EventClass} instances.
     */
    ImmutableSet<EventClass> eventClasses();
}
