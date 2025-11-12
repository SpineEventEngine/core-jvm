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

package io.spine.client;

import com.google.protobuf.Message;
import io.spine.logging.Logger;

import java.util.function.Consumer;

/**
 * Functional interface for error handlers.
 *
 * @see SubscribingRequest#onStreamingError(ErrorHandler)
 */
@FunctionalInterface
public interface ErrorHandler extends Consumer<Throwable> {

    /**
     * Obtains the handler which logs the fact of the error using
     * the {@code ERROR} level of the passed logger.
     *
     * @param logger
     *         the instance of the logger to use for reporting the error
     * @param messageFormat
     *         the formatting message where the sole parameter is the type of
     *         the message which caused the error
     * @param type
     *         the type of messages delivered to the consumers
     * @return the logging error handler
     */
    @SuppressWarnings("NonApiType") // https://github.com/SpineEventEngine/core-java/issues/1526
    static ErrorHandler
    logError(Logger logger, String messageFormat, Class<? extends Message> type) {
        return new LoggingTypeErrorHandler(logger, messageFormat, type);
    }
}
