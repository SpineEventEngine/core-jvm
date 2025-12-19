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
package io.spine.server.entity;

import io.spine.annotation.Internal;
import io.spine.base.Error;
import io.spine.core.Event;
import io.spine.validation.NonValidated;

/**
 * A common contract for the {@linkplain Transaction transaction} listeners.
 *
 * <p>Provides an ability to add callbacks to the transaction execution stages.
 *
 * @param <I>
 *         ID type of the entity under transaction
 */
@Internal
public interface TransactionListener<I> {

    /**
     * A callback invoked before applying a {@linkplain Phase transaction phase}.
     *
     * @param phase
     *         the phase which is being applied
     */
    void onBeforePhase(Phase<I> phase);

    /**
     * A callback invoked after applying a {@linkplain Phase transaction phase}.
     *
     * <p>This callback is invoked for both successfully applied and failed phases.
     *
     * @param phase
     *         the phase which was applied before this callback is invoked
     */
    void onAfterPhase(Phase<I> phase);

    /**
     * A callback invoked before committing the transaction.
     *
     * @param entityRecord
     *         the entity modified within the transaction
     * @apiNote The {@code entityRecord} is {@code @NonValidated} because the changes
     *         are not yet committed and it's not possible to guarantee that the record
     *         will be valid.
     */
    void onBeforeCommit(@NonValidated EntityRecord entityRecord);

    /**
     * A callback invoked if the commit has failed.
     *
     * @param cause
     *         the error which caused the commit failure
     * @param entityRecord
     *         the uncommitted entity state
     * @apiNote The {@code entityRecord} is {@code @NonValidated} because the transaction
     *         failed and it's not possible to guarantee that the record is valid.
     */
    void onTransactionFailed(Error cause, @NonValidated EntityRecord entityRecord);

    /**
     * A callback invoked if the commit has failed due to a Rejection.
     *
     * @param cause
     *         the rejection which caused the commit failure
     * @param entityRecord
     *         the uncommitted entity state
     * @apiNote The {@code entityRecord} is {@code @NonValidated} because the transaction
     *         failed and it's not possible to guarantee that the record is valid.
     */
    void onTransactionFailed(Event cause, @NonValidated EntityRecord entityRecord);

    /**
     * A callback invoked after a successful commit.
     *
     * @param change
     *         the change of the entity under transaction
     */
    void onAfterCommit(EntityRecordChange change);
}
