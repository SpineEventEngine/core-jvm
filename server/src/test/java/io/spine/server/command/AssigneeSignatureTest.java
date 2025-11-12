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

package io.spine.server.command;

import io.spine.server.command.model.AssigneeSignature;
import io.spine.server.command.model.given.handler.InvalidAssignee;
import io.spine.server.command.model.given.handler.ValidAssignee;
import io.spine.server.model.ReceptorSignatureTest;
import org.junit.jupiter.api.DisplayName;

import java.lang.reflect.Method;
import java.util.stream.Stream;

@DisplayName("`AssigneeSignature` should")
class AssigneeSignatureTest extends ReceptorSignatureTest<AssigneeSignature> {

    @Override
    protected Stream<Method> validMethods() {
        return methodsAnnotatedWith(Assign.class, ValidAssignee.class).stream();
    }

    @Override
    protected Stream<Method> invalidMethods() {
        return methodsAnnotatedWith(Assign.class, InvalidAssignee.class).stream();
    }

    @Override
    protected AssigneeSignature signature() {
        return new AssigneeSignature();
    }
}
