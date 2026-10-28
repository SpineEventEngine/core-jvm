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
import org.junit.jupiter.api.AfterEach;
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

    @AfterEach
    void closeContext() {
        context.close();
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
