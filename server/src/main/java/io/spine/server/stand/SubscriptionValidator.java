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

package io.spine.server.stand;

import io.spine.client.Subscription;
import io.spine.server.tenant.TenantAwareFunction;
import org.jspecify.annotations.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Objects.requireNonNull;

/**
 * Validates the {@linkplain Subscription} instances submitted to {@linkplain Stand}.
 */
final class SubscriptionValidator extends RequestValidator<Subscription> {

    private final SubscriptionRegistry registry;

    /**
     * Creates an instance of {@code SubscriptionValidator} based on the subscription registry.
     *
     * @param registry
     *         the registry to validate the subscription against
     */
    SubscriptionValidator(SubscriptionRegistry registry) {
        super();
        this.registry = registry;
    }

    @Override
    protected boolean isSupported(Subscription request) {
        return checkInRegistry(request);
    }

    @Override
    protected String errorMessage(Subscription request) {
        return "Cannot find the subscription in the registry";
    }

    private boolean checkInRegistry(Subscription request) {
        var tenantId = request.getTopic()
                              .getContext()
                              .getTenantId();
        var result = new TenantAwareFunction<Subscription, Boolean>(tenantId) {

            @Override
            public Boolean apply(@Nullable Subscription input) {
                requireNonNull(input);
                var result = registry.containsId(input.getId());
                return result;
            }
        }.execute(request);

        checkNotNull(result);
        return result;
    }
}
