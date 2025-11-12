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

package io.spine.server.tuple.given;

import io.spine.server.tuple.Quintet;
import io.spine.test.tuple.quintet.InstrumentNumber;
import io.spine.test.tuple.quintet.Viola;
import io.spine.test.tuple.quintet.Violin;
import io.spine.test.tuple.quintet.ViolinCello;

import static io.spine.server.tuple.given.QuintetTestEnv.InstrumentFactory.newViola;
import static io.spine.server.tuple.given.QuintetTestEnv.InstrumentFactory.newViolin;
import static io.spine.server.tuple.given.QuintetTestEnv.InstrumentFactory.newViolinCello;
import static io.spine.validate.Validate.check;

public class QuintetTestEnv {

    /** Prevents instantiation of this utility class. */
    private QuintetTestEnv() {
    }

    /**
     * Creates typical <a href="https://en.wikipedia.org/wiki/String_quintet">String quintets</a>.
     */
    public static class QuintetFactory {

        public static final InstrumentNumber NUM_1 = InstrumentNumber
                .newBuilder()
                .setValue(1)
                .build();

        public static final InstrumentNumber NUM_2 = InstrumentNumber
                .newBuilder()
                .setValue(2)
                .build();

        /** Prevents instantiation of this utility class. */
        private QuintetFactory() {
        }

        public static Quintet<Violin, Violin, Viola, ViolinCello, ViolinCello> newCelloQuintet() {
            return Quintet.of(newViolin(NUM_1),
                              newViolin(NUM_2),
                              newViola(),
                              newViolinCello(NUM_1),
                              newViolinCello(NUM_2));
        }

        public static Quintet<Violin, Violin, Viola, Viola, ViolinCello> newViolaQuintet() {
            return Quintet.of(newViolin(NUM_1),
                              newViolin(NUM_2),
                              newViola(NUM_1),
                              newViola(NUM_2),
                              newViolinCello());
        }
    }

    /**
     * Creates instruments.
     */
    public static class InstrumentFactory {

        /** Prevents instantiation of this utility class. */
        private InstrumentFactory() {
        }

        public static Violin newViolin(InstrumentNumber number) {
            var result = Violin.newBuilder()
                    .setNumber(number)
                    .build();
            check(result);
            return result;
        }

        public static Viola newViola() {
            var result = Viola.newBuilder()
                    .setSingle(true)
                    .build();
            check(result);
            return result;
        }

        public static Viola newViola(InstrumentNumber number) {
            var result = Viola.newBuilder()
                    .setNumber(number)
                    .build();
            check(result);
            return result;
        }

        public static ViolinCello newViolinCello(InstrumentNumber number) {
            var result = ViolinCello.newBuilder()
                    .setNumber(number)
                    .build();
            check(result);
            return result;
        }

        public static ViolinCello newViolinCello() {
            var result = ViolinCello.newBuilder()
                    .setSingle(true)
                    .build();
            check(result);
            return result;
        }
    }
}
