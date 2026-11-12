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

package io.spine.server.route;

import io.grpc.stub.StreamObserver;
import io.spine.core.Ack;
import io.spine.core.Subscribe;
import io.spine.server.BoundedContext;
import io.spine.server.BoundedContextBuilder;
import io.spine.server.commandbus.CommandBus;
import io.spine.server.event.AbstractEventSubscriber;
import io.spine.server.given.context.switchman.Log;
import io.spine.server.given.context.switchman.SwitchId;
import io.spine.server.given.context.switchman.SwitchPosition;
import io.spine.server.given.context.switchman.Switchman;
import io.spine.server.given.context.switchman.SwitchmanBureau;
import io.spine.server.given.context.switchman.command.SetSwitch;
import io.spine.server.given.context.switchman.event.SwitchPositionConfirmed;
import io.spine.testing.client.TestActorRequestFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.google.common.collect.Lists.newLinkedList;
import static com.google.common.truth.Truth.assertThat;
import static io.spine.base.Identifier.newUuid;
import static io.spine.grpc.StreamObservers.noOpObserver;
import static io.spine.server.given.context.switchman.SwitchPosition.RIGHT;
import static io.spine.server.given.context.switchman.SwitchmanBureau.MISSING_SWITCHMAN_NAME;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that a command can be rejected within a {@linkplain CommandRoute routing function}.
 */
@DisplayName("`CommandRouting` rejection should")
class CommandRoutingRejectionTest {

    private final TestActorRequestFactory requestFactory = new TestActorRequestFactory(getClass());
    private final StreamObserver<Ack> observer = noOpObserver();

    private BoundedContext context;
    private CommandBus commandBus;
    private Log.Repository logRepository;
    private SwitchmanObserver switchmanObserver;

    @BeforeEach
    void setUp() {
        logRepository = new Log.Repository();
        context = BoundedContextBuilder.assumingTests("Railway Station")
                .add(new SwitchmanBureau())
                .add(logRepository)
                .build();
        switchmanObserver = new SwitchmanObserver();
        context.eventBus()
               .register(switchmanObserver);
        commandBus = context.commandBus();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    /**
     * Verifies that a command rejected during routing gets rejected status.
     *
     * <p>The test initially posts a command that should be processed correctly. This is done to
     * ensure that basic processing works.
     *
     * <p>Then the test posts a command with the argument that should cause rejection in the
     * routing function.
     */
    @Test
    @DisplayName("result in a rejected command")
    void resultInRejectedCommand() {
        // Post a successful command to make sure general case works.
        var switchmanName = Switchman.class.getName();
        var command = requestFactory.createCommand(
                SetSwitch.newBuilder()
                         .setSwitchId(generateSwitchId())
                         .setSwitchmanName(switchmanName)
                         .setPosition(RIGHT)
                         .build()
        );
        var assertObservedEvents = assertThat(switchmanObserver.events);
        assertObservedEvents.isEmpty();
        commandBus.post(command, observer);
        assertObservedEvents.hasSize(1);

        // Post a command with the argument that causes rejection in routing.
        var commandToReject = requestFactory.createCommand(
                SetSwitch.newBuilder()
                         // This name is specifically checked in the routing function to
                         // throw an error.
                         .setSwitchmanName(MISSING_SWITCHMAN_NAME)
                         .setSwitchId(generateSwitchId())
                         .setPosition(SwitchPosition.LEFT)
                         .build()
        );

        commandBus.post(commandToReject, observer);
        var foundLog = logRepository.find(Log.ID);
        assertTrue(foundLog.isPresent());
        var log = foundLog.get().state();
        assertTrue(log.containsCounters(switchmanName));
        assertThat(log.getMissingSwitchmanList())
                .contains(MISSING_SWITCHMAN_NAME);
    }

    private static SwitchId generateSwitchId() {
        return SwitchId.newBuilder()
                .setId(newUuid())
                .build();
    }

    private static class SwitchmanObserver extends AbstractEventSubscriber {

        private final List<SwitchPositionConfirmed> events = newLinkedList();

        @Subscribe
        void to(SwitchPositionConfirmed event) {
            events.add(event);
        }
    }
}
