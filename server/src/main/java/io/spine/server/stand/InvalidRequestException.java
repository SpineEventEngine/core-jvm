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

import com.google.protobuf.Message;
import io.spine.validation.ValidationError;

import java.io.Serial;

/**
 * An exception thrown when a request submitted to the {@linkplain Stand} fails validation.
 *
 * @see UnsupportedRequestException
 */
public final class InvalidRequestException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 0L;

    private final Message request;
    private final ValidationError validationError;

    /**
     * Creates a new instance.
     *
     * @param messageText
     *         the text of the exception message
     * @param request
     *         the request that fails validation
     * @param validationError
     *         the constraints that the request violates
     */
    InvalidRequestException(String messageText, Message request, ValidationError validationError) {
        super(messageText);
        this.request = request;
        this.validationError = validationError;
    }

    /**
     * Obtains the request that fails validation.
     */
    public Message getRequest() {
        return request;
    }

    /**
     * Obtains the constraints that the request violates.
     */
    public ValidationError validationError() {
        return validationError;
    }
}
