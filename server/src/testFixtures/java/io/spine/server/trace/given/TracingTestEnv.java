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

package io.spine.server.trace.given;

import io.spine.test.trace.Airport;
import io.spine.test.trace.AirportId;
import io.spine.test.trace.Boarding;
import io.spine.test.trace.CancelFlight;
import io.spine.test.trace.Flight;
import io.spine.test.trace.FlightId;
import io.spine.test.trace.RescheduleFlight;
import io.spine.test.trace.ScheduleFlight;
import io.spine.test.trace.Timetable;
import io.spine.time.LocalDateTimes;
import io.spine.time.LocalDates;
import io.spine.time.LocalTimes;
import io.spine.time.Month;
import io.spine.time.ZoneId;
import io.spine.time.ZoneIds;
import io.spine.time.ZonedDateTimes;
import io.spine.type.TypeUrl;

/**
 * The data factory for tests of the Spine Trace API.
 */
public final class TracingTestEnv {

    public static final AirportId FROM = AirportId
            .newBuilder()
            .setCode("KBP")
            .build();

    private static final Airport FROM_AIRPORT = Airport
            .newBuilder()
            .setId(FROM)
            .setCity("Kyiv")
            .setCountry("Ukraine")
            .build();

    private static final AirportId TO = AirportId
            .newBuilder()
            .setCode("HRK")
            .build();

    private static final Airport TO_AIRPORT = Airport.
            newBuilder()
            .setId(TO)
            .setCity("Kharkiv")
            .setCountry("Ukraine")
            .build();

    private static final ZoneId ZONE = ZoneIds.of(java.time.ZoneId.systemDefault());
    public static final FlightId FLIGHT = FlightId.generate();
    public static final TypeUrl FLIGHT_TYPE = TypeUrl.of(Flight.class);
    public static final TypeUrl BOARDING_TYPE = TypeUrl.of(Boarding.class);
    public static final TypeUrl TIMETABLE_TYPE = TypeUrl.of(Timetable.class);

    /**
     * Prevents the utility class instantiation.
     */
    private TracingTestEnv() {
    }

    public static ScheduleFlight scheduleFlight() {
        var localDepartureTime = LocalDateTimes.of(
                LocalDates.of(2019, Month.JUNE, 20),
                LocalTimes.of(7, 30, 0)
        );
        var builder = localDepartureTime.toBuilder();
        builder.getTimeBuilder()
               .setHour(8);
        var localArrivalTime = builder.build();
        var departureTime = ZonedDateTimes.of(localDepartureTime, ZONE);
        var arrivalTime = ZonedDateTimes.of(localArrivalTime, ZONE);
        return ScheduleFlight.newBuilder()
                .setFrom(FROM_AIRPORT)
                .setTo(TO_AIRPORT)
                .setId(FLIGHT)
                .setScheduledDeparture(departureTime)
                .setScheduledArrival(arrivalTime)
                .build();
    }

    /**
     * Creates a command moving the {@linkplain #scheduleFlight() scheduled} flight
     * two hours later.
     */
    public static RescheduleFlight rescheduleFlight() {
        var date = LocalDates.of(2019, Month.JUNE, 20);
        var localDepartureTime = LocalDateTimes.of(date, LocalTimes.of(9, 30, 0));
        var localArrivalTime = LocalDateTimes.of(date, LocalTimes.of(10, 30, 0));
        return RescheduleFlight.newBuilder()
                .setId(FLIGHT)
                .setScheduledDeparture(ZonedDateTimes.of(localDepartureTime, ZONE))
                .setScheduledArrival(ZonedDateTimes.of(localArrivalTime, ZONE))
                .build();
    }

    public static CancelFlight cancelFlight() {
        return CancelFlight.newBuilder()
                .setId(FLIGHT)
                .build();
    }
}
