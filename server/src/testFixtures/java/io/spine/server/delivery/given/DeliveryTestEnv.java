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

package io.spine.server.delivery.given;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.protobuf.Any;
import io.spine.protobuf.AnyPacker;
import io.spine.server.NodeId;
import io.spine.server.aggregate.AggregateRepository;
import io.spine.server.delivery.InboxMessage;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.delivery.ShardObserver;
import io.spine.server.route.EventRoute;
import io.spine.server.route.EventRouting;
import io.spine.test.delivery.Calc;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.spine.base.Identifier.newUuid;
import static java.util.Collections.synchronizedList;
import static java.util.Collections.synchronizedSet;

/**
 * Test environment for {@link Delivery} tests.
 */
public class DeliveryTestEnv {

    /** Prevents instantiation of this utility class. */
    private DeliveryTestEnv() {
    }

    public static ImmutableSet<String> manyTargets(int size) {
        return new SecureRandom().ints(size)
                                 .mapToObj((i) -> "calc-" + i)
                                 .collect(ImmutableSet.toImmutableSet());
    }

    public static ImmutableSet<String> singleTarget() {
        return ImmutableSet.of("the-calculator");
    }

    public static NodeId generateNodeId() {
        return NodeId
                .newBuilder()
                .setValue(newUuid())
                .build();
    }

    public static class CalculatorRepository
            extends AggregateRepository<String, CalcAggregate, Calc> {

        /** How many calls there were to {@link #doStore(CalcAggregate)} method, grouped by ID. */
        private static final Map<String, Integer> storeCalls = Maps.newConcurrentMap();

        /** How many calls there were to {@link #doLoadOrCreate(String)} method, grouped by ID. */
        private static final Map<String, Integer> loadOrCreateCalls = Maps.newConcurrentMap();

        public CalculatorRepository() {
            storeCalls.clear();
            loadOrCreateCalls.clear();
        }

        @Override
        protected void setupImportRouting(EventRouting<String> routing) {
            routeByFirstField(routing);
        }

        @Override
        protected void setupEventRouting(EventRouting<String> routing) {
            routeByFirstField(routing);
        }

        private static void routeByFirstField(EventRouting<String> routing) {
            routing.replaceDefault( EventRoute.byFirstMessageField(String.class));
        }

        @Override
        protected void doStore(CalcAggregate aggregate) {
            var id = aggregate.id();
            incrementByKey(id, storeCalls);
            super.doStore(aggregate);
        }

        @Override
        protected CalcAggregate doLoadOrCreate(String id) {
            incrementByKey(id, loadOrCreateCalls);
            return super.doLoadOrCreate(id);
        }

        private static void incrementByKey(String id, Map<String, Integer> counters) {
            if (!counters.containsKey(id)) {
                counters.put(id, 0);
            }
            counters.put(id, counters.get(id) + 1);
        }

        public int storeCallsCount(String id) {
            return storeCalls.getOrDefault(id, 0);
        }

        public int loadOrCreateCallsCount(String id) {
            return loadOrCreateCalls.getOrDefault(id, 0);
        }
    }

    /**
     * An observer of the messages delivered to shards, which remembers all the delivered messages
     * per {@code CalculatorAggregate} identifier.
     *
     * <p>Unpacks {@linkplain InboxMessage#getPayloadCase() the payload} of each
     * {@code InboxMessage} observed.
     */
    public static class SignalMemoizer implements ShardObserver {

        private final Multimap<String, CalculatorSignal> signals = ArrayListMultimap.create();

        @Override
        public synchronized void onMessage(InboxMessage update) {
            Any packed;
            if (update.hasCommand()) {
                packed = update.getCommand()
                               .getMessage();
            } else {
                packed = update.getEvent()
                               .getMessage();
            }
            var msg = (CalculatorSignal) AnyPacker.unpack(packed);
            signals.put(msg.getCalculatorId(), msg);
        }

        public ImmutableSet<CalculatorSignal> messagesBy(String id) {
            return ImmutableSet.copyOf(signals.get(id));
        }
    }

    /**
     * An observer of the messages delivered to shards, which remembers all the delivered messages.
     *
     * <p>Memoizes the observed {@code InboxMessage}s as-is.
     */
    public static class RawMessageMemoizer implements ShardObserver {

        private final List<InboxMessage> rawMessages = synchronizedList(new ArrayList<>());

        @Override
        public void onMessage(InboxMessage update) {
            rawMessages.add(update);
        }

        public ImmutableList<InboxMessage> messages() {
            return ImmutableList.copyOf(rawMessages);
        }
    }

    /**
     * An observer of the messages delivered to shards, which remembers all the shards involved.
     */
    public static class ShardIndexMemoizer implements ShardObserver {

        private final Set<ShardIndex> observedShards = synchronizedSet(new HashSet<>());

        @Override
        public void onMessage(InboxMessage update) {
            observedShards.add(update.shardIndex());
        }

        public ImmutableSet<ShardIndex> shards() {
            return ImmutableSet.copyOf(observedShards);
        }
    }
}
