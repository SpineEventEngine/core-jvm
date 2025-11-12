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

import com.google.protobuf.Message;
import com.google.protobuf.ProtocolMessageEnum;
import io.spine.base.Error;
import io.spine.protobuf.AnyPacker;
import io.spine.type.TypeName;
import io.spine.validate.Validate;
import io.spine.validate.ValidationError;
import io.spine.validate.diags.ViolationText;
import org.jspecify.annotations.Nullable;

import static java.lang.String.format;

/**
 * An abstract base of validators for the incoming requests to {@linkplain Stand}.
 *
 * @param <M>
 *         the type of request
 */
abstract class RequestValidator<M extends Message> {

    /**
     * Returns the error code to use in the {@linkplain Error validation error}, in case
     * the validated request message does not satisfy the validation constraints.
     */
    protected abstract ProtocolMessageEnum invalidMessageErrorCode();

    /**
     * Obtains error code for an error of unsupported request target.
     */
    protected abstract ProtocolMessageEnum unsupportedTargetErrorCode();

    /**
     * Creates the exception to be thrown if the request {@code Message} is invalid.
     *
     * <p>Allows the descendants to create exceptions of custom types.
     */
    protected abstract
    InvalidRequestException invalidMessageException(String exceptionMsg, M request, Error error);

    /**
     * Determines if this request is supported by the system.
     */
    protected abstract boolean isSupported(M request);

    /**
     * Composes an error message for an unsupported request.
     */
    protected abstract String errorMessage(M request);

    /**
     * Creates an exception for a request in error.
     */
    protected abstract InvalidRequestException unsupportedException(M request, Error error);

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
     * <p>In case the validation is not successful, an {@link InvalidRequestException} is thrown.
     *
     * @param request
     *         the request {@code Message} to validate
     * @throws IllegalArgumentException
     *         if the passed request is not valid
     */
    void validate(M request) throws InvalidRequestException {
        handleValidationResult(validateMessage(request));
        handleValidationResult(checkSupported(request));
        handleValidationResult(validateOwnRules(request));
    }

    /**
     * Handles the {@linkplain InvalidRequestException request validation exception},
     * if it is present.
     *
     * <p>The given {@code responseObserver} is fed with the exception details.
     * Also, the {@code exception} is thrown, wrapped as an {@code IllegalStateException}.
     */
    private static void handleValidationResult(@Nullable InvalidRequestException exception) {
        if (exception != null) {
            throw exception;
        }
    }

    /**
     * Checks whether this request is supported, forms the proper {@link Error error}
     * and packs it into an exception.
     *
     * @param request the request to check for support
     * @return an instance of exception or null if the request is supported.
     */
    private @Nullable InvalidRequestException checkSupported(M request) {
        if (isSupported(request)) {
            return null;
        }

        var unsupportedErrorCode = unsupportedTargetErrorCode();
        var errorMessage = errorMessage(request);
        var errorTypeName = unsupportedErrorCode.getDescriptorForType()
                                                .getFullName();
        var error = Error.newBuilder()
                .setType(errorTypeName)
                .setCode(unsupportedErrorCode.getNumber())
                .setMessage(errorMessage)
                .build();

        var exception = unsupportedException(request, error);
        return exception;
    }

    /**
     * Checks whether the {@code Message} of the given request conforms the constraints.
     *
     * @param request the request message to validate
     * @return an instance of exception or null if the request message is valid.
     */
    private @Nullable InvalidRequestException validateMessage(M request) {
        var violations = Validate.violationsOf(request);
        if (violations.isEmpty()) {
            return null;
        }
        var validationError = ValidationError.newBuilder()
                .addAllConstraintViolation(violations)
                .build();
        var errorCode = invalidMessageErrorCode();
        var typeName = errorCode.getDescriptorForType()
                                .getFullName();
        var errorMessage = errorConstraintsViolated(request);
        var violationsText = ViolationText.ofAll(violations);
        var errorText = format("%s %s", errorMessage, violationsText);
        var error = Error.newBuilder()
                .setType(typeName)
                .setCode(errorCode.getNumber())
                .setDetails(AnyPacker.pack(validationError))
                .setMessage(errorText)
                .build();
        var exceptionMsg = formatExceptionMessage(request, validationError);
        var exception = invalidMessageException(exceptionMsg, request, error);
        return exception;
    }

    private @Nullable InvalidRequestException validateOwnRules(M request) {
        var error = checkOwnRules(request);
        if (error == null) {
            return null;
        } else {
            return invalidMessageException(error.getMessage(), request, error);
        }
    }

    /**
     * Validates the request by the type-specific rules.
     *
     * @param request
     *         the request message to validate
     * @return an {@code Error} if the request is invalid, {@code null} otherwise
     */
    protected @Nullable Error checkOwnRules(M request) {
        return null;
    }

    private String formatExceptionMessage(M request, ValidationError error) {
        return format("%s. Validation error: `%s`.", errorConstraintsViolated(request), error);
    }

    private String errorConstraintsViolated(M request) {
        return format("`%s` message does not satisfy the validation constraints.",
                      TypeName.of(request));
    }
}
