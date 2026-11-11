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

package io.spine.system.server;

import io.spine.annotation.VisibleForTesting;

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
    SystemContext target() {
        return context;
    }
}
