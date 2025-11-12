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

package io.spine.system.server;

import io.spine.annotation.VisibleForTesting;
import io.spine.server.BoundedContext;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The default implementation of {@link SystemClient}.
 *
 * <p>This client uses the {@linkplain SystemReadSide#newInstance default} implementation of
 * the read side and the {@linkplain SystemWriteSide#newInstance default} implementation of
 * the write side.
 *
 * @see SystemContext#createClient()
 */
final class DefaultSystemClient implements SystemClient {

    private final SystemWriteSide writeSide;
    private final SystemReadSide readSide;
    private final SystemContext context;

    DefaultSystemClient(SystemContext system) {
        this.context = checkNotNull(system);
        this.readSide = SystemReadSide.newInstance(context);
        this.writeSide = SystemWriteSide.newInstance(context);
    }

    @Override
    public SystemWriteSide writeSide() {
        return writeSide;
    }

    @Override
    public SystemReadSide readSide() {
        return readSide;
    }

    @Override
    public void closeSystemContext() {
        context.close();
    }

    @VisibleForTesting
    BoundedContext target() {
        return context;
    }
}
