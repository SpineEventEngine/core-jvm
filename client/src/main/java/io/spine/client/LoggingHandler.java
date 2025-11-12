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

import io.spine.logging.Logger;
import io.spine.logging.LoggingApi;

/**
 * Abstract base for error handlers that write a message to the associated logger.
 *
 * <p>The handler always uses {@linkplain Logger#atError() ERROR} level.
 */
abstract class LoggingHandler {

    private final Logger logger;
    private final String messageFormat;

    /**
     * Creates a new instance of the logging handler.
     *  @param logger
     *         the instance of the logger to use for reporting the error
     * @param messageFormat
     *         the formatting message for an error text
     */
    LoggingHandler(Logger logger, String messageFormat) {
        this.logger = logger;
        this.messageFormat = messageFormat;
    }

    final String messageFormat() {
        return messageFormat;
    }

    /** Obtains logging API at {@code sever} level. */
    protected final LoggingApi<?> error() {
        return logger.atError();
    }

    /** Obtains logging API at {@code sever} level and initializes it with the passed cause. */
    protected final LoggingApi<?> error(Throwable cause) {
        return error().withCause(cause);
    }
}
