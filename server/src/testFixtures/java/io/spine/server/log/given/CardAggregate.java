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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.spine.logging.WithLogging;
import io.spine.server.aggregate.Aggregate;
import io.spine.server.aggregate.Apply;
import io.spine.server.command.Assign;
import io.spine.server.log.Book;
import io.spine.server.log.BookReturned;
import io.spine.server.log.BooksBorrowed;
import io.spine.server.log.BorrowBooks;
import io.spine.server.log.Isbn;
import io.spine.server.log.LibraryCard;
import io.spine.server.log.LibraryCardId;
import io.spine.server.log.ReturnBook;
import io.spine.server.log.UnknownBook;

import java.util.ArrayList;
import java.util.List;

import static io.spine.server.log.given.Books.BIG_BLUE_BOOK;
import static io.spine.server.log.given.Books.BIG_RED_BOOK;
import static io.spine.server.log.given.Books.SMALL_GREEN_BOOK;
import static io.spine.server.log.given.Books.dddDistilled;
import static io.spine.server.log.given.Books.domainDrivenDesign;
import static io.spine.server.log.given.Books.implementingDdd;
import static java.lang.String.format;

public final class CardAggregate
        extends Aggregate<LibraryCardId, LibraryCard, LibraryCard.Builder>
        implements WithLogging {

    private static final ImmutableMap<Isbn, Book> knownBooks = ImmutableMap.of(
            BIG_BLUE_BOOK, domainDrivenDesign(),
            BIG_RED_BOOK, implementingDdd(),
            SMALL_GREEN_BOOK, dddDistilled()
    );

    @Assign
    BooksBorrowed handle(BorrowBooks command) throws UnknownBook {
        var event = BooksBorrowed.newBuilder().setCard(id());
        List<Isbn> unknownBooks = new ArrayList<>();
        for (var bookId : command.getBookIdList()) {
            var book = knownBooks.get(bookId);
            if (book != null) {
                event.addBook(book);
                var authors = book.getAuthorList();
                var firstAuthor = authors.get(0);
                logger().atDebug().log(() -> format(
                        "Adding to order: %s by %s %s",
                            book.getTitle(),
                            firstAuthor.getGivenName(),
                            firstAuthor.getFamilyName()));
            } else {
                logger().atWarning().log(() -> format(
                        "Cannot lend an unknown book. ISBN: `%s`", bookId.getValue()));
                unknownBooks.add(bookId);
            }
        }
        if (!unknownBooks.isEmpty()) {
            throw UnknownBook.newBuilder()
                    .addAllBook(unknownBooks)
                    .build();
        } else {
            return event.build();
        }
    }

    @Assign
    BookReturned handle(ReturnBook command) throws UnknownBook {
        var isbn = command.getBook();
        var book = knownBooks.get(isbn);
        if (book == null) {
            var rejection = UnknownBook.newBuilder()
                    .addAllBook(ImmutableList.of(isbn))
                    .build();
            logger().atError().withCause(rejection).log(() -> format(
                    "Cannot return an unknown book. ISBN: `%s`", isbn.getValue()));
            throw rejection;
        } else {
            return BookReturned.newBuilder()
                    .setCard(id())
                    .setBook(book)
                    .build();
        }
    }

    @Apply
    private void on(BooksBorrowed event) {
        builder().addAllBook(event.getBookList());
    }

    @Apply
    private void on(BookReturned event) {
        var list = builder().getBookList();
        var book = event.getBook();
        var bookIndex = list.indexOf(book);
        builder().removeBook(bookIndex);
    }
}
