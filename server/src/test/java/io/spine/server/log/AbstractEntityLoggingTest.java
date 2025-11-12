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

package io.spine.server.log;

import io.spine.core.UserId;
import io.spine.server.log.given.Books;
import io.spine.server.log.given.CardAggregate;
import io.spine.testing.core.given.GivenUserId;
import io.spine.testing.logging.LoggingTest;
import io.spine.testing.logging.mute.MuteLogging;
import io.spine.testing.server.blackbox.BlackBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.spine.server.log.given.Books.THE_HOBBIT;
import static io.spine.server.log.given.Books.implementingDdd;
import static java.util.logging.Level.ALL;
import static java.util.logging.Level.FINE;

@MuteLogging
@DisplayName("`AbstractEntity` logging should")
class AbstractEntityLoggingTest extends LoggingTest {

    AbstractEntityLoggingTest() {
        super(CardAggregate.class, ALL);
    }

    @BeforeEach
    void startIntercepting() {
        interceptLogging();
    }

    @AfterEach
    void stopIntercepting() {
        restoreLogging();
    }

    @Test
    @DisplayName("log handler method name and parameters")
    void includeSignalName() {
        var user = GivenUserId.generated();
        var command = borrowBooks(user);
        context().receivesCommand(command);
        var assertLog = assertLog().record();
        assertLog
                .hasLevelThat()
                .isEqualTo(FINE);
        assertLog
                .hasMessageThat()
                .containsMatch(implementingDdd().getTitle());
        assertLog
                .hasMessageThat()
                .contains(command.getClass().getSimpleName());
    }

    @Test
    @DisplayName("log handler class name")
    void includeClassName() {
        var user = GivenUserId.generated();
        var command = borrowBooks(user);
        context().receivesCommand(command);
        assertLog()
                .record()
                .hasClassNameThat()
                .isEqualTo(CardAggregate.class.getName());
    }

    @Test
    @DisplayName("pass the throwable unchanged")
    void withCause() {
        var user = GivenUserId.generated();
        var command = ReturnBook.newBuilder()
                .setCard(cardId(user))
                .setBook(THE_HOBBIT)
                .build();
        context().receivesCommand(command);
        var assertRecord = assertLog().record();
        assertRecord.isError();
        assertRecord.hasThrowableThat()
                    .isInstanceOf(UnknownBook.class);
    }

    private static BlackBox context() {
        return BlackBox.singleTenantWith(CardAggregate.class);
    }

    private static BorrowBooks borrowBooks(UserId reader) {
        var id = cardId(reader);
        var command = BorrowBooks.newBuilder()
                .setCard(id)
                .addBookId(Books.BIG_RED_BOOK)
                .build();
        return command;
    }

    private static LibraryCardId cardId(UserId reader) {
        return LibraryCardId.newBuilder()
                    .setReader(reader)
                    .build();
    }
}
