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

package io.spine.server.event;

import io.spine.core.Event;
import io.spine.server.type.EventEnvelope;
import io.spine.test.event.ProjectCreated;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.protobuf.AnyPacker.pack;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("`EventValidator` should")
class EventValidatorTest {

    @Test
    @DisplayName("validate event messages")
    void validateEventMessages() {
        var eventWithDefaultMessage = Event.newBuilder()
                .setMessage(pack(ProjectCreated.getDefaultInstance()))
                .buildPartial();
        var eventValidator = new EventValidator();
        var error = eventValidator.validate(
                EventEnvelope.of(eventWithDefaultMessage)
        );
        assertTrue(error.isPresent());
        assertThat(error.get().getConstraintViolationList())
                .isNotEmpty();
    }
}
