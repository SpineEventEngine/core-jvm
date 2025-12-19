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

package io.spine.server.entity;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.OverridingMethodsMustInvokeSuper;
import com.google.errorprone.annotations.concurrent.LazyInit;
import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;
import io.spine.annotation.Internal;
import io.spine.annotation.VisibleForTesting;
import io.spine.base.EntityState;
import io.spine.base.Identifier;
import io.spine.core.Version;
import io.spine.core.Versions;
import io.spine.logging.LoggingFactory;
import io.spine.logging.MetadataKey;
import io.spine.logging.context.ScopedLoggingContext;
import io.spine.server.entity.model.EntityClass;
import io.spine.server.entity.rejection.CannotModifyArchivedEntity;
import io.spine.server.entity.rejection.CannotModifyDeletedEntity;
import io.spine.server.log.ReceptorLifecycle;
import io.spine.server.model.Receptor;
import io.spine.string.Stringifiers;
import io.spine.validation.ConstraintViolation;
import io.spine.validation.Validate;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static io.spine.util.Exceptions.newIllegalArgumentException;
import static io.spine.util.Exceptions.newIllegalStateException;
import static io.spine.validation.Validate.check;
import static io.spine.validation.Validate.violationsOf;

/**
 * Abstract base for entities.
 *
 * @param <I>
 *         the type of entity identifiers
 * @param <S>
 *         the type of entity state objects
 */
@SuppressWarnings({
        "SynchronizeOnThis" /* This class uses double-check idiom for lazy init of some
            fields. See Effective Java 2nd Ed. Item #71. */,
        "ClassWithTooManyMethods"})
public abstract class AbstractEntity<I, S extends EntityState<I>>
        implements Entity<I, S>, ReceptorLifecycle<AbstractEntity<I, S>> {

    /**
     * The key for the metadata value which contains the list with the names of
     * parameter types of a receptor.
     *
     * @see #beforeInvoke(Receptor)
     * @see #afterInvoke(Receptor)
     */
    @SuppressWarnings("rawtypes") // to avoid generics hell.
    private static final MetadataKey<List> RECEPTOR_PARAM_TYPES =
            LoggingFactory.singleMetadataKey("receptor_param_types", List.class);

    /**
     * Lazily initialized reference to the model class of this entity.
     *
     * @see #thisClass()
     * @see #modelClass()
     */
    @LazyInit
    private volatile @MonotonicNonNull EntityClass<?> thisClass;

    /**
     * The ID of the entity.
     *
     * <p>Assigned either through the {@linkplain #AbstractEntity(Object) constructor which
     * accepts the ID}, or via {@link #setId(Object)}. Is never {@code null}.
     *
     * @apiNote The field is named with the underscore prefix to avoid
     *          name clash with the extension property name in Kotlin.
     */
    @SuppressWarnings("FieldNamingConvention") // See `apiNote` above.
    private @MonotonicNonNull I _id;

    /** Cached version of string ID. */
    @LazyInit
    private volatile @MonotonicNonNull String stringId;

    /**
     * The state of the entity.
     *
     * <p>Lazily initialized to the {@linkplain #defaultState() default state},
     * if {@linkplain #state() accessed} before {@linkplain #setState(EntityState)}
     * initialization}.
     *
     * @apiNote The field is named with the underscore prefix to avoid
     *          name clash with the extension property name in Kotlin.
     */
    @SuppressWarnings("FieldNamingConvention") // See `apiNote` above.
    @LazyInit
    private volatile @MonotonicNonNull S _state;

    /**
     * The version of the entity.
     *
     * @apiNote The field is named with the underscore prefix to avoid
     *          name clash with the extension property name in Kotlin.
     */
    @SuppressWarnings("FieldNamingConvention") // See `apiNote` above.
    private volatile Version _version;

    /**
     * The lifecycle flags of the entity.
     */
    private volatile @MonotonicNonNull LifecycleFlags lifecycleFlags;

    /**
     * Indicates if the lifecycle flags of the entity were changed since initialization.
     *
     * <p>Changed lifecycle flags should be updated when
     * {@linkplain io.spine.server.entity.Repository#store(io.spine.server.entity.Entity) storing}.
     */
    private volatile boolean lifecycleFlagsChanged;

    /**
     * A context for the logging operations performed by the entity in a receptor.
     *
     * <p>This field is {@code null}, if the entity is not being accessed through a receptor.
     *
     * @see #beforeInvoke(Receptor)
     * @see #afterInvoke(Receptor)
     */
    private @Nullable AutoCloseable loggingContext = null;

    /**
     * Creates a new instance with the zero version and cleared lifecycle flags.
     *
     * <p>When this constructor is called, the entity ID must be {@linkplain #setId(Object) set}
     * before any other interactions with the instance.
     */
    protected AbstractEntity() {
        setVersion(Versions.zero());
        clearLifecycleFlags();
    }

    /**
     * Creates new instance with the passed ID.
     */
    protected AbstractEntity(I id) {
        this();
        setId(id);
    }

    /**
     * Assigns the ID to the entity.
     */
    @SuppressWarnings("InstanceVariableUsedBeforeInitialized") // checked in `if`
    final void setId(I id) {
        checkNotNull(id);
        if (this._id != null) {
            checkState(id.equals(this._id),
                       "Entity ID already assigned to `%s`." +
                               " Attempted to reassign to `%s`.", this._id, id);
        }
        this._id = id;
    }

    /**
     * Creates a new instance with the passed ID and default entity state obtained
     * from the passed function.
     *
     * @param id
     *         the ID of the new entity
     * @param defaultState
     *         the function to obtain new entity state
     */
    protected AbstractEntity(I id, Function<I, S> defaultState) {
        this(id);
        checkNotNull(defaultState);
        setState(defaultState.apply(id));
    }

    @Override
    public I id() {
        return checkNotNull(_id);
    }

    /**
     * {@inheritDoc}
     *
     * <p>If the state of the entity was not initialized, it is set to
     * {@linkplain #defaultState() default value} and returned.
     *
     * @return the current state or default state value
     */
    @Override
    public final S state() {
        ensureAccessToState();
        var result = _state;
        if (result == null) {
            synchronized (this) {
                result = _state;
                if (result == null) {
                    _state = defaultState();
                    result = _state;
                }
            }
        }
        return result;
    }

    /**
     * Ensures that the callee is allowed to access Entity's {@link #state() state()} method.
     *
     * <p>In case the access is prohibited, throws a {@code RuntimeException}.
     *
     * <p>In some scenarios, the state of Entity may be not up-to-date,
     * so descendants of {@code AbstractEntity} are able to put the corresponding restrictions
     * on this method invocation.
     *
     * <p>By default, this method performs no checks,
     * thus allowing to access Entity's {@code state()} at any point of time.
     */
    @Internal
    @SuppressWarnings("NoopMethodInAbstractClass" /* By design. */)
    protected void ensureAccessToState() {
        // Do nothing by default.
    }

    /**
     * Obtains model class for this entity.
     */
    @Internal
    protected EntityClass<?> thisClass() {
        var result = thisClass;
        if (result == null) {
            synchronized (this) {
                result = thisClass;
                if (result == null) {
                    thisClass = modelClass();
                    result = thisClass;
                }
            }
        }
        return result;
    }

    /**
     * Obtains the model class.
     */
    @Internal
    @Override
    public EntityClass<?> modelClass() {
        return EntityClass.asEntityClass(getClass());
    }

    /**
     * Sets the entity state to the passed value.
     */
    void setState(S newState) {
        this._state = checkNotNull(newState);
    }

    /**
     * Obtains the default state of the entity.
     */
    protected final S defaultState() {
        @SuppressWarnings("unchecked")
        // cast is safe because this type of messages is saved to the map
        var result = (S) thisClass().defaultState();
        return result;
    }

    /**
     * Updates the state of the entity.
     *
     * <p>The new state must be {@linkplain #validate(EntityState) valid}.
     *
     * @param state
     *         the new state to set
     * @throws InvalidEntityStateException
     *         if the passed state is not {@linkplain #validate(EntityState) valid}
     */
    final void updateState(S state) {
        checkNotNull(state);
        validate(state);
        setState(state);
    }

    /**
     * Verifies the new entity state and returns {@link ConstraintViolation}s, if any.
     *
     * <p>Default implementation uses the {@linkplain Validate#violationsOf(Message) message
     * validation}.
     *
     * @param newState
     *         a state object to replace the current state
     * @return the violation constraints
     */
    protected final List<ConstraintViolation> checkEntityState(S newState) {
        checkNotNull(newState);
        ImmutableList.Builder<ConstraintViolation> violations = ImmutableList.builder();
        violations.addAll(violationsOf(newState));
        return violations.build();
    }

    /**
     * Ensures that the passed new state is valid.
     *
     * @param newState
     *         a state object to replace the current state
     * @throws InvalidEntityStateException
     *         if the state is not valid
     * @see #checkEntityState(EntityState)
     */
    private void validate(S newState) throws InvalidEntityStateException {
        var violations = checkEntityState(newState);
        if (!violations.isEmpty()) {
            throw InvalidEntityStateException.onConstraintViolations(newState, violations);
        }
    }

    /**
     * Obtains ID of the entity in the {@linkplain Stringifiers#toString(Object) string form}.
     *
     * <p>Subsequent calls to the method return a cached instance of the string, which minimizes
     * the performance impact of repeated calls.
     *
     * @return the string form of the entity ID
     */
    @Override
    public String idAsString() {
        var result = stringId;
        if (result == null) {
            synchronized (this) {
                result = stringId;
                if (result == null) {
                    stringId = Stringifiers.toString(id());
                    result = stringId;
                }
            }
        }
        return result;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                          .add("id", idAsString())
                          .toString();
    }

    /**
     * Sets status for the entity.
     */
    void setLifecycleFlags(LifecycleFlags lifecycleFlags) {
        if (!lifecycleFlags.equals(this.lifecycleFlags)) {
            this.lifecycleFlags = lifecycleFlags;
            this.lifecycleFlagsChanged = true;
        }
    }

    @Override
    public LifecycleFlags getLifecycleFlags() {
        var result = this.lifecycleFlags == null
                     ? LifecycleFlags.getDefaultInstance()
                     : this.lifecycleFlags;
        return result;
    }

    /**
     * Tests whether the entity is marked as archived.
     *
     * @return {@code true} if the entity is archived, {@code false} otherwise
     */
    @Override
    public final boolean isArchived() {
        return lifecycleFlags().getArchived();
    }

    /**
     * Sets {@code archived} status flag to the passed value.
     */
    protected void setArchived(boolean archived) {
        setLifecycleFlags(lifecycleFlags().toBuilder()
                                  .setArchived(archived)
                                  .build());
    }

    /**
     * Tests whether the entity is marked as deleted.
     *
     * @return {@code true} if the entity is deleted, {@code false} otherwise
     */
    @Override
    public final boolean isDeleted() {
        return lifecycleFlags().getDeleted();
    }

    /**
     * Sets {@code deleted} status flag to the passed value.
     */
    protected void setDeleted(boolean deleted) {
        setLifecycleFlags(lifecycleFlags().toBuilder()
                                  .setDeleted(deleted)
                                  .build());
    }

    /**
     * Ensures that the entity is not marked as {@code archived}.
     *
     * @throws CannotModifyArchivedEntity
     *         if the entity in the archived status
     * @see #lifecycleFlags()
     * @see io.spine.server.entity.LifecycleFlags#getArchived()
     */
    protected void checkNotArchived() throws CannotModifyArchivedEntity {
        if (lifecycleFlags().getArchived()) {
            var packedId = Identifier.pack(id());
            throw CannotModifyArchivedEntity.newBuilder()
                    .setEntityId(packedId)
                    .build();
        }
    }

    /**
     * Ensures that the entity is not marked as {@code deleted}.
     *
     * @throws CannotModifyDeletedEntity
     *         if the entity is marked as {@code deleted}
     * @see #lifecycleFlags()
     * @see io.spine.server.entity.LifecycleFlags#getDeleted()
     */
    protected void checkNotDeleted() throws CannotModifyDeletedEntity {
        if (lifecycleFlags().getDeleted()) {
            var packedId = Identifier.pack(id());
            throw CannotModifyDeletedEntity.newBuilder()
                    .setEntityId(packedId)
                    .build();
        }
    }

    @Override
    public boolean lifecycleFlagsChanged() {
        return lifecycleFlagsChanged;
    }

    /**
     * Clears the lifecycle flags and their {@linkplain #lifecycleFlags modification flag}.
     */
    private void clearLifecycleFlags() {
        setLifecycleFlags(LifecycleFlags.getDefaultInstance());
        lifecycleFlagsChanged = false;
    }

    /**
     * Updates the state and version of the entity.
     *
     * <p>The new state must be valid.
     *
     * <p>The passed version must have a number not less than the current version of the entity.
     *
     * @param state
     *         the state object to set
     * @param version
     *         the entity version to set
     * @throws IllegalStateException
     *         if the passed state is not valid
     * @throws IllegalArgumentException
     *         if the passed version has the number which is greater than the current
     *         version of the entity
     */
    final void updateState(S state, Version version) {
        updateState(state);
        updateVersion(version);
    }

    /**
     * Obtains the version number of the entity.
     */
    protected int versionNumber() {
        var result = version().getNumber();
        return result;
    }

    final void updateVersion(Version newVersion) {
        checkNotNull(newVersion);
        check(newVersion);
        if (_version.equals(newVersion)) {
            return;
        }
        var currentVersionNumber = versionNumber();
        var newVersionNumber = newVersion.getNumber();
        if (currentVersionNumber > newVersionNumber) {
            throw newIllegalArgumentException(
                    "A version with the lower number (%d) passed to `updateVersion()` " +
                            "of the entity `%s` (`%s`) with the version number %d.",
                    newVersionNumber, thisClass(), idAsString(), currentVersionNumber
            );
        }
        setVersion(newVersion);
    }

    /**
     * Updates the state incrementing the version number and recording time of the modification.
     *
     * <p>This is a test-only convenience method. Calling this method is equivalent to calling
     * {@link #updateState(EntityState, Version)} with the incremented by one version.
     *
     * <p>Please use {@link #updateState(EntityState, Version)} directly in the production code.
     *
     * @param newState
     *         a new state to set
     */
    @VisibleForTesting
    void incrementState(S newState) {
        updateState(newState, incrementedVersion());
    }

    void setVersion(Version version) {
        this._version = version;
    }

    private Version incrementedVersion() {
        return Versions.increment(version());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Overrides to simplify implementation of entities implementing
     * {@link io.spine.server.EventProducer}.
     */
    @Override
    public Version version() {
        return _version;
    }

    /**
     * Advances the current version by one and records the time of the modification.
     *
     * @return new version number
     */
    int incrementVersion() {
        setVersion(incrementedVersion());
        return _version.getNumber();
    }

    /**
     * Obtains timestamp of the entity version.
     */
    public Timestamp whenModified() {
        return _version.getTimestamp();
    }

    /**
     * Creates new {@link ScopedLoggingContext} containing the names of the types of
     * the parameters of the given {@link Receptor}.
     *
     * <p>The list will be displayed as {@code CONTEXT} metadata in a log record,
     * iff the receptor performs logging.
     *
     * @param method the receptor method which is going to be called
     *
     * @see #afterInvoke(Receptor)
     */
    @OverridingMethodsMustInvokeSuper
    @Override
    @SuppressWarnings("MustBeClosedChecker") // We close the `loggingContext` in `afterInvoke()`.
    public void beforeInvoke(Receptor<AbstractEntity<I, S>, ?, ?, ?> method) {
        checkNotNull(method);
        var paramTypes = method.params().simpleNames();
        loggingContext = ScopedLoggingContext.getInstance()
            .newContext()
            .withMetadata(RECEPTOR_PARAM_TYPES, paramTypes)
            .install();
    }

    /**
     * Releases the {@link #loggingContext} installed by {@link #beforeInvoke(Receptor)}.
     *
     * @see #beforeInvoke(Receptor)
     */
    @OverridingMethodsMustInvokeSuper
    @Override
    public void afterInvoke(Receptor<AbstractEntity<I, S>, ?, ?, ?> method) {
        if (loggingContext != null) {
            try {
                loggingContext.close();
            } catch (Exception e) {
                throw newIllegalStateException(e,
                           "Unable to close the logging context `%s`.", loggingContext);
            }
            loggingContext = null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AbstractEntity<?, ?> that)) {
            return false;
        }
        return Objects.equals(id(), that.id()) &&
                Objects.equals(state(), that.state()) &&
                Objects.equals(version(), that.version()) &&
                Objects.equals(lifecycleFlags(), that.lifecycleFlags());
    }

    @Override
    public int hashCode() {
        return Objects.hash(id(), state(), version(), lifecycleFlags());
    }
}
