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

package io.spine.server.event.model;

import com.google.common.collect.ImmutableSet;
import io.spine.server.route.EventRouting;
import io.spine.server.type.EventClass;

/**
 * Describes a class of objects that receive events.
 *
 * <p>A class can declare methods to receive events from the same Bounded Context (“domestic”
 * events), or events originated in another Bounded Context (“external” events).
 */
public interface EventReceiverClass {

    /**
     * Obtains a set of event classes which this class receives.
     *
     * <p>The returned set contains both domestic and external event classes.
     *
     * <p>For only the domestic events, please see {@link #domesticEvents()}.
     *
     * <p>For only the external events, please see {@link #externalEvents()}.
     */
    ImmutableSet<EventClass> events();

    /**
     * Obtains a set of domestic events which this class receives.
     */
    ImmutableSet<EventClass> domesticEvents();

    /**
     * Obtains a set of external events which this class receives.
     *
     * <p>External events are those that are delivered to the {@code BoundedContext}
     * to which this class belongs from outside.
     */
    ImmutableSet<EventClass> externalEvents();
}
