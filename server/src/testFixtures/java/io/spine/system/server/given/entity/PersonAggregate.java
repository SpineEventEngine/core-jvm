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

package io.spine.system.server.given.entity;

import io.spine.server.aggregate.Aggregate;
import io.spine.server.aggregate.Apply;
import io.spine.server.command.Assign;
import io.spine.system.server.CreatePerson;
import io.spine.system.server.ExposePerson;
import io.spine.system.server.HidePerson;
import io.spine.system.server.Person;
import io.spine.system.server.PersonCreated;
import io.spine.system.server.PersonExposed;
import io.spine.system.server.PersonHidden;
import io.spine.system.server.PersonId;
import io.spine.system.server.PersonRenamed;
import io.spine.system.server.RenamePerson;
import io.spine.type.TypeUrl;

/**
 * Test aggregate on which to track history.
 */
public class PersonAggregate extends Aggregate<PersonId, Person, Person.Builder> {

    public static final TypeUrl TYPE = TypeUrl.of(Person.class);

    protected PersonAggregate(PersonId id) {
        super(id);
    }

    @Assign
    PersonCreated handle(CreatePerson command) {
        return PersonCreated
                .newBuilder()
                .setId(command.getId())
                .setName(command.getName())
                .build();
    }

    @Assign
    PersonHidden handle(HidePerson command) {
        return PersonHidden
                .newBuilder()
                .setId(command.getId())
                .build();
    }

    @Assign
    PersonExposed handle(ExposePerson command) {
        return PersonExposed
                .newBuilder()
                .setId(command.getId())
                .build();
    }

    @Assign
    PersonRenamed handle(RenamePerson command) {
        return PersonRenamed
                .newBuilder()
                .setId(command.getId())
                .setNewFirstName(command.getNewFirstName())
                .build();
    }

    @Apply
    private void on(PersonCreated event) {
        builder().setId(event.getId())
                 .setName(event.getName());
    }

    @Apply
    private void on(PersonHidden event) {
        setArchived(true);
    }

    @Apply
    private void on(PersonExposed event) {
        setArchived(false);
    }

    @Apply
    private void on(PersonRenamed event) {
        var builder = builder();
        var newName = builder.getName().toBuilder()
                .setGivenName(event.getNewFirstName())
                .build();
        builder.setName(newName);
    }
}
