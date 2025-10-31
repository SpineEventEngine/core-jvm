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

package io.spine.server.delivery;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.Duration;
import io.spine.server.ServerEnvironment;
import io.spine.server.delivery.memory.InMemoryShardedWorkRegistry;
import io.spine.server.storage.StorageFactory;
import io.spine.server.storage.memory.InMemoryStorageFactory;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A builder for {@code Delivery} instances.
 */
@SuppressWarnings("ClassWithTooManyMethods")    // That's expected for the centerpiece configurator.
public final class DeliveryBuilder {

    /**
     * The default number of messages to be delivered in scope of a single {@link DeliveryStage}.
     */
    private static final int DEFAULT_PAGE_SIZE = 500;

    /**
     * The default number of the events to recall per single read operation during the catch-up.
     */
    private static final int DEFAULT_CATCH_UP_PAGE_SIZE = 500;

    private @MonotonicNonNull InboxStorage inboxStorage;
    private @MonotonicNonNull CatchUpStorage catchUpStorage;
    private @MonotonicNonNull DeliveryStrategy strategy;
    private @MonotonicNonNull ShardedWorkRegistry workRegistry;
    private @MonotonicNonNull Duration deduplicationWindow;
    private @MonotonicNonNull DeliveryMonitor deliveryMonitor;
    private @MonotonicNonNull Integer pageSize;
    private @MonotonicNonNull Integer catchUpPageSize;

    /**
     * Prevents a direct instantiation of this class.
     */
    DeliveryBuilder() {
    }

    /**
     * Returns the value of the configured {@code InboxStorage} or {@code Optional.empty()} if no
     * such value was configured.
     *
     * @deprecated Use {@link #getInboxStorage()} and {@link #hasInboxStorage()} instead.
     */
    @Deprecated
    public Optional<InboxStorage> inboxStorage() {
        return Optional.ofNullable(inboxStorage);
    }

    /**
     * Checks whether the {@code InboxStorage} has been configured.
     *
     * @return {@code true} if the inbox storage was set, {@code false} otherwise
     */
    public boolean hasInboxStorage() {
        return inboxStorage != null;
    }

    /**
     * Returns the configured {@code InboxStorage}.
     *
     * @return the inbox storage
     * @throws NullPointerException if the inbox storage was not set
     */
    public InboxStorage getInboxStorage() {
        return checkNotNull(inboxStorage);
    }

    /**
     * Returns the value of the configured {@code CatchUpStorage} or {@code Optional.empty()} if no
     * such value was configured.
     *
     * @deprecated Use {@link #getCatchUpStorage()} and {@link #hasCatchUpStorage()} instead.
     */
    @Deprecated
    public Optional<CatchUpStorage> catchUpStorage() {
        return Optional.ofNullable(catchUpStorage);
    }

    /**
     * Checks whether the {@code CatchUpStorage} has been configured.
     *
     * @return {@code true} if the catch-up storage was set, {@code false} otherwise
     */
    public boolean hasCatchUpStorage() {
        return catchUpStorage != null;
    }

    /**
     * Returns the configured {@code CatchUpStorage}.
     *
     * @return the catch-up storage
     * @throws NullPointerException if the catch-up storage was not set
     */
    public CatchUpStorage getCatchUpStorage() {
        return checkNotNull(catchUpStorage);
    }

    /**
     * Returns the value of the configured {@code DeliveryStrategy} or {@code Optional.empty()}
     * if no such value was configured.
     *
     * @deprecated Use {@link #getStrategy()} and {@link #hasStrategy()} instead.
     */
    @Deprecated
    public Optional<DeliveryStrategy> strategy() {
        return Optional.ofNullable(strategy);
    }

    /**
     * Checks whether the {@code DeliveryStrategy} has been configured.
     *
     * @return {@code true} if the strategy was set, {@code false} otherwise
     */
    public boolean hasStrategy() {
        return strategy != null;
    }

    /**
     * Returns the configured {@code DeliveryStrategy}.
     *
     * @return the delivery strategy
     * @throws NullPointerException if the strategy was not set
     */
    public DeliveryStrategy getStrategy() {
        return checkNotNull(strategy);
    }

    /**
     * Returns the value of the configured {@code ShardedWorkRegistry} or {@code Optional.empty()}
     * if no such value was configured.
     *
     * @deprecated Use {@link #getWorkRegistry()} and {@link #hasWorkRegistry()} instead.
     */
    @Deprecated
    public Optional<ShardedWorkRegistry> workRegistry() {
        return Optional.ofNullable(workRegistry);
    }

    /**
     * Checks whether the {@code ShardedWorkRegistry} has been configured.
     *
     * @return {@code true} if the work registry was set, {@code false} otherwise
     */
    public boolean hasWorkRegistry() {
        return workRegistry != null;
    }

    /**
     * Returns the configured {@code ShardedWorkRegistry}.
     *
     * @return the work registry
     * @throws NullPointerException if the work registry was not set
     */
    public ShardedWorkRegistry getWorkRegistry() {
        return checkNotNull(workRegistry);
    }

    /**
     * Returns the value of the configured deduplication window or {@code Optional.empty()}
     * if no such value was configured.
     *
     * @deprecated Use {@link #getDeduplicationWindow()} and {@link #hasDeduplicationWindow()} instead.
     */
    @Deprecated
    public Optional<Duration> deduplicationWindow() {
        return Optional.ofNullable(deduplicationWindow);
    }

    /**
     * Checks whether the deduplication window has been configured.
     *
     * @return {@code true} if the deduplication window was set, {@code false} otherwise
     */
    public boolean hasDeduplicationWindow() {
        return deduplicationWindow != null;
    }

    /**
     * Returns the configured deduplication window.
     *
     * @return the deduplication window
     * @throws NullPointerException if the deduplication window was not set
     */
    public Duration getDeduplicationWindow() {
        return checkNotNull(deduplicationWindow);
    }

    /**
     * Returns the value of the configured {@code DeliveryMonitor} or {@code Optional.empty()}
     * if no such value was configured.
     *
     * @deprecated Use {@link #getDeliveryMonitor()} and {@link #hasDeliveryMonitor()} instead.
     */
    @Deprecated
    public Optional<DeliveryMonitor> deliveryMonitor() {
        return Optional.ofNullable(deliveryMonitor);
    }

    /**
     * Checks whether the {@code DeliveryMonitor} has been configured.
     *
     * @return {@code true} if the delivery monitor was set, {@code false} otherwise
     */
    public boolean hasDeliveryMonitor() {
        return deliveryMonitor != null;
    }

    /**
     * Returns the configured {@code DeliveryMonitor}.
     *
     * @return the delivery monitor
     * @throws NullPointerException if the delivery monitor was not set
     */
    public DeliveryMonitor getDeliveryMonitor() {
        return checkNotNull(deliveryMonitor);
    }

    /**
     * Returns the configured {@code DeliveryMonitor}.
     *
     * @return the delivery monitor
     * @throws NullPointerException if the delivery monitor was not set
     * @deprecated Use {@link #getDeliveryMonitor()} instead.
     */
    @Deprecated
    DeliveryMonitor getMonitor() {
        return getDeliveryMonitor();
    }

    /**
     * Returns the value of the configured page size or {@code Optional.empty()}
     * if no such value was configured.
     *
     * @deprecated Use {@link #getPageSize()} and {@link #hasPageSize()} instead.
     */
    @Deprecated
    public Optional<Integer> pageSize() {
        return Optional.ofNullable(pageSize);
    }

    /**
     * Checks whether the page size has been configured.
     *
     * @return {@code true} if the page size was set, {@code false} otherwise
     */
    public boolean hasPageSize() {
        return pageSize != null;
    }

    /**
     * Returns the configured page size.
     *
     * @return the page size
     * @throws NullPointerException if the page size was not set
     */
    public Integer getPageSize() {
        return checkNotNull(pageSize);
    }

    /**
     * Returns the value of the configured catch-up page size or {@code Optional.empty()}
     * if no such value was configured.
     *
     * @deprecated Use {@link #getCatchUpPageSize()} and {@link #hasCatchUpPageSize()} instead.
     */
    @Deprecated
    public Optional<Integer> catchUpPageSize() {
        return Optional.ofNullable(catchUpPageSize);
    }

    /**
     * Checks whether the catch-up page size has been configured.
     *
     * @return {@code true} if the catch-up page size was set, {@code false} otherwise
     */
    public boolean hasCatchUpPageSize() {
        return catchUpPageSize != null;
    }

    /**
     * Returns the configured catch-up page size.
     *
     * @return the catch-up page size
     * @throws NullPointerException if the catch-up page size was not set
     */
    public Integer getCatchUpPageSize() {
        return checkNotNull(catchUpPageSize);
    }

    @CanIgnoreReturnValue
    public DeliveryBuilder setWorkRegistry(ShardedWorkRegistry workRegistry) {
        this.workRegistry = checkNotNull(workRegistry);
        return this;
    }

    /**
     * Sets strategy of assigning a shard index for a message that is delivered to a particular
     * target.
     *
     * <p>If none set, {@link UniformAcrossAllShards#singleShard()} is be used.
     */
    @CanIgnoreReturnValue
    public DeliveryBuilder setStrategy(DeliveryStrategy strategy) {
        this.strategy = checkNotNull(strategy);
        return this;
    }

    /**
     * Sets for how long the previously delivered messages should be kept in the {@code Inbox}
     * to ensure the incoming messages aren't duplicates.
     *
     * <p>If none set, zero duration is used.
     */
    @CanIgnoreReturnValue
    public DeliveryBuilder setDeduplicationWindow(Duration deduplicationWindow) {
        this.deduplicationWindow = checkNotNull(deduplicationWindow);
        return this;
    }

    /**
     * Sets the custom {@code InboxStorage}.
     *
     * <p>If none set, the storage is initialized by the {@code StorageFactory} specific for
     * this {@code ServerEnvironment}.
     *
     * <p>If no {@code StorageFactory} is present in the {@code ServerEnvironment}, a new
     * {@code InMemoryStorageFactory} is used.
     */
    @CanIgnoreReturnValue
    public DeliveryBuilder setInboxStorage(InboxStorage inboxStorage) {
        this.inboxStorage = checkNotNull(inboxStorage);
        return this;
    }

    /**
     * Sets the custom {@code CatchUpStorage}.
     *
     * <p>If none set, the storage is initialized by the {@code StorageFactory} specific for
     * this {@code ServerEnvironment}.
     *
     * <p>If no {@code StorageFactory} is present in the {@code ServerEnvironment}, a new
     * {@code InMemoryStorageFactory} is used.
     */
    @CanIgnoreReturnValue
    public DeliveryBuilder setCatchUpStorage(CatchUpStorage catchUpStorage) {
        this.catchUpStorage = checkNotNull(catchUpStorage);
        return this;
    }

    /**
     * Sets the custom {@code DeliveryMonitor}.
     *
     * <p>If none set, {@link DeliveryMonitor#alwaysContinue()}  is used.
     */
    @CanIgnoreReturnValue
    public DeliveryBuilder setMonitor(DeliveryMonitor monitor) {
        this.deliveryMonitor = checkNotNull(monitor);
        return this;
    }

    /**
     * Sets the maximum amount of messages to deliver within a {@link DeliveryStage}.
     *
     * <p>If none set, {@linkplain #DEFAULT_PAGE_SIZE} is used.
     */
    @CanIgnoreReturnValue
    public DeliveryBuilder setPageSize(int pageSize) {
        checkArgument(pageSize > 0);
        this.pageSize = pageSize;
        return this;
    }

    /**
     * Sets the maximum number of events to read from an event store per single read operation
     * during the catch-up.
     *
     * <p>If none set, {@linkplain #DEFAULT_CATCH_UP_PAGE_SIZE} is used.
     */
    @CanIgnoreReturnValue
    public DeliveryBuilder setCatchUpPageSize(int catchUpPageSize) {
        checkArgument(catchUpPageSize > 0);
        this.catchUpPageSize = catchUpPageSize;
        return this;
    }

    @SuppressWarnings("PMD.NPathComplexity")    // The readability of this method is fine.
    public Delivery build() {
        if (strategy == null) {
            strategy = UniformAcrossAllShards.singleShard();
        }

        if (deduplicationWindow == null) {
            deduplicationWindow = Duration.getDefaultInstance();
        }

        var factory = storageFactory();
        if (this.inboxStorage == null) {
            this.inboxStorage = factory.createInboxStorage(false);
        }

        if (this.catchUpStorage == null) {
            this.catchUpStorage = factory.createCatchUpStorage(false);
        }

        if (workRegistry == null) {
            workRegistry = new InMemoryShardedWorkRegistry();
        }

        if (deliveryMonitor == null) {
            deliveryMonitor = DeliveryMonitor.alwaysContinue();
        }

        if (pageSize == null) {
            pageSize = DEFAULT_PAGE_SIZE;
        }

        if (catchUpPageSize == null) {
            catchUpPageSize = DEFAULT_CATCH_UP_PAGE_SIZE;
        }

        var delivery = new Delivery(this);
        return delivery;
    }

    private static StorageFactory storageFactory() {
        var serverEnvironment = ServerEnvironment.instance();
        var currentStorageFactory = serverEnvironment.optionalStorageFactory();
        return currentStorageFactory.orElseGet(InMemoryStorageFactory::newInstance);
    }
}
