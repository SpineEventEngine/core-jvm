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
import io.spine.base.Error;
import io.spine.server.MessageError;

import java.io.Serial;

/**
 * A base class for exceptions fired in case an invalid request
 * has been submitted to {@linkplain Stand}.
 */
public class InvalidRequestException extends RuntimeException implements MessageError {

    @Serial
    private static final long serialVersionUID = 0L;

    private final Message request;
    private final Error error;

    /**
     * Creates a new instance.
     *
     * @param messageText the error message text
     * @param request     the related actor request
     * @param error       the error occurred
     */
    InvalidRequestException(String messageText, Message request, Error error) {
        super(messageText);
        this.request = request;
        this.error = error;
    }

    /**
     * Obtains an original request which caused the {@code error}.
     */
    public Message getRequest() {
        return request;
    }

    @Override
    public Error asError() {
        return error;
    }

    @Override
    public Throwable asThrowable() {
        return this;
    }
}
