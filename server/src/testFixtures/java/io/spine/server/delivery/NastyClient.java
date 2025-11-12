/*
 * Copyright 2025, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import io.spine.server.ServerEnvironment;
import io.spine.server.delivery.given.CalcAggregate;
import io.spine.server.delivery.given.CalculatorSignal;
import io.spine.server.delivery.given.DeliveryTestEnv;
import io.spine.test.delivery.AddNumber;
import io.spine.test.delivery.Calc;
import io.spine.test.delivery.NumberImported;
import io.spine.test.delivery.NumberReacted;
import io.spine.testing.server.blackbox.BlackBox;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.google.common.collect.Streams.concat;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;

/**
 * Posts numerous messages to instances of {@link CalcAggregate} in a selected number of threads
 * and verifies that each of the targets calculated a proper sum.
 *
 * <p>Verifies the results of its job by inspecting the system environment and repositories.
 */
public class NastyClient {

    private final int threadCount;
    private final boolean shouldInboxBeEmpty;
    private final DeliveryTestEnv.CalculatorRepository repository;

    /** Which signals are expected to be delivered to which targets. */
    private @Nullable Map<String, List<CalculatorSignal>> signalsPerTarget;

    /**
     * Creates the client operating simultaneously in a specified number of threads.
     *
     * <p>Also, the client is going to assume that all inboxes should be empty, once all
     * the messages posted by the client are propagated.
     */
    NastyClient(int threadCount) {
        this(threadCount, true);
    }

    /**
     * Create the client operating in several number of threads.
     *
     * <p>This constructor allows to tell weather the system inboxes should be empty after
     * all the messages posted by the client are propagated.
     */
    NastyClient(int threadCount, boolean shouldInboxBeEmpty) {
        this.threadCount = threadCount;
        this.shouldInboxBeEmpty = shouldInboxBeEmpty;
        this.repository = new DeliveryTestEnv.CalculatorRepository();
    }

    /**
     * Generates some number of commands and events and delivers them to the specified
     * {@linkplain CalcAggregate target entities} via the selected number of threads.
     *
     * @param targets
     *         the identifiers of target entities
     */
    void runWith(Set<String> targets) {
        try (var context = BlackBox.singleTenantWith(repository)) {
            var memoizer = subscribeToDelivered();

            var streamSize = targets.size() * 30;

            var targetsIterator = Iterators.cycle(targets);
            var commands = commands(streamSize, targetsIterator);
            var importEvents = eventsToImport(streamSize, targetsIterator);
            var reactEvents = eventsToReact(streamSize, targetsIterator);

            postAsync(context, commands, importEvents, reactEvents);

            Stream<CalculatorSignal> signals =
                    concat(commands.stream(), importEvents.stream(), reactEvents.stream());

            signalsPerTarget = signals.collect(groupingBy(CalculatorSignal::getCalculatorId));

            for (var calcId : signalsPerTarget.keySet()) {

                var receivedMessages = memoizer.messagesBy(calcId);
                Set<CalculatorSignal> targetSignals =
                        ImmutableSet.copyOf(signalsPerTarget.get(calcId));
                assertEquals(targetSignals, receivedMessages);

                var sumForTarget =
                        targetSignals.stream()
                                .map(CalculatorSignal::getValue)
                                .reduce(0, Integer::sum);
                var expectedState = Calc.newBuilder()
                        .setId(calcId)
                        .setSum(sumForTarget)
                        .build();
                context.assertState(calcId, Calc.class)
                       .isEqualTo(expectedState);

            }
            ensureInboxesEmpty();
        }
    }

    /**
     * Returns the number of calls made to the
     * {@link DeliveryTestEnv.CalculatorRepository#doStore(CalcAggregate) doStore()} method.
     *
     * @param id
     *         identifier of the {@link CalcAggregate}, calls to which are counted
     */
    int callsToRepoStore(String id) {
        return repository.storeCallsCount(id);
    }


    /**
     * Returns the number of calls made to the
     * {@link DeliveryTestEnv.CalculatorRepository#doLoadOrCreate(String) doLoadOrCreate()} method.
     *
     * @param id
     *         identifier of the {@link CalcAggregate}, calls to which are counted
     */
    int callsToRepoLoadOrCreate(String id) {
        return repository.loadOrCreateCallsCount(id);
    }

    /**
     * Returns the collection of signals per target, which are expected to be delivered.
     */
    ImmutableMap<String, List<CalculatorSignal>> signalsPerTarget() {
        if (signalsPerTarget == null) {
            return ImmutableMap.of();
        }
        return ImmutableMap.copyOf(signalsPerTarget);
    }

    private static List<NumberReacted> eventsToReact(int streamSize,
                                                     Iterator<String> targetsIterator) {
        var ints = IntStream.range(0, streamSize);
        return ints.mapToObj((value) ->
                                     NumberReacted.newBuilder()
                                             .setCalculatorId(targetsIterator.next())
                                             .setValue(value)
                                             .build())
                   .collect(toList());
    }

    private static List<NumberImported> eventsToImport(int streamSize,
                                                       Iterator<String> targetsIterator) {
        var ints = IntStream.range(streamSize, streamSize * 2);
        return ints.mapToObj((value) ->
                                     NumberImported.newBuilder()
                                             .setCalculatorId(targetsIterator.next())
                                             .setValue(value)
                                             .build())
                   .collect(toList());
    }

    private static List<AddNumber> commands(int streamSize, Iterator<String> targetsIterator) {
        var ints = IntStream.range(streamSize * 2, streamSize * 3);
        return ints.mapToObj((value) ->
                                     AddNumber.newBuilder()
                                              .setCalculatorId(targetsIterator.next())
                                              .setValue(value)
                                              .build())
                   .collect(toList());
    }

    private static DeliveryTestEnv.SignalMemoizer subscribeToDelivered() {
        var observer = new DeliveryTestEnv.SignalMemoizer();
        ServerEnvironment.instance()
                         .delivery()
                         .subscribe(observer);
        return observer;
    }

    private void ensureInboxesEmpty() {
        if (shouldInboxBeEmpty) {
            var shardedItems = InboxContents.get();

            for (var index : shardedItems.keySet()) {
                var page = shardedItems.get(index);
                assertThat(page).isEmpty();
            }
        }
    }

    private void postAsync(BlackBox context,
                           List<AddNumber> commands,
                           List<NumberImported> eventsToImport,
                           List<NumberReacted> eventsToReact) {

        var signalStream =
                concat(
                        commandCallables(context, commands),
                        importEventCallables(context, eventsToImport),
                        reactEventsCallables(context, eventsToReact)
                );
        Collection<Callable<Object>> signals = signalStream.collect(toList());
        if (1 == threadCount) {
            runSync(signals);
        } else {
            runAsync(signals);
        }
    }

    private void runAsync(Collection<Callable<Object>> signals) {
        var executorService = newFixedThreadPool(threadCount);
        try {
            executorService.invokeAll(signals);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            executorService.shutdownNow();
        }
    }

    private static void runSync(Collection<Callable<Object>> signals) {
        for (var signal : signals) {
            try {
                signal.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static Stream<Callable<Object>>
    commandCallables(BlackBox context, List<AddNumber> commands) {
        return commands.stream()
                       .map((c) -> () -> {
                           context.receivesCommand(c);
                           return new Object();
                       });
    }

    private static Stream<Callable<Object>>
    importEventCallables(BlackBox context, List<NumberImported> events) {
        return events.stream()
                     .map((e) -> () -> {
                         context.importsEvent(e);
                         return new Object();
                     });
    }

    private static Stream<Callable<Object>>
    reactEventsCallables(BlackBox context, List<NumberReacted> events) {
        return events.stream()
                     .map((e) -> () -> {
                         context.receivesEvent(e);
                         return new Object();
                     });
    }
}
