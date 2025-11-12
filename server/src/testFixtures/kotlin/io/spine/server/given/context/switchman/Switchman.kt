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

import io.spine.server.aggregate.Aggregate
import io.spine.server.aggregate.Apply
import io.spine.server.command.Assign
import io.spine.server.entity.alter
import io.spine.server.given.context.switchman.command.SetSwitch
import io.spine.server.given.context.switchman.event.SwitchPositionConfirmed
import io.spine.server.given.context.switchman.event.switchPositionConfirmed

/**
 * The aggregate that handles commands send to a switchman.
 */
class Switchman internal constructor(id: String) :
    Aggregate<String, SwitchmanLog, SwitchmanLog.Builder>(id) {

    @Assign
    internal fun on(cmd: SetSwitch): SwitchPositionConfirmed =
        switchPositionConfirmed {
            switchmanName = id()
            switchId = cmd.switchId
            position = cmd.position
        }

    @Apply
    private fun event(event: SwitchPositionConfirmed) = alter {
        name = event.switchmanName
        switchCount += 1
    }
}
