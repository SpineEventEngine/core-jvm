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

package io.spine.server.event.given;

import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Timestamp;
import io.grpc.stub.StreamObserver;
import io.spine.core.Event;
import io.spine.test.event.ProjectCreated;
import io.spine.test.event.TaskAdded;
import io.spine.testdata.Sample;
import io.spine.testing.server.TestEventFactory;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.collect.Sets.newConcurrentHashSet;
import static org.junit.jupiter.api.Assertions.fail;

public class EventStoreTestEnv {

    private static final TestEventFactory eventFactory =
            TestEventFactory.newInstance(EventStoreTestEnv.class);

    /** Prevents instantiation of this utility class. */
    private EventStoreTestEnv() {
    }

    public static Event projectCreated(Timestamp when) {
        var msg = Sample.messageOfType(ProjectCreated.class);
        return eventFactory.createEvent(msg, null, when);
    }

    public static Event taskAdded(Timestamp when) {
        var msg = Sample.messageOfType(TaskAdded.class);
        return eventFactory.createEvent(msg, null, when);
    }

    public static void assertDone(AtomicBoolean done) {
        if (!done.get()) {
            fail("Please use the `MoreExecutors.directExecutor` in `EventStore` for tests.");
        }
    }

    public static class ResponseObserver implements StreamObserver<Event> {

        private final Collection<Event> events = newConcurrentHashSet();
        private final AtomicBoolean doneFlag;

        public ResponseObserver(AtomicBoolean doneFlag) {
            this.doneFlag = doneFlag;
        }

        public ImmutableSet<Event> getEvents() {
            return ImmutableSet.copyOf(events);
        }

        @Override
        public void onNext(Event value) {
            events.add(value);
        }

        @Override
        public void onError(Throwable t) {
            fail(t.getMessage());
        }

        @Override
        public void onCompleted() {
            doneFlag.getAndSet(true);
        }
    }
}
