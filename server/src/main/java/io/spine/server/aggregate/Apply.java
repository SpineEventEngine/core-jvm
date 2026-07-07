/*
 * Copyright 2026, TeamDev. All rights reserved.
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

package io.spine.server.aggregate;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method of an aggregate as an <i>event applier</i> — a method that modified the
 * aggregate's state from a played event.
 *
 * @deprecated Event sourcing has been removed from aggregates. An aggregate now mutates its state
 *         directly in {@code @Assign} / {@code @React} receptors via {@link Aggregate#builder()}
 *         and loads from its latest persisted state instead of replaying events. A class that
 *         still declares an {@code @Apply}-annotated method fails fast at model-building time with
 *         a {@code ModelError}: move each applier's body into the receptor that emits the event,
 *         and delete the {@code @Apply} method. This annotation is retained (deprecated) only so
 *         that such classes are detected for the fail-fast check; it is scheduled for removal in
 *         v2.0.0.
 */
@Deprecated
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Apply {

    /**
     * Formerly enabled importing of events into the aggregate.
     *
     * @deprecated Event import has been removed. External facts now enter via
     *         {@code (external) = true} reactions or context gateways. This attribute is ignored.
     */
    @Deprecated
    boolean allowImport() default false;
}
