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

package io.spine.testing.server.blackbox.probe;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import io.spine.logging.WithLogging;
import io.spine.system.server.DiagnosticEvent;

import static io.spine.type.Json.toJson;
import static java.lang.String.format;

/**
 * Provides a handy shortcut for logging exceptions happened during
 * {@linkplain DiagnosticEvent events} handling.
 */
interface DiagnosticLogging extends WithLogging {

    /**
     * Performs exception logging of the supplied {@code event}.
     *
     * @param errorMessage
     *         the error message to log
     * @param formatArgs
     *         the arguments, if any, for the error message
     */
    @FormatMethod
    default void log(DiagnosticEvent event,
                     @FormatString String errorMessage,
                     Object... formatArgs) {
        var msg = format(errorMessage, formatArgs);
        log(msg, event);
    }

    /**
     * Performs exception logging of the supplied {@code event}.
     *
     * @param msg
     *         the formatted error message to log
     */
    @SuppressWarnings("FloggerLogString")
    default void log(String msg, DiagnosticEvent event) {
        var errorLogger = logger().atError();
        var loggingEnabled = errorLogger.isEnabled();
        var eventJson = toJson(event);
        if (loggingEnabled) {
            errorLogger.log(() -> msg);
            errorLogger.log(() -> eventJson);
        } else {
            @SuppressWarnings("UseOfSystemOutOrSystemErr")
            // Edge case for disabled/misconfigured logging.
            var stderr = System.err;
            stderr.println(msg);
            stderr.println(eventJson);
        }
    }
}
