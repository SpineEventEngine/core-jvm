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

package io.spine.server.aggregate.given.thermometer;

import io.spine.server.aggregate.AggregateRepository;
import io.spine.server.aggregate.given.thermometer.event.TemperatureChanged;
import io.spine.server.route.EventRouting;

import static io.spine.util.Preconditions2.checkNotDefaultArg;

/**
 * A {@link SafeThermometer thermometer} repository.
 */
public final class SafeThermometerRepo
        extends AggregateRepository<ThermometerId, SafeThermometer, Thermometer> {

    private final ThermometerId thermometer;

    /**
     * Creates a new repository for the {@code thermometer}.
     */
    public SafeThermometerRepo(ThermometerId thermometer) {
        this.thermometer = checkNotDefaultArg(thermometer);
    }

    @Override
    protected void setupEventRouting(EventRouting<ThermometerId> routing) {
        routing.unicast(TemperatureChanged.class, (e) -> thermometer);
    }
}
