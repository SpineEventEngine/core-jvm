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

package io.spine.system.server.given.command;

import io.spine.server.aggregate.Aggregate;
import io.spine.server.aggregate.Apply;
import io.spine.server.command.Assign;
import io.spine.system.server.Company;
import io.spine.system.server.CompanyEstablished;
import io.spine.system.server.CompanyId;
import io.spine.system.server.CompanyNameAlreadyTaken;
import io.spine.system.server.EstablishCompany;
import io.spine.type.TypeUrl;

/**
 * An aggregate handling company-related commands.
 */
public class CompanyAggregate extends Aggregate<CompanyId, Company, Company.Builder> {

    public static final TypeUrl TYPE = TypeUrl.of(Company.class);
    public static final String TAKEN_NAME = "NameIsTaken!";

    @Assign
    CompanyEstablished handle(EstablishCompany command) throws CompanyNameAlreadyTaken {
        if (TAKEN_NAME.equals(command.getFinalName())) {
            throw CompanyNameAlreadyTaken
                    .newBuilder()
                    .setId(id())
                    .setTakenName(TAKEN_NAME)
                    .build();
        }
        return CompanyEstablished
                .newBuilder()
                .setId(command.getId())
                .setName(command.getFinalName())
                .build();
    }

    @Apply
    private void on(CompanyEstablished event) {
        builder().setId(event.getId())
                 .setName(event.getName());
    }
}
