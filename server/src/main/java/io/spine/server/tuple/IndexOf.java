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

package io.spine.server.tuple;

/**
 * Defines the indexes of the elements in tuples.
 *
 * <p>Serves to replace numerous "magic numbers" across the codebase.
 *
 * <p>Each index is put into a correspondence to the capital letter which is used as to name
 * a generic parameter in the tuple's declaration.
 */
enum IndexOf {

    /**
     * The index of the first element in tuples.
     */
    A(0),

    /**
     * The index of the second element in tuples.
     */
    B(1),

    /**
     * The index of the third element in tuples.
     */
    C(2),

    /**
     * The index of the fourth element in tuples.
     */
    D(3),

    /**
     * The index of the fifth element in tuples.
     */
    E(4);

    private final int value;

    IndexOf(int value) {
        this.value = value;
    }

    /**
     * Returns the index value.
     */
    int value() {
        return value;
    }

    /**
     * Tells whether the passed value equals to the index value of this instance.
     */
    boolean is(int value) {
        return value == this.value;
    }
}
