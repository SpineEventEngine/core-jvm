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

import com.google.common.base.Objects;
import io.spine.annotation.Internal;
import io.spine.annotation.SPI;
import io.spine.annotation.VisibleForTesting;
import io.spine.core.BoundedContextName;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.core.BoundedContextNames.newName;

/**
 * Specification of a bounded context.
 *
 * <p>The spec includes the values required to build a {@link BoundedContext}.
 */
@SPI
public final class ContextSpec {

    private final BoundedContextName name;
    private final boolean multiTenant;
    private final boolean storeEvents;

    private ContextSpec(BoundedContextName name, boolean multiTenant, boolean storeEvents) {
        this.name = checkNotNull(name);
        this.multiTenant = multiTenant;
        this.storeEvents = storeEvents;
    }

    /**
     * Creates a spec of a single tenant context with the given name.
     */
    @VisibleForTesting
    public static ContextSpec singleTenant(String name) {
        return createDomain(name, false);
    }

    /**
     * Creates a spec of a multitenant context with the given name.
     */
    @VisibleForTesting
    public static ContextSpec multiTenant(String name) {
        return createDomain(name, true);
    }

    private static ContextSpec createDomain(String name, boolean multiTenant) {
        checkNotNull(name);
        var contextName = newName(name);
        return new ContextSpec(contextName, multiTenant, true);
    }

    /**
     * Obtains the context name.
     */
    public BoundedContextName name() {
        return name;
    }

    /**
     * Checks if the context is multitenant or not.
     */
    public boolean isMultiTenant() {
        return multiTenant;
    }

    /**
     * Checks if the specified context stores its event log.
     *
     * <p>All domain-specific contexts store their events. A System context may be configured
     * to store or not to store its events.
     *
     * @return {@code true} if the context persists its event log, {@code false} otherwise
     */
    @Internal
    public boolean storesEvents() {
        return storeEvents;
    }

    /**
     * Converts this spec into the System spec for the counterpart of this domain context.
     */
    ContextSpec toSystem() {
        return new ContextSpec(name.toSystem(), multiTenant, storeEvents);
    }

    /**
     * Creates a spec that has all the attributes of this instance and does NOT
     * {@linkplain #storesEvents() store events}.
     */
    ContextSpec notStoringEvents() {
        return new ContextSpec(name, multiTenant, false);
    }

    /**
     * Returns a single-tenant version of this instance, if it is multitenant, or
     * this instance if it is single-tenant.
     */
    public ContextSpec toSingleTenant() {
        if (isMultiTenant()) {
            var result = singleTenant(name.getValue());
            return result;
        }
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ContextSpec spec)) {
            return false;
        }
        return isMultiTenant() == spec.isMultiTenant() &&
                storeEvents == spec.storeEvents &&
                Objects.equal(name, spec.name);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name, isMultiTenant(), storeEvents);
    }

    @Override
    public String toString() {
        var tenancy = multiTenant
                      ? "Multitenant"
                      : "Single tenant";
        return String.format("%s context %s", tenancy, name.getValue());
    }
}
