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

package io.spine.server.model;

import io.spine.model.contexts.projects.event.SigProjectCreated;
import io.spine.model.contexts.projects.event.SigTaskStarted;
import io.spine.server.event.model.EventReactorSignatures;
import io.spine.server.event.model.SubscriberSignature;
import io.spine.server.model.handler.given.CovariantReactor;
import io.spine.server.model.handler.given.InvalidProtectedSubscriber;
import io.spine.server.model.handler.given.MismatchedProtectedSubscriber;
import io.spine.server.model.handler.given.RougeProtectedSubscriber;
import io.spine.server.model.handler.given.ValidProtectedSubscriber;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.server.model.AccessModifier.PROTECTED_CONTRACT;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("`MethodSignature` access modifiers should")
class ModifiersInSignatureTest {

    @Test
    @DisplayName("allow for protected methods which are overridden from a superclass")
    void validProtected() throws NoSuchMethodException {
        var method = ValidProtectedSubscriber.class
                .getDeclaredMethod("overridingProtected", SigProjectCreated.class);
        var foundModifier = new SubscriberSignature()
                .modifier()
                .stream()
                .filter(m -> m.test(method))
                .findFirst()
                .orElseGet(Assertions::fail);
        assertThat(foundModifier)
                .isSameInstanceAs(PROTECTED_CONTRACT);
    }

    @Test
    @DisplayName("not allow for protected methods in general")
    void invalidProtected() throws NoSuchMethodException {
        var method = RougeProtectedSubscriber.class
                .getDeclaredMethod("justForTheLols", SigProjectCreated.class);
        var foundModifier = new SubscriberSignature()
                .modifier()
                .stream()
                .filter(m -> m.test(method))
                .findFirst();
        assertThat(foundModifier)
                .isEmpty();
    }

    @Test
    @DisplayName("not allow for protected methods in with an invalid contract")
    void invalid() throws NoSuchMethodException {
        var method = InvalidProtectedSubscriber.class
                .getDeclaredMethod("plainWrong", SigProjectCreated.class);
        var error = assertThrows(ModelError.class, () -> PROTECTED_CONTRACT.test(method));
        assertThat(error)
                .hasMessageThat()
                .endsWith("not marked with `@ContractFor`.");
    }

    @Test
    @DisplayName("not allow for protected contract methods in with a mismatched handler type")
    void mismatched() throws NoSuchMethodException {
        var method = MismatchedProtectedSubscriber.class
                .getDeclaredMethod("reactor", SigProjectCreated.class);
        var error = assertThrows(ModelError.class, () -> PROTECTED_CONTRACT.test(method));
        assertThat(error)
                .hasMessageThat()
                .endsWith("not marked with `@React`.");
    }

    @Test
    @DisplayName("allow parameter type covariance")
    void covariance() throws NoSuchMethodException {
        var method = CovariantReactor.class
                .getDeclaredMethod("covariantReactor", SigTaskStarted.class);
        var foundModifier = EventReactorSignatures.createForTest().modifier()
                .stream()
                .filter(m -> m.test(method))
                .findFirst()
                .orElseGet(Assertions::fail);
        assertThat(foundModifier)
                .isSameInstanceAs(PROTECTED_CONTRACT);
    }
}
