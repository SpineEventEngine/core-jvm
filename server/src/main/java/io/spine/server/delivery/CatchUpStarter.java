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

package io.spine.server.delivery;

import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Timestamp;
import io.spine.annotation.Internal;
import io.spine.base.Identifier;
import io.spine.core.TenantId;
import io.spine.server.BoundedContext;
import io.spine.server.ServerEnvironment;
import io.spine.server.delivery.event.CatchUpRequested;
import io.spine.server.projection.ProjectionRepository;
import io.spine.server.tenant.TenantFunction;
import io.spine.server.type.EventClass;
import io.spine.type.TypeUrl;
import org.jspecify.annotations.Nullable;

import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Streams.stream;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

/**
 * The one who starts the catch-up process.
 *
 * <p>Checks whether the catch-up is already started for the requested IDs before posting
 * an event that requests the catch-up. The event is posted to the System context of
 * the Bounded Context of the projection repository, where a catch-up process handles it.
 *
 * @param <I>
 *         the type of the identifiers of the catching-up entities
 */
@Internal
public final class CatchUpStarter<I> {

    private final BoundedContext context;
    private final TypeUrl projectionStateType;
    private final CatchUpStorage storage;
    private final ImmutableSet<EventClass> eventClasses;

    private CatchUpStarter(ProjectionRepository<I, ?, ?> repository, BoundedContext context) {
        this.context = context;
        this.projectionStateType = repository.entityStateType();
        this.eventClasses = repository.messageClasses();
        this.storage = ServerEnvironment.instance()
                                        .delivery()
                                        .catchUpStorage();
    }

    /**
     * Creates a new starter of the catch-up for the projections of the given repository.
     *
     * @param repository
     *         the repository of the projections to catch up
     * @param context
     *         the context of the repository
     * @param <I>
     *         the type of the identifiers of the catching-up entities
     * @return the new instance
     */
    public static <I> CatchUpStarter<I> of(ProjectionRepository<I, ?, ?> repository,
                                           BoundedContext context) {
        checkNotNull(repository);
        checkNotNull(context);
        return new CatchUpStarter<>(repository, context);
    }

    /**
     * Starts the catch-up restricting it to the set of projection instances by certain identifiers.
     *
     * <p>In a multi-tenant context, the catch-up is started for the current tenant.
     *
     * <p>The check for a catch-up already in progress sees another catch-up only once its
     * request is processed. That may happen after this method returns: for example, when
     * the system events are posted in parallel, or when the {@code Delivery} delivers messages
     * later. So two calls for the same instances, one right after the other, may both pass
     * the check.
     *
     * @param ids
     *         the IDs of the projection instances to catch-up, or {@code null} if all entities of
     *         this kind need to catch up.
     * @param since
     *         since when the catch-up is going to read the events
     * @return identifier of the catch-up operation
     * @throws CatchUpAlreadyStartedException
     *         if the catch-up is already in progress for at least one of the requested entities
     */
    public CatchUpId start(@Nullable Set<I> ids, Timestamp since)
            throws CatchUpAlreadyStartedException {
        checkNotActive(ids);

        var request = buildRequest(ids, since);
        var id = CatchUpId.newBuilder()
                .setUuid(Identifier.newUuid())
                .setProjectionType(projectionStateType.value())
                .build();
        var eventMessage = CatchUpRequested.newBuilder()
                .setId(id)
                .setRequest(request)
                .build();
        context.systemClient()
               .writeSide()
               .postEvent(eventMessage);
        return id;
    }

    @SuppressWarnings("MethodWithMultipleLoops")
    private CatchUp.Request buildRequest(@Nullable Set<I> ids, Timestamp since) {
        var requestBuilder = CatchUp.Request.newBuilder();
        if (ids != null) {
            for (var id : ids) {
                var packed = Identifier.pack(id);
                requestBuilder.addTarget(packed);
            }
        }

        requestBuilder.setSinceWhen(since);
        for (var eventClass : eventClasses) {
            var name = eventClass.typeName();
            requestBuilder.addEventType(name.value());
        }
        return requestBuilder.build();
    }

    /**
     * Checks that no ongoing catch-up of the current tenant includes the passed instances.
     */
    private void checkNotActive(@Nullable Set<I> ids) throws CatchUpAlreadyStartedException {
        var tenant = currentTenant();
        var ongoing = storage.readByType(projectionStateType);
        var active = stream(ongoing)
                .filter(catchUp -> CatchUpStatus.COMPLETED != catchUp.getStatus())
                .filter(catchUp -> catchUp.isOfTenant(tenant))
                .collect(toList());
        var alreadyCatchingUp = hasIntersections(active, ids);
        if (alreadyCatchingUp) {
            throw new CatchUpAlreadyStartedException(projectionStateType, ids);
        }
    }

    private TenantId currentTenant() {
        var function = new TenantFunction<TenantId>(context.isMultitenant()) {
            @Override
            public TenantId apply(TenantId tenant) {
                return tenant;
            }
        };
        return checkNotNull(function.execute());
    }

    @SuppressWarnings("MethodWithMultipleLoops")
    private static boolean hasIntersections(Iterable<CatchUp> ongoing, @Nullable Set<?> ids) {
        if (ids == null) {
            return ongoing.iterator()
                          .hasNext();
        }
        if (ids.isEmpty()) {
            return false;
        }
        var packedIds = ids.stream()
                .map(Identifier::pack)
                .collect(toSet());
        for (var ongoingProcess : ongoing) {
            var targets = ongoingProcess.getRequest()
                                        .getTargetList();
            if (targets.isEmpty()) {
                return true;
            }
            for (var target : targets) {
                if (packedIds.contains(target)) {
                    return true;
                }
            }
        }
        return false;
    }
}
