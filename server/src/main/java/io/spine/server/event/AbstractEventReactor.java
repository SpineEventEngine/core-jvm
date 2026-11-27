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

package io.spine.server.event;

import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.concurrent.LazyInit;
import com.google.protobuf.Any;
import io.spine.base.Error;
import io.spine.core.MessageId;
import io.spine.core.Version;
import io.spine.core.Versions;
import io.spine.logging.WithLogging;
import io.spine.protobuf.TypeConverter;
import io.spine.server.BoundedContext;
import io.spine.server.ContextAware;
import io.spine.server.Identity;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.dispatch.DispatchOutcomeHandler;
import io.spine.server.event.model.EventReactorClass;
import io.spine.server.tenant.TenantAwareRunner;
import io.spine.server.type.EventClass;
import io.spine.server.type.EventEnvelope;
import io.spine.system.server.HandlerFailedUnexpectedly;
import io.spine.system.server.NoOpSystemWriteSide;
import io.spine.system.server.SystemWriteSide;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

import java.util.function.Supplier;

import static io.spine.server.dispatch.DispatchOutcomes.ignored;
import static io.spine.util.Suppliers2.memoize;
import static java.lang.String.format;

/**
 * An abstract base for all classes that may produce events in response to other events.
 *
 * <p>Events may be produced in response to both domestic and external events.
 *
 * <p>To use one, do the following:
 * <ol>
 *     <li>Create an instance, specifying the event bus that receives emitted events.
 *     <li>{@linkplain io.spine.server.BoundedContextBuilder#addEventDispatcher(EventDispatcher) Register}.
 * </ol>
 *
 * @see React reactors
 */
public abstract class AbstractEventReactor
        implements EventReactor, EventDispatcher, ContextAware, WithLogging {

    private final EventReactorClass<?> thisClass;
    private final Supplier<MessageId> eventAnchor;
    @LazyInit
    private SystemWriteSide system = NoOpSystemWriteSide.INSTANCE;

    /** The event bus to which the emitted events are posted. */
    @LazyInit
    private @MonotonicNonNull EventBus eventBus;

    private final Supplier<Any> producerId;

    protected AbstractEventReactor() {
        var cls = getClass();
        this.thisClass = EventReactorClass.asReactorClass(cls);
        this.eventAnchor = memoize(() -> Identity.ofSingleton(cls));
        this.producerId = memoize(() -> TypeConverter.toAny(cls.getName()));
    }

    @Override
    public void registerWith(BoundedContext context) {
        checkNotRegistered();
        eventBus = context.eventBus();
        system = context.systemClient().writeSide();
    }

    @Override
    public final boolean isRegistered() {
        return eventBus != null;
    }

    @Override
    public final ImmutableSet<EventClass> messageClasses() {
        return thisClass.events();
    }

    @Override
    public DispatchOutcome dispatch(EventEnvelope event) {
        return TenantAwareRunner.with(event.tenantId())
                                .evaluate(() -> reactAndPost(event));
    }

    @SuppressWarnings("FloggerLogString" /* Re-using the logged message. */)
    private DispatchOutcome reactAndPost(EventEnvelope event) {
        var method = thisClass.reactorOf(event);
        if (method.isPresent()) {
            var outcome = method.get().invoke(this, event);
            DispatchOutcomeHandler
                    .from(outcome)
                    .onEvents(eventBus::post)
                    .onError(error -> postFailure(error, event))
                    .handle();
            return outcome;
        } else {
            var eventId = event.id();
            var msg = format("Reactor `%s` filtered out and ignored event %s[ID: %s].",
                             thisClass, event.messageClass(), eventId.value());
            logger().atDebug().log(() -> msg);
            return ignored(event, msg);
        }
    }

    private MessageId eventAnchor() {
        return eventAnchor.get();
    }

    private void postFailure(Error error, EventEnvelope event) {
        var systemEvent = HandlerFailedUnexpectedly.newBuilder()
                .setEntity(eventAnchor())
                .setHandledSignal(event.messageId())
                .setError(error)
                .build();
        system.postEvent(systemEvent, event.asMessageOrigin());
    }

    /**
     * Obtains the name of this reactor, {@linkplain TypeConverter#toAny(Object) packed to Any}.
     */
    @Override
    public Any producerId() {
        return producerId.get();
    }

    /**
     * Returns a {@linkplain Versions#zero() zero version}.
     */
    @Override
    public Version version() {
        return Versions.zero();
    }

    @Override
    public final ImmutableSet<EventClass> externalEventClasses() {
        return thisClass.externalEvents();
    }

    @Override
    public final ImmutableSet<EventClass> domesticEventClasses() {
        return thisClass.domesticEvents();
    }
}
