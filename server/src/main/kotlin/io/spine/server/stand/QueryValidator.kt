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

package io.spine.server.stand

import com.google.common.collect.ImmutableList
import com.google.protobuf.UInt32Value
import io.spine.base.FieldPath
import io.spine.client.Query
import io.spine.client.ResponseFormat
import io.spine.option.EntityOption.Visibility.QUERY
import io.spine.protobuf.pack
import io.spine.string.TemplateString
import io.spine.type.TypeName
import io.spine.validation.ConstraintViolation

/**
 * Validates the [Query] instances submitted to [Stand].
 */
internal class QueryValidator(typeRegistry: TypeRegistry) :
    AbstractTargetValidator<Query>(QUERY, typeRegistry) {

    /**
     * Validates the request by the type-specific rules.
     *
     * A query cannot have a limit without ordering.
     *
     * @param request The request message to validate.
     * @return The violated rules, or an empty list if the request satisfies them.
     */
    override fun checkOwnRules(request: Query): ImmutableList<ConstraintViolation> {
        val format = request.format
        val limit = format.limit
        val orderByMissing = format.orderByCount == 0
        if (limit <= 0 || !orderByMissing) {
            return ImmutableList.of()
        }
        val violation = ConstraintViolation.newBuilder()
            .setMessage(
                TemplateString.newBuilder()
                    .setWithPlaceholders("Query limit cannot be set without ordering.")
            )
            .setTypeName(TypeName.of(request).value())
            .setFieldPath(limitPath)
            .setFieldValue(UInt32Value.of(limit).pack())
            .build()
        return ImmutableList.of(violation)
    }

    override fun isSupported(request: Query): Boolean {
        val target = request.target
        return typeRegistryContains(target) && visibilitySufficient(target)
    }

    override fun errorMessage(request: Query): String {
        val targetType = getTypeOf(request.target)
        return "The query target type is not supported: ${targetType.typeName()}"
    }

    private companion object {

        /**
         * The path to the limit of the query results, `format.limit`.
         */
        val limitPath: FieldPath by lazy {
            FieldPath.newBuilder()
                .addFieldName(
                    Query.getDescriptor()
                        .findFieldByNumber(Query.FORMAT_FIELD_NUMBER)
                        .name
                )
                .addFieldName(
                    ResponseFormat.getDescriptor()
                        .findFieldByNumber(ResponseFormat.LIMIT_FIELD_NUMBER)
                        .name
                )
                .build()
        }
    }
}
