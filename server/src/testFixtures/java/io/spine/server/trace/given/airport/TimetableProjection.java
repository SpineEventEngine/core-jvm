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

package io.spine.server.trace.given.airport;

import io.spine.core.Subscribe;
import io.spine.server.projection.Projection;
import io.spine.test.trace.AirportId;
import io.spine.test.trace.FlightCanceled;
import io.spine.test.trace.FlightId;
import io.spine.test.trace.FlightRescheduled;
import io.spine.test.trace.FlightScheduled;
import io.spine.test.trace.Timetable;
import io.spine.test.trace.Timetable.Schedule;

import java.util.List;

import static com.google.common.collect.Lists.newArrayList;
import static io.spine.util.Exceptions.newIllegalStateException;

final class TimetableProjection extends Projection<AirportId, Timetable, Timetable.Builder> {

    @Subscribe
    void on(FlightScheduled event) {
        var schedule = Schedule.newBuilder()
                .setFlight(event.getId())
                .setDestination(event.getTo()
                                     .getId())
                .setScheduledDeparture(event.getScheduledDeparture())
                .build();
        builder().addScheduledFlight(schedule);
    }

    @Subscribe
    void on(FlightRescheduled event) {
        var newDepartureTime = event.getScheduledDeparture();
        findFlight(event.getId())
                .setScheduledDeparture(newDepartureTime);
    }

    @Subscribe
    void on(FlightCanceled event) {
        List<Schedule> schedules = newArrayList(builder().getScheduledFlightList());
        schedules.removeIf(schedule -> schedule.getFlight().equals(event.getId()));
        builder().clearScheduledFlight()
                 .addAllScheduledFlight(schedules);
    }

    private Schedule.Builder findFlight(FlightId id) {
        var schedules = builder().getScheduledFlightBuilderList();
        var flight = schedules.stream()
                .filter(schedule -> schedule.getFlight()
                                            .equals(id))
                .findAny()
                .orElseThrow(() -> newIllegalStateException("Unknown flight %s.", id.getUuid()));
        return flight;
    }
}
