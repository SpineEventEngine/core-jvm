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

package io.spine.server;

import io.spine.client.QueryResponse;
import io.spine.core.Responses;
import io.spine.grpc.MemoizingObserver;
import io.spine.server.Given.ProjectDetailsRepository;
import io.spine.server.Given.ThrowingProjectDetailsRepository;
import io.spine.testing.logging.mute.MuteLogging;
import io.spine.testing.server.TestApplications;
import io.spine.testing.server.model.ModelTests;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.jspecify.annotations.Nullable;

import java.util.Optional;

import static io.spine.core.ErrorCase.UNSUPPORTED_REQUEST;
import static io.spine.grpc.StreamObservers.errorCaseOf;
import static io.spine.grpc.StreamObservers.memoizingObserver;
import static io.spine.server.Given.CUSTOMERS_CONTEXT_NAME;
import static io.spine.server.Given.PROJECTS_CONTEXT_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("`QueryService` should")
class QueryServiceTest {

    private @Nullable Application application;
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
        closeApplication();
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

        assertEquals(Optional.of(UNSUPPORTED_REQUEST),
                     errorCaseOf(responseObserver.getError()));
    }

    @Test
    @MuteLogging
    @DisplayName("answer `UNSUPPORTED_REQUEST` if the requested entity type is unknown")
    void failOnUnknownType() {
        var query = Given.AQuery.readUnknownType();
        service.read(query, responseObserver);
        assertEquals(Optional.of(UNSUPPORTED_REQUEST),
                     errorCaseOf(responseObserver.getError()));
    }

    /**
     * Sets up the {@link QueryService} based on a context with a single given repository.
     *
     * <p>Closes the application of the previous service first, so that the new contexts can
     * serve the same entity state types.
     */
    private void setUpService(ProjectDetailsRepository repository) {
        closeApplication();
        responseObserver = memoizingObserver();
        // Create Projects Bounded Context with one repository and one projection.
        var projects = BoundedContextBuilder.assumingTests(PROJECTS_CONTEXT_NAME)
                .add(new Given.ProjectAggregateRepository())
                .add(repository);

        // Create Customers Bounded Context with one repository.
        var customers = BoundedContextBuilder.assumingTests(CUSTOMERS_CONTEXT_NAME)
                .add(new Given.CustomerAggregateRepository());

        var app = TestApplications.newBuilder()
                .add(projects)
                .add(customers)
                .build();
        application = app;
        service = QueryService.of(app);
    }

    private void closeApplication() {
        if (application != null) {
            application.close();
            application = null;
        }
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
