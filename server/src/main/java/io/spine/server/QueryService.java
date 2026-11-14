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

import com.google.protobuf.Message;
import io.grpc.BindableService;
import io.grpc.stub.StreamObserver;
import io.spine.base.Error;
import io.spine.base.EventMessage;
import io.spine.client.Query;
import io.spine.client.QueryResponse;
import io.spine.client.grpc.QueryServiceGrpc;
import io.spine.core.TenantId;
import io.spine.logging.WithLogging;
import io.spine.server.app.RefusedRequest;
import io.spine.server.stand.InvalidRequestException;
import io.spine.server.stand.UnsupportedRequestException;
import io.spine.system.server.event.QueryExecutionFailed;
import io.spine.system.server.event.QueryTenantNotExpected;
import io.spine.system.server.event.QueryTenantNotSpecified;
import io.spine.system.server.event.QueryValidationFailed;
import io.spine.system.server.event.UnsupportedQueryReceived;
import io.spine.type.TypeUrl;
import org.jspecify.annotations.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.core.ErrorCase.INVALID_REQUEST;
import static io.spine.core.ErrorCase.UNSUPPORTED_REQUEST;
import static io.spine.type.ProtoTexts.shortDebugString;
import static java.lang.String.format;

/**
 * The {@code QueryService} provides a synchronous way to fetch read-side state from the server.
 *
 * <p>The service serves the queries with the Bounded Contexts of an {@link Application}.
 * A query to a multi-tenant context must have a tenant registered in
 * the {@linkplain Application#tenantIndex() tenant index} of the application, and a query to
 * a single-tenant context must have no tenant.
 *
 * <p>For asynchronous read-side updates please see {@link SubscriptionService}.
 */
public final class QueryService
        extends QueryServiceGrpc.QueryServiceImplBase
        implements WithLogging {

    private final QueryServiceImpl impl;

    @SuppressWarnings("ThisEscapedInObjectConstruction") // Safe as we're injecting at the end.
    private QueryService(TypeDictionary types, Application application) {
        super();
        this.impl = new QueryServiceImpl(this, types, application);
    }

    /**
     * Creates the service that serves the queries with the Bounded Contexts
     * of the given application.
     */
    public static QueryService of(Application application) {
        checkNotNull(application);
        var dictionary = TypeDictionary.newBuilder();
        application.contexts().forEach(
                context -> dictionary.putAll(context, c -> c.stand().exposedTypes())
        );
        return new QueryService(dictionary.build(), application);
    }

    /**
     * Executes the passed query returning results to the passed observer.
     *
     * <p>A query that cannot be served fails the call with a status that carries only
     * the {@linkplain io.spine.core.ErrorCase kind} of the problem, which the client obtains with
     * {@link io.spine.grpc.StreamObservers#errorCaseOf(Throwable)
     * StreamObservers.errorCaseOf(Throwable)}. The details of the problem stay on
     * the server side.
     */
    @Override
    public void read(Query query, StreamObserver<QueryResponse> observer) {
        logger().atDebug().log(() -> format(
                "Incoming query: `%s`.", shortDebugString(query)));
        impl.serve(query, observer, null);
    }

    private static final class QueryServiceImpl extends ServiceDelegate<Query, QueryResponse> {

        QueryServiceImpl(BindableService service, TypeDictionary types, Application application) {
            super(service, types, application);
        }

        @Override
        protected TypeUrl enclosedMessageType(Query request) {
            return request.getTarget()
                          .type();
        }

        @Override
        protected TenantId tenantOf(Query query) {
            return query.getContext()
                        .getTenantId();
        }

        @Override
        protected Message idOf(Query query) {
            return query.getId();
        }

        @Override
        protected RefusedRequest refusedRequest(Query query) {
            return RefusedRequest.newBuilder()
                    .setQuery(query)
                    .build();
        }

        @Override
        protected EventMessage tenantNotSpecified(Query query) {
            return QueryTenantNotSpecified.newBuilder()
                    .setId(query.getId())
                    .setActorContext(query.getContext())
                    .build();
        }

        @Override
        protected EventMessage tenantNotExpected(Query query, TenantId tenant) {
            return QueryTenantNotExpected.newBuilder()
                    .setId(query.getId())
                    .setActorContext(query.getContext())
                    .setTenant(tenant)
                    .build();
        }

        @Override
        protected void serve(BoundedContext context,
                             Query query,
                             StreamObserver<QueryResponse> observer,
                             @Nullable Object params) {
            try {
                context.stand()
                       .execute(query, observer);
            } catch (InvalidRequestException e) {
                var failed = QueryValidationFailed.newBuilder()
                        .setId(query.getId())
                        .setValidationError(e.validationError())
                        .setActorContext(query.getContext())
                        .build();
                reject(context, query, failed, INVALID_REQUEST, observer);
            } catch (UnsupportedRequestException e) {
                var unsupported = UnsupportedQueryReceived.newBuilder()
                        .setId(query.getId())
                        .setTypeUrl(query.getTarget()
                                         .getType())
                        .setActorContext(query.getContext())
                        .build();
                reject(context, query, unsupported, UNSUPPORTED_REQUEST, observer);
            }
        }

        /**
         * Responds with {@link io.spine.core.ErrorCase#UNSUPPORTED_REQUEST UNSUPPORTED_REQUEST},
         * since no bounded context exposes the target type of the query.
         */
        @Override
        protected void serveNoContext(Query query,
                                      StreamObserver<QueryResponse> observer,
                                      @Nullable Object params) {
            refuseNotServed(query, observer);
        }

        @Override
        protected EventMessage failureEvent(Query query, Error error) {
            return QueryExecutionFailed.newBuilder()
                    .setId(query.getId())
                    .setError(error)
                    .setActorContext(query.getContext())
                    .build();
        }
    }
}
