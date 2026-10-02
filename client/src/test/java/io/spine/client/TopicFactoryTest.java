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

package io.spine.client;

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

            var actualContext = topic.getContext();
            verifyContext(requestFactory.newActorContext(), actualContext);
        }
    }
}
