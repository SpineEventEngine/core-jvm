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
     * The ID of the tenant in a multitenant application.
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

        private UserId actor;
        private ZoneId zoneId;

        private @Nullable TenantId tenantId;

        public UserId getActor() {
            return actor;
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
         */
        public @Nullable ZoneId getZoneId() {
            return zoneId;
        }

        /**
         * Sets the ID of the time zone in which the user works.
         *
         * @param zoneId the
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
         */
        public @Nullable TenantId getTenantId() {
            return tenantId;
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
