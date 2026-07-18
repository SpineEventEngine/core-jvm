/*
 * Copyright 2026, TeamDev. All rights reserved.
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

import javax.annotation.concurrent.ThreadSafe;

/**
 * A counter providing the version for the incoming {@code InboxMessage}s.
 *
 * <p>Serves to distinguish the messages arrived at the very same millisecond, even from
 * different threads.
 *
 * <p>Hard-coded to reset the value to zero every {@link #MAX_VERSION} times. This allows
 * avoiding any number overflows, but still is sufficient for realistic use-cases.
 */
@ThreadSafe
final class VersionCounter {

    private static final int MAX_VERSION = 10_000;
    private static final VersionCounter instance = new VersionCounter();

    private int counter;

    private synchronized int getNextValue() {
        counter++;
        counter = counter % MAX_VERSION;
        return counter;
    }

    /**
     * Obtains the next version value.
     */
    static int next() {
        return instance.getNextValue();
    }
}
