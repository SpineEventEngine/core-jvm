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

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.Duration;
import com.google.protobuf.util.Durations;
import io.spine.annotation.Experimental;
import io.spine.annotation.Internal;
import io.spine.annotation.VisibleForTesting;
import io.spine.core.BoundedContextName;
import io.spine.core.BoundedContextNames;
import io.spine.logging.WithLogging;
import io.spine.server.BoundedContext;
import io.spine.server.ContextSpec;
import io.spine.server.ServerEnvironment;
import io.spine.server.bus.MulticastDispatchListener;
import io.spine.server.delivery.memory.InMemoryShardedWorkRegistry;
import io.spine.server.projection.ProjectionRepository;
import io.spine.string.Stringifiers;
import io.spine.type.TypeUrl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;
import static java.util.Collections.synchronizedList;

/**
 * Delivers the messages to the entities.
 *
 * <p>Splits the incoming messages into shards and allows to deliver the
 * messages to their destinations on a per-shard basis. Guarantees that one and only one
 * application server node serves the messages from the given shard at a time, thus preventing
 * any concurrent modifications of entity state.
 *
 * <p>Delegates the message dispatching and low-level handling of message duplicates to
 * {@link #newInbox(TypeUrl) Inbox}es of each target entity. The respective {@code Inbox} instances
 * should be created in each of {@code Entity} repositories.
 *
 * <h1>Configuration</h1>
 *
 * <h2>Delivery Strategy</h2>
 *
 * <p>By default, a shard is assigned according to the identifier of the target entity. The
 * messages heading to a single entity will always reside in a single shard. However,
 * the framework users may {@linkplain DeliveryBuilder#setStrategy(DeliveryStrategy) customize}
 * this behavior.
 *
 * <p>The typical customization would be to specify the same shard index for the related targets.
 * E.g. if there is an {@code OrderAggregate}, {@code OrderItemAggregate}
 * and {@code OrderItemProjection}, they could share the same shard index. In this way the messages
 * headed to these entities will be dispatched and processed together. In turn, that will reduce
 * the eventual consistency lag between {@code C} side (i.e. aggregate state updates)
 * and {@code Q} side (i.e. the respective updates in projections).
 *
 * <h2>Deduplication</h2>
 *
 * <p>As long as the underlying storage and transport mechanisms are restricted by the CAP theorem,
 * there may be duplicates in the messages written, read or dispatched. The {@code Delivery}
 * responds to it by storing some of the already delivered messages for longer and using them as
 * a source for deduplication.
 *
 * <p>{@linkplain DeliveryBuilder#setDeduplicationWindow(Duration) Provides} the time-based
 * deduplication capabilities to eliminate the messages, which may have been already delivered
 * to their targets. The duplicates will be detected among the messages, which are not older, than
 * {@code now - [deduplication window]}.
 *
 * <h2>Customizing {@code InboxStorage}</h2>
 *
 * <p>{@code Delivery} is responsible for providing the {@link InboxStorage} for every inbox
 * registered. Framework users may {@linkplain DeliveryBuilder#setInboxStorage(InboxStorage)
 * configure} the storage. By default, the {@code InboxStorage} for the delivery is provided
 * by the environment-specific {@linkplain ServerEnvironment#storageFactory() storage factory}
 * and is single-tenant. In case there is at least one multi-tenant {@code BoundedContext}
 * served by the {@code Delivery}, the {@code InboxStorage} should be configured
 * to support the multi-tenancy.
 *
 * <h2>Catch-up</h2>
 *
 * <p>In addition to delivering the messages sent in a real-time, {@code Delivery} dispatches
 * the historical events sent to the catching-up projections. These events are dispatched through
 * the same shards as the live messages. A special {@link CatchUpStation} is responsible for
 * handling this use-case. See more on that in the respective section.
 *
 * <p>To control how many historical events are read and put into shards, the end-users may
 * configure the {@linkplain DeliveryBuilder#setCatchUpPageSize(int) maximum number of messages}
 * read from the history at a time. This is helpful to balance the per-shard throughput, so
 * that the live messages are still dispatched through the same shards in a reasonable time.
 *
 * <p>The statuses of the ongoing catch-up processes are stored in a dedicated
 * {@link CatchUpStorage}. The {@code DeliveryBuilder} {@linkplain
 * DeliveryBuilder#setCatchUpStorage(CatchUpStorage) exposes an API} for the customization of this
 * storage. By default, the {@code CatchUpStorage} is single-tenant. However,
 * as with the {@code InboxStorage} used by the {@code Delivery}, it should be configured
 * as multi-tenant if at least one {@code BoundedContext} served by the {@code Delivery}
 * is multi-tenant.
 *
 * <h2>Observers</h2>
 *
 * <p>Once a message is written to the {@code Inbox},
 * the {@linkplain Delivery#subscribe(ShardObserver) pre-configured shard observers} are
 * {@linkplain ShardObserver#onMessage(InboxMessage) notified}. In this way any third-party
 * environment planners, load balancers, and schedulers may plug into the delivery and perform
 * various routines to enable the further processing of the sharded messages. In a distributed
 * environment a message queue may be used to notify the node cluster of a shard that has some
 * messages pending for the delivery.
 *
 * <h2>Work registry</h2>
 *
 * <p>Once an application node picks the shard to deliver the messages from it, it registers itself
 * in a {@link ShardedWorkRegistry}. It serves as a list of locks-per-shard that only allows
 * to pick a shard to a single node at a time. The framework users may configure the implementation
 * of the registry by calling {@link DeliveryBuilder#setWorkRegistry(ShardedWorkRegistry)}.
 *
 * <h2>Dispatching messages</h2>
 *
 * <h3>Delivery stages</h3>
 *
 * <p>The delivery process for each shard index is split into {@link DeliveryStage}s. In scope of
 * each stage, a certain number of messages is read from the respective shard of the {@code Inbox}.
 * The messages are grouped per-target and delivered in batches if possible. The maximum
 * number of the messages within a {@code DeliveryStage} can be
 * {@linkplain DeliveryBuilder#setPageSize(int) configured}.
 *
 * <p>After each {@code DeliveryStage} it is possible to stop the delivery by
 * {@link DeliveryBuilder#setMonitor(DeliveryMonitor) supplying} a custom delivery monitor.
 * Please refer to the {@link DeliveryMonitor documentation} for the details.
 *
 * <h3>Conveyor and stations</h3>
 *
 * <p>In a scope of {@code DeliveryStage} the page of the {@code InboxMessage}s is placed
 * to the {@link Conveyor} responsible for tracking the status of each message.
 * The conveyor is run through the pipeline of stations, each modifying the state of the messages.
 * At the end of the pipeline, the changed made to the messages are committed to the underlying
 * {@code InboxStorage} in a bulk. Such an approach allows to minimize the number of the requests
 * sent to the storage.
 *
 * <p>As long as the new {@code DeliveryStage} is started, the new instance of the {@code Conveyor}
 * is created.
 *
 * <p>Below is the list of the conveyor stations in the pipeline.
 *
 * <b>1. Catch-up station</b>
 *
 * <p>This station is responsible for dispatching the historical events in
 * {@link InboxMessageStatus#TO_CATCH_UP TO_CATCH_UP} status to the respective targets. Also,
 * while the target entity is under a catch-up, all the live messages headed to it are ignored.
 * Once the catch-up is completed, this station handles the transition period, in which the last
 * batch of the historical events and live messages are dispatched together.
 * See {@link CatchUpStation} for more details.
 *
 * <b>2. Live delivery station</b>
 *
 * <p>This station is responsible for dispatching the messages sent in a real-time. It ignores
 * the messages in {@link InboxMessageStatus#TO_CATCH_UP TO_CATCH_UP} status. Another responsibility
 * of this station is to set for how long the delivered messages should be kept according to the
 * {@linkplain DeliveryBuilder#setDeduplicationWindow(Duration) deduplication window} settings.
 * See {@link LiveDeliveryStation} for more details.
 *
 * <b>3. Cleanup station</b>
 *
 * <p>This station removes the messages which are already delivered and are no longer needed for the
 * deduplication. See {@link CleanupStation} for the description.
 *
 * <b>Deduplication</b>
 *
 * <p>During the dispatching, {@code Conveyor} keeps track of the delivered messages. The stations
 * performing the actual message dispatching rely onto this knowledge and deduplicate
 * the messages prior to calling the target's endpoint.
 *
 * <p>Additionally, the {@code Delivery} provides a {@linkplain DeliveredMessagesCache cache of recently
 * delivered messages}. Each instance of the {@code Conveyor} has an access to it and uses it
 * in deduplication procedures.
 *
 * <h2>Local environment</h2>
 *
 * <p>By default, the delivery is configured to {@linkplain Delivery#local() run locally}. It
 * uses {@linkplain LocalDispatchingObserver see-and-dispatch observer}, which delivers the
 * messages from the observed shard once a message is passed to its
 * {@link LocalDispatchingObserver#onMessage(InboxMessage) onMessage(InboxMessage)} method. This
 * process is synchronous.
 *
 * <p>To deal with the multi-threaded access in a local mode,
 * an {@linkplain InMemoryShardedWorkRegistry} is used. It operates on top of the
 * {@code synchronized} in-memory data structures and prevents several threads from picking up the
 * same shard.
 *
 * <h2>Shard maintenance</h2>
 *
 * <p>To perform the maintenance procedures, the {@code Delivery} requires all the {@code
 * BoundedContext}s to register themselves in it. Upon this registration, a special
 * {@link ShardMaintenanceProcess} is registered as an event dispatcher in a passed
 * {@code BoundedContext}. Such a registration is performed automatically when the context is
 * {@linkplain  io.spine.server.BoundedContextBuilder#build() built}.
 */
@SuppressWarnings({"OverlyCoupledClass", "ClassWithTooManyMethods"}) // It's fine for a centerpiece.
public final class Delivery implements WithLogging {

    /**
     * The width of the deduplication window in a local environment.
     *
     * <p>Selected to be pretty big to avoid dispatching duplicates to any entities.
     */
    private static final Duration LOCAL_DEDUPLICATION_WINDOW = Durations.fromSeconds(30);

    /**
     * The name of the system bounded context which performs the delivery of signals.
     *
     * <p>This name is used to initialize the storage implementations.
     */
    private static final BoundedContextName SYSTEM_DELIVERY =
            BoundedContextNames.newName("__System_Delivery__");

    /**
     * The strategy of assigning a shard index for a message that is delivered to a particular
     * target.
     */
    private final DeliveryStrategy strategy;

    /**
     * For how long we keep the previously delivered message per-target to ensure the new messages
     * aren't duplicates.
     */
    private final Duration deduplicationWindow;

    /**
     * The delivery strategies to use for the postponed message dispatching.
     *
     * <p>Stored per {@link TypeUrl}, which is a state type of a target {@code Entity}.
     *
     * <p>Once messages arrive for the postponed processing, a corresponding delivery is selected
     * according to the message contents. The {@code TypeUrl} in this map is stored
     * as {@code String} to avoid an extra boxing into {@code TypeUrl} of the value,
     * which resides as a Protobuf {@code string} inside an incoming message.
     */
    private final InboxDeliveries deliveries;

    /**
     * The observers that are notified when a message is written into a particular shard.
     */
    private final List<ShardObserver> shardObservers;

    /**
     * The registry keeping track of which shards are processed by which application nodes.
     */
    private final ShardedWorkRegistry workRegistry;

    /**
     * The storage of messages to deliver.
     */
    private final InboxStorage inboxStorage;

    /**
     * The storage of ongoing catch-up process states.
     */
    private final CatchUpStorage catchUpStorage;

    /**
     * How many messages to read per query when recalling the historical events from the event log
     * during the catch-up.
     */
    private final int catchUpPageSize;

    /**
     * The monitor of delivery stages.
     */
    private final DeliveryMonitor monitor;

    /**
     * The cache of the locally delivered messages.
     */
    private final DeliveredMessagesCache deliveredMessagesCache;

    /**
     * The maximum amount of messages to deliver within a {@link DeliveryStage}.
     */
    private final int pageSize;

    /**
     * The listener of the dispatching operations inside the {@link io.spine.server.bus.MulticastBus
     * MulticastBus}es.
     *
     * <p>Responsible for sending the notifications to the shard observers.
     */
    private final DeliveryDispatchListener dispatchListener =
            new DeliveryDispatchListener(this::onNewMessage);

    Delivery(DeliveryBuilder builder) {
        this.strategy = builder.getStrategy();
        this.workRegistry = builder.getWorkRegistry();
        this.deduplicationWindow = builder.getDeduplicationWindow();
        this.inboxStorage = builder.getInboxStorage();
        this.catchUpStorage = builder.getCatchUpStorage();
        this.catchUpPageSize = builder.getCatchUpPageSize();
        this.monitor = builder.getDeliveryMonitor();
        this.pageSize = builder.getPageSize();
        this.deliveries = new InboxDeliveries();
        this.shardObservers = synchronizedList(new ArrayList<>());
        this.deliveredMessagesCache = new DeliveredMessagesCache();
    }

    /**
     * Creates an instance of new {@code Builder} of {@code Delivery}.
     */
    public static DeliveryBuilder newBuilder() {
        return new DeliveryBuilder();
    }

    /**
     * Creates a new instance of {@code Delivery} suitable for local and development environment.
     *
     * <p>In this setup, the {@code InboxMessage}s are delivered to their targets synchronously.
     *
     * <p>Uses a {@linkplain UniformAcrossAllShards#singleShard() single-shard} splitting.
     *
     * <p>To construct a {@code Delivery} instance, a {@code StorageFactory} is needed.
     * If it was not configured in the {@code ServerEnvironment}, uses a new {@code
     * InMemoryStorageFactory}.
     *
     * @see #localAsync() to create an asynchronous version of the local {@code Delivery}
     */
    public static Delivery local() {
        return localWithShardsAndWindow(1, LOCAL_DEDUPLICATION_WINDOW);
    }

    /**
     * Creates a new instance of {@code Delivery} for local and development environment.
     *
     * <p>The {@code InboxMessage}s are delivered to their targets asynchronously.
     *
     * <p>The returned instance of {@code Delivery} is configured to use
     * {@linkplain UniformAcrossAllShards#singleShard() the single shard}.
     *
     * <p>To construct a {@code Delivery} instance, a {@code StorageFactory} is needed.
     * If it was not configured in the {@code ServerEnvironment}, a new {@code
     * InMemoryStorageFactory} used.
     *
     * @see #local() to create a syncrhonous version of the local {@code Delivery}
     */
    public static Delivery localAsync() {
        var delivery = newBuilder()
                .setStrategy(UniformAcrossAllShards.singleShard())
                .build();
        delivery.subscribe(new LocalDispatchingObserver(true));
        return delivery;
    }

    /**
     * Creates a new instance of {@code Delivery} suitable for local and development environment
     * with the given number of shards.
     */
    @VisibleForTesting
    static Delivery localWithShardsAndWindow(int shardCount, Duration deduplicationWindow) {
        checkArgument(shardCount > 0, "Shard count must be positive");
        checkNotNull(deduplicationWindow);

        var strategy = UniformAcrossAllShards.forNumber(shardCount);
        return localWithStrategyAndWindow(strategy, deduplicationWindow);
    }

    @VisibleForTesting
    static Delivery localWithStrategyAndWindow(DeliveryStrategy strategy,
                                               Duration deduplicationWindow) {
        var delivery = newBuilder()
                .setDeduplicationWindow(deduplicationWindow)
                .setStrategy(strategy)
                .build();
        delivery.subscribe(new LocalDispatchingObserver());
        return delivery;
    }

    /**
     * Creates a new instance of {@code Delivery} which makes all signals to skip
     * the {@code Inbox}es and be delivered straight to their respective targets.
     *
     * <p>In this mode, no signals are stored in the {@code InboxStorage}. Also, the signals
     * are not sharded.
     *
     * <p>This mode only suits the applications which operate in a single-thread mode
     * and in scope of a single JVM. Typically, that would be some tools executing
     * linear-style jobs, which aim for the maximum performance under
     * the strictly controlled circumstances.
     *
     * <p>Any concurrent dispatching of signals (command posting, receiving external events, etc.)
     * will likely break the consistency of their target entities.
     *
     * <p>This API is experimental. Use with caution.
     *
     * @return a new instance of {@code Delivery}
     */
    @Experimental
    public static Delivery direct() {
        var delivery = newBuilder()
                .setStrategy(UniformAcrossAllShards.singleShard())
                .setInboxStorage(NoOpInboxStorage.instance())
                .build();
        delivery.subscribe(update -> {
            var action = delivery.deliveries.get(update);
            action.deliverDirectly(update);
        });
        return delivery;
    }

    /**
     * Delivers the messages put into the shard with the passed index to their targets.
     *
     * <p>At a given moment of time, exactly one application node may serve messages from
     * a particular shard. Therefore, in scope of this delivery, an approach based on pessimistic
     * locking per-{@code ShardIndex} is applied.
     *
     * <p>In case the given shard is already processed by some node, this method does nothing and
     * returns {@code Optional.empty()}.
     *
     * <p>The content of the shard is read and delivered on page-by-page basis. The runtime
     * exceptions occurring while a page is being delivered are accumulated and then the first
     * exception is rethrown, if any.
     *
     * <p>After all the pages are read, the delivery process is launched again for the same shard.
     * It is required in order to handle the messages, that may have been put to the same shard
     * as an outcome of the first-wave messages.
     *
     * <p>Once the shard has no more messages to deliver, the delivery process ends, releasing
     * the lock for the respective {@code ShardIndex}.
     *
     * @param index
     *         the shard index to deliver the messages from.
     * @return the statistics on the performed delivery, or {@code Optional.empty()} if there
     *         were no delivery performed
     */
    @CanIgnoreReturnValue
    public Optional<DeliveryStats> deliverMessagesFrom(ShardIndex index) {
        var currentNode = ServerEnvironment.instance()
                                           .nodeId();
        PickUpOutcome outcome;
        try {
            outcome = workRegistry.pickUp(index, currentNode);
        } catch (RuntimeException e) {
            return onRuntimeFailure(index, e);
        }
        if (outcome.hasAlreadyPicked()) {
            return onAlreadyPickedUp(index, outcome.getAlreadyPicked());
        }
        var session = outcome.getSession();
        monitor.onDeliveryStarted(index);

        RunResult runResult;
        var totalDelivered = 0;
        try {
            do {
                runResult = runDelivery(session);
                totalDelivered += runResult.deliveredCount();
            } while (runResult.shouldRunAgain());
        } finally {
            workRegistry.release(session);
        }
        var stats = new DeliveryStats(index, totalDelivered);
        monitor.onDeliveryCompleted(stats);
        var lateMessage = inboxStorage.newestMessageToDeliver(index);
        lateMessage.ifPresent(this::onNewMessage);

        return Optional.of(stats);
    }

    /**
     * Notifies the {@code DeliveryMonitor} telling about the runtime error
     * occurred while performing the delivery from certain shard.
     *
     * <p>Executes the failure handler {@code Action} returned by the monitor.
     *
     * <p>If the monitor tells to repeat the delivery, returns the stats of the repeated
     * delivery process, if any.
     */
    private Optional<DeliveryStats> onRuntimeFailure(ShardIndex shard, RuntimeException e) {
        var failure = new RuntimeFailure(shard, e, () -> deliverMessagesFrom(shard));
        var result = monitor.onShardPickUpFailure(failure)
                            .execute();
        return result;
    }

    /**
     * Notifies the {@code DeliveryMonitor} telling the shard from which the delivery
     * was asked to run, is already picked up.
     *
     * <p>Executes the failure handler {@code Action} returned by the monitor.
     *
     * <p>If the monitor tells to repeat the delivery, returns the stats of the repeated
     * delivery process, if any.
     */
    private Optional<DeliveryStats> onAlreadyPickedUp(ShardIndex shard, ShardAlreadyPickedUp pickedUp) {
        var failure = new AlreadyPickedUp(shard, pickedUp, () -> deliverMessagesFrom(shard));
        var result = monitor.onShardAlreadyPicked(failure)
                            .execute();
        return result;
    }

    /**
     * Runs the delivery for the shard, which session is passed.
     *
     * <p>The messages are read page-by-page according to the {@link #pageSize page size} setting.
     *
     * <p>After delivering each page of messages, a {@code DeliveryStage} is produced.
     * The configured {@link #monitor DeliveryMonitor} may stop the execution according to
     * the monitored {@code DeliveryStage}.
     *
     * @return the results of the run
     */
    private RunResult runDelivery(ShardSessionRecord session) {
        var index = session.getIndex();

        var startingPage = inboxStorage.readAll(index, pageSize);
        var maybePage = Optional.of(startingPage);

        var shouldContinue = true;
        List<DeliveryStage> stages = new ArrayList<>();
        Iterable<CatchUp> catchUpJobs = refreshCatchUpJobs();
        while (shouldContinue && maybePage.isPresent()) {
            var currentPage = maybePage.get();
            var messages = currentPage.contents();
            if (!messages.isEmpty()) {
                var stage = deliverMessages(messages, index, catchUpJobs);
                stages.add(stage);
                shouldContinue = monitorTellsToContinueAfter(stage);
            }
            if (shouldContinue) {
                if(messages.size() < pageSize) {
                    catchUpJobs = refreshCatchUpJobs();
                }
                maybePage = currentPage.next();
            }
        }

        int totalMessagesDelivered = stages.stream()
                                           .map(DeliveryStage::getMessagesDelivered)
                                           .reduce(0, Integer::sum);
        return new RunResult(totalMessagesDelivered, !shouldContinue);
    }

    private ImmutableList<CatchUp> refreshCatchUpJobs() {
        return ImmutableList.copyOf(catchUpStorage.readAll());
    }

    private DeliveryStage deliverMessages(ImmutableList<InboxMessage> messages,
                                          ShardIndex index,
                                          Iterable<CatchUp> catchUpJobs) {
        var conveyor = new Conveyor(messages, deliveredMessagesCache);
        DeliveryAction action = new GroupByTargetAndDeliver(deliveries, monitor, conveyor);
        List<Station> stations = conveyorStationsFor(catchUpJobs, action);
        var stage = launch(conveyor, stations, index);
        return stage;
    }

    /**
     * Launches the conveyor, running it through the passed stations and processing the messages
     * in the specified shard.
     *
     * <p>Once all the stations complete their routine, this {@code DeliveryStage} is considered
     * completed.
     *
     * @return the delivery stage results
     */
    private DeliveryStage launch(Conveyor conveyor, Iterable<Station> stations, ShardIndex index) {
        var deliveredInBatch = 0;

        for (var station : stations) {
            var result = station.process(conveyor);
            result.errors()
                  .throwIfAny();
            deliveredInBatch += result.deliveredCount();
        }
        notifyOfDuplicatesIn(conveyor);
        conveyor.flushTo(inboxStorage);

        return newStage(index, deliveredInBatch);
    }

    private ImmutableList<Station> conveyorStationsFor(Iterable<CatchUp> catchUpJobs,
                                                       DeliveryAction action) {
        return ImmutableList.of(
                new MaintenanceStation(deliveryInfoWith(catchUpJobs)),
                new CatchUpStation(action, catchUpJobs),
                new LiveDeliveryStation(action, deduplicationWindow),
                new CleanupStation()
        );
    }

    private static DeliveryRunInfo deliveryInfoWith(Iterable<CatchUp> catchUpJobs) {
        return DeliveryRunInfo.newBuilder()
                              .addAllCatchUpJob(catchUpJobs)
                              .build();
    }

    private void notifyOfDuplicatesIn(Conveyor conveyor) {
        var streamOfDuplicates = conveyor.recentDuplicates();
        streamOfDuplicates.forEach((message) -> {
            var delivery = deliveries.get(message);
            delivery.onDuplicate(message);
        });
    }

    private static DeliveryStage newStage(ShardIndex index, int deliveredInBatch) {
        return DeliveryStage
                .newBuilder()
                .setIndex(index)
                .setMessagesDelivered(deliveredInBatch)
                .build();
    }

    private boolean monitorTellsToContinueAfter(DeliveryStage stage) {
        return monitor.shouldContinueAfter(stage);
    }

    /**
     * Notifies that the contents of the shard with the given index have been updated
     * with some message.
     *
     * @param message
     *         a message that was written into the shard
     */
    @SuppressWarnings("OverlyBroadCatchBlock")
    private void onNewMessage(InboxMessage message) {
        for (var observer : shardObservers) {
            try {
                observer.onMessage(message);
            } catch (Exception e) {
                logger().atError().withCause(e).log(() -> format(
                        "Error calling a shard observer with the message `%s`.",
                        Stringifiers.toString(message)));
            }
        }
    }

    /**
     * Creates an instance of {@link Inbox.Builder} for the given entity type.
     *
     * @param entityType
     *         the type of the entity, to which the inbox will belong
     * @param <I>
     *         the type if entity identifiers
     * @return the builder for the {@code Inbox}
     */
    public <I> Inbox.Builder<I> newInbox(TypeUrl entityType) {
        return Inbox.newBuilder(entityType, inboxWriter());
    }

    /**
     * Creates a new instance of the builder for {@link CatchUpProcess}.
     *
     * @param repo
     *         projection repository for which the catch-up process will be created
     * @param <I>
     *         the type of identifiers of entities managed by the projection repository
     * @return new builder for the {@code CatchUpProcess}
     */
    public <I> CatchUpProcessBuilder<I> newCatchUpProcess(ProjectionRepository<I, ?, ?> repo) {
        var builder = CatchUpProcess.newBuilder(repo);
        CatchUpRepositories.cache().put(repo);
        return builder.setStorage(catchUpStorage)
                      .setPageSize(catchUpPageSize);
    }

    /**
     * Registers the internal {@code Delivery} message dispatchers
     * in the given {@code BoundedContext}.
     *
     * <p>The registration of the dispatchers allows to handle the {@code Delivery}-specific events.
     *
     * @param context
     *         Bounded Context in which the message dispatchers should be registered
     */
    @Internal
    public void registerDispatchersIn(BoundedContext context) {
        context.internalAccess()
               .registerEventDispatcher(new ShardMaintenanceProcess(this));
    }

    /**
     * Returns a listener of the dispatching operations occurring in the
     * {@link io.spine.server.bus.MulticastBus MulticastBus}es.
     */
    @Internal
    public MulticastDispatchListener dispatchListener() {
        return dispatchListener;
    }

    /**
     * Subscribes to the updates of shard contents.
     *
     * <p>The passed observer will be notified that the contents of a shard with a particular index
     * were changed.
     *
     * @param observer
     *         an observer to notify of updates.
     */
    public void subscribe(ShardObserver observer) {
        shardObservers.add(observer);
    }

    /**
     * Registers the passed {@code Inbox} and puts its {@linkplain Inbox#delivery() delivery
     * callbacks} into the list of those to be called, when the previously sharded messages
     * are dispatched to their targets.
     */
    void register(Inbox<?> inbox) {
        deliveries.register(inbox);
    }

    /**
     * Determines the shard index for the message, judging on the identifier of the entity,
     * to which this message is dispatched.
     *
     * @param entityId
     *         the ID of the entity, to which the message is heading
     * @param entityStateType
     *         the state type of the entity, to which the message is heading
     * @return the index of the shard for the message
     */
    ShardIndex whichShardFor(Object entityId, TypeUrl entityStateType) {
        return strategy.determineIndex(entityId, entityStateType);
    }

    /**
     * Unregisters the given {@code Inbox} and removes all the {@linkplain Inbox#delivery()
     * delivery callbacks} previously registered by this {@code Inbox}.
     */
    void unregister(Inbox<?> inbox) {
        deliveries.unregister(inbox);
    }

    /**
     * Returns the instance of {@link InboxStorage} used by this {@code Delivery}.
     */
    @VisibleForTesting
    InboxStorage inboxStorage() {
        return inboxStorage;
    }

    /**
     * Returns the instance of {@link CatchUpStorage} used by this {@code Delivery}.
     */
    @VisibleForTesting
    CatchUpStorage catchUpStorage() {
        return catchUpStorage;
    }

    int shardCount() {
        return strategy.shardCount();
    }

    @VisibleForTesting
    ImmutableList<ShardObserver> shardObservers() {
        return ImmutableList.copyOf(shardObservers);
    }

    private InboxWriter inboxWriter() {
        return new NotifyingWriter(inboxStorage) {

            @Override
            protected void onShardUpdated(InboxMessage message) {
                Delivery.this.dispatchListener.notifyOf(message);
            }
        };
    }

    /**
     * Returns the specification of a Bounded Context describing the {@code Delivery} flow.
     *
     * <p>Usually, this context is single-tenant, as the batch processing
     * of {@code InboxMessage}s is more effective when the batches are not
     * split across tenants.
     *
     * @param multitenant
     *         whether the Bounded Context supports multi-tenancy
     */
    @SuppressWarnings({"TestOnlyProblems" /* The called code is not test-only. */,
            "WeakerAccess" /* Used in descendant libraries to configure storage. */})
    public static ContextSpec contextSpec(boolean multitenant) {
        var name = SYSTEM_DELIVERY.value();
        return multitenant ?
               ContextSpec.multitenant(name) :
               ContextSpec.singleTenant(name);
    }
}
