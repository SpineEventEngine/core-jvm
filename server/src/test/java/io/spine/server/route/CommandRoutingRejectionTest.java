/*
 * Copyright 2022, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Redistribution and use in source and/or binary forms, with or without
 * modification, must retain the above copyright notice and the following
 * disclaimer.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.spine.server.route;

import io.grpc.stub.StreamObserver;
import io.spine.core.Ack;
import io.spine.core.Subscribe;
import io.spine.server.BoundedContext;
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
        context = BoundedContext.singleTenant("Railway Station").build();
        var contextAccess = context.internalAccess();
        contextAccess.register(new SwitchmanBureau());
        switchmanObserver = new SwitchmanObserver();
        context.eventBus()
               .register(switchmanObserver);
        logRepository = new Log.Repository();
        contextAccess.register(logRepository);
        commandBus = context.commandBus();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    /**
     * Verifies that a command rejected during routing gets rejected status.
     *
     * <p>The test initially posts a command which should be processed correctly. This is done to
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

        // Post a command with the argument which causes rejection in routing.
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
