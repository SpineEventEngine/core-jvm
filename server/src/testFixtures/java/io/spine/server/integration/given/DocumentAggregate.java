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

package io.spine.server.integration.given;

import io.spine.core.CommandContext;
import io.spine.core.EventContext;
import io.spine.core.External;
import io.spine.server.aggregate.Aggregate;
import io.spine.server.aggregate.Apply;
import io.spine.server.command.Assign;
import io.spine.server.event.React;
import io.spine.server.integration.CreateDocument;
import io.spine.server.integration.Document;
import io.spine.server.integration.DocumentCreated;
import io.spine.server.integration.DocumentId;
import io.spine.server.integration.DocumentImported;
import io.spine.server.integration.Edit;
import io.spine.server.integration.EditText;
import io.spine.server.integration.OpenOfficeDocumentUploaded;
import io.spine.server.integration.PaperDocumentScanned;
import io.spine.server.integration.TextEdited;
import io.spine.server.tuple.Pair;
import io.spine.time.Now;

/**
 * A test aggregate representing a {@code Document}.
 *
 * <p>Used for testing event posting via {@link io.spine.server.integration.EventFunnel}.
 */
public class DocumentAggregate extends Aggregate<DocumentId, Document, Document.Builder> {

    @Assign
    DocumentCreated handle(CreateDocument command, CommandContext context) {
        return DocumentCreated
                .newBuilder()
                .setId(command.getId())
                .setOwner(context.actor())
                .setWhenCreated(Now.get().asLocalDateTime())
                .build();
    }

    @Assign
    TextEdited handle(EditText command, CommandContext context) {
        var edit = Edit.newBuilder()
                .setEditor(context.actor())
                .setPosition(command.getPosition())
                .setTextAdded(command.getNewText())
                .setCharsDeleted(command.getCharsToDelete())
                .build();
        return TextEdited.newBuilder()
                .setId(command.getId())
                .setEdit(edit)
                .build();
    }

    /**
     * Reacts on an external {@code OpenOfficeDocumentUploaded} event with
     * a {@code DocumentImported} event.
     *
     * <p>This flow is intentionally complex so that the aggregate reacts to both external and
     * domestic events.
     */
    @React
    DocumentImported on(@External OpenOfficeDocumentUploaded event, EventContext context) {
        return DocumentImported
                .newBuilder()
                .setId(event.getId())
                .setOwner(context.actor())
                .setText(event.getText())
                .setWhenUploaded(Now.get().asLocalDateTime())
                .build();
    }

    @React
    Pair<DocumentCreated, TextEdited> on(DocumentImported event) {
        var documentId = event.getId();
        var user = event.getOwner();
        var when = event.getWhenUploaded();
        var created = DocumentCreated.newBuilder()
                .setId(documentId)
                .setWhenCreated(when)
                .setOwner(user)
                .build();
        var edit = Edit.newBuilder()
                .setEditor(user)
                .setPosition(0)
                .setTextAdded(event.getText())
                .build();
        var edited = TextEdited.newBuilder()
                .setId(documentId)
                .setEdit(edit)
                .build();
        return Pair.of(created, edited);
    }

    @Apply
    private void event(DocumentImported event) {
        // Do nothing. As the event is produced, it must be applied.
    }

    @Apply
    private void event(DocumentCreated e) {
        builder()
                .setOwner(e.getOwner())
                .setLastEdit(e.getWhenCreated());
    }

    @Apply
    private void event(TextEdited e) {
        var edit = e.getEdit();
        var text = builder().getText();
        var position = edit.getPosition();
        var start = text.substring(0, position);
        var end = text.substring(position);
        var deletedCount = edit.getCharsDeleted();
        if (deletedCount > 0) {
            end = end.substring(deletedCount);
        }
        var resultText = start + edit.getTextAdded() + end;
        builder().setText(resultText);
    }

    @Apply(allowImport = true)
    private void event(PaperDocumentScanned e) {
        builder()
                .setText(e.getText())
                .setOwner(e.getOwner())
                .setLastEdit(e.getWhenCreated());
    }
}
