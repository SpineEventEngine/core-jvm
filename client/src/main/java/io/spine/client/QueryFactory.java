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

import io.spine.base.EntityState;
import io.spine.core.ActorContext;
import org.jspecify.annotations.Nullable;

import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.base.Identifier.newUuid;
import static io.spine.client.Targets.composeTarget;
import static java.lang.String.format;

/**
 * A factory of {@link Query} instances.
 *
 * <p>Uses the supplied {@link ActorRequestFactory} as a source of the query meta information,
 * such as the actor.
 *
 * @see ActorRequestFactory#query()
 */
public final class QueryFactory {

    private final ActorContext actorContext;

    /**
     * Creates a new {@code QueryFactory} that uses supplied {@code actorRequestFactory}
     * to generate the {@code ActorContext}.
     */
    QueryFactory(ActorRequestFactory actorRequestFactory) {
        checkNotNull(actorRequestFactory);
        this.actorContext = actorRequestFactory.newActorContext();
    }

    /**
     * Creates a new instance of {@link QueryBuilder} for the further {@link Query}
     * construction.
     *
     * @param targetType
     *         the {@linkplain Query query} target type
     * @return new instance of {@link QueryBuilder}
     */
    public QueryBuilder select(Class<? extends EntityState<?>> targetType) {
        checkNotNull(targetType);
        var queryBuilder = new QueryBuilder(targetType, this);
        return queryBuilder;
    }

    /**
     * Creates a {@link Query} to read certain entity states by IDs.
     *
     * <p>The mask paths are ignored. The query is the same as the one created by
     * {@link #byIds(Class, Set)}, except that the passed set of IDs must not be empty.
     *
     * @param entityClass
     *         the class of a target entity
     * @param ids
     *         the IDs of interest of type {@link io.spine.base.Identifier#checkSupported(Class)
     *         which is supported as identifier}
     * @param maskPaths
     *         the ignored property paths
     * @return an instance of {@code Query} formed according to the passed parameters
     * @deprecated Field masks are no longer supported. The query results always contain
     *         all the fields. Please use {@link #byIds(Class, Set)} instead.
     */
    @Deprecated
    public Query byIdsWithMask(Class<? extends EntityState<?>> entityClass,
                               Set<?> ids,
                               String... maskPaths) {
        checkSpecified(entityClass);
        checkNotNull(ids);
        checkArgument(!ids.isEmpty(), "Entity ID set must not be empty.");
        checkNotNull(maskPaths);
        return byIds(entityClass, ids);
    }

    /**
     * Creates a {@link Query} to read certain entity states by IDs.
     *
     * <p>Allows specifying a set of identifiers to be used during the {@code Query} processing.
     * The processing results will contain only the entities whose IDs are present among
     * the {@code ids}.
     *
     * @param entityClass
     *         the class of a target entity
     * @param ids
     *         the IDs of interest of type {@link io.spine.base.Identifier#checkSupported(Class)
     *         which is supported as identifier}
     * @return an instance of {@code Query} formed according to the passed parameters
     * @throws IllegalArgumentException
     *         if any of IDs have invalid type or are {@code null}
     */
    public Query byIds(Class<? extends EntityState<?>> entityClass, Set<?> ids) {
        checkSpecified(entityClass);
        checkNotNull(ids);
        return composeQuery(entityClass, ids, null);
    }

    /**
     * Creates a {@link Query} to read all states of a certain entity.
     *
     * <p>The mask paths are ignored. The query is the same as the one created by
     * {@link #all(Class)}.
     *
     * @param entityClass
     *         the class of a target entity
     * @param maskPaths
     *         the ignored property paths
     * @return an instance of {@code Query} formed according to the passed parameters
     * @deprecated Field masks are no longer supported. The query results always contain
     *         all the fields. Please use {@link #all(Class)} instead.
     */
    @Deprecated
    public Query allWithMask(Class<? extends EntityState<?>> entityClass,
                             String... maskPaths) {
        checkSpecified(entityClass);
        checkNotNull(maskPaths);
        return all(entityClass);
    }

    /**
     * Creates a {@link Query} to read all states of a certain entity.
     *
     * @param entityClass
     *         the class of a target entity
     * @return an instance of {@code Query} formed according to the passed parameters
     */
    public Query all(Class<? extends EntityState<?>> entityClass) {
        checkSpecified(entityClass);
        return composeQuery(entityClass, null, null);
    }

    private Query composeQuery(Class<? extends EntityState<?>> entityClass,
                               @Nullable Set<?> ids,
                               @Nullable Set<CompositeFilter> filters) {
        var format = responseFormat(null, 0);
        var builder = queryBuilderFor(entityClass, ids, filters).setFormat(format);
        var query = newQuery(builder);
        return query;
    }

    private static void checkSpecified(Class<? extends EntityState<?>> entityClass) {
        checkNotNull(entityClass, "The class of `Entity` must be specified for a `Query`.");
    }

    private static Query.Builder queryBuilderFor(Class<? extends EntityState<?>> entityClass,
                                                 @Nullable Set<?> ids,
                                                 @Nullable Set<CompositeFilter> filters) {
        var target = composeTarget(entityClass, ids, filters);
        var builder = queryBuilderFor(target);
        return builder;
    }

    Query composeQuery(Target target) {
        checkTargetNotNull(target);
        return composeQuery(target, 0, null);
    }

    Query composeQuery(Target target, OrderBy orderBy) {
        checkTargetNotNull(target);
        checkNotNull(orderBy);
        return composeQuery(target, 0, orderBy);
    }

    Query composeQuery(Target target, OrderBy orderBy, int limit) {
        checkTargetNotNull(target);
        checkNotNull(orderBy);
        return composeQuery(target, limit, orderBy);
    }

    private Query composeQuery(Target target, int limit, @Nullable OrderBy orderBy) {
        var format = responseFormat(orderBy, limit);
        var builder = queryBuilderFor(target).setFormat(format);
        var query = newQuery(builder);
        return query;
    }

    private static Query.Builder queryBuilderFor(Target target) {
        return Query
                .newBuilder()
                .setTarget(target);
    }

    private static void checkTargetNotNull(Target target) {
        checkNotNull(target, "A `Target` must be specified to compose a `Query`.");
    }

    private Query newQuery(Query.Builder builder) {
        return builder
                .setId(newQueryId())
                .setContext(actorContext)
                .build();
    }

    private static QueryId newQueryId() {
        var formattedId = format("query-%s", newUuid());
        return QueryId.newBuilder()
                .setValue(formattedId)
                .build();
    }

    private static ResponseFormat responseFormat(@Nullable OrderBy ordering, int limit) {
        var result = ResponseFormat.newBuilder();
        if (ordering != null) {
            result.addOrderBy(ordering);
        }
        if (limit > 0) {
            result.setLimit(limit);
        }
        return result.build();
    }
}
