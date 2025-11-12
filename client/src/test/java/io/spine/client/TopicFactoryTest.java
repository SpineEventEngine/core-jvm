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
package io.spine.client;

import com.google.protobuf.FieldMask;
import io.spine.test.client.TestEntityId;
import io.spine.testing.core.given.GivenUserId;
import io.spine.time.ZoneIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.google.common.collect.Sets.newHashSet;
import static io.spine.base.Identifier.newUuid;
import static io.spine.client.given.TopicFactoryTestEnv.TARGET_ENTITY_TYPE_URL;
import static io.spine.client.given.TopicFactoryTestEnv.TEST_ENTITY_TYPE;
import static io.spine.client.given.TopicFactoryTestEnv.entityId;
import static io.spine.client.given.TopicFactoryTestEnv.verifyContext;
import static io.spine.protobuf.AnyPacker.unpack;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link io.spine.client.TopicFactory}.
 */
@DisplayName("`TopicFactory` should")
class TopicFactoryTest {

    private ActorRequestFactory requestFactory;
    private TopicFactory factory;

    private static ActorRequestFactory requestFactory() {
        return ActorRequestFactory.newBuilder()
                .setZoneId(ZoneIds.systemDefault())
                .setActor(GivenUserId.of(newUuid()))
                .build();
    }

    @BeforeEach
    void createFactory() {
        requestFactory = requestFactory();
        factory = requestFactory.topic();
    }

    @Nested
    @DisplayName("create topic")
    class CreateTopic {

        @Test
        @DisplayName("for all of a kind")
        void forAllOfKind() {
            var topic = factory.select(TEST_ENTITY_TYPE)
                               .build();

            verifyTargetAndContext(topic);

            assertEquals(0, topic.getTarget()
                                 .getFilters()
                                 .getIdFilter()
                                 .getIdCount());
        }

        @Test
        @DisplayName("for objects with specified IDs")
        void forSomeOfKind() {
            Set<TestEntityId> ids = newHashSet(entityId(1), entityId(2), entityId(3));
            var topic = factory.select(TEST_ENTITY_TYPE)
                               .byId(ids)
                               .build();

            verifyTargetAndContext(topic);

            var actualIds = topic.getTarget()
                                 .getFilters()
                                 .getIdFilter()
                                 .getIdList();
            assertEquals(ids.size(), actualIds.size());
            for (var actualId : actualIds) {
                var unpackedId = unpack(actualId, TestEntityId.class);
                assertTrue(ids.contains(unpackedId));
            }
        }

        @Test
        @DisplayName("for given target")
        void forTarget() {
            var givenTarget = Targets.allOf(TEST_ENTITY_TYPE);
            var topic = factory.forTarget(givenTarget);

            verifyTargetAndContext(topic);
        }

        private void verifyTargetAndContext(Topic topic) {
            assertNotNull(topic);
            assertNotNull(topic.getId());

            assertEquals(TARGET_ENTITY_TYPE_URL.value(), topic.getTarget()
                                                              .getType());
            assertEquals(FieldMask.getDefaultInstance(), topic.getFieldMask());

            var actualContext = topic.getContext();
            verifyContext(requestFactory.newActorContext(), actualContext);
        }
    }
}
