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

package io.spine.server.storage.given;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.FieldMask;
import com.google.protobuf.Timestamp;
import io.spine.test.storage.StgProject;
import io.spine.test.storage.StgProjectId;

import java.util.Collection;
import java.util.List;
import java.util.stream.IntStream;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.base.Identifier.newUuid;
import static io.spine.server.storage.given.GivenStorageProject.newState;
import static io.spine.test.storage.StgProject.Column.dueDate;
import static io.spine.test.storage.StgProject.Status.DONE;
import static java.util.stream.Collectors.toList;

public final class DelegatingRecordStorageTestEnv {

    /** Prevents instantiation of this utility class. */
    private DelegatingRecordStorageTestEnv() {
    }

    public static StgProjectId generateId() {
        return StgProjectId.newBuilder()
                           .setId(newUuid())
                           .build();
    }

    /**
     * Creates the field mask that only has {@code ID} and {@code due_date} fields.
     */
    public static FieldMask idAndDueDate() {
        return FieldMask.newBuilder()
                        .addPaths("id")
                        .addPaths(dueDate().name()
                                           .value())
                        .build();
    }

    /**
     * Strips the given set of identifiers to six.
     */
    public static ImmutableList<StgProjectId> halfDozenOf(ImmutableSet<StgProjectId> ids) {
        return ids.asList()
                  .subList(0, 6);
    }

    /**
     * Creates two {@code StgProject} instances in the {@code DONE} state with the given due date.
     *
     * @param dueDate
     *         the due date to set
     */
    public static ImmutableList<StgProject> coupleOfDone(Timestamp dueDate) {
        return ImmutableList.of(
                newState(generateId(), DONE, dueDate),
                newState(generateId(), DONE, dueDate)
        );
    }

    public static void assertHaveIds(Collection<StgProject> items, Collection<StgProjectId> ids) {
        assertThat(items).hasSize(ids.size());
        var actualIds = toIds(items);
        assertThat(actualIds).containsExactlyElementsIn(ids);
    }

    /**
     * Transforms the given records into a list of their identifiers.
     */
    public static List<StgProjectId> toIds(Collection<StgProject> records) {
        return records.stream()
                      .map(StgProject::getId)
                      .collect(toList());
    }

    /**
     * Creates twelve records with random IDs and names.
     *
     * <p>Each record has the due date set to be a day ahead of the current time and
     * the {@code CREATED} project status.
     */
    public static ImmutableMap<StgProjectId, StgProject> dozenOfRecords() {
        ImmutableMap.Builder<StgProjectId, StgProject> builder = ImmutableMap.builder();
        IntStream.range(0, 11)
                 .forEach((i) -> {
                     var id = generateId();
                     builder.put(id, newState(id));
                 });
        return builder.build();
    }
}
