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

import com.google.common.collect.ImmutableList;
import com.google.protobuf.UInt32Value;
import io.spine.base.FieldPath;
import io.spine.client.Query;
import io.spine.string.TemplateString;
import io.spine.type.TypeName;
import io.spine.validation.ConstraintViolation;

import static io.spine.option.EntityOption.Visibility.QUERY;
import static io.spine.protobuf.AnyPacker.pack;
import static java.lang.String.format;

/**
 * Validates the {@link Query} instances submitted to {@link Stand}.
 */
final class QueryValidator extends AbstractTargetValidator<Query> {

    QueryValidator(TypeRegistry typeRegistry) {
        super(QUERY, typeRegistry);
    }

    /**
     * {@inheritDoc}
     *
     * <p>A query cannot have a limit without ordering.
     */
    @Override
    protected ImmutableList<ConstraintViolation> checkOwnRules(Query request) {
        var format = request.getFormat();
        var limit = format.getLimit();
        var orderByMissing = format.getOrderByCount() == 0;
        if (limit <= 0 || !orderByMissing) {
            return ImmutableList.of();
        }
        var limitPath = FieldPath.newBuilder()
                .addFieldName("format")
                .addFieldName("limit");
        var violation = ConstraintViolation.newBuilder()
                .setMessage(TemplateString.newBuilder()
                                    .setWithPlaceholders(
                                            "Query limit cannot be set without ordering."))
                .setTypeName(TypeName.of(request).value())
                .setFieldPath(limitPath)
                .setFieldValue(pack(UInt32Value.of(limit)))
                .build();
        return ImmutableList.of(violation);
    }

    @Override
    protected boolean isSupported(Query request) {
        var target = request.getTarget();
        return typeRegistryContains(target) && visibilitySufficient(target);
    }

    @Override
    protected String errorMessage(Query request) {
        var targetType = getTypeOf(request.getTarget());
        return format("The query target type is not supported: %s", targetType.typeName());
    }
}
