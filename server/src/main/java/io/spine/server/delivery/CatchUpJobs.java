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

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;
import com.google.protobuf.Any;
import io.spine.core.TenantId;
import io.spine.type.TypeUrl;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Objects;

import static io.spine.server.delivery.CatchUpStatus.COMPLETED;

/**
 * The {@code CatchUp} jobs read from the {@link CatchUpStorage}, indexed by the inboxes
 * whose messages they process.
 *
 * <p>The jobs of the completed catch-ups stay in the storage. So, once the same projection
 * instances are caught up again, a message may match both a completed job and the new one.
 * Each message must be processed by at most one job, which {@link #jobFor(InboxMessage)}
 * chooses.
 *
 * <p>A catch-up may make {@code Delivery} process millions of messages, and each of them
 * passes through the {@link CatchUpStation}. So the jobs are indexed once per read from
 * the storage, and choosing the job for a message takes at most four lookups, however many
 * jobs and target IDs there are.
 */
final class CatchUpJobs {

    private static final CatchUpJobs NONE = new CatchUpJobs(ImmutableList.of());

    /**
     * The tenant under which the jobs with no tenant are indexed.
     */
    private static final TenantId NO_TENANT = TenantId.getDefaultInstance();

    private final ImmutableList<CatchUp> all;

    /**
     * The jobs catching up all the instances of a projection type, by the type and the tenant.
     */
    private final ImmutableMap<Scope, CatchUp> ofAllInstances;

    /**
     * The jobs catching up particular projection instances, by the type and the tenant,
     * and by the packed target ID.
     */
    private final ImmutableTable<Scope, Any, CatchUp> byTarget;

    private CatchUpJobs(ImmutableList<CatchUp> all) {
        this.all = all;
        var ofAll = new HashMap<Scope, CatchUp>();
        var ofTargets = HashBasedTable.<Scope, Any, CatchUp>create();
        for (var job : all) {
            if (job.getRequest().getTargetList().isEmpty()) {
                ofAll.merge(Scope.of(job), job, CatchUpJobs::preferred);
            } else {
                indexTargets(job, ofTargets);
            }
        }
        this.ofAllInstances = ImmutableMap.copyOf(ofAll);
        this.byTarget = ImmutableTable.copyOf(ofTargets);
    }

    /**
     * Puts the passed job to the passed table under each of its targets,
     * unless a {@linkplain #preferred(CatchUp, CatchUp) preferred} job is there already.
     */
    private static void indexTargets(CatchUp job, Table<Scope, Any, CatchUp> table) {
        var scope = Scope.of(job);
        for (var target : job.getRequest().getTargetList()) {
            var present = table.get(scope, target);
            table.put(scope, target, present == null ? job : preferred(present, job));
        }
    }

    /**
     * Indexes the passed jobs.
     *
     * @param jobs
     *         the jobs of any status
     */
    static CatchUpJobs of(Iterable<CatchUp> jobs) {
        return new CatchUpJobs(ImmutableList.copyOf(jobs));
    }

    /**
     * Returns no jobs.
     */
    static CatchUpJobs none() {
        return NONE;
    }

    /**
     * Returns all the jobs, in the order they were passed in.
     */
    ImmutableList<CatchUp> all() {
        return all;
    }

    /**
     * Chooses the job to process the passed message.
     *
     * <p>The chosen job always {@linkplain CatchUp#matches(InboxMessage) matches} the message.
     * If both a completed job and the job of an ongoing catch-up match, the ongoing one is
     * chosen. It is the newest of the matching jobs, because a catch-up cannot be started for
     * the instances that are still catching up. Between two jobs of the same rank, either may
     * be chosen: completed jobs process a message alike, and two ongoing jobs can come only
     * from catch-ups started at the same moment.
     *
     * <p>The jobs of the tenant of the message are looked up first, and then the jobs with
     * no tenant. One Bounded Context serves a projection type, so either all the jobs
     * of a type have a tenant, or none does.
     *
     * @param message
     *         the message to choose the job for
     * @return the chosen job, or {@code null} if no job matches the message
     */
    @Nullable CatchUp jobFor(InboxMessage message) {
        var inbox = message.getInboxId();
        var type = TypeUrl.parse(inbox.getTypeUrl());
        var target = inbox.getEntityId().getId();
        var ofTenant = jobFor(new Scope(type, message.tenant()), target);
        if (ofTenant != null) {
            return ofTenant;
        }
        return jobFor(new Scope(type, NO_TENANT), target);
    }

    private @Nullable CatchUp jobFor(Scope scope, Any target) {
        var forTarget = byTarget.get(scope, target);
        var forAll = ofAllInstances.get(scope);
        if (forTarget == null) {
            return forAll;
        }
        if (forAll == null) {
            return forTarget;
        }
        return preferred(forTarget, forAll);
    }

    /**
     * Returns the job that is not {@code COMPLETED}. If both jobs are completed,
     * or neither is, returns the first one.
     */
    private static CatchUp preferred(CatchUp job, CatchUp another) {
        if (job.getStatus() != COMPLETED) {
            return job;
        }
        return another.getStatus() != COMPLETED ? another : job;
    }

    /**
     * The projection type and the tenant, by which the jobs are indexed.
     */
    private static final class Scope {

        private final TypeUrl type;
        private final TenantId tenant;

        private Scope(TypeUrl type, TenantId tenant) {
            this.type = type;
            this.tenant = tenant;
        }

        /**
         * Returns the scope of the passed job.
         *
         * <p>A job with no tenant gets the {@link #NO_TENANT} one.
         */
        private static Scope of(CatchUp job) {
            var tenant = job.hasTenant() ? job.getTenant() : NO_TENANT;
            return new Scope(job.projectionType(), tenant);
        }

        @Override
        public boolean equals(@Nullable Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Scope other)) {
                return false;
            }
            return type.equals(other.type) && tenant.equals(other.tenant);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, tenant);
        }
    }
}
