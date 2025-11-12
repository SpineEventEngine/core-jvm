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

package io.spine.server.bus.given;

import io.spine.base.Error;
import io.spine.base.RejectionThrowable;
import io.spine.core.Ack;
import io.spine.server.bus.BusFilter;
import io.spine.server.commandbus.CommandFilter;
import io.spine.server.type.CommandEnvelope;

import java.util.Optional;

public final class BusFilters {

    /**
     * Prevents instantiation of this test env class.
     */
    private BusFilters() {
    }

    public static final class Accepting implements BusFilter<CommandEnvelope> {

        @Override
        public Optional<Ack> filter(CommandEnvelope envelope) {
            return letPass();
        }
    }

    public static final class RejectingWithOk implements BusFilter<CommandEnvelope> {

        @Override
        public Optional<Ack> filter(CommandEnvelope envelope) {
            return reject(envelope);
        }
    }

    public static final class RejectingWithError implements BusFilter<CommandEnvelope> {

        private final Error error;

        public RejectingWithError(Error error) {
            this.error = error;
        }

        @Override
        public Optional<Ack> filter(CommandEnvelope envelope) {
            return reject(envelope, error);
        }
    }

    public static final class RejectingWithRejectionThrowable implements CommandFilter {

        private final RejectionThrowable rejection;

        public RejectingWithRejectionThrowable(RejectionThrowable rejection) {
            this.rejection = rejection;
        }

        @Override
        public Optional<Ack> filter(CommandEnvelope envelope) {
            return reject(envelope, rejection);
        }
    }

}
