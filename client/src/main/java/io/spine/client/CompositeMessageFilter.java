/*
 * Copyright 2022, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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

package io.spine.client;

import com.google.protobuf.Message;
import io.spine.client.CompositeFilter.CompositeOperator;

import java.util.List;
import java.util.function.Predicate;

import static io.spine.util.Exceptions.newIllegalArgumentException;

/**
 * A message filter that is an {@linkplain #operator() operation} on other
 * message {@linkplain #filters() filters}.
 *
 * @param <M>
 *         the type of the filtered messages
 */
interface CompositeMessageFilter<M extends Message> extends MessageFilter<M> {

    /**
     * Obtains message filters included into this composite message filter.
     */
    List<MessageFilter<M>> filters();

    /**
     * Obtains the operator that will be used for combining enclosed message filters.
     *
     * @see CompositeOperator
     */
    CompositeOperator operator();

    /**
     * Verifies if this filter passed the message.
     */
    @Override
    @SuppressWarnings("EnumSwitchStatementWhichMissesCases") // OK for Proto enum.
    default boolean test(M message) {
        var filters = filters().stream();
        var operator = operator();
        Predicate<MessageFilter<M>> passesFilter = f -> f.test(message);
        return switch (operator) {
            case ALL -> filters.allMatch(passesFilter);
            case EITHER -> filters.anyMatch(passesFilter);
            default -> throw newIllegalArgumentException(
                    "Unknown composite filter operator `%s`.", operator);
        };
    }
}
