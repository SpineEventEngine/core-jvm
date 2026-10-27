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

import io.spine.annotation.GeneratedMixin;
import io.spine.annotation.Internal;
import io.spine.core.TenantId;

/**
 * A mixin for the state of the {@linkplain CatchUpProcess catch-up process job}.
 */
@GeneratedMixin
@Internal
interface CatchUpMixin extends CatchUpOrBuilder {

    /**
     * Tells whether this catch-up job works with the data of the passed tenant.
     *
     * <p>A job of a multi-tenant Bounded Context has its tenant set, and works only with
     * the data of this tenant. A job of a single-tenant context has no tenant, and works
     * with the data of any tenant.
     *
     * <p>The tenant is not set in single-tenant contexts on purpose. The signals of such
     * a context carry either an empty tenant ID, or the ID that a single-tenant context
     * uses for its only tenant, so no tenant ID would match them all.
     *
     * @param tenant
     *         the tenant to check
     * @return {@code true} if the job works with the data of the tenant
     */
    default boolean isOfTenant(TenantId tenant) {
        return !hasTenant() || getTenant().equals(tenant);
    }

    /**
     * Tells whether the passed {@code InboxMessage} matches this catch-up job.
     *
     * <p>To match, three conditions must be met:
     *
     * <ol>
     *     <li>the target entity type of the job and the message must be the same;
     *
     *     <li>the job must {@linkplain #isOfTenant(TenantId) be of} the tenant
     *     of the message;
     *
     *     <li>the identifier of the message target must be included into the list of the
     *     identifiers specified in the job OR the job matches all the targets of the entity type.
     * </ol>
     *
     * <p>{@link CatchUpJobs} indexes the jobs by the same rule, so that the delivery matches
     * a message to its job in constant time. The two must change together.
     *
     * @param message
     *         the message to match to the job
     * @return {@code true} if the message matches the job, {@code false} otherwise
     */
    default boolean matches(InboxMessage message) {
        var expectedProjectionType = getId().getProjectionType();
        var targetInbox = message.getInboxId();
        var actualTargetType = targetInbox.getTypeUrl();
        if (!expectedProjectionType.equals(actualTargetType)) {
            return false;
        }
        if (!isOfTenant(message.tenant())) {
            return false;
        }
        var targets = getRequest().getTargetList();
        if (targets.isEmpty()) {
            return true;
        }
        var rawEntityId = targetInbox.getEntityId().getId();
        return targets.stream()
                      .anyMatch((t) -> t.equals(rawEntityId));
    }
}
