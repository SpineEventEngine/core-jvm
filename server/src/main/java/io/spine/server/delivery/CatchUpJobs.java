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
import org.jspecify.annotations.Nullable;

import java.util.HashMap;

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
 * the storage, and choosing the job for a message takes two lookups, however many jobs and
 * target IDs there are.
 */
final class CatchUpJobs {

    private final ImmutableList<CatchUp> all;

    /**
     * The jobs catching up all the instances of a projection type, by the type.
     */
    private final ImmutableMap<String, CatchUp> ofAllInstances;

    /**
     * The jobs catching up particular projection instances, by the type
     * and the packed target ID.
     */
    private final ImmutableTable<String, Any, CatchUp> byTarget;

    private CatchUpJobs(ImmutableList<CatchUp> all) {
        this.all = all;
        var ofAll = new HashMap<String, CatchUp>();
        var ofTargets = HashBasedTable.<String, Any, CatchUp>create();
        for (var job : all) {
            if (job.getRequest().getTargetList().isEmpty()) {
                ofAll.merge(job.getId().getProjectionType(), job, CatchUpJobs::preferred);
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
    private static void indexTargets(CatchUp job, Table<String, Any, CatchUp> table) {
        var type = job.getId().getProjectionType();
        for (var target : job.getRequest().getTargetList()) {
            var present = table.get(type, target);
            table.put(type, target, present == null ? job : preferred(present, job));
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
     * @param message
     *         the message to choose the job for
     * @return the chosen job, or {@code null} if no job matches the message
     */
    @Nullable CatchUp jobFor(InboxMessage message) {
        var inbox = message.getInboxId();
        var type = inbox.getTypeUrl();
        var forTarget = byTarget.get(type, inbox.getEntityId().getId());
        var forAll = ofAllInstances.get(type);
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
}
