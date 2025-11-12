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
import com.google.common.truth.OptionalSubject;
import com.google.protobuf.Message;
import com.google.protobuf.StringValue;
import io.spine.base.Identifier;
import io.spine.core.Enrichment.Container;
import io.spine.server.type.EventEnvelope;
import io.spine.server.type.given.GivenEvent;
import io.spine.test.core.given.EtProjectCreated;
import io.spine.test.core.given.EtProjectDetails;
import io.spine.test.core.given.EtProjectInfo;
import io.spine.testing.TestValues;
import io.spine.testing.UtilityClassTest;
import io.spine.testing.server.TestEventFactory;
import io.spine.type.TypeName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.base.Identifier.newUuid;
import static io.spine.core.Enrichments.containerIn;
import static io.spine.protobuf.AnyPacker.pack;
import static io.spine.server.type.given.GivenEvent.context;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Enrichments utility should")
class EnrichmentsTest extends UtilityClassTest<Enrichments> {

    private EtProjectCreated projectCreated;
    private EtProjectInfo projectInfo;
    private TestEventFactory eventFactory;
    private EventContext context;

    EnrichmentsTest() {
        super(Enrichments.class);
    }

    @Override
    protected void configure(NullPointerTester tester) {
        super.configure(tester);
        tester.setDefault(StringValue.class, StringValue.getDefaultInstance())
              .setDefault(EventContext.class, context());

    }

    /**
     * Creates a new {@link EventContext} enriched with the passed message.
     *
     * <p>The key in the map is a fully-qualified {@code TypeName} of the message.
     * See {@link Container#getItemsMap()} or {@code Enrichment} proto type definition
     * for details.
     */
    private static EventContext givenContextEnrichedWith(Message enrichment) {
        var enrichmentKey = TypeName.of(enrichment).value();
        var enrichments = Enrichment.newBuilder()
                .setContainer(Container.newBuilder()
                                      .putItems(enrichmentKey, pack(enrichment)));
        var context = context().toBuilder()
                .setEnrichment(enrichments)
                .build();
        return context;
    }

    @BeforeEach
    void setUp() {
        var producerId = newUuid();
        projectCreated = EtProjectCreated.newBuilder()
                .setId(producerId)
                .build();
        projectInfo = EtProjectInfo.newBuilder()
                .setProjectName("Project info of " + getClass().getSimpleName())
                .build();
        eventFactory = TestEventFactory.newInstance(Identifier.pack(producerId), getClass());
        context = eventFactory.createEvent(projectCreated)
                              .context();
    }

    @Test
    @DisplayName("recognize if event enrichment is enabled")
    void recognizeEnrichmentEnabled() {
        var event = EventEnvelope.of(eventFactory.createEvent(projectCreated));

        assertTrue(event.isEnrichmentEnabled());
    }

    @Test
    @DisplayName("recognize if event enrichment is disabled")
    void recognizeEnrichmentDisabled() {
        var event = EventEnvelope.of(GivenEvent.withDisabledEnrichmentOf(projectCreated));

        assertFalse(event.isEnrichmentEnabled());
    }

    @Test
    @DisplayName("verify if there are enrichments")
    void getAllEnrichments() {
        var context = givenContextEnrichedWith(projectInfo);

        assertThat(hasEnrichments(context))
                .isTrue();
    }

    @Test
    @DisplayName("tell if there are no enrichments in `EventContext`")
    void returnAbsentOnNoEnrichments() {
        assertThat(hasEnrichments(context))
                .isFalse();
    }

    private static
    OptionalSubject assertEnrichment(EventContext ctx, Class<? extends Message> cls) {
        return assertThat(ctx.find(cls));
    }

    @Test
    @DisplayName("obtain specific event enrichment from context")
    void obtainSpecificEnrichment() {
        var context = givenContextEnrichedWith(projectInfo);

        var assertEnrichment = assertEnrichment(context, projectInfo.getClass());
        assertEnrichment.isPresent();
        assertEnrichment.hasValue(projectInfo);
    }

    @Test
    @DisplayName("return absent if there are no enrichments in context when searching for one")
    void returnAbsentOnNoEnrichmentsSearch() {
        assertEnrichment(context, EtProjectInfo.class)
                .isEmpty();
    }

    @Test
    @DisplayName("return absent if there is no specified enrichment in context")
    void returnAbsentOnEnrichmentNotFound() {
        var context = givenContextEnrichedWith(
                EtProjectDetails.newBuilder()
                        .setProjectDescription(TestValues.randomString())
                        .setLogoUrl("https://spine.io/img/spine-logo-white.svg")
                        .build()
        );
        assertEnrichment(context, EtProjectInfo.class)
              .isEmpty();
    }

    /**
     * Verifies if the passed event context has at least one enrichment.
     */
    private static boolean hasEnrichments(EventContext context) {
        var optional = containerIn(context);
        if (optional.isEmpty()) {
            return false;
        }
        var container = optional.get();
        var result = !container.getItemsMap()
                               .isEmpty();
        return result;
    }
}
