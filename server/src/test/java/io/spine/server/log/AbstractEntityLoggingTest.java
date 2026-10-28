/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
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
        try (var context = context()) {
            context.receivesCommand(command);
        }
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
        try (var context = context()) {
            context.receivesCommand(command);
        }
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
        try (var context = context()) {
            context.receivesCommand(command);
        }
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
