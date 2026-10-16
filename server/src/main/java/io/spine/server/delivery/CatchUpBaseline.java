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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import org.jspecify.annotations.Nullable;

import java.util.Iterator;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableMap.toImmutableMap;

/**
 * The {@code CatchUp} jobs against which later jobs are compared.
 *
 * <p>The jobs first passed to {@link #compare(Iterator)} become the baseline, and are indexed.
 * Each later call compares the passed jobs with the baseline by their identifiers and statuses,
 * and notes a difference.
 *
 * <p>While there is no difference, the indexed baseline stands for the passed jobs:
 * the type and the targets of a job do not change, and {@link CatchUpJobs} chooses
 * among the jobs by their statuses.
 */
final class CatchUpBaseline {

    private @Nullable CatchUpJobs indexed;
    private @Nullable ImmutableMap<CatchUpId, CatchUpStatus> statuses;
    private boolean changed;

    /**
     * Compares the passed jobs with the baseline, or makes them the baseline if there is
     * none yet.
     *
     * @param jobs
     *         the jobs of any status
     * @return the jobs of the baseline, indexed
     */
    CatchUpJobs compare(Iterator<CatchUp> jobs) {
        if (indexed == null) {
            var all = ImmutableList.copyOf(jobs);
            indexed = CatchUpJobs.of(all);
            statuses = statusesOf(all.stream());
        } else if (!statusesOf(Streams.stream(jobs)).equals(statuses)) {
            changed = true;
        }
        return indexed;
    }

    /**
     * Tells whether the jobs of any call differed from the baseline by their identifiers
     * or statuses.
     */
    boolean changed() {
        return changed;
    }

    private static ImmutableMap<CatchUpId, CatchUpStatus> statusesOf(Stream<CatchUp> jobs) {
        return jobs.collect(toImmutableMap(CatchUp::getId, CatchUp::getStatus));
    }
}
