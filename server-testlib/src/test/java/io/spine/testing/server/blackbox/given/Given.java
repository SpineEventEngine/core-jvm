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

package io.spine.testing.server.blackbox.given;

import io.spine.core.MessageId;
import io.spine.core.UserId;
import io.spine.server.bus.Listener;
import io.spine.server.commandbus.CommandDispatcher;
import io.spine.server.event.EventDispatcher;
import io.spine.server.type.CommandEnvelope;
import io.spine.server.type.EventEnvelope;
import io.spine.testing.TestValues;
import io.spine.testing.core.given.GivenVersion;
import io.spine.testing.server.blackbox.BbProject;
import io.spine.testing.server.blackbox.BbProjectId;
import io.spine.testing.server.blackbox.BbReportId;
import io.spine.testing.server.blackbox.BbTask;
import io.spine.testing.server.blackbox.BbTaskId;
import io.spine.testing.server.blackbox.command.BbAddTask;
import io.spine.testing.server.blackbox.command.BbAssignProject;
import io.spine.testing.server.blackbox.command.BbAssignSelf;
import io.spine.testing.server.blackbox.command.BbCreateProject;
import io.spine.testing.server.blackbox.command.BbCreateReport;
import io.spine.testing.server.blackbox.command.BbFailProject;
import io.spine.testing.server.blackbox.command.BbFinalizeProject;
import io.spine.testing.server.blackbox.command.BbInitProject;
import io.spine.testing.server.blackbox.command.BbRegisterCommandDispatcher;
import io.spine.testing.server.blackbox.command.BbStartProject;
import io.spine.testing.server.blackbox.event.BbEventDispatcherRegistered;
import io.spine.testing.server.blackbox.event.BbProjectDone;
import io.spine.testing.server.blackbox.event.BbProjectFailed;
import io.spine.testing.server.blackbox.event.BbTaskAdded;
import io.spine.testing.server.blackbox.event.BbUserDeleted;
import io.spine.type.TypeUrl;

import java.util.stream.IntStream;

import static com.google.common.collect.Lists.newArrayList;
import static io.spine.base.Identifier.newUuid;
import static io.spine.protobuf.AnyPacker.pack;
import static io.spine.testing.TestValues.newUuidValue;

public class Given {

    /** Prevents instantiation of this utility class. */
    private Given() {
    }

    public static BbProjectId newProjectId() {
        return BbProjectId.generate();
    }

    public static BbAddTask addTask(BbProjectId projectId) {
        return BbAddTask.newBuilder()
                .setProjectId(projectId)
                .setTask(newTask())
                .build();
    }

    public static BbTaskAdded taskAdded(BbProjectId projectId) {
        return BbTaskAdded.newBuilder()
                .setProjectId(projectId)
                .setTask(newTask())
                .build();
    }

    private static BbTask newTask() {
        return BbTask.newBuilder()
                .setTaskId(BbTaskId.generate())
                .setTitle(newUuid())
                .build();
    }

    public static BbCreateReport createReport(BbProjectId projectId) {
        return BbCreateReport.newBuilder()
                .setReportId(newReportId())
                .addProjectId(projectId)
                .build();
    }

    private static BbReportId newReportId() {
        return BbReportId.newBuilder()
                .setId(newUuid())
                .build();
    }

    public static BbCreateProject createProject() {
        return createProject(newProjectId());
    }

    private static UserId generateUserId() {
        return UserId.newBuilder()
                .setValue(TestValues.randomString())
                .build();
    }

    public static BbCreateProject createProject(BbProjectId id) {
        return BbCreateProject.newBuilder()
                .setProjectId(id)
                .build();
    }

    public static BbAssignSelf assignSelf(BbProjectId id) {
        return BbAssignSelf.newBuilder()
                .setProjectId(id)
                .build();
    }

    public static BbInitProject initProject(BbProjectId id, boolean scrum) {
        var builder = BbInitProject.newBuilder()
                .setProjectId(id);
        // Generate a random team.
        IntStream.range(0, TestValues.random(1, 10))
                 .forEach(i -> builder.addMember(generateUserId()));
        if (scrum) {
            builder.setScrumMaster(generateUserId());
        }
        return builder.build();
    }

    public static BbStartProject startProject(BbProjectId id) {
        return BbStartProject.newBuilder()
                .setProjectId(id)
                .build();
    }

    public static BbProject createdProjectState(BbCreateProject createProject) {
        return BbProject.newBuilder()
                .setId(createProject.getProjectId())
                .setStatus(BbProject.Status.CREATED)
                .build();
    }

    public static BbAssignProject addProjectAssignee(BbProjectId projectId, UserId id) {
        return BbAssignProject.newBuilder()
                .setId(projectId)
                .setUserId(id)
                .build();
    }

    public static BbUserDeleted userDeleted(UserId id, BbProjectId... projectIds) {
        return BbUserDeleted.newBuilder()
                .setId(id)
                .addAllProject(newArrayList(projectIds))
                .build();
    }

    public static BbProjectDone projectDone(BbProjectId projectId) {
        return BbProjectDone.newBuilder()
                .setId(projectId)
                .build();
    }

    public static BbFinalizeProject finalizeProject(BbProjectId projectId) {
        return BbFinalizeProject.newBuilder()
                .setProjectId(projectId)
                .build();
    }

    public static BbFailProject failProject(BbProjectId projectId) {
        return BbFailProject.newBuilder()
                .setProjectId(projectId)
                .build();
    }

    public static BbProjectFailed projectFailed(BbProjectId projectId) {
        return BbProjectFailed.newBuilder()
                .setProjectId(projectId)
                .build();
    }

    public static MessageId someMessageId() {
        return MessageId
                .newBuilder()
                .setVersion(GivenVersion.withNumber(42))
                .setTypeUrl(TypeUrl.of(BbProjectDone.class)
                                   .value())
                .setId(pack(newUuidValue()))
                .build();
    }

    public static Listener<CommandEnvelope> commandListener() {
        return envelope -> {};
    }

    public static Listener<EventEnvelope> eventListener() {
        return envelope -> {};
    }
}
