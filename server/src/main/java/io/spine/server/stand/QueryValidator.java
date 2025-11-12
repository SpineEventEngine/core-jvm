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
package io.spine.server.stand;

import com.google.protobuf.ProtocolMessageEnum;
import com.google.protobuf.Value;
import io.spine.base.Error;
import io.spine.client.Query;
import io.spine.client.QueryValidationError;
import org.jspecify.annotations.Nullable;

import static io.spine.client.QueryValidationError.INVALID_QUERY;
import static io.spine.client.QueryValidationError.UNSUPPORTED_QUERY_TARGET;
import static io.spine.option.EntityOption.Visibility.QUERY;
import static java.lang.String.format;

/**
 * Validates the {@link Query} instances submitted to {@link Stand}.
 */
final class QueryValidator extends AbstractTargetValidator<Query> {

    QueryValidator(TypeRegistry typeRegistry) {
        super(QUERY, typeRegistry);
    }

    @Override
    protected @Nullable Error checkOwnRules(Query request) {
        var format = request.getFormat();
        var limit = format.getLimit();
        if (limit > 0) {
            var orderByMissing = format.getOrderByCount() == 0;
            if (orderByMissing) {
                var limitValue = Value.newBuilder()
                        .setNumberValue(limit)
                        .build();
                @SuppressWarnings("DuplicateStringLiteralInspection") // "limit" is used in tests.
                var error = Error.newBuilder()
                        .setType(QueryValidationError.class.getSimpleName())
                        .setCode(INVALID_QUERY.getNumber())
                        .setMessage("Query limit cannot be set without ordering.")
                        .putAttributes("limit", limitValue)
                        .build();
                return error;
            }
        }
        return null;
    }

    @Override
    protected QueryValidationError invalidMessageErrorCode() {
        return INVALID_QUERY;
    }

    @Override
    protected ProtocolMessageEnum unsupportedTargetErrorCode() {
        return UNSUPPORTED_QUERY_TARGET;
    }

    @Override
    protected InvalidQueryException
    invalidMessageException(String exceptionMsg, Query request, Error error) {
        return new InvalidQueryException(exceptionMsg, request, error);
    }

    @Override
    protected boolean isSupported(Query request) {
        var target = request.getTarget();
        return typeRegistryContains(target) && visibilitySufficient(target);
    }

    @Override
    protected InvalidRequestException unsupportedException(Query request, Error error) {
        var message = errorMessage(request);
        return new InvalidQueryException(message, request, error);
    }

    @Override
    protected String errorMessage(Query request) {
        var targetType = getTypeOf(request.getTarget());
        return format("The query target type is not supported: %s", targetType.typeName());
    }
}
