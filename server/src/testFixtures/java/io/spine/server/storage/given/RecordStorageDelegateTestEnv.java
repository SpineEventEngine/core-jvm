/*
 * Copyright 2025, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
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
import static io.spine.protobuf.Messages.isDefault;
import static io.spine.protobuf.ProtocolMessageEnums.isDefault;
import static io.spine.server.storage.given.GivenStorageProject.newState;
import static io.spine.test.storage.StgProject.Column.dueDate;
import static io.spine.test.storage.StgProject.Status.DONE;
import static java.util.stream.Collectors.toList;

public final class RecordStorageDelegateTestEnv {

    /** Prevents instantiation of this utility class. */
    private RecordStorageDelegateTestEnv() {
    }

    public static StgProjectId generateId() {
        return StgProjectId.newBuilder()
                           .setId(newUuid())
                           .build();
    }

    /**
     * Creates the field mask which only has {@code ID} and {@code due_date} fields.
     */
    public static FieldMask idAndDueDate() {
        return FieldMask.newBuilder()
                        .addPaths("id")
                        .addPaths(dueDate().name()
                                           .value())
                        .build();
    }

    /**
     * Asserts that the given record has only its ID and due date set.
     *
     * <p>The rest of the fields are asserted to have a default value.
     *
     * @param actual
     *         the record to check
     */
    public static void assertOnlyIdAndDueDate(StgProject actual) {
        assertThat(isDefault(actual.getId())).isFalse();
        assertThat(isDefault(actual.getDueDate())).isFalse();

        assertThat(actual.getName()).isEmpty();
        assertThat(actual.getTaskList()).isEmpty();
        assertThat(isDefault(actual.getStatus())).isTrue();
        assertThat(actual.getIdString()).isEmpty();
        assertThat(actual.getInternal()).isFalse();
        assertThat(isDefault(actual.getWrappedState())).isTrue();
        assertThat(isDefault(actual.getProjectVersion())).isTrue();
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
