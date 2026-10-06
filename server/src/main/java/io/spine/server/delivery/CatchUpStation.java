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

import com.google.common.collect.ImmutableList;
import com.google.protobuf.Duration;
import com.google.protobuf.util.Durations;
import io.spine.server.delivery.event.CatchUpStarted;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.spine.server.delivery.CatchUpStatus.COMPLETED;
import static io.spine.server.delivery.InboxMessageStatus.TO_CATCH_UP;
import static io.spine.server.delivery.InboxMessageStatus.TO_DELIVER;
import static io.spine.util.Exceptions.newIllegalStateException;

/**
 * A station that performs the delivery of messages to the catching-up targets.
 */
final class CatchUpStation extends Station {

    private static final Duration HOW_LONG_TO_KEEP = Durations.fromMillis(1000);
    private static final Comparator<InboxMessage> COMPARATOR = new CatchUpMessageComparator();

    private final DeliveryAction action;
    private final Iterable<CatchUp> jobs;

    /**
     * Creates a new instance of this station.
     *
     * @param action
     *         the action on how to deliver the messages to their targets
     * @param jobs
     *         current list of {@code CatchUp} jobs of any status
     */
    CatchUpStation(DeliveryAction action, Iterable<CatchUp> jobs) {
        super();
        this.action = action;
        this.jobs = ImmutableList.copyOf(jobs);
    }

    /**
     * Delivers the messages sent for catch-up from the passed conveyor.
     *
     * <p>Prior to the dispatching, the messages are deduplicated and put into the chronological
     * order in scope of the processed message batch.
     *
     * <p>The ordering changes are not reflected on the message order in the conveyor.
     *
     * @param conveyor
     *         the conveyor on which the messages are travelling
     * @return the result of the processing telling how many messages were dispatched and whether
     *         there were any errors in that
     * @see JobFilter for the processing details
     */
    @Override
    public final Result process(Conveyor conveyor) {
        var jobFilter = new JobFilter(jobs, conveyor);
        var toDispatch = jobFilter.messagesToDispatch();
        return dispatch(toDispatch, conveyor);
    }

    /**
     * Dispatches the passed messages to their targets and marks them as {@code DELIVERED} in
     * the passed conveyor.
     *
     * <p>Prior to dispatching, the passed messages are sorted chronologically, putting the events
     * of a framework-internal {@link CatchUpStarted} type first. Such an order guarantees that
     * the target entities will know of the started catch-up before any message
     * in {@code TO_CATCH_UP} status is dispatched to them.
     *
     * @param messages
     *         messages to dispatch
     * @param conveyor
     *         the conveyor to use for marking the messages as {@code DELIVERED}
     * @return the result of dispatching
     */
    private Result dispatch(Collection<InboxMessage> messages, Conveyor conveyor) {
        if (messages.isEmpty()) {
            return emptyResult();
        }

        List<InboxMessage> ordered = new ArrayList<>(messages);
        ordered.sort(COMPARATOR);

        var errors = action.executeFor(ordered);
        conveyor.markDelivered(ordered);
        var result = new Result(ordered.size(), errors);
        return result;
    }

    /**
     * Filters the messages in {@link InboxMessageStatus#TO_CATCH_UP TO_CATCH_UP} status,
     * by matching them to the {@code CatchUp} jobs.
     *
     * <p>Each message is processed by at most one job,
     * {@linkplain #jobFor(InboxMessage) chosen} among the jobs it matches. Depending on
     * the {@linkplain CatchUp#getStatus() status} of the job and the status of the message,
     * the latter may be accepted for dispatching.
     *
     * <p>Duplicated messages are removed from the passed conveyor.
     *
     * <p>The messages that have passed through the filter are considered to be ready
     * for dispatching.
     */
    private static class JobFilter {

        private final Map<DispatchingId, InboxMessage> dispatchToCatchUp = new HashMap<>();
        private final Iterable<CatchUp> jobs;
        private final Conveyor conveyor;

        /**
         * Creates a new filter.
         *
         * @param jobs
         *         the {@code CatchUp} jobs of any status
         * @param conveyor
         *         the conveyor containing the messages to filter
         */
        private JobFilter(Iterable<CatchUp> jobs, Conveyor conveyor) {
            this.jobs = jobs;
            this.conveyor = conveyor;
        }

        /**
         * Runs each of the messages through the conveyor and returns those that have passed
         * all the stages and are ready for the dispatching.
         */
        private Collection<InboxMessage> messagesToDispatch() {
            for (var message : conveyor) {
                accept(message);
            }
            return dispatchToCatchUp.values();
        }

        /**
         * Processes the message if the matching job is in {@link CatchUpStatus#STARTED
         * STARTED} status.
         *
         * <p>All the matched live messages (i.e. in {@link InboxMessageStatus#TO_DELIVER TO_DELIVER}
         * status) are ignored and removed from the conveyor.
         *
         * <p>The matched messages in {@link InboxMessageStatus#TO_CATCH_UP TO_CATCH_UP} status
         * are not yet dispatched to their targets.
         *
         * @param message
         *         the message to process
         */
        private void started(InboxMessage message) {
            var dispatched = dispatchAsCatchUpSignal(message, CatchUpStarted.class);
            if(dispatched) {
                return;
            }
            if (message.getStatus() == TO_DELIVER) {
                conveyor.remove(message);
            }
        }

        private boolean dispatchAsCatchUpSignal(InboxMessage message,
                                                Class<? extends CatchUpSignal> signalType) {
            if(message.hasEvent()) {
                var event = message.getEvent();
                var eventType = event.enclosedMessage().getClass();
                if (eventType.equals(signalType)) {
                    var dispatchingId = new DispatchingId(message);
                    dispatchToCatchUp.put(dispatchingId, message);
                    return true;
                }
            }
            return false;
        }

        /**
         * Processes the message if the matching job is in {@link CatchUpStatus#IN_PROGRESS
         * IN_PROGRESS} status.
         *
         * <p>The matched messages in {@link InboxMessageStatus#TO_CATCH_UP TO_CATCH_UP} status are
         * passed to be later dispatched to their targets. All the matched live messages
         * (i.e. in {@link InboxMessageStatus#TO_DELIVER TO_DELIVER} status) are ignored
         * and removed from the conveyor.
         *
         * @param message
         *         the message to process
         */
        private void inProgress(InboxMessage message) {
            var dispatchingId = new DispatchingId(message);
            if (message.getStatus() == TO_CATCH_UP) {
                if (dispatchToCatchUp.containsKey(dispatchingId)) {
                    conveyor.remove(message);
                } else {
                    dispatchToCatchUp.put(dispatchingId, message);
                }
            } else if (message.getStatus() == TO_DELIVER) {
                conveyor.remove(message);
            }
        }

        /**
         * Processes the message if the matching job is in {@link CatchUpStatus#FINALIZING
         * FINALIZING} status.
         *
         * <p>When the catch-up job is being finalized, it means that the historical events may be
         * dated close to the present time and, thus, interfere with the live events headed to the
         * same entities.
         *
         * <p>Therefore, the matched messages in either status are "paused" meaning they are
         * neither dispatched nor removed from their inboxes. Instead, they are held until
         * the catch-up job is completed to be deduplicated and delivered all at once.
         *
         * <p>To hold the live messages from being delivered down the conveyor pipeline,
         * the live messages are marked as {@code TO_CATCH_UP}.
         *
         * @param message
         *         the message to process
         */
        private void finalizingWith(InboxMessage message) {
            if (message.getStatus() == TO_DELIVER) {
                conveyor.markCatchUp(message);
            }
        }

        /**
         * Processes the message if the matching job is in {@link CatchUpStatus#COMPLETED COMPLETED}
         * status.
         *
         * <p>At this stage the event history is fully processed. The inboxes contain the messages
         * in {@code TO_CATCH_UP} status, which are in fact the mix of the last portion of the
         * replayed event history and potentially some "paused" live events. So, all the matching
         * messages in {@code TO_CATCH_UP} status are accepted to be dispatched to their targets.
         *
         * <p>If the deduplication window is
         * {@linkplain DeliveryBuilder#setDeduplicationWindow(Duration) set in the system},
         * the messages accepted for delivery are
         * {@linkplain Conveyor#keepForLonger(InboxMessage, Duration) set to be kept} in their
         * inboxes for the duration, corresponding to the width of the window. In this way, they
         * will not be removed after being delivered and will be available as a source
         * for the deduplication.
         *
         * @param message
         *         the message to process
         */
        private void completedWith(InboxMessage message) {
            var dispatchingId = new DispatchingId(message);
            if (message.getStatus() == TO_CATCH_UP) {
                if (!dispatchToCatchUp.containsKey(dispatchingId)) {
                    dispatchToCatchUp.put(dispatchingId, message);
                    conveyor.keepForLonger(message, HOW_LONG_TO_KEEP);
                } else {
                    conveyor.remove(message);
                }
            } else if (message.getStatus() == TO_DELIVER
                    && dispatchToCatchUp.containsKey(dispatchingId)) {
                conveyor.remove(message);
            }
        }

        /**
         * Filters the message according to the status of the job chosen for it.
         *
         * @param message
         *         the message to run through the filter
         */
        private void accept(InboxMessage message) {
            var job = jobFor(message);
            if (job == null) {
                return;
            }
            var jobStatus = job.getStatus();

            switch (jobStatus) {
                case STARTED -> started(message);
                case IN_PROGRESS -> inProgress(message);
                case FINALIZING -> finalizingWith(message);
                case COMPLETED -> completedWith(message);
                case CUS_UNDEFINED, UNRECOGNIZED -> throw newIllegalStateException(
                        "The catch-up job must have a definite status: `%s`.", job
                );
                default -> {
                    // Skip the message.
                }
            }
        }

        /**
         * Chooses the job to process the passed message.
         *
         * <p>The jobs of the completed catch-ups stay in the storage. So, once the same
         * projection instances are caught up again, a message may match both a completed job
         * and the new one. Processed by both, the message would be accepted for dispatching
         * by one job and removed from the conveyor by the other.
         *
         * <p>Therefore, a job in any status but {@link CatchUpStatus#COMPLETED COMPLETED}
         * takes precedence. It is the newest of the matching jobs, because a catch-up cannot
         * be started for the instances that are still catching up. Otherwise, any of
         * the completed jobs is chosen, since they all process a message alike.
         *
         * @param message
         *         the message to choose the job for
         * @return the chosen job, or {@code null} if no job matches the message
         */
        private @Nullable CatchUp jobFor(InboxMessage message) {
            CatchUp completed = null;
            for (var job : jobs) {
                if (!job.matches(message)) {
                    continue;
                }
                if (job.getStatus() != COMPLETED) {
                    return job;
                }
                if (completed == null) {
                    completed = job;
                }
            }
            return completed;
        }
    }
}
