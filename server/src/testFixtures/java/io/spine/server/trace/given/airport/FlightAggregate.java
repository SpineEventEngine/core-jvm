/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package io.spine.server.trace.given.airport;

import io.spine.server.aggregate.Aggregate;
import io.spine.server.command.Assign;
import io.spine.test.trace.AirportId;
import io.spine.test.trace.CancelFlight;
import io.spine.test.trace.Flight;
import io.spine.test.trace.FlightCanceled;
import io.spine.test.trace.FlightId;
import io.spine.test.trace.FlightRescheduled;
import io.spine.test.trace.FlightScheduled;
import io.spine.test.trace.RescheduleFlight;
import io.spine.test.trace.ScheduleFlight;

final class FlightAggregate extends Aggregate<FlightId, Flight, Flight.Builder> {

    @Assign
    FlightScheduled handle(ScheduleFlight command) {
        var event = FlightScheduled
                .newBuilder()
                .setId(command.getId())
                .setFrom(command.getFrom())
                .setTo(command.getTo())
                .setScheduledDeparture(command.getScheduledDeparture())
                .setScheduledArrival(command.getScheduledArrival())
                .build();
        builder().setFrom(event.getFrom())
                 .setTo(event.getTo())
                 .setScheduledDeparture(event.getScheduledDeparture())
                 .setScheduledArrival(event.getScheduledArrival());
        return event;
    }

    @Assign
    FlightRescheduled handle(RescheduleFlight command) {
        var event = FlightRescheduled
                .newBuilder()
                .setId(command.getId())
                .setScheduledDeparture(command.getScheduledDeparture())
                .setScheduledArrival(command.getScheduledArrival())
                .setFrom(departureAirport())
                .build();
        builder().setScheduledDeparture(event.getScheduledDeparture())
                 .setScheduledArrival(event.getScheduledArrival());
        return event;
    }

    @Assign
    FlightCanceled handle(CancelFlight command) {
        var event = FlightCanceled.newBuilder()
                             .setId(command.getId())
                             .setFrom(departureAirport())
                             .build();
        setDeleted(true);
        return event;
    }

    private AirportId departureAirport() {
        return state().getFrom()
                      .getId();
    }
}
