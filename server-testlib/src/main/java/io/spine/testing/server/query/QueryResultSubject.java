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

package io.spine.testing.server.query;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.common.truth.extensions.proto.IterableOfProtosSubject;
import com.google.common.truth.extensions.proto.ProtoSubject;
import io.spine.annotation.VisibleForTesting;
import io.spine.base.EntityState;
import io.spine.client.QueryResponse;
import io.spine.core.Status;
import io.spine.core.Status.StatusCase;
import io.spine.core.Version;
import io.spine.testing.server.entity.EntityVersionSubject;
import io.spine.testing.server.entity.IterableEntityVersionSubject;

import java.util.Collection;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.extensions.proto.ProtoTruth.protos;
import static io.spine.testing.server.entity.IterableEntityVersionSubject.entityVersions;
import static io.spine.testing.server.query.ResponseStatusSubject.responseStatus;

/**
 * A set of assertions for a {@link io.spine.client.Query Query} execution result.
 *
 * <p>The class base methods check a set of {@linkplain EntityState entity states} received in the
 * {@link QueryResponse}. The subject can be used as follows:
 * <pre>
 * context.assertQueryResult(query)
 *        .containsExactly(state1, state2);
 * </pre>
 *
 * <p>If the actual queried messages are required, the subject can return them as follows:
 * <pre>
 * context.assertQueryResult(query)
 *        .actual();
 * </pre>
 *
 * <p>There are also convenience methods for checking response {@link Status} and received entity
 * {@linkplain Version versions}, as well as some others.
 *
 * <p>This class is not a "typical" {@link Subject} in a sense that it's not accessible to the
 * outer world through it's {@linkplain #queryResult() factory}, but is rather created with a
 * custom {@code static} {@linkplain #assertQueryResult(QueryResponse) method}.
 */
@VisibleForTesting
public final class QueryResultSubject
        extends IterableOfProtosSubject<EntityState<?>> {

    /**
     * A helper {@code Subject} which allows to check the {@link QueryResponse} status.
     *
     * <p>Is effectively {@code final}, as the only way to create an instance of
     * {@code QueryResultSubject} is to use the {@link #assertQueryResult(QueryResponse)} method.
     */
    private ResponseStatusSubject statusSubject;

    /**
     * A helper {@code Subject} which allows to verify the {@linkplain Version versions} of
     * entities in {@link QueryResponse}.
     *
     * <p>Is effectively {@code final}, as the only way to create an instance of
     * {@code QueryResultSubject} is to use the {@link #assertQueryResult(QueryResponse)} method.
     */
    private IterableEntityVersionSubject versionsSubject;

    private final ImmutableList<EntityState<?>> actual;

    private QueryResultSubject(FailureMetadata failureMetadata,
                               Iterable<EntityState<?>> entityStates) {
        super(failureMetadata, entityStates);
        this.actual = ImmutableList.copyOf(entityStates);
    }

    /**
     * Creates a new instance of the subject.
     *
     * <p>Unlike other {@code Subject}s, the {@code QueryResultSubject} does not accept
     * {@code null} arguments, as {@code null} {@code QueryResponse} always indicates an error.
     */
    public static QueryResultSubject assertQueryResult(QueryResponse queryResponse) {
        checkNotNull(queryResponse, "`QueryResponse` must never be `null`.");

        Iterable<EntityState<?>> entityStates = extractEntityStates(queryResponse);
        var subject = assertAbout(queryResult()).that(entityStates);
        subject.initChildSubjects(queryResponse);
        return subject;
    }

    private void initChildSubjects(QueryResponse queryResponse) {
        var status = extractStatus(queryResponse);
        statusSubject = check("getResponse().getStatus()").about(responseStatus())
                                                          .that(status);

        Iterable<Version> versions = extractEntityVersions(queryResponse);
        versionsSubject = check("getEntityVersions()").about(entityVersions())
                                                      .that(versions);
    }

    /**
     * Obtains the actually queried entity states.
     */
    public ImmutableList<EntityState<?>> actual() {
        return actual;
    }

    /**
     * Verifies the status of the received {@link QueryResponse}.
     */
    public void hasStatus(StatusCase status) {
        checkNotNull(status);
        statusSubject.hasStatusCase(status);
    }

    /**
     * Obtains a {@code ResponseStatusSubject} for more detailed status checks.
     */
    public ResponseStatusSubject hasStatusThat() {
        return statusSubject;
    }

    /**
     * Verifies that the {@link QueryResponse} yields a single entity and returns a
     * {@code ProtoSubject} for its {@link EntityState state}.
     */
    public ProtoSubject containsSingleEntityStateThat() {
        assertContainsSingleItem();
        var state = actual.iterator().next();
        var subject = check("singleEntityState()").about(protos())
                                                  .that(state);
        return subject;
    }

    /**
     * Verifies that the {@link QueryResponse} yields a single entity and returns a {@code Subject}
     * for its {@link Version}.
     */
    public EntityVersionSubject containsSingleEntityVersionThat() {
        return versionsSubject.containsSingleEntityVersionThat();
    }

    /**
     * Returns a {@code Subject} to assess the versions of entities yielded in the
     * {@link QueryResponse}.
     */
    public IterableEntityVersionSubject containsEntityVersionListThat() {
        return versionsSubject;
    }

    private void assertContainsSingleItem() {
        hasSize(1);
    }

    private static ImmutableList<EntityState<?>> extractEntityStates(QueryResponse queryResponse) {
        ImmutableList<EntityState<?>> result = ImmutableList.copyOf(queryResponse.states());
        return result;
    }

    private static Collection<Version> extractEntityVersions(QueryResponse queryResponse) {
        return queryResponse.versions();
    }

    private static Status extractStatus(QueryResponse queryResponse) {
        return queryResponse
                .getResponse()
                .getStatus();
    }

    static Subject.Factory<QueryResultSubject, Iterable<EntityState<?>>> queryResult() {
        return QueryResultSubject::new;
    }
}
