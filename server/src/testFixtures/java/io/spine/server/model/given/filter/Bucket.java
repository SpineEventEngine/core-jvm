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

package io.spine.server.model.given.filter;

import io.spine.core.Subscribe;
import io.spine.core.Where;

import java.lang.reflect.Method;

import static io.spine.util.Exceptions.illegalStateWithCauseOf;

/**
 * Test environment class which declares filtering and non-filtering subscriber methods.
 *
 * @see io.spine.server.model.ArgumentFilterTest
 */
public class Bucket {

    private long peas;
    private long other;

    public static Method onlyPeas() {
        return method("onlyPeas");
    }

    public static Method everythingElse() {
        return method("everythingElse");
    }

    @Subscribe
    void onlyPeas(@Where(field = "kind", equals = "PEA") BeanAdded e) {
        peas = peas + e.getNumber();
    }

    @Subscribe
    void everythingElse(BeanAdded e) {
        other = other + e.getNumber();
    }

    private static Method method(String name) {
        try {
            return Bucket.class.getDeclaredMethod(name, BeanAdded.class);
        } catch (NoSuchMethodException e) {
            throw illegalStateWithCauseOf(e);
        }
    }
}
