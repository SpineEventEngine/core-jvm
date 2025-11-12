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

package io.spine.server.model;

import com.google.errorprone.annotations.Immutable;
import io.spine.server.type.MessageEnvelope;

/**
 * A specification of {@linkplain Receptor receptor} parameters, specific for various
 * {@code HandlerMethod} implementations.
 *
 * <p>As long as handler methods are passed with a {@linkplain MessageEnvelope Message envelope},
 * the specification also transfers knowledge on how to extract the designed argument values
 * from the given envelope for the method with this parameter spec.
 *
 * <p>Implementing classes are required to be {@code enumeration}s.
 *
 * @param <E>
 *         the type of message envelope
 */
@Immutable
public interface ParameterSpec<E extends MessageEnvelope<?, ?, ?>> {

    /**
     * Tells if this specification of parameters matches the passed parameters.
     */
    boolean matches(MethodParams params);

    /**
     * Extracts the values to be used during the invocation of the method with
     * this parameter specification.
     *
     * @param envelope the envelope to use as a source
     * @return the values to use during the method invocation
     */
    ExtractedArguments extractArguments(E envelope);
}
