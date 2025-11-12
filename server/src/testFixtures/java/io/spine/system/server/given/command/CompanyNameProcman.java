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

import io.spine.core.CommandContext;
import io.spine.server.command.Assign;
import io.spine.server.command.Command;
import io.spine.server.procman.ProcessManager;
import io.spine.system.server.CompanyEstablishing;
import io.spine.system.server.CompanyEstablishingStarted;
import io.spine.system.server.CompanyId;
import io.spine.system.server.CompanyNameRethought;
import io.spine.system.server.EstablishCompany;
import io.spine.system.server.FinalizeCompanyName;
import io.spine.system.server.ProposeCompanyName;
import io.spine.system.server.StartCompanyEstablishing;
import io.spine.type.TypeUrl;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A process manager handling company name resolution.
 */
public class CompanyNameProcman
        extends ProcessManager<CompanyId, CompanyEstablishing, CompanyEstablishing.Builder> {

    private CompanyNameProcman(CompanyId id) {
        super(id);
    }

    public static final TypeUrl TYPE = TypeUrl.of(CompanyEstablishing.class);
    public static final String FAULTY_NAME = "This name is exceptionally faulty";

    @Assign
    CompanyEstablishingStarted handle(StartCompanyEstablishing command) {
        builder().setId(command.getId());

        return CompanyEstablishingStarted.newBuilder()
                .setId(command.getId())
                .build();
    }

    @Assign
    CompanyNameRethought handle(ProposeCompanyName command) {
        var name = command.getName();
        checkArgument(!name.equals(FAULTY_NAME));
        builder().setProposedName(name);

        return CompanyNameRethought.newBuilder()
                .setId(command.getId())
                .setName(command.getName())
                .build();
    }

    @Command
    EstablishCompany transform(FinalizeCompanyName command, CommandContext context) {
        var name = builder().getProposedName();
        var establishCommand = EstablishCompany.newBuilder()
                .setId(builder().getId())
                .setFinalName(name)
                .build();
        return establishCommand;
    }
}
