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

package io.spine.server.delivery.given;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.errorprone.annotations.OverridingMethodsMustInvokeSuper;
import io.spine.core.Subscribe;
import io.spine.logging.WithLogging;
import io.spine.server.projection.Projection;
import io.spine.server.projection.ProjectionRepository;
import io.spine.server.route.EventRouting;
import io.spine.test.delivery.ConsecutiveNumberView;
import io.spine.test.delivery.NegativeNumberEmitted;
import io.spine.test.delivery.PositiveNumberEmitted;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

import static io.spine.util.Exceptions.newIllegalArgumentException;
import static java.lang.Math.abs;
import static java.lang.String.format;

/**
 * A projection which expects the events to be delivered with the consecutive positive
 * or consecutive negative number data.
 *
 * <p>In the {@linkplain #usePositives() positives-only} mode the projection state stores {@code 0},
 * expecting each following event to bring the next positive number increased by one.
 *
 * <p>In the {@linkplain #useNegatives() negatives-only} mode the projection state also
 * stores {@code 0} at the beginning, but it expects the incoming events to bring the next negative
 * number decreased by one.
 *
 * <p>In this manner it is possible to check that the ordering of the delivered events is the same
 * as expected.
 */
public class ConsecutiveProjection
        extends Projection<String, ConsecutiveNumberView, ConsecutiveNumberView.Builder>
        implements WithLogging {

    private static UsageMode mode = UsageMode.POSITIVES_ONLY;

    public static void useNegatives() {
        mode = UsageMode.NEGATIVES_ONLY;
    }

    public static void usePositives() {
        mode = UsageMode.POSITIVES_ONLY;
    }

    @Subscribe
    void on(PositiveNumberEmitted event) {
        updateState(event.getId(), event.getValue());
    }

    @Subscribe
    void on(NegativeNumberEmitted event) {
        updateState(event.getId(), event.getValue());
    }

    @SuppressWarnings("FloggerLogString")
    private void updateState(String id, int newValue) {
        builder().setId(id);

        mode.validate(newValue);

        var lastValue = state().getLastValue();
        var difference = abs(newValue) - abs(lastValue);
        if (difference != 1) {
            logger().atWarning().log(() -> format(
                    "`ConsecutiveNumberProjection` with ID `%s` got wrong value." +
                    " Current value is %d, but got `%d`.", id, lastValue, newValue));
        } else {
            builder().setLastValue(newValue);
        }
    }

    @SuppressWarnings("Immutable")  // effectively immutable.
    public enum UsageMode {
        POSITIVES_ONLY((value) -> value > 0),
        NEGATIVES_ONLY((value) -> value < 0);

        private final Function<Integer, Boolean> validator;

        UsageMode(Function<Integer, Boolean> validator) {
            this.validator = validator;
        }

        void validate(int incoming) {
            var valid = validator.apply(incoming);
            if (!valid) {
                throw newIllegalArgumentException(
                        "The mode `%s` does not accept the supplied `%d` value.",
                        this.toString(), incoming);
            }
        }
    }

    /**
     * A repository of {@linkplain ConsecutiveProjection} instances.
     *
     * <p>Allows excluding some instances from the event routing.
     */
    public static final class Repo
            extends ProjectionRepository<String, ConsecutiveProjection, ConsecutiveNumberView> {

        private final Set<String> excludedTargets = new HashSet<>();

        @OverridingMethodsMustInvokeSuper
        @Override
        protected void setupEventRouting(EventRouting<String> routing) {
            super.setupEventRouting(routing);

            routing.route(PositiveNumberEmitted.class, (e, c) ->
                    mode == UsageMode.POSITIVES_ONLY
                    ? withoutExcluded(ImmutableSet.of(e.getId()))
                    : ImmutableSet.of());

            routing.route(NegativeNumberEmitted.class, (e, c) ->
                    mode == UsageMode.NEGATIVES_ONLY
                    ? withoutExcluded(ImmutableSet.of(e.getId()))
                    : ImmutableSet.of()
            );
        }

        private Set<String> withoutExcluded(Set<String> original) {
            return Sets.difference(original, excludedTargets);
        }

        public void excludeFromRouting(String targetId) {
            excludedTargets.add(targetId);
        }
    }
}
