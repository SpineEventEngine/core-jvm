/*
 * Copyright 2023, TeamDev. All rights reserved.
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
package io.spine.server.given.context.switchman

import com.google.common.collect.ImmutableSet
import io.spine.base.Time
import io.spine.server.aggregate.Aggregate
import io.spine.server.aggregate.AggregateRepository
import io.spine.server.aggregate.Apply
import io.spine.server.entity.alter
import io.spine.server.event.React
import io.spine.server.given.context.switchman.event.SwitchPositionConfirmed
import io.spine.server.given.context.switchman.event.SwitchWorkRecorded
import io.spine.server.given.context.switchman.event.SwitchmanAbsenceRecorded
import io.spine.server.given.context.switchman.event.switchWorkRecorded
import io.spine.server.given.context.switchman.event.switchmanAbsenceRecorded
import io.spine.server.given.context.switchman.rejection.Rejections.SwitchmanUnavailable
import io.spine.server.route.EventRouting

/**
 * The aggregate that accumulates information about switchman work and absence.
 *
 * There's only one log per system.
 */
class Log : Aggregate<Long, LogState, LogState.Builder>() {

    @React
    internal fun on(rejection: SwitchmanUnavailable): SwitchmanAbsenceRecorded =
        switchmanAbsenceRecorded {
            switchmanName = rejection.switchmanName
            timestamp = Time.currentTime()
        }

    @Apply
    private fun event(event: SwitchmanAbsenceRecorded) = alter {
        addMissingSwitchman(event.switchmanName)
    }

    @React
    internal fun on(event: SwitchPositionConfirmed): SwitchWorkRecorded =
        switchWorkRecorded {
            switchId = event.switchId
            switchmanName = event.switchmanName
        }

    @Apply
    private fun event(event: SwitchWorkRecorded) = alter {
        val switchmanName = event.switchmanName
        val currentCount = countersMap[switchmanName]
        putCounters(
            switchmanName,
            if (currentCount == null) 1 else currentCount + 1
        )
    }

    /**
     * The repository with default routing functions that route to the singleton aggregate.
     */
    class Repository : AggregateRepository<Long, Log, LogState>() {

        override fun setupEventRouting(routing: EventRouting<Long>) {
            super.setupEventRouting(routing)
            routing.replaceDefault { _, _ -> SINGLETON_ID_SET }
        }

        companion object {
            private val SINGLETON_ID_SET = ImmutableSet.of(ID)
        }
    }

    companion object {

        /** The ID of the singleton log.  */
        const val ID = 42L
    }
}
