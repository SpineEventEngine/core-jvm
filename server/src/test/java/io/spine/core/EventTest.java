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
package io.spine.core;

import com.google.common.testing.NullPointerTester;
import com.google.protobuf.StringValue;
import io.spine.base.CommandMessage;
import io.spine.base.Identifier;
import io.spine.base.RejectionThrowable;
import io.spine.base.Time;
import io.spine.server.entity.rejection.EntityAlreadyArchived;
import io.spine.server.entity.rejection.StandardRejections;
import io.spine.server.event.EventFactory;
import io.spine.server.type.CommandEnvelope;
import io.spine.server.type.EventEnvelope;
import io.spine.server.type.given.GivenEvent;
import io.spine.test.core.given.EtDoSomething;
import io.spine.test.core.given.EtProjectCreated;
import io.spine.testing.UtilityClassTest;
import io.spine.testing.client.TestActorRequestFactory;
import io.spine.testing.core.given.GivenUserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.protobuf.AnyPacker.pack;
import static io.spine.server.event.RejectionFactory.reject;
import static io.spine.server.type.given.EventsTestEnv.commandContext;
import static io.spine.server.type.given.EventsTestEnv.event;
import static io.spine.server.type.given.EventsTestEnv.tenantId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test functionality of {@link Events} utility class.
 *
 * <p>This test suite is placed under the {@code server} module to avoid dependency on the event
 * generation code which belongs to server-side.
 */
@DisplayName("`Event` should")
public class EventTest extends UtilityClassTest<Events> {

    private static final TestActorRequestFactory requestFactory =
            new TestActorRequestFactory(EventTest.class);

    private Event event;
    private EventContext context;

    EventTest() {
        super(Events.class);
    }

    @BeforeEach
    void setUp() {
        var cmd = generate();
        var producerId = StringValue.of(getClass().getSimpleName());
        var eventFactory = EventFactory.on(cmd, Identifier.pack(producerId));
        event = eventFactory.createEvent(GivenEvent.message(), null);
        context = event.context();
    }

    private static CommandEnvelope generate() {
        return CommandEnvelope.of(requestFactory.generateCommand());
    }

    @Override
    protected void configure(NullPointerTester tester) {
        super.configure(tester);
        var defaultRejectionThrowable = EntityAlreadyArchived.newBuilder()
                .setEntityId(pack(GivenUserId.newUuid()))
                .build();
        tester.setDefault(StringValue.class, StringValue.getDefaultInstance())
              .setDefault(EventContext.class, GivenEvent.context())
              .setDefault(Version.class, Version.getDefaultInstance())
              .setDefault(Event.class, Event.getDefaultInstance())
              .setDefault(RejectionThrowable.class, defaultRejectionThrowable);
    }

    @Nested
    @DisplayName("provide")
    class GetFromEvent {

        @Test
        @DisplayName("message")
        void message() {
            var message = GivenEvent.message();
            var event = GivenEvent.withMessage(message);
            assertThat(event.enclosedMessage())
                 .isEqualTo(message);
        }

        @Test
        @DisplayName("timestamp")
        void timestamp() {
            var event = GivenEvent.occurredMinutesAgo(1);

            assertThat(event.timestamp())
                    .isEqualTo(event.context()
                                    .getTimestamp());
        }

        @Test
        @DisplayName("root command ID")
        void rootCommandId() {
            var command = generate();
            var event = newEvent(command);

            assertThat(event.rootMessage().asCommandId())
                    .isEqualTo(command.id());
        }

        @Test
        @DisplayName("type name")
        void typeName() {
            var command = generate();
            var event = newEvent(command);

            var typeName = EventEnvelope.of(event)
                                        .messageTypeName();
            assertNotNull(typeName);
            assertEquals(EtProjectCreated.class.getSimpleName(), typeName.simpleName());
        }

        private Event newEvent(CommandEnvelope command) {
            var producerId = StringValue.of(getClass().getSimpleName());
            var ef = EventFactory.on(command, Identifier.pack(producerId));
            return ef.createEvent(GivenEvent.message(), null);
        }
    }


    @Nested
    @DisplayName("expose tenant ID")
    class GetTenantId {

        @Test
        @DisplayName("from the previous message")
        void fromPastMessage() {
            var targetTenantId = tenantId();
            var actorContext = ActorContext.newBuilder()
                    .setTenantId(targetTenantId)
                    .buildPartial();
            var origin = Origin.newBuilder()
                    .setActorContext(actorContext)
                    .buildPartial();
            var context = contextWithoutOrigin().setPastMessage(origin)
                                                .buildPartial();
            var event = event(context);

            assertThat(event.tenant())
                    .isEqualTo(targetTenantId);
        }

        @SuppressWarnings("deprecation") // Required for backward compatibility.
        @Test
        @DisplayName("from enclosed command context")
        void fromCommandContext() {
            var targetTenantId = tenantId();
            var commandContext = commandContext(targetTenantId);
            var context = contextWithoutOrigin().setCommandContext(commandContext)
                                                .buildPartial();
            var event = event(context);

            assertThat(event.tenant())
                    .isEqualTo(targetTenantId);
        }

        @SuppressWarnings("deprecation") // Required for backward compatibility.
        @Test
        @DisplayName("from enclosed context of origin event, which had command as origin")
        void fromEventContextWithCommandContext() {
            var targetTenantId = tenantId();
            var commandContext = commandContext(targetTenantId);
            var outerContext =
                    contextWithoutOrigin()
                            .setCommandContext(commandContext)
                            .build();
            var context =
                    contextWithoutOrigin()
                            .setEventContext(outerContext)
                            .build();
            var event = event(context);

            assertThat(event.tenant())
                    .isEqualTo(targetTenantId);
        }
    }

    @Test
    @DisplayName("tell if it is a rejection")
    void tellWhenRejection() {
        var requestFactory = new TestActorRequestFactory(getClass());
        var cmd = requestFactory.createCommand(commandMessage());
        var event = reject(cmd, new StubRejectionThrowable());

        assertThat(event.isRejection())
                .isTrue();
    }


    @Test
    @DisplayName("tell if it is NOT a rejection")
    void tellWhenNotRejection() {
        assertThat(event.isRejection())
                .isFalse();
    }

    @SuppressWarnings("deprecation") // Required for backward compatibility.
    @Test
    @DisplayName("clear enrichments from the event and its origin")
    void clearEnrichments() {
        var someEnrichment = Enrichment.newBuilder()
                .setDoNotEnrich(true)
                .build();
        var grandOriginContext = context.toBuilder().setEnrichment(someEnrichment);
        var originContext =
                contextWithoutOrigin()
                        .setEventContext(grandOriginContext)
                        .setEnrichment(someEnrichment);
        var eventContext =
                contextWithoutOrigin()
                        .setEventContext(originContext)
                        .setEnrichment(someEnrichment)
                        .build();
        var event = event(eventContext);

        var eventWithoutEnrichments = event.clearEnrichments();

        var context = eventWithoutEnrichments.getContext();
        var origin = context.getEventContext();
        var grandOrigin = origin.getEventContext();

        assertThat(context.hasEnrichment()).isFalse();
        assertThat(origin.hasEnrichment()).isFalse();
        assertThat(grandOrigin.hasEnrichment()).isTrue();
    }

    @SuppressWarnings("deprecation") // Required for backward compatibility.
    @Test
    @DisplayName("clear enrichment hierarchy")
    void clearEnrichmentHierarchy() {
        var someEnrichment = Enrichment.newBuilder()
                .setDoNotEnrich(true)
                .build();
        var grandOriginContext = context.toBuilder().setEnrichment(someEnrichment);
        var originContext = contextWithoutOrigin()
                .setEventContext(grandOriginContext);
        var context = contextWithoutOrigin()
                .setEventContext(originContext)
                .build();
        var event = event(context);

        var eventWithoutEnrichments = event.clearAllEnrichments();

        var grandOrigin = eventWithoutEnrichments.getContext()
                                                 .getEventContext()
                                                 .getEventContext();
        assertThat(grandOrigin.hasEnrichment()).isFalse();
    }

    private EventContext.Builder contextWithoutOrigin() {
        return context.toBuilder()
                      .clearOrigin();
    }

    private static CommandMessage commandMessage() {
        return EtDoSomething.newBuilder()
                .setId(EventTest.class.getName())
                .build();
    }

    private static class StubRejectionThrowable extends RejectionThrowable {

        private static final long serialVersionUID = 0L;

        private StubRejectionThrowable() {
            super(rejectionMessage());
        }

        private static StandardRejections.EntityAlreadyArchived rejectionMessage() {
            return StandardRejections.EntityAlreadyArchived.newBuilder()
                    .setEntityId(pack(Time.currentTime()))
                    .build();
        }
    }
}
