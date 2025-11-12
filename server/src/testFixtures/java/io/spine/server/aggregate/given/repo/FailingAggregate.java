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

package io.spine.server.aggregate.given.repo;

import io.spine.base.EventMessage;
import io.spine.base.Identifier;
import io.spine.base.Time;
import io.spine.server.aggregate.Aggregate;
import io.spine.server.aggregate.Apply;
import io.spine.server.command.Assign;
import io.spine.server.entity.rejection.CannotModifyArchivedEntity;
import io.spine.server.event.React;
import io.spine.server.test.shared.LongIdAggregate;
import io.spine.test.aggregate.number.DoNothing;
import io.spine.test.aggregate.number.FloatEncountered;
import io.spine.test.aggregate.number.NumberPassed;
import io.spine.test.aggregate.number.RejectNegativeInt;
import io.spine.test.aggregate.number.RejectNegativeLong;
import io.spine.time.TimestampTemporal;

import java.time.temporal.ChronoField;
import java.util.List;

import static java.util.Collections.emptyList;

/**
 * The aggregate which throws {@link IllegalArgumentException} in response to negative numbers.
 *
 * <p>Normally aggregates should reject commands via rejections. This class does not do so,
 * because it is a test environment for checking how {@code AggregateRepository} handles errors.
 *
 * @see FailingAggregateRepository
 */
class FailingAggregate extends Aggregate<Long, LongIdAggregate, LongIdAggregate.Builder> {

    @SuppressWarnings("NumericCastThatLosesPrecision") // Int. part as ID.
    static long toId(FloatEncountered message) {
        var floatValue = message.getNumber();
        return (long) Math.abs(floatValue);
    }

    /** Rejects a negative value via command rejection. */
    @Assign
    NumberPassed on(RejectNegativeInt value) {
        if (value.getNumber() < 0) {
            throw new IllegalArgumentException("Negative value passed");
        }
        return now();
    }

    /** Rejects a negative value via command rejection. */
    @Assign
    NumberPassed on(RejectNegativeLong value) throws CannotModifyArchivedEntity {
        if (value.getNumber() < 0L) {
            throw CannotModifyArchivedEntity
                    .newBuilder()
                    .setEntityId(Identifier.pack(id()))
                    .build();
        }
        return now();
    }

    /** Invalid command handler, which does not produce events. */
    @Assign
    List<EventMessage> on(DoNothing value) {
        return emptyList();
    }

    @React
    NumberPassed on(FloatEncountered value) {
        var floatValue = value.getNumber();
        if (floatValue < 0) {
            var longValue = toId(value);
            // Complain only if the passed value represents ID of this aggregate.
            // This would allow other aggregates react on this message.
            if (longValue == id()) {
                throw new IllegalArgumentException("Negative floating point value passed");
            }
        }
        return now();
    }

    @Apply
    private void apply(NumberPassed event) {
        var whichSecond = TimestampTemporal
                .from(event.getWhen())
                .toInstant()
                .get(ChronoField.MILLI_OF_SECOND);
        builder().setValue(builder().getValue() + whichSecond);
    }

    private static NumberPassed now() {
        var time = Time.currentTime();
        return NumberPassed.newBuilder()
                .setWhen(time)
                .build();
    }
}
