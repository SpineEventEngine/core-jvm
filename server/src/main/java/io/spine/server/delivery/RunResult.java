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

/**
 * How the delivery of all messages read from the {@code Inbox} according to a certain
 * {@code ShardIndex} ended.
 */
class RunResult {

    private final int deliveredMsgCount;
    private final boolean stoppedByMonitor;
    private final boolean catchUpJobsChanged;

    /**
     * Creates the result of a run.
     *
     * @param count
     *         the number of the delivered messages
     * @param stoppedByMonitor
     *         whether the {@code DeliveryMonitor} stopped the run
     * @param catchUpJobsChanged
     *         whether the run ended before delivering a page, because the statuses of
     *         the catch-up jobs changed
     */
    RunResult(int count, boolean stoppedByMonitor, boolean catchUpJobsChanged) {
        deliveredMsgCount = count;
        this.stoppedByMonitor = stoppedByMonitor;
        this.catchUpJobsChanged = catchUpJobsChanged;
    }

    /**
     * Tells if another run is required.
     *
     * <p>Another run is required if the finished one delivered messages or ended because
     * the statuses of the catch-up jobs changed, unless the {@code DeliveryMonitor}
     * stopped the run.
     */
    boolean shouldRunAgain() {
        return !stoppedByMonitor && (deliveredMsgCount > 0 || catchUpJobsChanged);
    }

    /**
     * Returns the number of delivered messages.
     */
    int deliveredCount() {
        return this.deliveredMsgCount;
    }
}
