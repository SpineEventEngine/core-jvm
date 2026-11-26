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

package io.spine.server.stand;

import io.spine.client.EntityStateUpdate;
import io.spine.client.Filter;
import io.spine.client.Filters;
import io.spine.client.SubscriptionUpdate;
import io.spine.client.Subscriptions;
import io.spine.core.Event;
import io.spine.core.EventId;
import io.spine.protobuf.AnyPacker;
import io.spine.server.type.EventEnvelope;
import io.spine.test.aggregate.AggProject;
import io.spine.test.aggregate.ProjectId;
import io.spine.test.event.EvTeamId;
import io.spine.test.event.ProjectCreated;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.client.Targets.composeTarget;
import static io.spine.server.stand.given.SubscriptionRecordTestEnv.OTHER_TYPE;
import static io.spine.server.stand.given.SubscriptionRecordTestEnv.projectCreatedEnvelope;
import static io.spine.server.stand.given.SubscriptionRecordTestEnv.projectId;
import static io.spine.server.stand.given.SubscriptionRecordTestEnv.projectWithName;
import static io.spine.server.stand.given.SubscriptionRecordTestEnv.stateChangedEnvelope;
import static io.spine.server.stand.given.SubscriptionRecordTestEnv.subscription;
import static io.spine.server.stand.given.SubscriptionRecordTestEnv.subscriptionProjectId;
import static java.util.Collections.singleton;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("`SubscriptionRecord` should")
class SubscriptionRecordTest {

    private static final String TARGET_ID = "target-ID";
    private static final AggProject EMPTY_PRJ = AggProject.getDefaultInstance();

    @Test
    @DisplayName("detect an update according to type")
    void notMatchImproperType() {
        var record = SubscriptionRecord.of(subscription());
        var id = ProjectId.getDefaultInstance();

        var matches = stateChangedEnvelope(id, EMPTY_PRJ, EMPTY_PRJ);
        assertThat(record.detectUpdates(matches)).hasSize(1);

        var notMatches = stateChangedEnvelope(id, EMPTY_PRJ, EMPTY_PRJ, OTHER_TYPE);
        assertThat(record.detectUpdates(notMatches)).isEmpty();
    }

    @Nested
    @DisplayName("detect an update by comparing IDs")
    class MatchById {

        @Test
        @DisplayName("in case of entity subscription")
        void entitySubscription() {
            var targetId = projectId(TARGET_ID);
            var state = AggProject.getDefaultInstance();
            var record = SubscriptionRecord.of(subscription(targetId));

            var envelope = stateChangedEnvelope(targetId, state, state);
            assertThat(record.detectUpdates(envelope)).hasSize(1);

            var otherId = projectId("some-other-ID");
            var envelope2 = stateChangedEnvelope(otherId, state, state);
            assertThat(record.detectUpdates(envelope2)).isEmpty();
        }

        @Test
        @DisplayName("in case of event subscription")
        void eventSubscription() {
            var targetId = EventId.newBuilder()
                    .setValue(TARGET_ID)
                    .build();
            var target = composeTarget(ProjectCreated.class, singleton(targetId), null);
            var subscription = subscription(target);
            var record = SubscriptionRecord.of(subscription);

            var envelope = projectCreatedEnvelope(targetId);
            assertThat(record.detectUpdates(envelope)).hasSize(1);

            var otherId = EventId.newBuilder()
                    .setValue("other-event-ID")
                    .build();
            var nonMatching = projectCreatedEnvelope(otherId);
            assertThat(record.detectUpdates(nonMatching)).isEmpty();
        }
    }

    @Nested
    @DisplayName("detect an update by analyzing state")
    class MatchByState {

        @Test
        @DisplayName("in case of entity subscription")
        void entitySubscription() {
            var targetName = "super-project";
            var targetId = projectId(TARGET_ID);
            var record = projectRecord(targetId,
                                       Filters.eq("name", targetName));
            var matching = projectWithName(targetName);
            var envelope = stateChangedEnvelope(targetId, EMPTY_PRJ, matching);
            var updates = record.detectUpdates(envelope);
            assertThat(updates).hasSize(1);
            var entityUpdate = firstEntityUpdate(updates);
            assertEquals(matching, AnyPacker.unpack(entityUpdate.getState()));

            var nonMatching = projectWithName("some-other-name");
            var envelope2 = stateChangedEnvelope(targetId, EMPTY_PRJ, nonMatching);
            assertThat(record.detectUpdates(envelope2)).isEmpty();
        }

        @Test
        @DisplayName("in case of event subscription")
        void eventSubscription() {
            var targetId = EventId.newBuilder()
                    .setValue(TARGET_ID)
                    .build();
            var targetTeamId = "target-team-ID";

            var filter = Filters.eq("team_id.id", targetTeamId);
            var compositeFilter = Filters.all(filter);
            var filters = singleton(compositeFilter);
            var target = composeTarget(ProjectCreated.class, singleton(targetId), filters);

            var subscription = subscription(target);
            var record = SubscriptionRecord.of(subscription);

            var matchingTeamId = EvTeamId.newBuilder()
                    .setId(targetTeamId)
                    .build();
            var matching = ProjectCreated.newBuilder()
                    .setProjectId(subscriptionProjectId("Subscription Project"))
                    .setTeamId(matchingTeamId)
                    .build();
            var envelope = projectCreatedEnvelope(targetId, matching);
            var updates = record.detectUpdates(envelope);
            assertThat(updates).hasSize(1);

            var event = firstEventUpdate(updates);
            var message = EventEnvelope.of(event).message();
            assertEquals(matching, message);

            var otherTeamId = EvTeamId.newBuilder()
                    .setId("some-other-team-ID")
                    .build();
            var other = ProjectCreated.newBuilder()
                    .setTeamId(otherTeamId)
                    .setProjectId(subscriptionProjectId("Another Subscription Project"))
                    .build();
            var nonMatching = projectCreatedEnvelope(targetId, other);
            assertThat(record.detectUpdates(nonMatching)).isEmpty();
        }
    }

    @Test
    @DisplayName("detect that the entity state stopped matching the subscription criteria")
    void tellNoLongerMatching() {
        var targetId = projectId(TARGET_ID);
        var targetName = "previously-matching-project";

        var record = projectRecord(targetId, Filters.eq("name", targetName));

        var matching = projectWithName(targetName);
        var nonMatching = projectWithName("not-matching-anymore");

        var envelope = stateChangedEnvelope(targetId, matching, nonMatching);
        var updates = record.detectUpdates(envelope);
        assertThat(updates).hasSize(1);

        var entityUpdate = firstEntityUpdate(updates);
        assertTrue(entityUpdate.getNoLongerMatching());
    }

    @Test
    @DisplayName("be equal only to `SubscriptionRecord` that has same subscription")
    void beEqualToSame() {
        var oneSubscription = subscription();
        var breakingId = Subscriptions.newId("breaking-id");
        var otherSubscription = oneSubscription.toBuilder()
                .setId(breakingId)
                .build();
        @SuppressWarnings("QuestionableName")
        var one = SubscriptionRecord.of(oneSubscription);
        var similar = SubscriptionRecord.of(otherSubscription);
        var same = SubscriptionRecord.of(oneSubscription);
        assertNotEquals(one, similar);
        assertEquals(one, same);
    }

    /**
     * Creates a {@code SubscriptionRecord} with the target referring to the passed {@code TargetId}
     * and with the passed {@code filter} applied.
     *
     * @implNote This method accesses the package-private API, hence it isn't moved to the
     *         corresponding test environment.
     */
    private static SubscriptionRecord projectRecord(ProjectId targetId, Filter filter) {
        var compositeFilter = Filters.all(filter);
        var filters = singleton(compositeFilter);
        var target = composeTarget(AggProject.class, singleton(targetId), filters);

        var subscription = subscription(target);
        return SubscriptionRecord.of(subscription);
    }


    private static EntityStateUpdate firstEntityUpdate(List<SubscriptionUpdate> updates) {
        var update = updates.get(0);
        var updateList = update.getEntityUpdates().getUpdateList();
        assertEquals(1, updateList.size());

        return updateList.get(0);
    }

    private static Event firstEventUpdate(List<SubscriptionUpdate> updates) {
        var update = updates.get(0);
        var updateList = update.getEventUpdates().getEventList();
        assertEquals(1, updateList.size());

        return updateList.get(0);
    }
}
