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

import io.spine.server.aggregate.AggregateRepository
import io.spine.server.given.context.switchman.command.SetSwitch
import io.spine.server.given.context.switchman.rejection.SwitchmanUnavailable
import io.spine.server.route.CommandRouting
import io.spine.server.route.CommandRouting.Companion.unableToRoute

/**
 * A repository which fires a rejection in response to a command with a particular
 * value of the target aggregate ID.
 */
class SwitchmanBureau : AggregateRepository<String, Switchman, SwitchmanLog>() {

    override fun setupCommandRouting(routing: CommandRouting<String>) {
        super.setupCommandRouting(routing)
        routing.route<SetSwitch> { cmd, _ -> routeToSwitchman(cmd) }
    }

    companion object {

        /**
         * The ID of the aggregate for which a [command][SetSwitch] would be rejected.
         */
        const val MISSING_SWITCHMAN_NAME = "Petrovich"

        /**
         * Returns the route to a switchman, checking whether he is available.
         *
         * In case the switchman isn't available, throws a `RuntimeException`.
         */
        private fun routeToSwitchman(cmd: SetSwitch): String {
            val name = cmd.switchmanName
            if (name == MISSING_SWITCHMAN_NAME) {
                unableToRoute(
                    cmd,
                    SwitchmanUnavailable.newBuilder()
                        .setSwitchmanName(name)
                        .build()
                )
            }
            return name
        }
    }
}
