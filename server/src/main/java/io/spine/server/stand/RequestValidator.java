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
import com.google.protobuf.Message;
import io.spine.type.TypeName;
import io.spine.validation.ConstraintViolation;
import io.spine.validation.Validate;
import io.spine.validation.ValidationError;
import io.spine.validation.ViolationText;

import java.util.List;

import static java.lang.String.format;

/**
 * An abstract base of validators for the incoming requests to {@linkplain Stand}.
 *
 * @param <M>
 *         the type of request
 */
abstract class RequestValidator<M extends Message> {

    /**
     * Determines if this request is supported by the system.
     */
    protected abstract boolean isSupported(M request);

    /**
     * Composes an error message for an unsupported request.
     */
    protected abstract String errorMessage(M request);

    /**
     * Checks whether the passed {@code request} is valid:
     *
     * <ol>
     *      <li>as a {@code Message}, according to the constraints set in its Protobuf definition;
     *      <li>meaning it is supported by a target {@code Stand}
     *          and may be passed for the further processing;
     *      <li>according to the {@linkplain #checkOwnRules(Message) type-specific rules}.
     * </ol>
     *
     * @param request
     *         the request {@code Message} to validate
     * @throws InvalidRequestException
     *         if the request violates the constraints or the type-specific rules
     * @throws UnsupportedRequestException
     *         if the request is not supported
     */
    void validate(M request) throws InvalidRequestException, UnsupportedRequestException {
        checkViolations(request, Validate.violationsOf(request));
        if (!isSupported(request)) {
            throw new UnsupportedRequestException(request, errorMessage(request));
        }
        checkViolations(request, checkOwnRules(request));
    }

    /**
     * Validates the request by the type-specific rules.
     *
     * @param request
     *         the request message to validate
     * @return the violated rules, or an empty list if the request satisfies them
     */
    protected ImmutableList<ConstraintViolation> checkOwnRules(M request) {
        return ImmutableList.of();
    }

    /**
     * Throws {@link InvalidRequestException} if the given list of violations is not empty.
     */
    private static void checkViolations(Message request, List<ConstraintViolation> violations) {
        if (violations.isEmpty()) {
            return;
        }
        var validationError = ValidationError.newBuilder()
                .addAllConstraintViolation(violations)
                .build();
        var messageText = format("`%s` message does not satisfy the validation constraints. %s",
                                 TypeName.of(request), ViolationText.ofAll(violations));
        throw new InvalidRequestException(messageText, request, validationError);
    }
}
