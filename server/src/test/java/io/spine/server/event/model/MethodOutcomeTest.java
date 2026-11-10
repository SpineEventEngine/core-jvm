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

package io.spine.server.event.model;

import io.spine.base.Error;
import io.spine.server.BoundedContextBuilder;
import io.spine.server.event.model.given.reactor.RcHandlerFailedSubscriber;
import io.spine.server.event.model.given.reactor.RcReactWithRejection;
import io.spine.server.model.IllegalOutcomeException;
import io.spine.test.model.ModProjectCreated;
import io.spine.testing.server.TestEventFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static io.spine.base.Identifier.newUuid;
import static io.spine.testing.server.TestContexts.buildForTests;

@DisplayName("`DispatchOutcome` from a method should")
class MethodOutcomeTest {

    @Test
    @DisplayName("be an error when the method returns a rejection")
    void returnRejection() {
        var diagnosticSubscriber = new RcHandlerFailedSubscriber();
        var context = buildForTests(BoundedContextBuilder
                .assumingTests()
                .addEventDispatcher(new RcReactWithRejection())
                .addEventDispatcher(diagnosticSubscriber));
        var event = ModProjectCreated.newBuilder()
                .setId(newUuid())
                .build();
        var e = TestEventFactory.newInstance(MethodOutcomeTest.class)
                                .createEvent(event);
        context.eventBus().post(e);
        var partialExpectedError = Error.newBuilder()
                .setType(IllegalOutcomeException.class.getCanonicalName())
                .buildPartial();
        assertThat(diagnosticSubscriber.events())
                  .hasSize(1);
        assertThat(diagnosticSubscriber.events().get(0).getError())
                .comparingExpectedFieldsOnly()
                .isEqualTo(partialExpectedError);
    }
}
