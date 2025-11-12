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

package io.spine.server.aggregate;

import com.google.common.collect.ImmutableList;
import io.spine.base.CommandMessage;
import io.spine.base.EntityState;
import io.spine.client.ArchivedColumn;
import io.spine.client.CommandFactory;
import io.spine.client.DeletedColumn;
import io.spine.client.Query;
import io.spine.client.QueryFactory;
import io.spine.server.BoundedContext;
import io.spine.server.BoundedContextBuilder;
import io.spine.server.aggregate.given.query.AggregateQueryingTestEnv.InvisibleSound;
import io.spine.server.aggregate.given.query.AggregateQueryingTestEnv.PhotoAggregate;
import io.spine.server.entity.EntityRecord;
import io.spine.test.aggregate.query.MRPhoto;
import io.spine.test.aggregate.query.MRPhotoId;
import io.spine.test.aggregate.query.MRSoundRecord;
import io.spine.testing.client.TestActorRequestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.client.EntityQueryToProto.transformWith;
import static io.spine.grpc.StreamObservers.noOpObserver;
import static io.spine.protobuf.AnyPacker.unpackFunc;
import static io.spine.server.aggregate.given.query.AggregateQueryingTestEnv.archive;
import static io.spine.server.aggregate.given.query.AggregateQueryingTestEnv.delete;
import static io.spine.server.aggregate.given.query.AggregateQueryingTestEnv.givenPhotos;
import static io.spine.server.aggregate.given.query.AggregateQueryingTestEnv.jxBrowserLogo7K;
import static io.spine.server.aggregate.given.query.AggregateQueryingTestEnv.newPhotosRepository;
import static io.spine.server.aggregate.given.query.AggregateQueryingTestEnv.projectLogo1000by800;
import static io.spine.server.aggregate.given.query.AggregateQueryingTestEnv.spineLogo200by200;
import static io.spine.server.aggregate.given.query.AggregateQueryingTestEnv.upload;
import static io.spine.test.aggregate.query.MRPhotoType.CROP_FRAME;
import static io.spine.test.aggregate.query.MRPhotoType.FULL_FRAME;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

@DisplayName("`AggregateRepository` running queries should ")
class AggregateQueryingTest {

    private QueryFactory queries;
    private Collection<MRPhoto> givenPhotos;
    private BoundedContext context;
    private CommandFactory commands;
    private AggregateRepository<MRPhotoId, PhotoAggregate, MRPhoto> repository;

    @BeforeEach
    void setUp() {
        repository = newPhotosRepository();
        context = BoundedContextBuilder.assumingTests(false)
                                       .add(repository)
                                       .build();
        var requestFactory = new TestActorRequestFactory(AggregateQueryingTest.class);
        queries = requestFactory.query();
        commands = requestFactory.command();
        givenPhotos = givenPhotos();
        prepareAggregates(givenPhotos);
    }

    @Nested
    @DisplayName("respond with results when the Protobuf `Query` asks for")
    class ExecuteProtobufQueries extends ExecuteQueries<Query> {

        @Override
        void checkRead(Query query, MRPhoto... expected) {
            var readMessages = execute(query);
            assertThat(readMessages).containsExactlyElementsIn(expected);
        }

        @Override
        void checkReadsNothing(Query query) {
            var readMessages = execute(query);
            assertThat(readMessages).isEmpty();
        }

        @Override
        Query toQuery(MRPhoto.QueryBuilder builder) {
            return builder.build(transformWith(queries));
        }

        @Override
        List<? extends EntityState<?>> execute(Query query) {
            var records = repository.findRecords(query.filters(),
                                                 query.responseFormat());
            var asList = ImmutableList.copyOf(records);
            var result = asList.stream()
                    .map(EntityRecord::getState)
                    .map(unpackFunc())
                    .map((s) -> (EntityState<?>) s)
                    .collect(toList());
            return result;
        }
    }

    @Nested
    @DisplayName("respond with results when the `EntityQuery` asks for")
    class ExecuteEntityQueries extends ExecuteQueries<MRPhoto.Query> {

        @Override
        void checkRead(MRPhoto.Query query, MRPhoto... expected) {
            var readMessages = execute(query);
            assertThat(readMessages).containsExactlyElementsIn(expected);
        }

        @Override
        void checkReadsNothing(MRPhoto.Query query) {
            var readMessages = execute(query);
            assertThat(readMessages).isEmpty();
        }

        @Override
        MRPhoto.Query toQuery(MRPhoto.QueryBuilder builder) {
            return builder.build();
        }

        @Override
        List<? extends EntityState<?>> execute(MRPhoto.Query query) {
            var records = repository.findStates(query);
            var result = ImmutableList.copyOf(records);
            return result;
        }
    }

    @Test
    @DisplayName("throw `IllegalStateException` if an invisible Aggregate is queried" +
            " via Proto `Query`")
    void prohibitQueryingForInvisible() {
        var query = MRSoundRecord.query()
                                 .build(transformWith(queries));
        assertThrows(IllegalStateException.class,
                     () -> InvisibleSound.repository()
                                         .findRecords(query.filters(), query.responseFormat()));
    }

    @Test
    @DisplayName("throw `IllegalStateException` if an invisible Aggregate " +
            "is queried via `EntityQuery`")
    void prohibitEntityQueryingForInvisible() {
        var entityQuery = MRSoundRecord.query().build();
        assertThrows(IllegalStateException.class,
                     () -> InvisibleSound.repository()
                                         .findStates(entityQuery));
    }

    /**
     * An abstract base which tests querying the {@link AggregateRepository} with a certain types
     * of queries.
     *
     * @param <Q>
     *         the type of queries to test with
     */
    @SuppressWarnings("unused")
    abstract class ExecuteQueries<Q> {

        /**
         * Transforms the given {@code QueryBuilder} into the query of type {@code Q}.
         */
        abstract Q toQuery(MRPhoto.QueryBuilder builder);

        /**
         * Executes the query.
         *
         * @return a list of {@code Entity} states
         */
        abstract List<? extends EntityState<?>> execute(Q query);

        /**
         * Checks that the execution of the given {@code query}
         * returns the {@code expected} results.
         */
        abstract void checkRead(Q query, MRPhoto... expected);

        /**
         * Checks that the execution of the given {@code query} returns no results.
         */
        abstract void checkReadsNothing(Q query);

        @Test
        @DisplayName("all instances")
        void includeAll() {
            var query = MRPhoto.query().build(transformWith(queries));
            var readMessages = execute(query);
            assertThat(readMessages).containsExactlyElementsIn(givenPhotos);
        }

        @Test
        @DisplayName("an instance by ID")
        void byId() {
            var target = onePhoto();
            readAndCheck(target);
        }

        @Test
        @DisplayName("an instance by its ID in case this aggregate instance is archived")
        void archivedInstance() {
            var target = onePhoto();
            archiveItem(target);
            var targetId = target.getId();
            var query = toQuery(
                    MRPhoto.query()
                           .id().is(targetId)
                           .where(ArchivedColumn.is(), true));
            checkRead(query, target);
        }

        @Test
        @DisplayName("an instance by its ID in case this aggregate instance is deleted")
        void deletedInstance() {
            var target = onePhoto();
            deleteItem(target);
            var targetId = target.getId();
            var query = toQuery(
                    MRPhoto.query()
                           .id().is(targetId)
                           .where(DeletedColumn.is(), true));
            checkRead(query, target);
        }

        @Test
        @DisplayName("all archived instances")
        void allArchived() {
            var firstPhoto = onePhoto();
            var secondPhoto = anotherPhoto();
            archiveItem(firstPhoto);
            archiveItem(secondPhoto);
            var query = toQuery(
                    MRPhoto.query()
                           .where(ArchivedColumn.is(), true)
                           .where(DeletedColumn.is(), false));
            checkRead(query, firstPhoto, secondPhoto);
        }

        @Test
        @DisplayName("all deleted instances")
        void allDeleted() {
            var firstPhoto = onePhoto();
            var secondPhoto = anotherPhoto();
            deleteItem(firstPhoto);
            deleteItem(secondPhoto);
            var query = toQuery(
                    MRPhoto.query()
                           .where(ArchivedColumn.is(), false)
                           .where(DeletedColumn.is(), true));
            checkRead(query, firstPhoto, secondPhoto);
        }

        @Test
        @DisplayName("instances matching them by the a single entity column")
        void bySingleEntityColumn() {
            var queryForNothing =
                    toQuery(MRPhoto.query()
                                   .height().isLessThan(20));
            checkReadsNothing(queryForNothing);

            var queryForOneElement = toQuery(
                    MRPhoto.query()
                           .height().isLessThan(201)
            );
            checkRead(queryForOneElement, spineLogo200by200());
        }

        @Test
        @DisplayName("instances matching them by the two entity columns joined with `AND`")
        void byTwoEntityColumnsWithAndOperator() {
            var query = toQuery(
                    MRPhoto.query()
                           .height().isGreaterOrEqualTo(7000)
                           .width().isGreaterThan(6999)
            );
            checkRead(query, jxBrowserLogo7K());
        }

        @Test
        @DisplayName("instances matching them by the two entity columns joined with `OR`")
        void byTwoEntityColumnsWithOrOperator() {
            var query = toQuery(
                    MRPhoto.query()
                           .either(q -> q.width().isLessOrEqualTo(200),
                                   q -> q.height().isGreaterThan(6999))
            );
            checkRead(query, spineLogo200by200(), jxBrowserLogo7K());
        }

        @Test
        @DisplayName("instances matching them " +
                "by the two parameters for the same columns with `OR` " +
                "and by one more column joined with `AND`")
        void byCombinationAndOr() {
            var query = toQuery(
                    MRPhoto.query()
                           .either(q -> q.sourceType().is(CROP_FRAME),
                                   q -> q.sourceType().is(FULL_FRAME))
                           .height().isLessThan(7000)
            );
            checkRead(query, projectLogo1000by800());
        }

        @Test
        @DisplayName("instances matching them by both entity and lifecycle columns")
        void byEntityAndLifecycleCols() {
            archiveItem(projectLogo1000by800());
            var query = toQuery(
                    MRPhoto.query()
                           .either(q -> q.where(ArchivedColumn.is(), true),
                                   q -> q.height().isGreaterThan(1000))
                           .width().isLessThan(7000)
            );

            checkRead(query, projectLogo1000by800());
        }

        private void readAndCheck(MRPhoto target) {
            var targetId = target.getId();
            var query = toQuery(MRPhoto.query()
                                       .id().is(targetId));
            checkRead(query, target);
        }

        private MRPhoto onePhoto() {
            var target = givenPhotos.stream()
                    .findFirst()
                    .orElseGet(() -> fail("No test data."));
            return target;
        }

        private MRPhoto anotherPhoto() {
            var target = givenPhotos.stream()
                    .skip(1)
                    .findFirst()
                    .orElseGet(() -> fail(
                            "Too few test data items: " + givenPhotos.size())
                    );
            return target;
        }

        private void archiveItem(MRPhoto photo) {
            dispatchCommand(archive(photo));
        }

        private void deleteItem(MRPhoto photo) {
            dispatchCommand(delete(photo));
        }

        private List<? extends EntityState<?>> execute(Query query) {
            var records = repository.findRecords(query.filters(),
                                                 query.responseFormat());
            var asList = ImmutableList.copyOf(records);
            List<? extends EntityState<?>> result =
                    asList.stream()
                          .map(EntityRecord::getState)
                          .map(unpackFunc())
                          .map((s) -> (EntityState<?>) s)
                          .collect(toList());
            return result;
        }
    }

    private void prepareAggregates(Collection<MRPhoto> aggregateStates) {
        for (var state : aggregateStates) {
            var upload = upload(state);
            dispatchCommand(upload);
        }
    }

    private void dispatchCommand(CommandMessage message) {
        var command = commands.create(message);
        context.commandBus()
               .post(command, noOpObserver());
    }
}
