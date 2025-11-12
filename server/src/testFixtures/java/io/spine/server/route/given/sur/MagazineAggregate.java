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

package io.spine.server.route.given.sur;

import io.spine.core.CommandContext;
import io.spine.protobuf.AnyPacker;
import io.spine.server.aggregate.Aggregate;
import io.spine.server.aggregate.Apply;
import io.spine.server.command.Assign;
import io.spine.server.route.given.sur.command.PublishArticle;
import io.spine.server.route.given.sur.event.ArticlePublished;

public final class MagazineAggregate extends Aggregate<String, Magazine, Magazine.Builder> {

    @Assign
    ArticlePublished handle(PublishArticle cmd, CommandContext ctx) {

        return ArticlePublished
                .newBuilder()
                .setMagazineName(id())
                .setAuthor(author(cmd, ctx))
                .setArticle(cmd.getArticle())
                .build();
    }

    @Apply
    private void event(ArticlePublished e) {
        builder().setName(id())
                 .addArticle(e.getArticle());
    }

    ArtistName author(PublishArticle cmd, CommandContext ctx) {
        var article = AnyPacker.unpack(cmd.getArticle());
        if (article instanceof Manifesto) {
            var manifesto = (Manifesto) article;
            return manifesto.getAuthor();
        }

        return ArtistName.newBuilder()
                .setValue(ctx.actor().getValue())
                .build();
    }
}
