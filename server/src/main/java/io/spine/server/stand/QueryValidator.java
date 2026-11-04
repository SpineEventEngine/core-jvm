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
                        .setType(QueryValidationError.getDescriptor().getFullName())
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
