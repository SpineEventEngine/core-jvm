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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.spine.annotation.Internal;
import io.spine.annotation.VisibleForTesting;
import io.spine.base.Time;
import io.spine.core.ActorContext;
import io.spine.core.CommandContext;
import io.spine.core.TenantId;
import io.spine.core.UserId;
import io.spine.time.ZoneId;
import io.spine.time.ZoneIds;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.jspecify.annotations.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.base.Time.currentTime;

/**
 * A factory for various requests fired from the client-side by an actor.
 */
public class ActorRequestFactory {

    private final UserId actor;

    /**
     * The ID of the time zone from which a request is made.
     *
     * <p>In case the zone ID is not defined when {@linkplain #newBuilder() constructing
     * the factory}, the system time zone ID will be set.
     */
    private final ZoneId zoneId;

    /**
     * The ID of the tenant in a multi-tenant application.
     *
     * <p>This field is null in a single tenant application.
     */
    private final @Nullable TenantId tenantId;

    protected ActorRequestFactory(Builder builder) {
        this.actor = builder.actor;
        this.zoneId = builder.zoneId;
        this.tenantId = builder.tenantId;
    }

    /**
     * Creates a builder for a new request factory.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Creates an instance by the passed {@code ActorContext}.
     */
    public static ActorRequestFactory fromContext(ActorContext actorContext) {
        checkNotNull(actorContext);
        var builder = newBuilder()
                .setActor(actorContext.getActor())
                .setTenantId(actorContext.getTenantId())
                .setZoneId(actorContext.getZoneId());
        return builder.build();
    }

    /**
     * Obtains the ID of the user on behalf of whom the requests are created.
     */
    public UserId actor() {
        return actor;
    }

    /**
     * Obtains the offset of the time zone in which the actor works.
     *
     * @deprecated please use {@link #zoneId()} instead
     * @return default instance always
     */
    @Deprecated
    public io.spine.time.ZoneOffset zoneOffset() {
        return io.spine.time.ZoneOffset.getDefaultInstance();
    }

    /**
     * Obtains the ID of the time zone in which the actor works.
     */
    public ZoneId zoneId() {
        return zoneId;
    }

    /**
     * Obtains the ID of the tenant to which the actor belongs or {@code null}
     * for single-tenant execution context.
     */
    public @Nullable TenantId tenantId() {
        return tenantId;
    }

    /**
     * Creates a copy of this factory placed at a new time zone.
     *
     * @deprecated please use {@link #switchTimeZone(ZoneId)}
     */
    @Deprecated
    public ActorRequestFactory switchTimeZone(io.spine.time.ZoneOffset ignored, ZoneId zoneId) {
        checkNotNull(ignored);
        checkNotNull(zoneId);
        var result = newBuilder().setActor(actor())
                            .setZoneId(zoneId)
                            .setTenantId(tenantId())
                            .build();
        return result;
    }

    /**
     * Creates a copy of this factory placed at a new time zone with the passed ID.
     *
     * @param zoneId
     *         the ID of the new time zone
     * @return new factory at the new time zone
     */
    public ActorRequestFactory switchTimeZone(ZoneId zoneId) {
        checkNotNull(zoneId);
        var result = newBuilder().setActor(actor())
                            .setZoneId(zoneId)
                            .setTenantId(tenantId())
                            .build();
        return result;
    }

    /**
     * Creates an instance of {@link QueryFactory} based on configuration of this
     * {@code ActorRequestFactory} instance.
     *
     * @return an instance of {@link QueryFactory}
     */
    public QueryFactory query() {
        return new QueryFactory(this);
    }

    /**
     * Creates an instance of {@link TopicFactory} based on configuration of this
     * {@code ActorRequestFactory} instance.
     *
     * @return an instance of {@link TopicFactory}
     */
    public TopicFactory topic() {
        return new TopicFactory(this);
    }

    /**
     * Creates an instance of {@link CommandFactory} based on configuration of this
     * {@code ActorRequestFactory} instance.
     *
     * @return an instance of {@link CommandFactory}
     */
    public CommandFactory command() {
        return new CommandFactory(this);
    }

    /**
     * Creates new {@code CommandContext}.
     *
     * @see CommandFactory#createContext()
     */
    @VisibleForTesting
    protected CommandContext createCommandContext() {
        return command().createContext();
    }

    /**
     * Creates an {@linkplain ActorContext actor context} based on the factory properties.
     *
     * <p>Sets the timestamp value to the
     * {@linkplain io.spine.base.Time#currentTime() current time}.
     */
    @Internal
    @SuppressWarnings("CheckReturnValue") // calling builder
    public final ActorContext newActorContext() {
        var builder = ActorContext.newBuilder()
                .setActor(actor)
                .setTimestamp(currentTime())
                .setZoneId(zoneId);
        if (tenantId != null) {
            builder.setTenantId(tenantId);
        }
        return builder.build();
    }

    /**
     * A builder for {@code ActorRequestFactory}.
     */
    public static class Builder {

        private @MonotonicNonNull UserId actor;
        private @MonotonicNonNull ZoneId zoneId;

        private @Nullable TenantId tenantId;

        /**
         * Checks whether the actor has been set.
         *
         * @return {@code true} if the actor was set, {@code false} otherwise
         */
        public boolean hasActor() {
            return actor != null;
        }

        /**
         * Returns the actor ID.
         *
         * @return the actor ID
         * @throws NullPointerException if the actor was not set
         */
        public UserId getActor() {
            return checkNotNull(actor);
        }

        /**
         * Sets the ID for the user generating commands.
         *
         * @param actor the ID of the user generating commands
         */
        @CanIgnoreReturnValue
        public Builder setActor(UserId actor) {
            this.actor = checkNotNull(actor);
            return this;
        }

        /**
         * Obtains the zone ID set in the builder.
         *
         * @return the zone ID or {@code null} if the value was not set
         * @deprecated Use {@link #getZoneId()} and {@link #hasZoneId()} instead.
         */
        @Deprecated
        public @Nullable ZoneId zoneId() {
            return zoneId;
        }

        /**
         * Checks whether the zone ID has been set.
         *
         * @return {@code true} if the zone ID was set, {@code false} otherwise
         */
        public boolean hasZoneId() {
            return zoneId != null;
        }

        /**
         * Returns the zone ID.
         *
         * @return the zone ID
         * @throws NullPointerException if the zone ID was not set
         */
        public ZoneId getZoneId() {
            return checkNotNull(zoneId);
        }

        /**
         * Sets the ID of the time zone in which the user works.
         *
         * @param zoneId the ID of the time zone in which the user works
         */
        @CanIgnoreReturnValue
        public Builder setZoneId(ZoneId zoneId) {
            this.zoneId = checkNotNull(zoneId);
            return this;
        }

        /**
         * Obtains the ID of the tenant in a multi-tenant application to which the actor belongs.
         *
         * @return the ID of the tenant or {@code null} for a single-tenant execution context
         * @deprecated This method returns {@code null}. Use {@link #getTenantId()} and {@link #hasTenantId()} instead.
         */
        @Deprecated
        public @Nullable TenantId tenantId() {
            return tenantId;
        }

        /**
         * Checks whether the tenant ID has been set.
         *
         * @return {@code true} if the tenant ID was set, {@code false} otherwise
         */
        public boolean hasTenantId() {
            return tenantId != null;
        }

        /**
         * Returns the tenant ID.
         *
         * @return the tenant ID
         * @throws NullPointerException if the tenant ID was not set
         */
        public TenantId getTenantId() {
            return checkNotNull(tenantId);
        }

        /**
         * Sets the ID of a tenant in a multi-tenant application to which the actor belongs.
         *
         * @param tenantId the ID of the tenant or {@code null} for single-tenant execution context
         */
        @CanIgnoreReturnValue
        public Builder setTenantId(@Nullable TenantId tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        /**
         * Ensures that all the {@code Builder} parameters are set properly.
         *
         * <p>Initializes {@code zoneOffset} and {@code zoneId} with default values if
         * they are not defined.
         *
         * @return a new instance of the factory
         */
        public ActorRequestFactory build() {
            checkNotNull(actor, "`actor` must be defined");
            var currentZone = Time.currentTimeZone();
            if (zoneId == null) {
                setZoneId(ZoneIds.of(currentZone));
            }
            return new ActorRequestFactory(this);
        }
    }
}
