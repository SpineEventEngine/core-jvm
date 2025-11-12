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
package io.spine.server;

import io.spine.client.QueryResponse;
import io.spine.core.Responses;
import io.spine.grpc.MemoizingObserver;
import io.spine.server.Given.ProjectDetailsRepository;
import io.spine.server.Given.ThrowingProjectDetailsRepository;
import io.spine.server.model.UnknownEntityStateTypeException;
import io.spine.testing.logging.mute.MuteLogging;
import io.spine.testing.server.model.ModelTests;
import io.spine.type.UnpublishedLanguageException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.google.common.collect.Sets.newHashSet;
import static com.google.common.truth.Truth.assertThat;
import static io.spine.grpc.StreamObservers.memoizingObserver;
import static io.spine.server.Given.CUSTOMERS_CONTEXT_NAME;
import static io.spine.server.Given.PROJECTS_CONTEXT_NAME;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("`QueryService` should")
class QueryServiceTest {

    private Set<BoundedContext> boundedContexts;
    private QueryService service;
    private MemoizingObserver<QueryResponse> responseObserver;

    @BeforeEach
    @SuppressWarnings("CheckReturnValue") // Calling builder.
    void setUp() {
        ModelTests.dropAllModels();

        setUpService(new ProjectDetailsRepository());
    }

    @AfterEach
    void tearDown() {
        for (var c : boundedContexts) {
            c.close();
        }
    }

    @Test
    @DisplayName("execute queries")
    void executeQueries() {
        var query = Given.AQuery.readAllProjects();
        service.read(query, responseObserver);
        checkOkResponse(responseObserver);
    }

    @Test
    @DisplayName("dispatch queries to proper Bounded Context")
    void dispatchQueriesToBc() {
        var query = Given.AQuery.readAllProjects();
        service.read(query, responseObserver);

        checkOkResponse(responseObserver);
    }

    @Test
    @MuteLogging
    @DisplayName("allow to create an instance serving no types")
    void notCreateWithNoBc() {
        assertDoesNotThrow(
                () -> QueryService.newBuilder()
                        .build()
        );
    }

    @Test
    @MuteLogging
    @DisplayName("return error if query failed to execute")
    void returnErrorOnQueryFail() {
        setUpService(new ThrowingProjectDetailsRepository());
        var query = Given.AQuery.readAllProjects();
        service.read(query, responseObserver);
        checkFailureResponse(responseObserver);
    }

    @Test
    @MuteLogging
    @DisplayName("reject queries for internal types")
    void rejectInternal() {
        var query = Given.AQuery.readInternalType();
        service.read(query, responseObserver);

        assertThat(responseObserver.getError())
                .isInstanceOf(UnpublishedLanguageException.class);
    }

    @Test
    @MuteLogging
    @DisplayName(
            "throw an `UnknownEntityStateTypeException` if the requested entity type is unknown")
    void failOnUnknownType() {
        var query = Given.AQuery.readUnknownType();
        service.read(query, responseObserver);
        var error = responseObserver.getError();
        var assertError = assertThat(error);
        assertError.isNotNull();
        assertError.isInstanceOf(UnknownEntityStateTypeException.class);
        var unknownTypeUrl = query.targetType().value();
        assertError.hasMessageThat().contains(unknownTypeUrl);
    }

    /**
     * Sets up the {@link QueryService} based on a context with a single given repository.
     */
    private void setUpService(ProjectDetailsRepository repository) {
        boundedContexts = newHashSet();
        responseObserver = memoizingObserver();
        // Create Projects Bounded Context with one repository and one projection.
        var projectsContext = BoundedContext.singleTenant(PROJECTS_CONTEXT_NAME).build();
        var projectRepo = new Given.ProjectAggregateRepository();
        projectsContext.register(projectRepo);
        projectsContext.register(repository);

        boundedContexts.add(projectsContext);

        // Create Customers Bounded Context with one repository.
        var customersContext = BoundedContext.singleTenant(CUSTOMERS_CONTEXT_NAME).build();
        var customerRepo = new Given.CustomerAggregateRepository();
        customersContext.register(customerRepo);
        boundedContexts.add(customersContext);

        var queryService = QueryService.newBuilder();
        for (var context : boundedContexts) {
            queryService.add(context);
        }

        service = queryService.build();
    }

    private static void checkOkResponse(MemoizingObserver<QueryResponse> responseObserver) {
        var responseHandled = responseObserver.firstResponse();
        assertNotNull(responseHandled);
        assertEquals(Responses.ok(), responseHandled.getResponse());
        assertTrue(responseObserver.isCompleted());
        assertNull(responseObserver.getError());
    }

    private static void checkFailureResponse(MemoizingObserver<QueryResponse> responseObserver) {
        assertTrue(responseObserver.responses().isEmpty());
        assertFalse(responseObserver.isCompleted());
        assertNotNull(responseObserver.getError());
    }
}
