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

package io.spine.server.bus;

import io.spine.server.type.MessageEnvelope;
import io.spine.validation.ValidationError;

import java.util.Optional;

/**
 * An interface defining the validator for a {@link MessageEnvelope}.
 *
 * @param <E> the type of the {@link MessageEnvelope} to validate
 */
public interface EnvelopeValidator<E extends MessageEnvelope<?, ?, ?>> {

    /**
     * Validates the given {@link MessageEnvelope} by some specific rules.
     *
     * @param envelope the envelope to validate
     * @return {@link Optional#empty()} if the envelope passes the validation, or
     *         the constraints that the envelope violates
     */
    Optional<ValidationError> validate(E envelope);
}
