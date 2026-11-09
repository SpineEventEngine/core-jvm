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

package io.spine.client;

import com.google.protobuf.Message;
import io.spine.core.ErrorCase;
import io.spine.logging.Logger;

import static io.spine.type.ProtoTexts.shortDebugString;
import static java.lang.String.format;

/**
 * A posting handler that logs the error case.
 */
final class LoggingServerErrorHandler extends LoggingHandler implements ServerErrorHandler {

    LoggingServerErrorHandler(Logger logger, String messageFormat) {
        super(logger, messageFormat);
    }

    @Override
    public void accept(Message message, ErrorCase errorCase) {
        error().log(() -> format(messageFormat(), shortDebugString(message), errorCase));
    }
}
