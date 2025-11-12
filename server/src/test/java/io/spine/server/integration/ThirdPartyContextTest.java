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

package io.spine.server.integration;

import com.google.common.collect.ImmutableList;
import com.google.common.testing.NullPointerTester;
import io.spine.base.EventMessage;
import io.spine.core.ActorContext;
import io.spine.core.TenantId;
import io.spine.core.UserId;
import io.spine.net.InternetDomain;
import io.spine.server.BoundedContext;
import io.spine.server.BoundedContextBuilder;
import io.spine.server.ServerEnvironment;
import io.spine.server.integration.given.DocumentRepository;
import io.spine.server.integration.given.EditHistoryRepository;
import io.spine.server.tenant.TenantAwareRunner;
import io.spine.server.type.given.GivenEvent;
import io.spine.testing.client.TestActorRequestFactory;
import io.spine.testing.core.given.GivenTenantId;
import io.spine.testing.core.given.GivenUserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.base.Time.currentTime;
import static io.spine.grpc.StreamObservers.noOpObserver;
import static io.spine.testing.DisplayNames.NOT_ACCEPT_NULLS;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

@DisplayName("`ThirdPartyContext` should")
class ThirdPartyContextTest {

    private BoundedContext context;
    private DocumentRepository documentRepository;
    private EditHistoryRepository editHistoryRepository;

    @BeforeEach
    void prepareContext() {
        documentRepository = new DocumentRepository();
        editHistoryRepository = new EditHistoryRepository();
        context = BoundedContextBuilder
                .assumingTests()
                .add(documentRepository)
                .add(editHistoryRepository)
                .build();
    }

    @AfterEach
    void closeContext() throws Exception {
        context.close();
        resetServerEnvironment();
    }

    @Test
    @DisplayName("not accept `null`s in factory methods")
    void nullsOnConstruction() {
        new NullPointerTester()
                .testAllPublicStaticMethods(ThirdPartyContext.class);
    }

    @Test
    @DisplayName(NOT_ACCEPT_NULLS)
    void nulls() {
        new NullPointerTester()
                .setDefault(UserId.class, UserId.getDefaultInstance())
                .setDefault(ActorContext.class, ActorContext.getDefaultInstance())
                .testAllPublicInstanceMethods(ThirdPartyContext.singleTenant("Directory"));
    }

    @Test
    @DisplayName("if multitenant, require a tenant ID for each event")
    void requireTenant() {
        var noTenantContext = ActorContext.newBuilder()
                .setActor(GivenUserId.of("42"))
                .setTimestamp(currentTime())
                .build();
        var calendar = ThirdPartyContext.multitenant("Calendar");
        assertThrows(IllegalArgumentException.class,
                     () -> calendar.emittedEvent(GivenEvent.message(), noTenantContext));
    }

    @Test
    @DisplayName("if single-tenant, fail if a tenant ID is supplied")
    void noTenant() {
        var actorWithTenant = ActorContext.newBuilder()
                .setActor(GivenUserId.of("42"))
                .setTimestamp(currentTime())
                .setTenantId(GivenTenantId.of("AcmeCorp"))
                .build();
        var calendar = ThirdPartyContext.singleTenant("Notes");
        assertThrows(IllegalArgumentException.class,
                     () -> calendar.emittedEvent(GivenEvent.message(), actorWithTenant));
    }

    @Test
    @DisplayName("deliver to external reactors")
    void externalReactor() {
        var johnDoe = GivenUserId.newUuid();
        var importEvent = OpenOfficeDocumentUploaded.newBuilder()
                .setId(DocumentId.generate())
                .setText("The scary truth about gluten")
                .build();
        postForSingleTenant(johnDoe, importEvent);
        var foundDoc = documentRepository.find(importEvent.getId());
        assertThat(foundDoc).isPresent();
        assertThat(foundDoc.get()
                           .state()
                           .getText())
                .isEqualTo(importEvent.getText());
    }

    @Test
    @DisplayName("not deliver to domestic reactors")
    void domesticReactor() {
        var johnDoe = GivenUserId.newUuid();
        var importEvent = DocumentImported.newBuilder()
                .setId(DocumentId.generate())
                .setText("Annual report")
                .build();
        postForSingleTenant(johnDoe, importEvent);
        var foundDoc = documentRepository.find(importEvent.getId());
        assertThat(foundDoc).isEmpty();
    }

    @Test
    @DisplayName("and deliver to external subscribers")
    void externalSubscriber() {
        var johnDoe = GivenUserId.newUuid();
        var requests = new TestActorRequestFactory(johnDoe);
        var documentId = DocumentId.generate();
        var crete = CreateDocument.newBuilder()
                .setId(documentId)
                .build();
        var edit = EditText.newBuilder()
                .setId(documentId)
                .setPosition(0)
                .setNewText("Fresh new document")
                .build();
        context.commandBus()
               .post(ImmutableList.of(requests.createCommand(crete), requests.createCommand(edit)),
                     noOpObserver());
        var historyAfterEdit = editHistoryRepository
                .find(documentId)
                .orElseGet(Assertions::fail);
        assertThat(historyAfterEdit.state().getEditList())
                .isNotEmpty();
        postForSingleTenant(johnDoe, UserDeleted.newBuilder()
                .setUser(johnDoe)
                .build());
        var historyAfterDeleted = editHistoryRepository
                .find(documentId)
                .orElseGet(Assertions::fail);
        assertThat(historyAfterDeleted.state().getEditList())
                .isEmpty();
    }

    @Test
    @DisplayName("and ignore domestic subscribers")
    void domesticSubscriber() {
        var documentId = DocumentId.generate();
        var event = TextEdited.newBuilder()
                .setId(documentId)
                .build();
        postForSingleTenant(GivenUserId.newUuid(), event);
        assertThat(editHistoryRepository.find(documentId)).isEmpty();
    }

    @Test
    @DisplayName("in a multitenant environment")
    void multitenant() {
        /* Getting rid of the class-level repositories and bounded contexts. */
        resetServerEnvironment();

        var documentRepository = new DocumentRepository();
        var boundedContext = BoundedContextBuilder
                .assumingTests(true)
                .add(documentRepository)
                .build();
        assertThat(boundedContext.isMultitenant())
                .isTrue();

        var johnDoe = GivenUserId.newUuid();
        var acmeCorp = TenantId.newBuilder()
                .setDomain(internetDomain("acme.com"))
                .build();
        var cyberdyne = TenantId.newBuilder()
                .setDomain(internetDomain("cyberdyne.com"))
                .build();
        var documentId = DocumentId.generate();
        var importEvent = OpenOfficeDocumentUploaded.newBuilder()
                .setId(documentId)
                .setText("Daily report")
                .build();
        postForTenant(acmeCorp, johnDoe, importEvent);

        var acmeDailyReport = TenantAwareRunner
                .with(acmeCorp)
                .evaluate(() -> documentRepository.find(documentId));
        assertThat(acmeDailyReport).isPresent();

        var cyberdyneDailyReport = TenantAwareRunner
                .with(cyberdyne)
                .evaluate(() -> documentRepository.find(documentId));
        assertThat(cyberdyneDailyReport).isEmpty();
    }

    private static void postForSingleTenant(UserId actor, EventMessage event) {
        try (var uploads = ThirdPartyContext.singleTenant("Imports")) {
            uploads.emittedEvent(event, actor);
        } catch (Exception e) {
            fail(e);
        }
    }

    private static void postForTenant(TenantId tenantId, UserId actor, EventMessage event) {
        try (var uploads = ThirdPartyContext.multitenant("Exports")) {
            var actorContext = ActorContext.newBuilder()
                    .setActor(actor)
                    .setTenantId(tenantId)
                    .setTimestamp(currentTime())
                    .build();
            uploads.emittedEvent(event, actorContext);
        } catch (Exception e) {
            fail(e);
        }
    }

    private static InternetDomain.Builder internetDomain(String value) {
        return InternetDomain.newBuilder()
                .setValue(value);
    }

    private static void resetServerEnvironment() {
        ServerEnvironment
                .instance()
                .reset();
    }
}
