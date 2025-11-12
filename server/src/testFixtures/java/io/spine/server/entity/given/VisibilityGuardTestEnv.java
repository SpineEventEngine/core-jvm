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

package io.spine.server.entity.given;

import io.spine.server.aggregate.Aggregate;
import io.spine.server.aggregate.AggregateRepository;
import io.spine.test.entity.FullAccessAggregate;
import io.spine.test.entity.HiddenAggregate;
import io.spine.test.entity.SubscribableAggregate;

public class VisibilityGuardTestEnv {

    /** Prevent instantiation of this utility class. */
    private VisibilityGuardTestEnv() {
    }

    static class Exposed
            extends Aggregate<Long, FullAccessAggregate, FullAccessAggregate.Builder> {
    }

    public static class ExposedRepository
            extends AggregateRepository<Long, Exposed, FullAccessAggregate> {
        public ExposedRepository() {
            super();
        }
    }

    static class Subscribable
            extends Aggregate<Long, SubscribableAggregate, SubscribableAggregate.Builder> {
    }

    public static class SubscribableRepository
            extends AggregateRepository<Long, Subscribable, SubscribableAggregate> {
        public SubscribableRepository() {
            super();
        }
    }

    static class Hidden extends Aggregate<String, HiddenAggregate, HiddenAggregate.Builder> {
    }

    public static class HiddenRepository
            extends AggregateRepository<String, Hidden, HiddenAggregate> {
        public HiddenRepository() {
            super();
        }
    }
}
