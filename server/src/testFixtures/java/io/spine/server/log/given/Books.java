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

package io.spine.server.log.given;

import io.spine.people.PersonName;
import io.spine.server.log.Book;
import io.spine.server.log.Isbn;

/**
 * A test factory for instances of {@code Book} and {@code Isbn}.
 */
public final class Books {

    public static final Isbn BIG_BLUE_BOOK = isbn("978-0321125217");
    public static final Isbn BIG_RED_BOOK = isbn("978-0321834577");
    public static final Isbn SMALL_GREEN_BOOK = isbn("978-0134434421");

    public static final Isbn THE_HOBBIT = isbn("978-0547928241");

    private static final String PUBLISHER = "A-W";

    /**
     * Prevents the utility class instantiation.
     */
    private Books() {
    }

    public static Book domainDrivenDesign() {
        return Book
                .newBuilder()
                .setIsbn(BIG_BLUE_BOOK)
                .setTitle("Domain-Driven Design")
                .addAuthor(PersonName.newBuilder().setGivenName("Eric"))
                .setPublisher(PUBLISHER)
                .build();
    }

    public static Book implementingDdd() {
        return Book
                .newBuilder()
                .setIsbn(BIG_BLUE_BOOK)
                .setTitle("Implementing DDD")
                .addAuthor(PersonName.newBuilder().setGivenName("Vaughn"))
                .setPublisher(PUBLISHER)
                .build();
    }

    public static Book dddDistilled() {
        return Book
                .newBuilder()
                .setIsbn(BIG_BLUE_BOOK)
                .setTitle("DDD Distilled")
                .addAuthor(PersonName.newBuilder().setGivenName("Vaughn"))
                .setPublisher(PUBLISHER)
                .build();
    }

    private static Isbn isbn(String value) {
        return Isbn
                .newBuilder()
                .setValue(value)
                .build();
    }
}
