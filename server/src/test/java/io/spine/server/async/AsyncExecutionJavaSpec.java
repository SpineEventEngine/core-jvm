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

package io.spine.server.async;

import com.google.protobuf.Message;
import com.google.protobuf.util.Durations;
import io.spine.base.Identifier;
import io.spine.server.BoundedContextBuilder;
import io.spine.server.command.Assign;
import io.spine.server.command.AsyncAssignee;
import io.spine.server.given.context.shipping.OrderId;
import io.spine.server.given.context.shipping.command.ReserveShipment;
import io.spine.server.given.context.shipping.event.ShipmentReserved;
import io.spine.testing.client.TestActorRequestFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.grpc.StreamObservers.noOpObserver;

/**
 * Checks that the API of asynchronous handlers, which is written in Kotlin, is convenient
 * for Java code.
 */
@DisplayName("`AsyncExecution` should")
class AsyncExecutionJavaSpec {

    private final OrderId order = OrderId.newBuilder()
            .setUuid(Identifier.newUuid())
            .build();

    @Test
    @DisplayName("be implemented with a lambda, and serve a handler written in Java")
    void implementedWithLambda() {
        List<AsyncInvocation> submitted = new ArrayList<>();
        List<Message> events = new ArrayList<>();
        AsyncExecution execution = invocation -> {
            submitted.add(invocation);
            invocation.run();
        };
        var context = BoundedContextBuilder
                .assumingTests("JavaShipping")
                .setAsyncExecution(execution)
                .addAssignee(new JavaAcmeHandler())
                .addEventListener(event -> events.add(event.message()))
                .build();
        var command = new TestActorRequestFactory(AsyncExecutionJavaSpec.class)
                .createCommand(ReserveShipment.newBuilder()
                                       .setOrder(order)
                                       .build());

        context.commandBus()
               .post(command, noOpObserver());
        context.close();
        // The default `close()` of a lambda declares no checked exception.
        execution.close();

        assertThat(submitted).hasSize(1);
        var invocation = submitted.get(0);
        assertThat(invocation.handlerClass()).isEqualTo(JavaAcmeHandler.class);
        assertThat(invocation.signal()).isEqualTo(command);
        assertThat(events).hasSize(1);
        assertThat(((ShipmentReserved) events.get(0)).getOrder()).isEqualTo(order);
    }

    @Test
    @DisplayName("be built with limits and a close timeout")
    void builtWithLimits() {
        var execution = AsyncExecution.newBuilder()
                .setLimit(JavaAcmeHandler.class, 2)
                .setLimit(4)
                .setCloseTimeout(Durations.fromSeconds(1))
                .build();

        assertThat(execution).isNotNull();
        execution.close();
    }

    @Test
    @DisplayName("be closed by `try`-with-resources without a `catch`")
    void closedByTryWithResources() {
        try (var execution = AsyncExecution.newBuilder().build()) {
            assertThat(execution).isNotNull();
        }
    }

    @Test
    @DisplayName("provide the execution on the calling thread")
    void onCallingThread() {
        var execution = AsyncExecution.onCallingThread();

        assertThat(execution).isSameInstanceAs(AsyncExecution.onCallingThread());
        execution.close();
    }

    /**
     * An asynchronous assignee written in Java.
     */
    private static final class JavaAcmeHandler extends AsyncAssignee {

        @Assign
        ShipmentReserved reserve(ReserveShipment command) {
            return ShipmentReserved.newBuilder()
                    .setOrder(command.getOrder())
                    .build();
        }
    }
}
