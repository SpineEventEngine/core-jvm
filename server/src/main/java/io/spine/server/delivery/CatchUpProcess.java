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
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.Any;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;
import io.spine.annotation.Internal;
import io.spine.base.EventMessage;
import io.spine.base.Identifier;
import io.spine.base.Time;
import io.spine.core.Event;
import io.spine.core.EventContext;
import io.spine.core.Versions;
import io.spine.grpc.MemoizingObserver;
import io.spine.server.BoundedContext;
import io.spine.server.ServerEnvironment;
import io.spine.server.delivery.event.CatchUpCompleted;
import io.spine.server.delivery.event.CatchUpRequested;
import io.spine.server.delivery.event.CatchUpStarted;
import io.spine.server.delivery.event.EntityPreparedForCatchUp;
import io.spine.server.delivery.event.HistoryEventsRecalled;
import io.spine.server.delivery.event.HistoryFullyRecalled;
import io.spine.server.delivery.event.LiveEventsPickedUp;
import io.spine.server.delivery.event.ShardProcessed;
import io.spine.server.delivery.event.ShardProcessingRequested;
import io.spine.server.event.AbstractStatefulReactor;
import io.spine.server.event.EventFactory;
import io.spine.server.event.EventStore;
import io.spine.server.event.EventStreamQuery;
import io.spine.server.event.EventStreamQuery.Limit;
import io.spine.server.event.NoReaction;
import io.spine.server.event.React;
import io.spine.server.projection.ProjectionRepository;
import io.spine.server.stand.Stand;
import io.spine.server.tuple.EitherOf2;
import io.spine.server.tuple.EitherOf3;
import io.spine.server.type.EventEnvelope;
import io.spine.type.TypeUrl;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.protobuf.util.Durations.fromMillis;
import static com.google.protobuf.util.Durations.fromNanos;
import static com.google.protobuf.util.Timestamps.subtract;
import static io.spine.server.delivery.CatchUpMessages.catchUpCompleted;
import static io.spine.server.delivery.CatchUpMessages.fullyRecalled;
import static io.spine.server.delivery.CatchUpMessages.limitOf;
import static io.spine.server.delivery.CatchUpMessages.liveEventsPickedUp;
import static io.spine.server.delivery.CatchUpMessages.recalled;
import static io.spine.server.delivery.CatchUpMessages.shardProcessingRequested;
import static io.spine.server.delivery.CatchUpMessages.started;
import static io.spine.server.delivery.CatchUpMessages.toFilters;
import static io.spine.server.delivery.CatchUpStatus.COMPLETED;
import static io.spine.server.delivery.CatchUpStatus.FINALIZING;
import static io.spine.server.delivery.CatchUpStatus.IN_PROGRESS;
import static io.spine.server.delivery.DeliveryStrategy.newIndex;
import static io.spine.server.route.EventRoute.noTargets;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

/**
 * A process that performs a projection catch-up.
 *
 * <p>Starts the catch-up process by emitting the {@link CatchUpRequested} event.
 *
 * <p>Has its own {@link Inbox}, so the messages arriving to it are dispatched by the
 * {@link Delivery}.
 *
 * <p>While recalling the event history, the process relies on the work of {@code Delivery},
 * which follows the status of the process and delivers messages in {@code TO_CATCH_UP} status
 * instead of live {@code TO_DELIVER} messages to the catching-up targets.
 *
 * <p>The tricky part is so called "turbulence" period, i.e. the time when the catch-up comes
 * close to the current time in the event history. In this phase the task is to understand when to
 * stop reading the history and switch back to delivering the live events dispatched to the
 * catching-up projections. To do so, a special {@code FINALIZING} status is introduced. Observed
 * by {@code Delivery} it tells to perform a transition from dispatching {@code TO_CATCH_UP} events
 * back to delivering those {@code TO_DELIVER}. See more on that below.
 *
 * <p>In this version of the process implementation, the turbulence period always equals 500 ms
 * meaning that the catch-up starts its finalization when the event history is read
 * up until {@code now - 500 ms}.
 *
 * <p>In its lifecycle, the process moves through the several statuses.
 *
 * <h3>{@linkplain CatchUpStatus#CUS_UNDEFINED Not started}</h3>
 *
 * <p>The process is created in this status upon receiving the {@code CatchUpRequested} event.
 * The further actions include:
 *
 * <ol>
 *     <li>A {@link CatchUpStarted} event is emitted. The target projection repository listens to
 *     this event and kills the state of the matching entities.
 *
 *     <li>The status if the catch-up process is set to {@link CatchUpStatus#IN_PROGRESS
 *     IN_PROGRESS}.
 * </ol>
 *
 * <h3>{@linkplain CatchUpStatus#STARTED STARTED}</h3>
 *
 * <p>The process is created in this status upon receiving the {@code CatchUpRequested} event.
 * The further actions include:
 *
 * <ol>
 *     <li>A {@link CatchUpStarted} event is emitted. The target projection repository listens to
 *     this event and kills the state of the matching entities.
 *
 *     <li>The status if the catch-up process is set to {@link CatchUpStatus#IN_PROGRESS
 *     IN_PROGRESS}.
 * </ol>
 *
 * <h3>{@link CatchUpStatus#IN_PROGRESS IN_PROGRESS}</h3>
 *
 * <p>When the process is in this status, the event history is read and the matching events are sent
 * to the {@code Inbox}es of the corresponding projections
 *
 * <p>The catch-up maintains this status until the history is read till the point in time,
 * which is very close to the {@link Time#currentTime() Time.currentTime()}. When this time comes,
 * the events arriving to the {@code EventStore} are very likely to be just-emitted in a live mode.
 * Therefore, the process needs to decide whether to decide that the history is fully read, or
 * to continue the traversal through the history.
 *
 * <p>At this stage the actions are as follows.
 *
 * <ol>
 *      <li>The historical event messages are read from the {@link EventStore} respecting
 *      the time range requested and the time of the last read operation performed by this process.
 *      The maximum number of the events read is determined by
 *      {@linkplain DeliveryBuilder#setCatchUpPageSize(int) one of the Delivery settings}.
 *
 *      <li>Depending on the targets requested for the catch-up, the events are posted to the
 *      corresponding entities.
 *
 *      <li>Unless the timestamps of the events are getting close to the current time, an
 *      {@link HistoryEventsRecalled} is emitted, leaving the process in the {@code IN_PROGRESS}
 *      status and triggering the next round similar to this one.
 *
 *      <p>If the timestamps of the events read on this step are as close to the current time as
 *      the turbulence period, the {@link HistoryFullyRecalled} is emitted.
 * </ol>
 *
 * <h3>{@link CatchUpStatus#FINALIZING FINALIZING}</h3>
 *
 * <p>The process moves to this status when the event history has been fully recalled and the
 * corresponding {@code HistoryFullyRecalled} is received. At this stage, the {@code Delivery} stops
 * the propagation of the events to the catch-up messages, waiting for this process to populate
 * the inboxes with the messages arriving to be dispatched during the turbulence period.
 * Potentially, the inboxes will contain the duplicates produced by both the live users
 * and this process. To deal with it, a deduplication is performed by the {@code Delivery}.
 * See {@link CatchUpStation} for more details.
 *
 * <p>The historical data pushed by the catch-up is dispatched through a number of shards,
 * each processed by the {@code Delivery} independently of each other. Starting to dispatch
 * the events from a shard, {@code Delivery} reads the details of the ongoing catch-up processes.
 * By interpreting the status of each catch-up, {@code Delivery} decides on the actions to apply
 * to the historical events. For instance, the historical events dispatched from a catch-up which
 * is already {@code COMPLETED} are considered junk and are immediately deleted.
 *
 * <p>Before moving to the catch-up completion, it is required to make sure every historical event
 * has been seen and dispatched by the {@code Delivery}. So if the catch-up process is moved
 * to the {@code COMPLETED} state too early, some historical events will get stuck in limbo, and
 * will eventually be deleted.
 *
 * <p>It's important to understand that different shards may be processed at different speeds and
 * potentially by different application nodes. Therefore, in order to switch to the
 * {@code COMPLETED} status, a catch-up process must ensure that <b>each</b> shard was processed
 * by the {@code Delivery} which witnessed the catch-up process in its {@code FINALIZING} state.
 * Such evidence would mean that this {@code Delivery} run is at the point in time by which
 * three things already happened:
 *
 * <ol type="a">
 *     <li>the catch-up process has emitted all the events from the history,
 *
 *     <li>the catch-up process has sent {@link HistoryFullyRecalled} after the historical events,
 *
 *     <li>all these events were dispatched already — otherwise, the process wasn't going
 *     to be in its {@code FINALIZING} state.
 * </ol>
 *
 * <p>In order to guarantee that prior to moving to the {@code COMPLETED} status, the catch-up
 * process emits special {@link ShardProcessingRequested} events for each shard. The events are
 * dispatched to a {@link ShardMaintenanceProcess}, which responds back by emitting
 * {@link ShardProcessed} events. The {@code Delivery} knows about this special-case signals
 * and appends the data in these events with its own picture of ongoing catch-up
 * processes and their statuses. By receiving {@code ShardProcessed} events populated with this
 * information the catch-up process knows which status was seen by the {@code Delivery}
 * at the moment when it had been dispatching the events from a certain shard.
 *
 * <p>The actions at this stage are as follows.
 *
 * <ol>
 *      <li>All the remaining messages of the matching event types are read {@link EventStore}.
 *      In this operation the read limits set by the {@code Delivery} are NOT used, since
 *      the goal is to read the remainder of the events.
 *
 *      <li>The {@link LiveEventsPickedUp} event is emitted. This allows some time for the posted
 *      events to become visible to the {@code Delivery}.
 *
 *      <li>In the next round of delivery, the emitted {@link LiveEventsPickedUp} event is
 *      dispatched back to this process. By this time, the last batch of the historical events
 *      has already been sent to their destinations. However, not all of them may have been yet
 *      delivered due to their large number or to the I/O performance. To ensure that every
 *      historical event was seen by the {@code Delivery}, a special
 *      {@link ShardProcessingRequested} event is emitted for every shard involved into
 *      dispatching the historical events up until now.
 *
 *      <li>Each {@link ShardProcessingRequested} event triggers the {@code Delivery} to read
 *      and dispatch the signals from a specific shard. Being sent, these events are dispatched
 *      to a special system {@link ShardMaintenanceProcess} serving as a handler. By reacting
 *      to a {@code ShardProcessingRequested}, the latter maintenance process emits
 *      a {@link ShardProcessed} event, telling that the {@code Delivery} has worked its way
 *      through the messages present in this shard.
 *
 *      <li>The catch-up process handles each {@code ShardProcessed} event. By gathering the data
 *      from these events, the catch-up is able to tell when all the affected shards were
 *      fully processed by the {@code Delivery}. Once all shards are processed,
 *      a {@link CatchUpCompleted} event is emitted.
 * </ol>
 *
 * <h3>{@link CatchUpStatus#COMPLETED COMPLETED}</h3>
 *
 * <p>Once the {@code CatchUpCompleted} event is received, the process moves
 * to the {@code COMPLETED} status. At this point, some live events and some historical events which
 * were located in the "turbulence" window are still paused for dispatching. To ensure they are
 * processed in each shard, the catch-up emits a number of {@code ShardProcessingRequested} events
 * again. By dispatching them, it makes sure that the {@code Delivery} observes the change of
 * catch-up status to {@code COMPLETED} and moves onto de-duplicating and delivering the previously
 * "paused" events.
 *
 * <p>After that, a normal delivery flow is resumed. The catch-up process stops its execution.
 *
 * @param <I>
 *         the type of identifiers of the processed projections
 * @implNote Technically, the instances of this class are not
 *         {@linkplain io.spine.server.procman.ProcessManager process managers}, since it is
 *         impossible to register the process managers with the same state in
 *         a multi-{@code BoundedContext} application.
 */
@SuppressWarnings("ClassWithTooManyMethods")    // It does a lot.
public final class CatchUpProcess<I>
        extends AbstractStatefulReactor<CatchUpId, CatchUp, CatchUp.Builder> {

    /**
     * The type URL of the process state.
     *
     * <p>Used for the message routing.
     */
    private static final TypeUrl TYPE = TypeUrl.from(CatchUp.getDescriptor());

    /**
     * The duration of the turbulence period, counting back from the current time.
     */
    private static final Turbulence TURBULENCE = Turbulence.of(fromMillis(500));

    private final DispatchCatchingUp<I> dispatchOperation;
    private final CatchUpStorage storage;
    private final CatchUpStarter.Builder<I> starterTemplate;
    private final Limit queryLimit;

    private @MonotonicNonNull CatchUpStarter<I> catchUpStarter;
    private @MonotonicNonNull Supplier<EventStore> eventStore;

    CatchUpProcess(CatchUpProcessBuilder<I> builder) {
        super(TYPE);
        this.dispatchOperation = builder.getDispatchOp();
        this.storage = builder.getStorage();
        this.queryLimit = limitOf(builder.getPageSize());
        this.starterTemplate = CatchUpStarter.newBuilder(builder.getRepository(), this.storage);
    }

    @Internal
    public static <I> CatchUpProcessBuilder<I> newBuilder(ProjectionRepository<I, ?, ?> repo) {
        checkNotNull(repo);
        return new CatchUpProcessBuilder<>(repo);
    }

    @Override
    @Internal
    public void registerWith(BoundedContext context) {
        super.registerWith(context);
        this.eventStore = () -> context.eventBus()
                                       .eventStore();
        this.catchUpStarter = starterTemplate.withContext(context)
                                             .build();
    }

    /**
     * Starts the catch-up for the projection instances selecting them by their identifiers.
     *
     * <p>If no identifiers is passed (i.e. {@code null}) then all of the instances should be
     * caught up
     *
     * @param since
     *         since when the catch-up should be performed
     * @param ids
     *         identifiers of the projections to catch up, or {@code null} if all of the
     *         instances should be caught up
     * @return identifier of the catch-up operation
     * @throws CatchUpAlreadyStartedException
     *         if at least one of the selected instances is already catching up at the moment
     */
    @Internal
    public CatchUpId startCatchUp(Timestamp since, @Nullable Set<I> ids)
            throws CatchUpAlreadyStartedException {
        return catchUpStarter.start(ids, since);
    }

    /**
     * Moves the process from {@code Not Started} to {@code STARTED} state.
     *
     * <p>Several important things happen at this stage:
     * <ol>
     *      <li>The reading timestamp of the process is set to the one specified in the request.
     *      However, people are used to say "I want to catch-up since 1 PM" meaning "counting
     *      the events happened at 1 PM sharp". Therefore process always subtracts a single
     *      nanosecond from the specified catch-up start time and then treats this interval as
     *      exclusive.
     *
     *      <li>{@link CatchUpStarted} event is dispatched directly to the inboxes of the
     *      catching-up targets based on the original catch-up request.
     *
     *      <li>The identifiers of the catch-up targets are defined. They are set according to
     *      the actual IDs of the projections to which the {@code CatchUpStarted} has been
     *      dispatched. It is important to know the target IDs, since their state has to be reset
     *      to default before the dispatching of the first historical event.
     *
     *      <li>The number of the projection instances to catch-up is remembered. The process
     *      then waits for {@link EntityPreparedForCatchUp} events to arrive for each of
     *      the instances before reading the events from the history.
     * </ol>
     *
     * <p>The event handler returns {@code Nothing}, as the results of its work are dispatched
     * directly to the inboxes of the catching-up projections.
     */
    @React
    NoReaction handle(CatchUpRequested e, EventContext ctx) {
        var id = e.getId();

        var request = e.getRequest();

        var sinceWhen = request.getSinceWhen();
        var withWindow = subtract(sinceWhen, fromNanos(1));
        builder().setWhenLastRead(withWindow)
                 .setRequest(request);
        var started = started(id);
        builder().setStatus(CatchUpStatus.STARTED);
        flushState();

        dispatchCatchUpStarted(started, ctx);
        return noReaction();
    }

    private void dispatchCatchUpStarted(CatchUpStarted started, EventContext ctx) {
        var event = wrapAsEvent(started, ctx);
        var ids = targetsForCatchUpSignals(builder().getRequest());
        var targetIds = dispatchAll(ImmutableList.of(event), ids);
        builder().setInstancesToClear(targetIds.size());
    }

    /**
     * Waits until all the projection instances report they are ready for the history events
     * to be dispatched.
     *
     * <p>Once all the instances are ready, performs the first reading from the event history
     * and dispatches the results to the inboxes of respective projections.
     *
     * <p>If the history has been read fully (i.e. until the start of the {@linkplain Turbulence
     * turbulence period}), emits {@code HistoryFullyRecalled} event. Otherwise, emits
     * {@code HistoryEventsRecalled} by which it triggers the next round of history reading.
     */
    @React
    EitherOf3<HistoryEventsRecalled, HistoryFullyRecalled, NoReaction>
    handle(EntityPreparedForCatchUp event) {
        var leftToClear = builder().getInstancesToClear() - 1;
        builder().setInstancesToClear(leftToClear);
        if (leftToClear > 0) {
            return EitherOf3.withC(noReaction());
        }
        builder().setStatus(IN_PROGRESS);

        var result = recallMoreEvents();
        if (result.hasA()) {
            return EitherOf3.withA(result.getA());
        } else {
            return EitherOf3.withB(result.getB());
        }
    }

    /**
     * Performs the second and all the following reads from the event history and dispatches the
     * historical events to the inboxes of the catching-up projections.
     *
     * <p>If the history has been read fully (i.e. until the start of the {@linkplain Turbulence
     * turbulence period}), emits {@code HistoryFullyRecalled} event. Otherwise,
     * emits {@code HistoryEventsRecalled} by which triggers the next round of history reading.
     */
    @React
    EitherOf2<HistoryEventsRecalled, HistoryFullyRecalled> handle(HistoryEventsRecalled event) {
        return recallMoreEvents();
    }

    /**
     * Reads the event history starting from the last event read and dispatches the events to the
     * inboxes of the target projections.
     *
     * <p>Each read operation is bounded by the {@linkplain CatchUp#getWhenLastRead()
     * timestamp of last recalled event} and the start of the {@linkplain Turbulence
     * turbulence period}.
     *
     * <p>Read operations are also supplied with a query limit configured for this process. Thus,
     * several events stamped with the same time value may be caught in-between query pages. As
     * long as their order is not defined, some of them could have gone lost. To deal with this
     * issue, this method strips the events with the most recent time from the query result and
     * aims to read all of them in the next read round.
     *
     * <p>After reading, the time of the last event is recorded to the process state and is used
     * as a starting point for the next read round.
     *
     * <p>If there were no events read, the history is considered fully recalled. The process
     * will still have to deal with the event potentially emitted during the turbulence.
     */
    private EitherOf2<HistoryEventsRecalled, HistoryFullyRecalled> recallMoreEvents() {
        var id = builder().getId();
        var request = builder().getRequest();

        var readInThisRound = readMore(request, TURBULENCE.whenStarts(), queryLimit);
        if (!readInThisRound.isEmpty()) {
            var stripped = stripLastTimestamp(readInThisRound);

            var lastEvent = stripped.get(stripped.size() - 1);
            var lastEventTimestamp = lastEvent.getContext().getTimestamp();
            builder().setWhenLastRead(lastEventTimestamp);
            dispatchAll(stripped);
        } else {
            return EitherOf2.withB(fullyRecalled(id));
        }
        return EitherOf2.withA(recalled(id));
    }

    /**
     * Sets the process status to {@link CatchUpStatus#FINALIZING FINALIZING} and reads all
     * the remaining events, then dispatching those to the projection inboxes.
     *
     * <p>Emits {@code LiveEventsPickedUp} event, which is dispatched to this process
     * in the next round.
     */
    @React
    LiveEventsPickedUp handle(HistoryFullyRecalled event, EventContext context) {
        var id = event.getId();
        if (builder().getStatus() != FINALIZING) {
            builder().setStatus(FINALIZING);
            flushState();
        }
        return runFinalization(id);
    }

    private LiveEventsPickedUp runFinalization(CatchUpId id) {
        var request = builder().getRequest();
        var events = readMore(request, null, null);
        if (events.size() > 0) {
            var lastEventTimestamp = events.get(events.size() - 1).timestamp();
            builder().setWhenLastRead(lastEventTimestamp);
            dispatchAll(events);
        }
        return liveEventsPickedUp(id);
    }

    /**
     * Emits an additional {@link ShardProcessingRequested} event and thus triggers the processing
     * of the potentially omitted live events in each of the shards affected in this
     * catch-up process.
     *
     * <p>The {@code Delivery} is equipped with a {@link MaintenanceStation}, which
     *
     * <p>The main goal of such a trick is to ensure that all shards do not contain any
     * of the historical events, and each of the shards is in {@code FINALIZING} status. Otherwise,
     * moving to the {@code COMPLETED} status right away would kill these historical events
     * as irrelevant.
     *
     * <p>See the {@link ShardMaintenanceProcess} which handles each of the emitted requests
     * for the shard processing.
     */
    @React
    List<ShardProcessingRequested> handle(LiveEventsPickedUp event, EventContext context) {
        var affectedShards = builder().getAffectedShardList();
        var totalShards = builder().getTotalShards();
        var messages = toShardEvents(totalShards, affectedShards);
        return messages;
    }

    /**
     * Waits until all of the shards are guaranteed to have all of their historical events
     * seen and dispatched by the {@code Delivery}.
     *
     * <p>By gathering the data from {@code ShardProcessed} events, the catch-up
     * process tracks the shards which are already ready for the catch-up completion. Once all
     * of the shards affected by this process become ready, a {@code CatchUpCompleted} is emitted.
     *
     * <p>If, for some reason, a {@code ShardProcessed} event is dispatched to this process when
     * its status is already {@code COMPLETED}, this reactor method does nothing.
     *
     * <p>See the class-level documentation for more details and the big picture.
     */
    @React
    EitherOf3<CatchUpCompleted, ShardProcessingRequested, NoReaction> handle(ShardProcessed event) {
        var builder = builder();
        if (builder.getStatus() == COMPLETED) {
            return EitherOf3.withC(noReaction());
        }

        var id = builder.getId();
        var stateVisibleToDelivery = findJob(id, event.getRunInfo());
        if (stateVisibleToDelivery.isPresent()) {
            var observedJob = stateVisibleToDelivery.get();
            if (observedJob.getStatus() == FINALIZING) {
                var indexOfFinalized = event.getIndex().getIndex();
                if (!builder.getFinalizedShardList().contains(indexOfFinalized)) {
                    builder.addFinalizedShard(indexOfFinalized);
                }
                var affectedShards = builder.getAffectedShardList();
                if (builder.getFinalizedShardList().containsAll(affectedShards)) {
                    var completed = completeProcess(builder.getId());
                    return EitherOf3.withA(completed);
                }
                return EitherOf3.withC(noReaction());
            }
        }
        var stillRequested = shardProcessingRequested(id, event.getIndex());
        return EitherOf3.withB(stillRequested);
    }

    private static Optional<CatchUp> findJob(CatchUpId id, DeliveryRunInfo deliveryInfo) {
        var stateVisibileToDelivery = deliveryInfo.getCatchUpJobList()
                .stream()
                .filter((job) -> id.equals(job.getId()))
                .findFirst();
        return stateVisibileToDelivery;
    }

    /**
     * Upon the catch-up completion, requests an additional processing of the messages in those
     * shards, which were involved into the dispatching during the catch-up.
     *
     * <p>In this way, the process ensures that any events, which delivery may have been potentially
     * paused during the process finalization, are propagated from their shards to the target
     * projection instances.
     */
    @SuppressWarnings("unused") // We just need the fact of dispatching.
    @React
    List<ShardProcessingRequested> on(CatchUpCompleted ignored) {
        var shardCount = builder().getTotalShards();
        var affectedShards = builder().getAffectedShardList();

        var events = toShardEvents(shardCount, affectedShards);
        return events;
    }

    private CatchUpCompleted completeProcess(CatchUpId id) {
        builder().setStatus(COMPLETED);
        flushState();
        var completed = catchUpCompleted(id);
        return completed;
    }

    private Set<I> targetsForCatchUpSignals(CatchUp.Request request) {
        Set<I> ids;
        var rawTargets = request.getTargetList();
        ids = rawTargets.isEmpty()
              ? ImmutableSet.copyOf(repository().index())
              : unpack(rawTargets);
        return ids;
    }

    private Event wrapAsEvent(EventMessage event, EventContext context) {
        var factory = EventFactory.forImport(context.actorContext(), producerId());
        var result = factory.createEvent(event, Versions.zero());
        return result;
    }

    private static List<Event> stripLastTimestamp(List<Event> events) {
        var lastIndex = events.size() - 1;
        var lastEvent = events.get(lastIndex);
        var lastTimestamp = lastEvent.getContext()
                                     .getTimestamp();
        for (var index = lastIndex; index >= 0; index--) {
            var event = events.get(index);
            var timestamp = event.getContext().getTimestamp();
            if (!timestamp.equals(lastTimestamp)) {
                var result = events.subList(0, index + 1);
                return result;
            }
        }
        return events;
    }

    private List<ShardProcessingRequested> toShardEvents(int totalShards, List<Integer> indexes) {
        var id = builder().getId();
        return indexes
                .stream()
                .map(indexValue -> {
                    var shardIndex = newIndex(indexValue, totalShards);
                    var event = shardProcessingRequested(id, shardIndex);
                    return event;
                })
                .collect(toList());
    }

    private void recordAffectedShards(Set<I> actualTargets) {
        var delivery = ServerEnvironment.instance()
                                        .delivery();
        var type = builder().getId().getProjectionType();
        var projectionType = TypeUrl.parse(type);

        var affectedShards =
                actualTargets.stream()
                             .map(t -> delivery.whichShardFor(t, projectionType))
                             .map(ShardIndex::getIndex)
                             .collect(toSet());
        var previouslyAffectedShards = builder().getAffectedShardList();
        Set<Integer> newValue = new TreeSet<>(affectedShards);
        newValue.addAll(previouslyAffectedShards);

        var totalShards = delivery.shardCount();
        builder().clearAffectedShard()
                 .addAllAffectedShard(newValue)
                 .setTotalShards(totalShards);
    }

    @CanIgnoreReturnValue
    private Set<I> dispatchAll(List<Event> events) {
        if (events.isEmpty()) {
            return ImmutableSet.of();
        }
        var request = builder().getRequest();
        var packedIds = request.getTargetList();
        if (packedIds.isEmpty()) {
            return dispatchAll(events, new HashSet<>());
        } else {
            var ids = unpack(packedIds);
            return dispatchAll(events, ids);
        }
    }

    /**
     * Dispatches the given list of events to the passed targets and returns the actual set of
     * entity identifiers to which the dispatching has been performed.
     *
     * @param events
     *         the events to dispatch
     * @param targets
     *         the set of identifiers of the targets to dispatch the events to;
     *         if empty, no particular targets are selected, so the target repository will
     *         decide on its own
     * @return the set of the identifiers to which the dispatching has been made in fact
     */
    @CanIgnoreReturnValue
    private Set<I> dispatchAll(List<Event> events, Set<I> targets) {
        if (events.isEmpty()) {
            return noTargets();
        }
        Set<I> actualTargets = new HashSet<>();
        @Nullable Set<I> targetsForDispatch = targets.isEmpty()
                                              ? null
                                              : targets;
        for (var event : events) {
            var targetsOfThisDispatch = dispatchOperation.perform(event, targetsForDispatch);
            actualTargets.addAll(targetsOfThisDispatch);
        }
        if (!actualTargets.isEmpty()) {
            recordAffectedShards(actualTargets);
        }
        return actualTargets;
    }

    private List<Event> readMore(CatchUp.Request request,
                                 @Nullable Timestamp readBefore,
                                 @Nullable Limit limit) {
        if (readBefore != null
                && Timestamps.compare(readBefore, builder().getWhenLastRead()) <= 0) {
            return ImmutableList.of();
        }
        var query = toEventQuery(request, readBefore, limit);
        var observer = new MemoizingObserver<Event>();
        eventStore.get()
                  .read(query, observer);
        var allEvents = observer.responses();
        return allEvents;
    }

    private Set<I> unpack(List<Any> packedIds) {
        return packedIds.stream()
                        .map((any) -> Identifier.unpack(any, repository().idClass()))
                        .collect(toSet());
    }

    /**
     * Loads the instance of corresponding {@link ProjectionRepository} from
     * the {@link CatchUpRepositories} cache.
     *
     * <p>Such a mechanism ensures that this process always has both its ID and underlying
     * repository matching each other.
     */
    private ProjectionRepository<I, ?, ?> repository() {
        var id = builder().getId();
        var projectionType = TypeUrl.parse(id.getProjectionType());
        ProjectionRepository<I, ?, ?> result = CatchUpRepositories.cache()
                                                                  .get(projectionType);
        return result;
    }

    private EventStreamQuery toEventQuery(CatchUp.Request request,
                                          @Nullable Timestamp readBefore,
                                          @Nullable Limit limit) {
        var filters = toFilters(request.getEventTypeList());
        var readAfter = builder().getWhenLastRead();
        var builder = EventStreamQuery.newBuilder()
                .setAfter(readAfter)
                .addAllFilter(filters);
        if (readBefore != null) {
            builder.setBefore(readBefore);
        }
        if (limit != null) {
            builder.setLimit(limit);
        }
        return builder.build();
    }

    /*
     * Event reactor lifecycle
     ************************/

    /**
     * {@inheritDoc}
     *
     * <p>Ensures that the passed signal is an instance of the supported types.
     */
    @Override
    public boolean canDispatch(EventEnvelope envelope) {
        var message = envelope.message();
        return message instanceof CatchUpSignal
                || message instanceof ShardProcessed;
    }

    @SuppressWarnings("ChainOfInstanceofChecks")    // Handling two distinct cases.
    @Override
    protected ImmutableSet<CatchUpId> route(EventEnvelope event) {
        var message = event.message();
        if (message instanceof CatchUpSignal) {
            return routeCatchUpSignal(message);
        }
        if (message instanceof ShardProcessed) {
            return routeShardProcessed((ShardProcessed) message);
        }
        return ImmutableSet.of();
    }

    private static ImmutableSet<CatchUpId> routeShardProcessed(ShardProcessed message) {
        var id = message.getProcess();
        return ImmutableSet.of(id);
    }

    private static ImmutableSet<CatchUpId> routeCatchUpSignal(EventMessage message) {
        var signal = (CatchUpSignal) message;
        return ImmutableSet.of(signal.getId());
    }

    @Override
    protected Optional<CatchUp> load(CatchUpId id) {
        return storage.read(id);
    }

    @Override
    protected void store(CatchUp updatedState) {
        storage.write(updatedState);
    }

    @Override
    protected CatchUp.Builder newStateBuilderWith(CatchUpId id) {
        return CatchUp.newBuilder()
                      .setId(id);
    }

    /**
     * Does NOT register this process in {@code Stand}, as the emitted events should
     * not be available for subscribing.
     */
    @Internal
    @Override
    protected void registerIn(Stand stand) {
        // Do nothing.
    }

    /**
     * A method object dispatching the event to catch-up.
     *
     * @param <I>
     *         the type of the identifiers of the entities to which the event should be dispatched.
     */
    @FunctionalInterface
    public interface DispatchCatchingUp<I> {

        /**
         * Dispatches the given event and optionally narrowing down the entities by the set
         * of entity identifiers to dispatch the event to.
         *
         * <p>If no particular IDs are specified, the event will be dispatched according to the
         * repository routing rules.
         *
         * @param event
         *         event to dispatch
         * @param narrowDownToIds
         *         optional set of identifiers of the targets to narrow down the event targets
         * @return the set of identifiers to which the event was actually dispatched
         */
        Set<I> perform(Event event, @Nullable Set<I> narrowDownToIds);
    }
}
