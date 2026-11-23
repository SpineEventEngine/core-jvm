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

import io.spine.dependency.kotlinx.Coroutines
import io.spine.dependency.local.Base
import io.spine.dependency.local.BaseTypes
import io.spine.dependency.local.Logging
import io.spine.dependency.local.Reflect
import io.spine.dependency.local.Time
import io.spine.dependency.local.Validation

plugins {
    module
    id("io.spine.core-jvm")
}

dependencies {
    api(Base.lib)
    api(BaseTypes.lib)
    api(Coroutines.core)
    api(Logging.lib)
    api(Time.lib)
    api(Validation.runtime)

    implementation(Reflect.lib)
    implementation(Time.javaExtensions)

    testImplementation(project(":core-testlib"))
    testImplementation(Time.testLib)
}

spine {
    coreJvm {
        compiler {
            forMessage("spine.core.Event") {
                // Describe the `Event` fields to allow non-reflective and strongly-typed access.
                markFieldsAs("io.spine.core.EventField")
            }

            // Enable the strongly typed fields generation for `spine.core.EventContext` to allow
            // creation of typed event filters based on event context.
            forMessage("spine.core.EventContext") {
                markFieldsAs("io.spine.core.EventContextField")
            }
        }
    }
}
