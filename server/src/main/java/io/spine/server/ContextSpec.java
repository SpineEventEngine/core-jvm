/*
 * Copyright 2022, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Redistribution and use in source and/or binary forms, with or without
 * modification, must retain the above copyright notice and the following
 * disclaimer.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
    private final boolean multitenant;
    private final boolean storeEvents;

    private ContextSpec(BoundedContextName name, boolean multitenant, boolean storeEvents) {
        this.name = checkNotNull(name);
        this.multitenant = multitenant;
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
    public static ContextSpec multitenant(String name) {
        return createDomain(name, true);
    }

    private static ContextSpec createDomain(String name, boolean multitenant) {
        checkNotNull(name);
        var contextName = newName(name);
        return new ContextSpec(contextName, multitenant, true);
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
    public boolean isMultitenant() {
        return multitenant;
    }

    /**
     * Checks if the specified context stores its event log.
     *
     * <p>All domain-specific contexts store their events. A System context may be configured
     * to store or not to store its events.
     *
     * @return {@code true} if the context persists its event log, {@code false otherwise}
     */
    @Internal
    public boolean storesEvents() {
        return storeEvents;
    }

    /**
     * Converts this spec into the System spec for the counterpart of this domain context.
     */
    ContextSpec toSystem() {
        return new ContextSpec(name.toSystem(), multitenant, storeEvents);
    }

    /**
     * Creates a spec which has all the attributes of this instance and does NOT
     * {@linkplain #storesEvents() store events}.
     */
    ContextSpec notStoringEvents() {
        return new ContextSpec(name, multitenant, false);
    }

    /**
     * Returns a single-tenant version of this instance, if it is multitenant, or
     * this instance if it is single-tenant.
     */
    public ContextSpec toSingleTenant() {
        if (isMultitenant()) {
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
        if (!(o instanceof ContextSpec)) {
            return false;
        }
        var spec = (ContextSpec) o;
        return isMultitenant() == spec.isMultitenant() &&
                storeEvents == spec.storeEvents &&
                Objects.equal(name, spec.name);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name, isMultitenant(), storeEvents);
    }

    @Override
    public String toString() {
        var tenancy = multitenant
                      ? "Multitenant"
                      : "Single tenant";
        return String.format("%s context %s", tenancy, name.getValue());
    }
}
