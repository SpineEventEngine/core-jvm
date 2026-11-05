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

@file:JvmName("ErrorCases")

package io.spine.core

import io.spine.base.Error
import io.spine.base.error

/**
 * Creates an [Error] that carries the same information as this error case, and nothing more.
 *
 * The [type][Error.getType] of the error is the full name of the [ErrorCase] enum,
 * its [code][Error.getCode] is the number of this value, and its [message][Error.getMessage]
 * is the name of this value.
 *
 * Use it where an `Error` is expected, for example, to pass an error case to a handler
 * of server errors on the client side.
 *
 * @throws IllegalArgumentException If this value is [ErrorCase.UNRECOGNIZED].
 */
public fun ErrorCase.toError(): Error {
    require(this != ErrorCase.UNRECOGNIZED) {
        "An unrecognized error case cannot be converted into an error."
    }
    val case = this
    return error {
        type = ErrorCase.getDescriptor().fullName
        code = case.number
        message = case.name
    }
}
