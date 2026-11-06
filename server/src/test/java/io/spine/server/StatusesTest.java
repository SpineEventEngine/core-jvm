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

package io.spine.server;

import com.google.common.testing.NullPointerTester;
import io.grpc.Status;
import io.spine.base.Error;
import io.spine.grpc.MetadataConverter;
import io.spine.server.commandbus.UnsupportedCommandException;
import io.spine.server.transport.Statuses;
import io.spine.testing.UtilityClassTest;
import io.spine.testing.client.TestActorRequestFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.spine.server.transport.Statuses.invalidArgumentWithCause;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("`Statuses` utility should")
class StatusesTest extends UtilityClassTest<Statuses> {

    StatusesTest() {
        super(Statuses.class);
    }

    @Override
    protected void configure(NullPointerTester tester) {
        super.configure(tester);
        tester.setDefault(Exception.class, new RuntimeException("Statuses test"))
              .setDefault(Error.class, Error.getDefaultInstance());
    }

    @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
    @Test
    @DisplayName("create invalid argument status exception")
    void createInvalidArgumentStatusEx() {
        var command = new TestActorRequestFactory(StatusesTest.class).generateCommand();
        var rejection = new UnsupportedCommandException(command);
        var statusRuntimeEx = invalidArgumentWithCause(rejection);
        @SuppressWarnings("OptionalGetWithoutIsPresent")
        var actualError = MetadataConverter.toError(statusRuntimeEx.getTrailers())
                                           .get();
        assertEquals(Status.INVALID_ARGUMENT.getCode(), statusRuntimeEx.getStatus()
                                                                       .getCode());
        assertEquals(rejection, statusRuntimeEx.getCause());
        assertEquals(rejection.asError(), actualError);
    }
}
