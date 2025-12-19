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

package io.spine.server.entity;

import com.google.errorprone.annotations.concurrent.LazyInit;
import io.spine.annotation.Experimental;
import io.spine.annotation.Internal;
import io.spine.base.EntityState;
import io.spine.core.Event;
import io.spine.core.Version;
import io.spine.logging.WithLogging;
import io.spine.system.server.event.MigrationApplied;
import io.spine.validation.ValidatingBuilder;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkState;
import static io.spine.core.Versions.increment;
import static io.spine.protobuf.Messages.isDefault;
import static java.lang.String.format;

/**
 * A stored {@link Entity} transformation done to account for the domain model changes.
 *
 * <p>At its core the {@code Migration} is a mapping of {@link EntityState entity state}, from old
 * to new. It is also capable of performing basic entity modifications like
 * {@linkplain #markArchived() archiving} and {@linkplain #markDeleted() deleting} it.
 *
 * <p>The process of applying the migration operation is always preceded by an {@link Entity} load
 * by ID and may be finalized by either {@linkplain Repository#store(Entity) saving} the
 * transformed entity back to the repository or by {@linkplain #removeFromStorage() deleting} the
 * entity record if the migration is configured to do so.
 *
 * <p>All entity modifications are applied under the opened entity {@link Transaction}.
 * The last step of a migration operation is a transaction {@linkplain Transaction#commit() commit}.
 * As a consequence, all entity lifecycle events occur as expected, having the
 * {@link MigrationApplied} event as the producing message.
 *
 * <p>To create a user-defined {@code Migration} in real life scenarios, consider inheriting from
 * {@link io.spine.server.projection.ProjectionMigration ProjectionMigration} and
 * {@link io.spine.server.procman.ProcessManagerMigration ProcessManagerMigration} types.
 *
 * @param <I>
 *         the entity ID type
 * @param <E>
 *         the entity type
 * @param <S>
 *         the entity state type
 * @param <B>
 *         the type of the validating builder for the entity state
 */
@Experimental
public abstract class Migration<I,
                                E extends TransactionalEntity<I, S, B>,
                                S extends EntityState<I>,
                                B extends ValidatingBuilder<S>>
        implements Function<S, S>, WithLogging {

    /**
     * The currently performed migration operation.
     */
    private @Nullable Operation<I, S, E> currentOperation;

    /**
     * Applies the migration {@linkplain Operation operation} to a given {@code entity}.
     */
    final void applyTo(E entity, RecordBasedRepository<I, E, S> repository) {
        startOperation(entity, repository);
        applyTo(entity);
    }

    private void startOperation(E entity, RecordBasedRepository<I, E, S> repository) {
        currentOperation = new Operation<>(entity, repository);
    }

    private void applyTo(E entity) {
        Transaction<I, E, S, ?> tx = txWithLifecycleMonitor();
        var oldState = entity.state();
        var newState = apply(oldState);
        var op = currentOperation();
        op.updateState(newState);
        op.updateLifecycle();
        tx.commit();
    }

    /**
     * Releases the {@linkplain #currentOperation currently performed operation}.
     *
     * <p>This method is used by Spine routines to reset the {@code Migration} instance passed to
     * {@link RecordBasedRepository#applyMigration(Object, Migration)
     * repository.applyMigration(I, Migration)}. It shouldn't be invoked by the user code directly.
     */
    final void finishCurrentOperation() {
        currentOperation = null;
    }

    private boolean isOperationInProgress() {
        return currentOperation != null;
    }

    private Operation<I, S, E> currentOperation() {
        checkState(isOperationInProgress(),
                   "Getter and mutator methods of migration should only be invoked " +
                           "from within `apply(S)` method.");
        return currentOperation;
    }

    /**
     * Marks the entity under migration as {@linkplain Entity#isArchived() archived}.
     */
    protected final void markArchived() {
        currentOperation().markArchived();
    }

    /**
     * Marks the entity under migration as {@linkplain Entity#isDeleted() deleted}.
     */
    protected final void markDeleted() {
        currentOperation().markDeleted();
    }

    /**
     * Configures the migration operation to delete the entity record from the storage.
     *
     * <p>All other configured entity modifications are still applied, allowing to trigger
     * {@linkplain EntityLifecycle entity lifecycle} events before the actual record deletion.
     *
     * <p>Depending on the storage implementation, this operation may be irreversible, so it should
     * be used in the caller code with care.
     */
    protected final void removeFromStorage() {
        currentOperation().removeFromStorage();
    }

    /**
     * Returns the ID of an entity under migration.
     */
    protected final I id() {
        return currentOperation().id();
    }

    /**
     * Returns the version of entity under migration.
     */
    protected final Version version() {
        return currentOperation().version();
    }

    /**
     * Returns {@code true} if the entity under migration is
     * {@linkplain Entity#isArchived() archived}.
     */
    protected final boolean isArchived() {
        return currentOperation().isArchived();
    }

    /**
     * Returns {@code true} if the entity under migration is
     * {@linkplain Entity#isDeleted() deleted}.
     */
    protected final boolean isDeleted() {
        return currentOperation().isDeleted();
    }

    /**
     * Returns {@code true} if the migration operation is configured to physically remove entity
     * record from the storage.
     */
    final boolean physicallyRemoveRecord() {
        return currentOperation().physicallyRemoveRecord();
    }

    /**
     * Returns the {@link MigrationApplied} event instance associated with this migration.
     *
     * <p>If system events posting is disabled, returns an empty {@code Optional}.
     */
    final Optional<Event> systemEvent() {
        return Optional.ofNullable(currentOperation().systemEvent());
    }

    /**
     * Opens a transaction on an entity.
     */
    @Internal
    protected abstract Transaction<I, E, S, B> startTransaction(E entity);

    /**
     * Opens a transaction with an {@link EntityLifecycleMonitor} as a {@link TransactionListener}.
     *
     * <p>The monitor is configured to have a {@link MigrationApplied} instance as
     * the last handled message.
     */
    private Transaction<I, E, S, B> txWithLifecycleMonitor() {
        var entity = currentOperation().entity;
        var id = entity.id();
        var tx = startTransaction(entity);
        var monitor = configureLifecycleMonitor(id);
        tx.setListener(monitor);
        currentOperation().tx = tx;
        return tx;
    }

    /**
     * Creates an entity lifecycle monitor which will post lifecycle events as usual, assigning
     * a {@link MigrationApplied} instance as the event-producing message.
     */
    private EntityLifecycleMonitor<I> configureLifecycleMonitor(I id) {
        var repository = currentOperation().repository;
        var event = repository.lifecycleOf(id)
                              .onMigrationApplied();
        if (event.isEmpty() || isDefault(event.get())) {
            warnOnNoSystemEventsPosted();
            return EntityLifecycleMonitor.newInstance(repository, id);
        }
        var migrationApplied = event.get();
        currentOperation().systemEvent = migrationApplied;
        return EntityLifecycleMonitor.withAcknowledgedMessage(repository, id, migrationApplied);
    }

    private void warnOnNoSystemEventsPosted() {
        logger().atWarning().log(() -> format(
                "Couldn't post an instance of `%s` event. No system events will occur " +
                            "during the migration.", MigrationApplied.class.getCanonicalName()));
    }

    /**
     * A migration operation on a single entity.
     *
     * <p>The operation is performed in scope of an active {@link Transaction}.
     *
     * <p>All entity state and meta-data changes are propagated to the transaction and remain in
     * pending state until the transaction is {@linkplain Transaction#commit() committed}, which is
     * the last step of a migration operation.
     *
     * <p>On a transaction commit, all changes are propagated to the actual entity passed to
     * {@link Migration#applyTo(TransactionalEntity, RecordBasedRepository)}, modifying it in-place.
     *
     * @param <I>
     *         the type of identifiers of the migrated entities
     * @param <S>
     *         the state type of the entity
     * @param <E>
     *         the type of entity
     */
    private static class Operation<I,
                                   S extends EntityState<I>,
                                   E extends TransactionalEntity<I, S, ?>> {

        private boolean archive;
        private boolean delete;
        private boolean physicallyRemoveRecord;

        private final E entity;
        private final RecordBasedRepository<I, E, S> repository;

        @LazyInit
        private @MonotonicNonNull Transaction<I, E, S, ?> tx;

        @LazyInit
        private @MonotonicNonNull Event systemEvent;

        private Operation(E entity, RecordBasedRepository<I, E, S> repository) {
            this.entity = entity;
            this.repository = repository;
        }

        private void updateState(S newState) {
            if (!entity.state().equals(newState)) {
                tx.builder().clear()
                            .mergeFrom(newState);
                var version = increment(entity.version());
                tx.setVersion(version);
            }
        }

        private void updateLifecycle() {
            if (archive) {
                tx.setArchived(true);
            }
            if (delete) {
                tx.setDeleted(true);
            }
        }

        private void markArchived() {
            archive = true;
        }

        private void markDeleted() {
            delete = true;
        }

        private void removeFromStorage() {
            physicallyRemoveRecord = true;
        }

        private I id() {
            return entity.id();
        }

        private Version version() {
            return entity.version();
        }

        private boolean isArchived() {
            return archive;
        }

        private boolean isDeleted() {
            return delete;
        }

        private boolean physicallyRemoveRecord() {
            return physicallyRemoveRecord;
        }

        private Event systemEvent() {
            return systemEvent;
        }
    }
}
