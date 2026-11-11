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

package io.spine.server.tenant;

import com.google.common.base.MoreObjects;
import io.spine.annotation.Internal;
import io.spine.core.TenantId;

import java.util.Objects;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * An identifier that exists for a specific {@link TenantId}.
 *
 * @param <I>
 *         the type of the identifier
 */
@Internal
public final class IdInTenant<I> {

    private final I id;
    private final TenantId tenantId;

    private IdInTenant(I id, TenantId tenantId) {
        this.id = id;
        this.tenantId = tenantId;
    }

    /**
     * Creates a new instance of {@code IdInTenant} with the given identifier value and
     * selected multitenancy mode.
     *
     * <p>Uses the current {@code Tenant}.
     */
    public static <I> IdInTenant<I> of(I id, boolean multiTenant) {
        checkNotNull(id);
        var tenant = TenantAware.currentTenant(multiTenant);
        return new IdInTenant<>(id, tenant);
    }

    /**
     * Creates a new instance of {@code IdInTenant} with the given identifier value and
     * {@code TenantId}.
     */
    public static <I> IdInTenant<I> of(I id, TenantId tenant) {
        checkNotNull(id);
        checkNotNull(tenant);
        return new IdInTenant<>(id, tenant);
    }

    /**
     * Returns the value of the identifier.
     */
    public I value() {
        return id;
    }

    /**
     * Returns the corresponding {@code TenantId}.
     */
    public TenantId tenant() {
        return tenantId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        var another = (IdInTenant<?>) o;
        return Objects.equals(id, another.id) &&
                Objects.equals(tenantId, another.tenantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, tenantId);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                          .add("id", id)
                          .add("tenantId", tenantId)
                          .toString();
    }
}
