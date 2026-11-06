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

package io.spine.server.given

import io.kotest.matchers.optional.shouldBePresent
import io.spine.core.ErrorCase
import io.spine.grpc.StreamObservers.errorCaseOf

/**
 * Obtains the error case that this failure of a call carries, asserting that it has one.
 */
internal fun Throwable.errorCase(): ErrorCase = errorCaseOf(this).shouldBePresent()
