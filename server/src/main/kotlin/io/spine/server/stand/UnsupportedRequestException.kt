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

import com.google.protobuf.Message
import java.io.Serial

/**
 * An exception thrown when the [Stand] does not serve a valid request.
 *
 * The `Stand` does not serve a query or a topic if it does not expose the target type,
 * and a subscription if it does not have it.
 *
 * @property request The request that is not served.
 * @param message The text of the exception message.
 * @see InvalidRequestException
 */
public class UnsupportedRequestException internal constructor(
    public val request: Message,
    message: String
) : RuntimeException(message) {

    private companion object {

        @Serial
        private const val serialVersionUID: Long = 0L
    }
}
