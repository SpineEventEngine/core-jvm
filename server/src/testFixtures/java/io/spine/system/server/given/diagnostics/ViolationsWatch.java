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

package io.spine.system.server.given.diagnostics;

import com.google.errorprone.annotations.OverridingMethodsMustInvokeSuper;
import com.google.protobuf.StringValue;
import io.spine.core.Subscribe;
import io.spine.core.Where;
import io.spine.server.projection.Projection;
import io.spine.server.projection.ProjectionRepository;
import io.spine.server.route.EventRouting;
import io.spine.system.server.ConstraintViolated;
import io.spine.system.server.test.InvalidText;
import io.spine.system.server.test.WatchId;

import static com.google.common.base.Preconditions.checkArgument;
import static io.spine.protobuf.AnyPacker.unpack;
import static io.spine.server.route.EventRoute.withId;
import static io.spine.validate.TemplateStrings.format;

/**
 * A projection which stores contents of invalid {@link TextValidated} events.
 */
public final class ViolationsWatch extends Projection<WatchId, InvalidText, InvalidText.Builder> {

    public static final WatchId DEFAULT = WatchId.generate();
    private static final String LAST_MESSAGE_TYPE_PATH = "last_message.type_url";

    @Subscribe
    void onInvalidText(
            @Where(field = LAST_MESSAGE_TYPE_PATH,
                   equals = "type.spine.io/spine.system.server.test.TextValidated")
            ConstraintViolated event) {
        var violations = event.getViolationList();
        checkArgument(violations.size() == 1);
        var violation = violations.get(0);
        var value = unpack(violation.getFieldValue());
        checkArgument(value instanceof StringValue);
        @SuppressWarnings("OverlyStrongTypeCast") // OrBuilder
        var stringValue = ((StringValue) value).getValue();
        builder().setInvalidText(stringValue);
    }

    @Subscribe
    void onInvalidVerification(
            @Where(field = LAST_MESSAGE_TYPE_PATH,
                   equals = "type.spine.io/spine.system.server.test.StartVerification")
            ConstraintViolated event) {
        var violations = event.getViolationList();
        checkArgument(violations.size() == 1);
        var violation = violations.get(0);
        builder().setErrorMessage(format(violation.getMessage()));
    }

    public static final class Repository
            extends ProjectionRepository<WatchId, ViolationsWatch, InvalidText> {

        @OverridingMethodsMustInvokeSuper
        @Override
        protected void setupEventRouting(EventRouting<WatchId> routing) {
            super.setupEventRouting(routing);
            routing.route(ConstraintViolated.class, (m, c) -> withId(DEFAULT));
        }
    }
}
