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

import java.util.function.BiConsumer;

/**
 * A handler for an error occurred when posting a message for asynchronous processing
 * at the server side.
 *
 * <p>The handler accepts a message that caused an error, and the {@link ErrorCase} obtained
 * from the server. For example, for an invalid command, the handler receives the command and
 * {@link ErrorCase#INVALID_REQUEST INVALID_REQUEST}.
 */
public interface ServerErrorHandler extends BiConsumer<Message, ErrorCase> {
}
