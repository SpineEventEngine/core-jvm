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

package io.spine.server.delivery.given;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.protobuf.Timestamp;
import io.spine.environment.Tests;
import io.spine.server.Closeable;
import io.spine.server.ServerEnvironment;
import io.spine.server.delivery.CatchUpStatus;
import io.spine.server.delivery.CatchUpStorage;
import io.spine.server.delivery.Delivery;
import io.spine.server.delivery.LocalDispatchingObserver;
import io.spine.test.delivery.NumberAdded;
import io.spine.testing.server.TestEventFactory;
import io.spine.testing.server.blackbox.BlackBox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.server.delivery.TestRoutines.findView;
import static io.spine.server.delivery.TestRoutines.post;
import static io.spine.testing.TestValues.nullRef;
import static java.util.stream.Collectors.toList;

/**
 * A convenience wrapper over the {@link CounterView} repository and the {@link BlackBox}
 * Bounded Context to be used in the catch-up tests.
 */
public class CounterCatchUp implements Closeable {

    private final CounterView.Repository repo;
    private final BlackBox ctx;
    private final String[] ids;

    public CounterCatchUp(String... ids) {
        this.ids = ids.clone();
        this.repo = new CounterView.Repository();
        this.ctx = BlackBox.singleTenantWith(repo);
    }

    public void addHistory(Timestamp when, List<NumberAdded> events) {
        var factory = TestEventFactory.newInstance(getClass());
        for (var message : events) {
            var event = factory.createEvent(message, null);
            var context = event.getContext();
            var modifiedContext = context.toBuilder()
                    .setTimestamp(when)
                    .build();
            var eventAtTime = event.toBuilder()
                    .setContext(modifiedContext)
                    .build();
            ctx.append(eventAtTime);
        }
    }

    public void dispatch(List<NumberAdded> events, int threads) throws InterruptedException {
        post(asPostEventJobs(ctx, events), threads);
    }

    public List<Integer> counterValues() {
        return Arrays.stream(ids)
                     .map((id) -> findView(repo, id).state()
                                                    .getTotal())
                     .collect(toList());
    }

    public Optional<CounterView> find(String id) {
        return repo.find(id);
    }

    public String[] targets() {
        return ids.clone();
    }

    public List<NumberAdded> generateEvents(int howMany) {
        var idIterator = Iterators.cycle(ids);
        List<NumberAdded> events = new ArrayList<>(howMany);
        for (var i = 0; i < howMany; i++) {
            events.add(NumberAdded.newBuilder()
                               .setCalculatorId(idIterator.next())
                               .setValue(0)
                               .build());
        }
        return events;
    }

    public void dispatchWithCatchUp(List<NumberAdded> events,
                                    int threads,
                                    WhatToCatchUp... whatToCatchUp) throws InterruptedException {
        List<Callable<Object>> jobs = new ArrayList<>();
        jobs.addAll(asCallableJobs(whatToCatchUp));
        jobs.addAll(asPostEventJobs(ctx, events));
        post(jobs, threads);
    }

    private ImmutableList<Callable<Object>> asCallableJobs(WhatToCatchUp... whatToCatchUp) {
        ImmutableList.Builder<Callable<Object>> jobs = ImmutableList.builder();
        for (var task : whatToCatchUp) {
            Callable<Object> callable = () -> {
                catchUp(task);
                return nullRef();
            };
            jobs.add(callable);
        }
        return jobs.build();
    }

    public void catchUp(WhatToCatchUp task) {
        if (task.shouldCatchUpAll()) {
            repo.catchUpAll(task.sinceWhen());
        } else {
            var targetId = checkNotNull(task.id());
            repo.catchUp(task.sinceWhen(), ImmutableSet.of(targetId));
        }
    }

    private static List<Callable<Object>>
    asPostEventJobs(BlackBox ctx, List<NumberAdded> events) {
        return events.stream()
                     .map(e -> (Callable<Object>) () -> ctx.receivesEvent(e))
                     .collect(toList());
    }

    public static void addOngoingCatchUpRecord(WhatToCatchUp target) {
        addOngoingCatchUpRecord(target, CatchUpStatus.IN_PROGRESS);
    }

    public static void addOngoingCatchUpRecord(WhatToCatchUp target, CatchUpStatus status) {
        var factory = ServerEnvironment.instance()
                                       .storageFactory();
        var storage = new CatchUpStorage(factory, false);
        Collection<Object> ids = null;
        if (!target.shouldCatchUpAll()) {
            var identifier = checkNotNull(target.id());
            ids = ImmutableList.of(identifier);
        }
        var record = TestCatchUpJobs.catchUpJob(
                CounterView.projectionType(), status, target.sinceWhen(), ids);
        TestCatchUpJobs.writeJob(storage, record);
        var delivery = Delivery.newBuilder()
                .setCatchUpStorage(storage)
                .build();
        delivery.subscribe(new LocalDispatchingObserver());
        ServerEnvironment.under(Tests.class)
                         .use(delivery);
    }

    @Override
    public void close() {
        ctx.close();
    }

    @Override
    public boolean isOpen() {
        return ctx.isOpen();
    }
}
