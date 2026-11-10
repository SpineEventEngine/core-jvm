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

package io.spine.server.integration;

import com.google.common.collect.ImmutableList;
import com.google.common.testing.NullPointerTester;
import io.spine.base.EventMessage;
import io.spine.core.ActorContext;
import io.spine.core.TenantId;
import io.spine.core.UserId;
import io.spine.net.InternetDomain;
import io.spine.server.Application;
import io.spine.server.BoundedContext;
import io.spine.server.BoundedContextBuilder;
import io.spine.server.integration.given.DocumentRepository;
import io.spine.server.integration.given.EditHistoryRepository;
import io.spine.server.tenant.TenantAwareRunner;
import io.spine.server.type.given.GivenEvent;
import io.spine.testing.client.TestActorRequestFactory;
import io.spine.testing.core.given.GivenTenantId;
import io.spine.testing.core.given.GivenUserId;
import io.spine.testing.server.TestApplications;
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

    private Application application;
    private BoundedContext context;
    private ThirdPartyContext imports;
    private DocumentRepository documentRepository;
    private EditHistoryRepository editHistoryRepository;

    @BeforeEach
    void prepareContext() {
        documentRepository = new DocumentRepository();
        editHistoryRepository = new EditHistoryRepository();
        imports = ThirdPartyContext.singleTenant("Imports");
        application = application(BoundedContextBuilder.assumingTests()
                                                       .add(documentRepository)
                                                       .add(editHistoryRepository),
                                  imports);
        context = application.contexts()
                             .get(0);
    }

    @AfterEach
    void closeApplication() {
        application.close();
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
    @DisplayName("refuse to emit an event until its application is built")
    void notBuilt() {
        var agenda = ThirdPartyContext.singleTenant("Agenda");

        assertThat(agenda.isOpen()).isFalse();
        assertThrows(IllegalStateException.class,
                     () -> agenda.emittedEvent(GivenEvent.message(), GivenUserId.newUuid()));
    }

    @Test
    @DisplayName("if multitenant, require a tenant ID for each event")
    void requireTenant() {
        var noTenantContext = ActorContext.newBuilder()
                .setActor(GivenUserId.of("42"))
                .setTimestamp(currentTime())
                .build();
        var calendar = ThirdPartyContext.multitenant("Calendar");
        try (var ignored = application(BoundedContextBuilder.assumingTests(true), calendar)) {
            assertThrows(IllegalArgumentException.class,
                         () -> calendar.emittedEvent(GivenEvent.message(), noTenantContext));
        }
    }

    @Test
    @DisplayName("if single-tenant, fail if a tenant ID is supplied")
    void noTenant() {
        var actorWithTenant = ActorContext.newBuilder()
                .setActor(GivenUserId.of("42"))
                .setTimestamp(currentTime())
                .setTenantId(GivenTenantId.of("AcmeCorp"))
                .build();
        var notes = ThirdPartyContext.singleTenant("Notes");
        try (var ignored = application(BoundedContextBuilder.assumingTests(), notes)) {
            assertThrows(IllegalArgumentException.class,
                         () -> notes.emittedEvent(GivenEvent.message(), actorWithTenant));
        }
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
        application.close();

        var documentRepository = new DocumentRepository();
        var exports = ThirdPartyContext.multitenant("Exports");
        // Assigned to the field that `closeApplication()` closes after each test.
        application = application(BoundedContextBuilder.assumingTests(/* multitenant = */ true)
                                                       .add(documentRepository),
                                  exports);
        context = application.contexts()
                             .get(0);
        assertThat(context.isMultitenant())
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
        postForTenant(exports, acmeCorp, johnDoe, importEvent);

        var acmeDailyReport = TenantAwareRunner
                .with(acmeCorp)
                .evaluate(() -> documentRepository.find(documentId));
        assertThat(acmeDailyReport).isPresent();

        var cyberdyneDailyReport = TenantAwareRunner
                .with(cyberdyne)
                .evaluate(() -> documentRepository.find(documentId));
        assertThat(cyberdyneDailyReport).isEmpty();
    }

    private void postForSingleTenant(UserId actor, EventMessage event) {
        imports.emittedEvent(event, actor);
    }

    private static void postForTenant(ThirdPartyContext exports,
                                      TenantId tenantId,
                                      UserId actor,
                                      EventMessage event) {
        var actorContext = ActorContext.newBuilder()
                .setActor(actor)
                .setTenantId(tenantId)
                .setTimestamp(currentTime())
                .build();
        exports.emittedEvent(event, actorContext);
    }

    /**
     * Builds an application of the given domain context and the given third-party context.
     */
    private static Application application(BoundedContextBuilder domain,
                                           ThirdPartyContext thirdParty) {
        return TestApplications.newBuilder()
                .add(domain)
                .add(thirdParty)
                .build();
    }

    private static InternetDomain.Builder internetDomain(String value) {
        return InternetDomain.newBuilder()
                .setValue(value);
    }
}
