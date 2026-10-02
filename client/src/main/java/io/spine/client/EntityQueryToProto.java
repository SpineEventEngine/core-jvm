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

import com.google.common.collect.ImmutableList;
import io.spine.query.EntityQuery;
import io.spine.query.QueryPredicate;
import io.spine.query.Subject;
import io.spine.query.SubjectParameter;

import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.spine.client.CompositeFilter.CompositeOperator.ALL;
import static io.spine.client.CompositeFilter.CompositeOperator.EITHER;
import static io.spine.client.Filter.Operator.EQUAL;
import static io.spine.client.Filter.Operator.GREATER_OR_EQUAL;
import static io.spine.client.Filter.Operator.GREATER_THAN;
import static io.spine.client.Filter.Operator.LESS_OR_EQUAL;
import static io.spine.client.Filter.Operator.LESS_THAN;
import static io.spine.client.Filters.createFilter;
import static io.spine.client.OrderBy.Direction.ASCENDING;
import static io.spine.client.OrderBy.Direction.DESCENDING;
import static io.spine.query.Direction.ASC;
import static io.spine.query.LogicalOperator.AND;

/**
 * Transforms {@link io.spine.query.EntityQuery} instances to the Protobuf-based Query objects.
 *
 * <p>Such a transformation is required in order to transfer the {@code EntityQuery}
 * instances over the wire.
 *
 * <p>The field mask of an {@code EntityQuery} is not transferred, as field masks are
 * no longer supported.
 *
 * @see io.spine.query.EntityQueryBuilder#build(Function)
 */
public final class EntityQueryToProto implements Function<EntityQuery<?, ?, ?>, Query> {

    /**
     * A factory of the {@code Query} instances that is used in a transformation process.
     */
    private final QueryFactory factory;

    private EntityQueryToProto(QueryFactory factory) {
        this.factory = factory;
    }

    /**
     * Creates an instance of a transformer in the context of the passed {@code ClientRequest}.
     *
     * <p>The instances of {@link Query} created by the transformer will rely on the properties
     * of the client associated with the request.
     *
     * @param request
     *         the request in scope of which the conversion is done
     * @return new instance of the query transformer
     */
    public static EntityQueryToProto transformWith(ClientRequest request) {
        checkNotNull(request);
        var client = request.client();
        var user = request.user();
        var factory = client.requestOf(user);
        return transformWith(factory.query());
    }

    /**
     * Creates an instance of a transformer that would use the passed query factory.
     *
     * @param factory
     *         the query factory
     * @return new instance of the query transformer
     */
    public static EntityQueryToProto transformWith(QueryFactory factory) {
        checkNotNull(factory);
        return new EntityQueryToProto(factory);
    }

    @Override
    public Query apply(EntityQuery<?, ?, ?> query) {
        var entityStateType = query.subject().recordType();
        var builder = factory.select(entityStateType);
        var result = toProtoQuery(builder, query);
        return result;
    }

    private static Query toProtoQuery(QueryBuilder builder, EntityQuery<?, ?, ?> query) {
        Subject<?, ?> subject = query.subject();
        addIds(builder, subject);
        addPredicate(builder, subject.predicate());
        addSorting(builder, query);
        addLimit(builder, query);
        return builder.build();
    }

    private static void addLimit(QueryBuilder builder, EntityQuery<?, ?, ?> query) {
        var originLimit = query.limit();
        if (originLimit != null) {
            builder.limit(originLimit);
        }
    }

    private static void addSorting(QueryBuilder builder, EntityQuery<?, ?, ?> query) {
        for (io.spine.query.SortBy<?, ?> sortBy : query.sorting()) {
            var columnName = sortBy.column()
                                   .name()
                                   .value();
            var direction = sortBy.direction() == ASC ? ASCENDING : DESCENDING;
            builder.orderBy(columnName, direction);
        }
    }

    private static void addPredicate(QueryBuilder builder, QueryPredicate<?> predicate) {
        var composite = toCompositeFilter(predicate);
        builder.where(composite);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")      /* Builder is using in step-by-step mode. */
    private static CompositeFilter toCompositeFilter(QueryPredicate<?> predicate) {
        var operator = predicate.operator();
        var builder = CompositeFilter.newBuilder();
        builder.setOperator(operator == AND ? ALL
                                            : EITHER);
        var parameters = predicate.allParams();
        var filters = toFilters(parameters);
        builder.addAllFilter(filters);

        var childFilters =
                predicate.children()
                         .stream()
                         .map(EntityQueryToProto::toCompositeFilter)
                         .collect(toImmutableList());
        var childCompositeFilter =
                builder.addAllCompositeFilter(childFilters)
                       .build();
        return childCompositeFilter;
    }

    private static ImmutableList<Filter>
    toFilters(ImmutableList<SubjectParameter<?, ?, ?>> params) {
        ImmutableList.Builder<Filter> filters = ImmutableList.builder();
        for (var parameter : params) {
            var filter = asProtoFilter(parameter);
            filters.add(filter);
        }
        var filterList = filters.build();
        return filterList;
    }

    private static Filter asProtoFilter(SubjectParameter<?, ?, ?> parameter) {
        var value = parameter.value();
        var comparison = parameter.operator();
        var column = parameter.column();
        var colName = column.name();

        var result = switch (comparison) {
            case EQUALS -> createFilter(colName, value, EQUAL);
            case GREATER_THAN -> createFilter(colName, value, GREATER_THAN);
            case GREATER_OR_EQUALS -> createFilter(colName, value, GREATER_OR_EQUAL);
            case LESS_THAN -> createFilter(colName, value, LESS_THAN);
            case LESS_OR_EQUALS -> createFilter(colName, value, LESS_OR_EQUAL);
        };
        return result;
    }

    private static void addIds(QueryBuilder builder, Subject<?, ?> subject) {
        var ids = subject.id().values();
        if (!ids.isEmpty()) {
            builder.byId(ids);
        }
    }
}
