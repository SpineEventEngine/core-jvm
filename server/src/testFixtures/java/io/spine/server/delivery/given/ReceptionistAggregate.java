/*
 * Copyright 2026, TeamDev. All rights reserved.
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

import io.spine.server.aggregate.Aggregate;
import io.spine.server.aggregate.Apply;
import io.spine.server.command.Assign;
import io.spine.test.delivery.Receptionist;
import io.spine.test.delivery.command.TurnConditionerOff;
import io.spine.test.delivery.command.TurnConditionerOn;
import io.spine.test.delivery.event.ConditionerTurnedOff;
import io.spine.test.delivery.event.ConditionerTurnedOn;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static io.spine.util.Exceptions.newIllegalStateException;

public final class ReceptionistAggregate
        extends Aggregate<String, Receptionist, Receptionist.Builder> {

    public static final String FAILURE_MESSAGE = "Receptionist failed to apply an event.";

    private static boolean failInAppliers = false;

    /**
     * The number of remaining {@linkplain TransientFailure transient} failures per aggregate ID.
     *
     * <p>A negative value means "fail every time". Used to simulate an eventually-consistent
     * storage that recovers after a few reads.
     */
    private static final Map<String, Integer> transientFailures = new ConcurrentHashMap<>();

    /**
     * The IDs of aggregates that should fail with a non-retryable
     * {@link IllegalStateException}.
     */
    private static final Set<String> unrelatedFailures = ConcurrentHashMap.newKeySet();

    /**
     * The number of times an applier was invoked per aggregate ID, including the invocations
     * that failed. Used to assert how many delivery attempts were made.
     */
    private static final Map<String, Integer> applierInvocations = new ConcurrentHashMap<>();

    @Assign
    ConditionerTurnedOn handler(TurnConditionerOn cmd) {
        return ConditionerTurnedOn.newBuilder()
                .setReceptionist(id())
                .build();
    }

    @Apply
    @SuppressWarnings("ResultOfMethodCallIgnored" /* Using Proto builder. */)
    private void apply(ConditionerTurnedOn event) {
        maybeFail(id());
        var newValue = builder().getHowManyCmdsHandled() + 1;
        builder().setId(event.getReceptionist())
                 .setHowManyCmdsHandled(newValue);
    }

    @Assign
    ConditionerTurnedOff handler(TurnConditionerOff cmd) {
        return ConditionerTurnedOff.newBuilder()
                .setReceptionist(id())
                .build();
    }

    @Apply
    @SuppressWarnings("ResultOfMethodCallIgnored" /* Using Proto builder. */)
    private void apply(ConditionerTurnedOff event) {
        maybeFail(id());
        var newValue = builder().getHowManyCmdsHandled() + 1;
        builder().setId(event.getReceptionist())
                 .setHowManyCmdsHandled(newValue);
    }

    private static void maybeFail(String id) {
        applierInvocations.merge(id, 1, Integer::sum);
        var remaining = transientFailures.getOrDefault(id, 0);
        if (remaining != 0) {
            if (remaining > 0) {
                transientFailures.put(id, remaining - 1);
            }
            throw new TransientFailure();
        }
        if (unrelatedFailures.contains(id) || failInAppliers) {
            throw newIllegalStateException(FAILURE_MESSAGE);
        }
    }

    public static void makeApplierFail() {
        failInAppliers = true;
    }

    public static void makeApplierPass() {
        failInAppliers = false;
    }

    /**
     * Makes the aggregate with the given ID fail with a {@link TransientFailure} the given number
     * of times, and then succeed.
     */
    public static void failTransiently(String id, int times) {
        transientFailures.put(id, times);
    }

    /**
     * Makes the aggregate with the given ID always fail with a {@link TransientFailure}.
     */
    public static void failAlwaysTransiently(String id) {
        transientFailures.put(id, -1);
    }

    /**
     * Makes the aggregate with the given ID fail with a non-retryable
     * {@link IllegalStateException}.
     */
    public static void failUnrelated(String id) {
        unrelatedFailures.add(id);
    }

    /**
     * Returns how many times an applier was invoked for the aggregate with the given ID,
     * including the invocations that threw.
     */
    public static int applierInvocations(String id) {
        return applierInvocations.getOrDefault(id, 0);
    }

    /**
     * Clears all the failure settings and the recorded applier invocations.
     */
    public static void resetFailures() {
        failInAppliers = false;
        transientFailures.clear();
        unrelatedFailures.clear();
        applierInvocations.clear();
    }
}
