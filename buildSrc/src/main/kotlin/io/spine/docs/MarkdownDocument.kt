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

package io.spine.docs

import java.io.File

/**
 * A virtual document written in Markdown.
 *
 * After it's finished, end-users would typically write it to a [real file][writeToFile].
 */
@Suppress("TooManyFunctions")
class MarkdownDocument {

    private val builder: StringBuilder = StringBuilder()

    /**
     * Appends the document with some plain text.
     */
    fun text(value: String): MarkdownDocument {
        builder.append(value)
        return this
    }

    /**
     * Appends the document with a new line symbol.
     */
    fun nl(): MarkdownDocument = text(System.lineSeparator())

    /**
     * Appends the document with a single space.
     */
    fun space(): MarkdownDocument = text(" ")

    /**
     * Appends the document with a number of space symbols.
     */
    private fun space(count: Int): MarkdownDocument {
        repeat(count) {
            space()
        }
        return this
    }

    /**
     * Appends the document with a space symbol.
     *
     * A DSL-style shortcut for [space].
     */
    fun and(): MarkdownDocument = space()

    /**
     * Adds some text rendered in bold.
     */
    fun bold(text: String): MarkdownDocument = text("**$text**")

    /**
     * Adds a heading of level 1.
     */
    fun h1(text: String): MarkdownDocument = nl().text("# $text")

    /**
     * Adds a heading of level 2.
     */
    fun h2(text: String): MarkdownDocument = nl().text("## $text")

    /**
     * Adds a link.
     */
    fun link(text: String, url: String): MarkdownDocument = text("[$text](${url})")

    /**
     * Adds a link and uses the passed [url] value as both text and a navigation destination.
     */
    fun link(url: String): MarkdownDocument = link(url, url)

    /**
     * Renders the item of an unordered list, also indenting it by the specified number of spaces.
     */
    fun ul(indent: Int): MarkdownDocument = nl().space(indent).text("* ")

    /**
     * Renders the item of an ordered list.
     */
    fun ol(): MarkdownDocument = nl().text("1. ")

    /**
     * Writes the content of this document to the passed file.
     *
     * If the file exists, it becomes overwritten.
     */
    fun writeToFile(file: File) {
        file.writeText(builder.toString())
    }
}
