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

package io.spine.server.projection.given.cls;

import io.spine.core.Subscribe;
import io.spine.core.Where;
import io.spine.server.projection.Projection;
import io.spine.server.projection.given.SavedString;
import io.spine.test.projection.event.StringImported;

/**
 * The projection class which is subscribed to events that are filtered by their field values.
 *
 * @see io.spine.server.projection.model.ProjectionClassTest.FilteringSubscription
 */
public final class FilteringProjection
        extends Projection<String, SavedString, SavedString.Builder> {

    private static final String VALUE_FIELD_PATH = "value";

    public static final String SET_A = "SET A";
    public static final String SET_B = "SET B";

    public static final String VALUE_A = "A";
    public static final String VALUE_B = "B";

    private FilteringProjection(String id) {
        super(id);
    }

    /** This method does not use the value passed in the event. */
    @Subscribe
    void onReserved(@Where(field = VALUE_FIELD_PATH, equals = SET_A) StringImported event) {
        builder().setValue(VALUE_A);
    }

    /** This method does not use the value passed in the event either. */
    @Subscribe
    void onSecret(@Where(field = VALUE_FIELD_PATH, equals = SET_B) StringImported event) {
        builder().setValue(VALUE_B);
    }

    /** This method uses the value passed in the event. */
    @Subscribe
    void on(StringImported event) {
        builder().setValue(event.getValue());
    }
}
