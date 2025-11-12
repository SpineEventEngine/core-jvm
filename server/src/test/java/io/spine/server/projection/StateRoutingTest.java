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

package io.spine.server.projection;

import com.google.protobuf.StringValue;
import io.spine.base.Identifier;
import io.spine.protobuf.AnyPacker;
import io.spine.server.route.given.sur.ArtistMood;
import io.spine.server.route.given.sur.ArtistMoodRepo;
import io.spine.server.route.given.sur.Gallery;
import io.spine.server.route.given.sur.MagazineAggregate;
import io.spine.server.route.given.sur.Manifesto;
import io.spine.server.route.given.sur.Surrealism;
import io.spine.server.route.given.sur.Works;
import io.spine.server.route.given.sur.WorksProjection;
import io.spine.server.route.given.sur.command.PublishArticle;
import io.spine.server.route.given.sur.event.PieceOfArtCreated;
import io.spine.testing.server.blackbox.BlackBox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.spine.server.route.given.sur.Surrealism.BRETON;
import static io.spine.server.route.given.sur.Surrealism.GOLL;

@DisplayName("`ProjectionRepository` state routing should")
class StateRoutingTest {

    private BlackBox context;

    @BeforeEach
    void setupContext() {
        context = BlackBox.singleTenantWith(
                MagazineAggregate.class,
                new ArtistMoodRepo(),
                new Gallery(),
                WorksProjection.class
        );
    }

    @Test
    @DisplayName("support explicit routing")
    void explicit() {
        var manifesto = Manifesto.newBuilder()
                .setAuthor(GOLL)
                .setTitle("My France")
                .setText("Lorem ipsum")
                .build();
        context.receivesCommand(
                PublishArticle.newBuilder()
                        .setMagazineName("Manifeste du surréalisme")
                        .setArticle(AnyPacker.pack(manifesto))
                        .build()
        );

        context.assertState(BRETON, ArtistMood.class)
               .comparingExpectedFieldsOnly()
               .isEqualTo(ArtistMood.newBuilder()
                                  .setMood(ArtistMood.Mood.ANGER)
                                  .buildPartial());
    }

    @Test
    @DisplayName("route state by the first field matching the ID type")
    void implicit() {
        var artist = Surrealism.name("André Masson");
        var automaticDrawing = AnyPacker.pack(StringValue.of("Automatic Drawing"));
        context.receivesEvent(
                PieceOfArtCreated.newBuilder()
                        .setUuid(Identifier.newUuid())
                        .setArtist(artist)
                        .setContent(automaticDrawing)
                        .build()
        );

        var works = Works
                .newBuilder()
                .setArtist(artist)
                .addWork(automaticDrawing)
                .build();
        context.assertState(artist, works);
    }
}
