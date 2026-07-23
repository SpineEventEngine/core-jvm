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

package io.spine.server.entity

import com.google.common.base.MoreObjects
import com.google.common.collect.ImmutableList
import com.google.errorprone.annotations.OverridingMethodsMustInvokeSuper
import com.google.protobuf.Timestamp
import io.spine.annotation.Internal
import io.spine.annotation.VisibleForTesting
import io.spine.base.EntityState
import io.spine.base.Identifier
import io.spine.core.Version
import io.spine.core.Versions
import io.spine.logging.LoggingFactory
import io.spine.logging.MetadataKey
import io.spine.logging.context.ScopedLoggingContext
import io.spine.server.entity.model.EntityClass
import io.spine.server.entity.rejection.CannotModifyArchivedEntity
import io.spine.server.entity.rejection.CannotModifyDeletedEntity
import io.spine.server.log.ReceptorLifecycle
import io.spine.server.model.Receptor
import io.spine.string.Stringifiers
import io.spine.util.Exceptions.newIllegalArgumentException
import io.spine.util.Exceptions.newIllegalStateException
import io.spine.validation.ConstraintViolation
import io.spine.validation.Validate
import java.util.Objects
import java.util.Optional
import java.util.function.Function

/**
 * Abstract base for entities.
 *
 * @param I The type of entity identifiers.
 * @param S The type of entity state objects.
 */
@Suppress(
    "TooManyFunctions" /* The base type for all entities, so it naturally exposes many members. */
)
public abstract class AbstractEntity<I : Any, S : EntityState<I>> :
    Entity<I, S>, ReceptorLifecycle<AbstractEntity<I, S>> {

    /**
     * Lazily initialized reference to the model class of this entity.
     *
     * @see thisClass
     * @see modelClass
     */
    @Volatile
    private var _thisClass: EntityClass<*>? = null

    /**
     * The ID of the entity.
     *
     * Assigned either through the [constructor which accepts the ID][AbstractEntity],
     * or via [setId]. Is never `null`.
     */
    private var _id: I? = null

    /**
     * The entity identifier.
     *
     * A property shortcut for `id()`.
     */
    public val id: I
        get() = id()

    /** Cached version of string ID. */
    @Volatile
    private var stringId: String? = null

    /**
     * The state of the entity.
     *
     * Lazily initialized to the [default state][defaultState],
     * if accessed via `state()` before [initialization][setState].
     */
    @Volatile
    private var _state: S? = null

    /**
     * The entity state.
     *
     * A property shortcut for `state()`.
     */
    public val state: S
        get() = state()

    /**
     * The version of the entity.
     */
    @Volatile
    private var _version: Version = Versions.zero()

    /**
     * The entity version.
     *
     * A property shortcut for `version()`.
     */
    public val version: Version
        get() = version()

    /**
     * The lifecycle flags of the entity.
     */
    @Volatile
    private var _lifecycleFlags: LifecycleFlags? = null

    /**
     * Indicates if the lifecycle flags of the entity were changed since initialization.
     *
     * Changed lifecycle flags should be updated when [storing][Repository.store].
     */
    @Volatile
    private var _lifecycleFlagsChanged: Boolean = false

    /**
     * A context for the logging operations performed by the entity in a receptor.
     *
     * This field is `null`, if the entity is not being accessed through a receptor.
     *
     * @see beforeInvoke
     * @see afterInvoke
     */
    private var loggingContext: AutoCloseable? = null

    /**
     * Creates a new instance with the zero version and cleared lifecycle flags.
     *
     * When this constructor is called, the entity ID must be [set][setId]
     * before any other interactions with the instance.
     */
    protected constructor() {
        clearLifecycleFlags()
    }

    /**
     * Creates a new instance with the passed ID.
     */
    protected constructor(id: I) : this() {
        setId(id)
    }

    /**
     * Creates a new instance with the passed ID and default entity state obtained
     * from the passed function.
     *
     * @param id The ID of the new entity.
     * @param defaultState The function to obtain a new entity state.
     */
    protected constructor(id: I, defaultState: Function<I, S>) : this(id) {
        setState(defaultState.apply(id))
    }

    /**
     * Assigns the ID to the entity.
     */
    protected fun setId(id: I) {
        _id?.let {
            check(id == it) {
                "Entity ID already assigned to `$it`. Attempted to reassign to `$id`."
            }
        }
        _id = id
    }

    override fun id(): I = _id ?: throw NullPointerException("The entity ID is not assigned yet.")

    /**
     * If the state of the entity was not initialized, it is set to
     * [default value][defaultState] and returned.
     *
     * @return The current state or default state value.
     */
    final override fun state(): S {
        _state?.let { return it }
        return synchronized(this) {
            _state ?: defaultState().also { _state = it }
        }
    }

    /**
     * Obtains the model class for this entity.
     */
    @Internal
    protected open fun thisClass(): EntityClass<*> {
        _thisClass?.let { return it }
        return synchronized(this) {
            _thisClass ?: modelClass().also { _thisClass = it }
        }
    }

    /**
     * Obtains the model class.
     */
    @Internal
    override fun modelClass(): EntityClass<*> = EntityClass.asEntityClass(javaClass)

    /**
     * Sets the entity state to the passed value.
     */
    @JvmName("setState")
    internal fun setState(newState: S) {
        _state = newState
    }

    /**
     * Obtains the default state of the entity.
     */
    public fun defaultState(): S {
        @Suppress("UNCHECKED_CAST")
        // cast is safe because this type of messages is saved to the map
        val result = thisClass().defaultState() as S
        return result
    }

    /**
     * Updates the state of the entity.
     *
     * The new state must be [valid][validate].
     *
     * @param state The new state to set.
     * @throws InvalidEntityStateException If the passed state is not [valid][validate].
     */
    @JvmName("updateState")
    internal fun updateState(state: S) {
        validate(state)
        setState(state)
    }

    /**
     * Verifies the new entity state and returns [ConstraintViolation]s, if any.
     *
     * The default implementation uses the [message validation][Validate.violationsOf].
     *
     * @param newState A state object to replace the current state.
     * @return The constraint violations.
     */
    protected fun checkEntityState(newState: S): List<ConstraintViolation> {
        val violations = ImmutableList.builder<ConstraintViolation>()
        violations.addAll(Validate.violationsOf(newState))
        return violations.build()
    }

    /**
     * Ensures that the passed new state is valid.
     *
     * @param newState A state object to replace the current state.
     * @throws InvalidEntityStateException If the state is not valid.
     * @see checkEntityState
     */
    private fun validate(newState: S) {
        val violations = checkEntityState(newState)
        if (violations.isNotEmpty()) {
            throw InvalidEntityStateException.onConstraintViolations(newState, violations)
        }
    }

    /**
     * Obtains the ID of the entity in the [string form][Stringifiers.toString].
     *
     * Subsequent calls to the method return a cached instance of the string, which minimizes
     * the performance impact of repeated calls.
     *
     * @return The string form of the entity ID.
     */
    override fun idAsString(): String {
        stringId?.let { return it }
        return synchronized(this) {
            stringId ?: Stringifiers.toString(id()).also { stringId = it }
        }
    }

    override fun toString(): String =
        MoreObjects.toStringHelper(this)
            .add("id", idAsString())
            .toString()

    /**
     * Sets status for the entity.
     */
    @JvmName("setLifecycleFlags")
    internal fun setLifecycleFlags(lifecycleFlags: LifecycleFlags) {
        if (lifecycleFlags != _lifecycleFlags) {
            _lifecycleFlags = lifecycleFlags
            _lifecycleFlagsChanged = true
        }
    }

    override fun getLifecycleFlags(): LifecycleFlags =
        _lifecycleFlags ?: LifecycleFlags.getDefaultInstance()

    /**
     * Tests whether the entity is marked as archived.
     *
     * @return `true` if the entity is archived, `false` otherwise.
     */
    final override fun isArchived(): Boolean = lifecycleFlags().archived

    /**
     * Sets `archived` status flag to the passed value.
     */
    protected open fun setArchived(archived: Boolean) {
        setLifecycleFlags(
            lifecycleFlags().toBuilder()
                .setArchived(archived)
                .build()
        )
    }

    /**
     * Tests whether the entity is marked as deleted.
     *
     * @return `true` if the entity is deleted, `false` otherwise.
     */
    final override fun isDeleted(): Boolean = lifecycleFlags().deleted

    /**
     * Sets `deleted` status flag to the passed value.
     */
    protected open fun setDeleted(deleted: Boolean) {
        setLifecycleFlags(
            lifecycleFlags().toBuilder()
                .setDeleted(deleted)
                .build()
        )
    }

    /**
     * Ensures that the entity is not marked as `archived`.
     *
     * @throws CannotModifyArchivedEntity If the entity is in the archived status.
     * @see lifecycleFlags
     * @see LifecycleFlags.archived
     */
    protected open fun checkNotArchived() {
        if (lifecycleFlags().archived) {
            val packedId = Identifier.pack(id())
            throw CannotModifyArchivedEntity.newBuilder()
                .setEntityId(packedId)
                .build()
        }
    }

    /**
     * Ensures that the entity is not marked as `deleted`.
     *
     * @throws CannotModifyDeletedEntity If the entity is marked as `deleted`.
     * @see lifecycleFlags
     * @see LifecycleFlags.deleted
     */
    protected open fun checkNotDeleted() {
        if (lifecycleFlags().deleted) {
            val packedId = Identifier.pack(id())
            throw CannotModifyDeletedEntity.newBuilder()
                .setEntityId(packedId)
                .build()
        }
    }

    override fun lifecycleFlagsChanged(): Boolean = _lifecycleFlagsChanged

    /**
     * Clears the lifecycle flags and their [modification flag][_lifecycleFlagsChanged].
     */
    private fun clearLifecycleFlags() {
        setLifecycleFlags(LifecycleFlags.getDefaultInstance())
        _lifecycleFlagsChanged = false
    }

    /**
     * Updates the state and version of the entity.
     *
     * The new state must be valid.
     *
     * The passed version must have a number not less than the current version of the entity.
     *
     * @param state The state object to set.
     * @param version The entity version to set.
     * @throws IllegalStateException If the passed state is not valid.
     * @throws IllegalArgumentException If the passed version has a number lower than
     *   the current version of the entity.
     */
    @JvmName("updateState")
    internal fun updateState(state: S, version: Version) {
        updateState(state)
        updateVersion(version)
    }

    /**
     * Obtains the version number of the entity.
     */
    protected open fun versionNumber(): Int = version().number

    /**
     * Updates the version of this entity with the passed value, validating it first.
     */
    internal fun updateVersion(newVersion: Version) {
        Validate.check(newVersion)
        if (_version == newVersion) {
            return
        }
        val currentVersionNumber = versionNumber()
        val newVersionNumber = newVersion.number
        if (currentVersionNumber > newVersionNumber) {
            throw newIllegalArgumentException(
                "A version with the lower number (%d) passed to `updateVersion()` " +
                        "of the entity `%s` (`%s`) with the version number %d.",
                newVersionNumber, thisClass(), idAsString(), currentVersionNumber
            )
        }
        setVersion(newVersion)
    }

    /**
     * Updates the state incrementing the version number and recording time of the modification.
     *
     * This is a test-only convenience method. Calling this method is equivalent to calling
     * `updateState` with a version incremented by one.
     *
     * Please use `updateState` directly in the production code.
     *
     * @param newState A new state to set.
     */
    @VisibleForTesting
    internal fun incrementState(newState: S) {
        updateState(newState, incrementedVersion())
    }

    /**
     * Assigns the version to this entity as is, without validation.
     */
    internal fun setVersion(version: Version) {
        _version = version
    }

    private fun incrementedVersion(): Version = Versions.increment(version())

    /**
     * Overrides to simplify implementation of entities implementing
     * [io.spine.server.EventProducer].
     */
    override fun version(): Version = _version

    /**
     * Advances the current version by one and records the time of the modification.
     *
     * @return The new version number.
     */
    internal fun incrementVersion(): Int {
        setVersion(incrementedVersion())
        return _version.number
    }

    /**
     * Obtains the timestamp of the entity version.
     */
    public open fun whenModified(): Timestamp = _version.timestamp

    /**
     * The recent history of the states this entity went through.
     *
     * Served lazily from the durable storage through the loader installed by
     * the repository. An entity created outside a
     * repository has no loader, so its history reads come back empty.
     */
    private val recentStateHistory = RecentStateHistory<S>()

    /**
     * Obtains the recent history of states of this entity.
     */
    protected fun recentStateHistory(): RecentStateHistory<S> = recentStateHistory

    /**
     * Installs the loader serving the [recent state history][recentStateHistory]
     * reads from the durable storage.
     *
     * Called by the repository unconditionally when an entity instance is
     * created. Whether the repository records the state history gates only
     * the behavior of the installed loader: while the recording is off,
     * reading through it fails fast rather than serving an empty history.
     */
    internal fun setStateHistoryLoader(loader: StateHistoryLoader) {
        recentStateHistory.useLoader(loader)
    }

    /**
     * Appends the given record to the [recent state history][recentStateHistory]
     * of this entity.
     *
     * Called by the repository once per successful dispatch, right after
     * the record is written to the durable state history, so that the
     * subsequent reads served by this instance — e.g., during the later
     * dispatches of a delivery batch — come from memory.
     */
    internal fun appendToStateHistory(record: EntityRecord) {
        recentStateHistory.append(record)
    }

    /**
     * Obtains the state this entity had at the given time, if the recorded
     * state history retains it.
     *
     * The result is the recorded state with the highest version among those
     * which became current not later than the given time. The answer is honest
     * about retention: an empty result means the question cannot be answered
     * from the retained window — the time either precedes the oldest retained
     * record, or predates the entity itself.
     *
     * The state produced by the current, not-yet-stored dispatch is not
     * recorded yet: a receptor reading the history observes the states
     * persisted by the previous dispatches.
     *
     * The state history is an opt-in feature — see
     * `AbstractEntityRepository.recordStateHistory()`. Reading it while the
     * repository of this entity does not record it is a configuration error.
     * An entity created outside a repository has no recorded history and
     * reads an empty `Optional`.
     *
     * @param time The point in time to look at.
     * @return The state at the given time, or an empty `Optional` if the
     *         recorded history does not retain it.
     * @throws IllegalStateException If the repository of this entity
     *   does not record the state history.
     */
    protected fun stateAt(time: Timestamp): Optional<S> {
        val state = recentStateHistory.stateAt(time)
        return Optional.ofNullable(state)
    }

    /**
     * Creates an iterator over up to [depth] most recent recorded states of
     * this entity, newest first.
     *
     * Fewer states are returned if the recorded history retains fewer.
     * The state produced by the current, not-yet-stored dispatch is not
     * recorded yet: a receptor reading the history observes the states
     * persisted by the previous dispatches.
     *
     * The state history is an opt-in feature — see
     * `AbstractEntityRepository.recordStateHistory()`. Reading it while the
     * repository of this entity does not record it is a configuration error.
     * An entity created outside a repository has no recorded history and
     * reads an empty iterator.
     *
     * @param depth The maximal number of the most recent states to return; must be positive.
     * @return New iterator instance.
     * @throws IllegalArgumentException If the [depth] is not positive.
     * @throws IllegalStateException If the repository of this entity does not record
     *   the state history.
     */
    protected fun stateHistoryBackward(depth: Int): Iterator<S> =
        recentStateHistory.read(depth)

    /**
     * Creates new [ScopedLoggingContext] containing the names of the types of
     * the parameters of the given [Receptor].
     *
     * The list will be displayed as `CONTEXT` metadata in a log record,
     * iff the receptor performs logging.
     *
     * @param method
     *         The receptor method that is going to be called.
     *
     * @see afterInvoke
     */
    @OverridingMethodsMustInvokeSuper
    override fun beforeInvoke(method: Receptor<AbstractEntity<I, S>, *, *, *>) {
        val paramTypes = method.params().simpleNames()
        loggingContext = ScopedLoggingContext.getInstance()
            .newContext()
            .withMetadata(RECEPTOR_PARAM_TYPES, paramTypes)
            .install()
    }

    /**
     * Releases the [loggingContext] installed by [beforeInvoke].
     *
     * @see beforeInvoke
     */
    @OverridingMethodsMustInvokeSuper
    @Suppress("TooGenericExceptionCaught") // `AutoCloseable.close()` declares `throws Exception`.
    override fun afterInvoke(method: Receptor<AbstractEntity<I, S>, *, *, *>) {
        loggingContext?.let { context ->
            try {
                context.close()
            } catch (e: Exception) {
                throw newIllegalStateException(
                    e, "Unable to close the logging context `%s`.", context
                )
            }
            loggingContext = null
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is AbstractEntity<*, *>) {
            return false
        }
        return id() == other.id() &&
                state() == other.state() &&
                version() == other.version() &&
                lifecycleFlags() == other.lifecycleFlags()
    }

    override fun hashCode(): Int =
        Objects.hash(id(), state(), version(), lifecycleFlags())

    private companion object {

        /**
         * The key for the metadata value that contains the list with the names of
         * parameter types of a receptor.
         *
         * @see beforeInvoke
         * @see afterInvoke
         */
        val RECEPTOR_PARAM_TYPES: MetadataKey<List<*>> =
            LoggingFactory.singleMetadataKey("receptor_param_types", List::class.java)
    }
}
