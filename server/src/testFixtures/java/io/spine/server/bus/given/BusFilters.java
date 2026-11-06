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

package io.spine.server.bus.given;

import io.spine.base.Error;
import io.spine.core.Ack;
import io.spine.server.bus.BusFilter;
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
}
