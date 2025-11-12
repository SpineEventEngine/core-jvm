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

package io.spine.server.query

import io.spine.base.EntityState

/**
 * A component capable of querying states of views.
 */
public interface Querying {

    /**
     * Creates a [QueryingClient] to find views of the given entity state class.
     *
     * This method is targeted for Java API users. If you use Kotlin, see the no-param overload for
     * prettier code.
     */
    public fun <P : EntityState<*>> select(type: Class<P>): QueryingClient<P>
}

/**
 * Creates a [QueryingClient] to find views of the given entity state type.
 *
 * This overload is for Kotlin API users. Java users cannot access `inline` methods. As the method
 * is `inline`, it cannot be declared inside the interface. Thus, we use an extension method for
 * this overload.
 */
public inline fun <reified P : EntityState<*>> Querying.select(): QueryingClient<P> {
    val cls = P::class.java
    return select(cls)
}
