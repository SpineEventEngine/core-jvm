/*
 * Copyright 2026, TeamDev. All rights reserved.
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

package io.spine.server.aggregate;

import com.google.common.testing.NullPointerTester;
import io.spine.base.CommandMessage;
import io.spine.base.EntityState;
import io.spine.client.QueryResponse;
import io.spine.grpc.MemoizingObserver;
import io.spine.server.BoundedContext;
import io.spine.server.BoundedContextBuilder;
import io.spine.server.aggregate.given.part.AnAggregateRoot;
import io.spine.server.aggregate.given.part.TaskCommentsPart;
import io.spine.server.aggregate.given.part.TaskCommentsRepository;
import io.spine.server.aggregate.given.part.TaskPart;
import io.spine.server.aggregate.given.part.TaskRepository;
import io.spine.server.aggregate.given.part.TaskRoot;
import io.spine.server.type.CommandEnvelope;
import io.spine.test.aggregate.ProjectId;
import io.spine.test.aggregate.task.AggTask;
import io.spine.test.aggregate.task.AggTaskComments;
import io.spine.testing.client.TestActorRequestFactory;
import io.spine.testing.server.model.ModelTests;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.Collection;

import static com.google.common.testing.NullPointerTester.Visibility.PACKAGE;
import static com.google.common.truth.Truth.assertThat;
import static io.spine.base.Time.currentTime;
import static io.spine.grpc.StreamObservers.memoizingObserver;
import static io.spine.protobuf.AnyPacker.unpack;
import static io.spine.server.aggregate.given.aggregate.AggregatePartTestEnv.ASSIGNEE;
import static io.spine.server.aggregate.given.aggregate.AggregatePartTestEnv.ID;
import static io.spine.server.aggregate.given.aggregate.AggregatePartTestEnv.commentTask;
import static io.spine.server.aggregate.given.aggregate.AggregatePartTestEnv.createTask;
import static io.spine.testing.DisplayNames.NOT_ACCEPT_NULLS;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("`AggregatePart` should")
@SuppressWarnings("deprecation") // Tests the deprecated aggregate parts API.
class AggregatePartTest {

    private static final TestActorRequestFactory factory =
            new TestActorRequestFactory(AggregatePartTest.class);

    private BoundedContext context;
    private TaskRoot root;
    private TaskPart taskPart;
    private TaskCommentsPart taskCommentsPart;
    private TaskRepository taskRepository;
    private TaskCommentsRepository taskCommentsRepository;

    @BeforeEach
    void setUp() {
        ModelTests.dropAllModels();
        context = BoundedContextBuilder.assumingTests().build();
        root = new TaskRoot(context, ID);
        taskPart = new TaskPart(root);
        taskCommentsPart = new TaskCommentsPart(root);
        taskRepository = new TaskRepository();
        taskCommentsRepository = new TaskCommentsRepository();
        var contextAccess = context.internalAccess();
        contextAccess.register(taskRepository);
        contextAccess.register(taskCommentsRepository);
        prepareAggregatePart();
    }

    @Test
    @DisplayName(NOT_ACCEPT_NULLS)
    void passNullToleranceCheck() throws NoSuchMethodException {
        createNullPointerTester()
                .testStaticMethods(AggregatePart.class, PACKAGE);
        createNullPointerTester().testAllPublicInstanceMethods(taskPart);
    }

    @Test
    @DisplayName("not override other parts with common ID when querying")
    @SuppressWarnings("CheckReturnValue") // Message dispatching called for the side effect.
    void notOverrideOtherParts() {
        assertEntityCount(AggTaskComments.class, 0);
        assertEntityCount(AggTask.class, 1);

        taskCommentsRepository.dispatchCommand(command(commentTask()));

        assertEntityCount(AggTaskComments.class, 1);
        assertEntityCount(AggTask.class, 1);
    }

    @Test
    @DisplayName("return aggregate part state by class")
    void returnAggregatePartStateByClass() {
        taskRepository.store(taskPart);
        var task = taskCommentsPart.partState(AggTask.class);
        assertThat(task.getAssignee())
                .isEqualTo(ASSIGNEE);
    }

    @Test
    @DisplayName("read its state history through the loader installed by the repository")
    void readStateHistoryViaRepository() {
        var part = taskRepository.create(ID);

        // The recording is not enabled for the repository: the installed
        // loader must fail fast, proving the part reads the repository
        // history rather than a silently empty one.
        assertThrows(IllegalStateException.class, () -> part.stateAt(currentTime()));
    }

    private void assertEntityCount(Class<? extends EntityState<?>> stateType, int expectedCount) {
        var entityStates = queryEntities(stateType);
        assertThat(entityStates).hasSize(expectedCount);
    }

    private Collection<? extends EntityState<?>>
    queryEntities(Class<? extends EntityState<?>> entityClass) {
        var query = factory.query().all(entityClass);
        MemoizingObserver<QueryResponse> observer = memoizingObserver();
        context.stand()
               .execute(query, observer);
        return observer.firstResponse()
                       .getMessageList()
                       .stream()
                       .map(state -> unpack(state.getState(), entityClass))
                       .collect(toList());
    }

    private NullPointerTester createNullPointerTester() throws NoSuchMethodException {
        var constructor =
                AnAggregateRoot.class.getDeclaredConstructor(BoundedContext.class, ProjectId.class);
        var tester = new NullPointerTester();
        tester.setDefault(Constructor.class, constructor)
              .setDefault(BoundedContext.class, context)
              .setDefault(AggregateRoot.class, root);
        return tester;
    }

    @SuppressWarnings("CheckReturnValue")
    private void prepareAggregatePart() {
        taskRepository.dispatchCommand(command(createTask()));
    }

    private static CommandEnvelope command(CommandMessage commandMessage) {
        return CommandEnvelope.of(factory.command()
                                         .create(commandMessage));
    }
}
