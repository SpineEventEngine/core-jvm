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

package io.spine.grpc;

import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.Range;
import io.grpc.stub.StreamObserver;
import io.spine.core.Response;
import io.spine.testing.UtilityClassTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Sets.newHashSet;
import static com.google.common.truth.Truth.assertThat;
import static io.spine.grpc.StreamObservers.forwardErrorsOnly;
import static io.spine.grpc.StreamObservers.memoizingObserver;
import static io.spine.grpc.StreamObservers.noOpObserver;
import static io.spine.testing.Assertions.assertIllegalState;
import static io.spine.testing.TestValues.nullRef;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("ThrowableNotThrown") // in custom assertions
@DisplayName("`StreamObservers` utility should")
class StreamObserversTest extends UtilityClassTest<StreamObservers> {

    StreamObserversTest() {
        super(StreamObservers.class);
    }

    @Test
    @DisplayName("provide non-null empty observer")
    void createEmptyObserver() {
        StreamObserver<Response> emptyObserver = noOpObserver();
        assertNotNull(emptyObserver);
        // Call methods just to add to coverage.
        emptyObserver.onNext(nullRef());
        emptyObserver.onError(nullRef());
        emptyObserver.onCompleted();
    }

    @Test
    @DisplayName("create proper error-forwarding observer")
    void createErrorForwardingObserver() {
        MemoizingObserver<?> delegate = new MemoizingObserver<>();

        var forwardingInstance = forwardErrorsOnly(delegate);

        forwardingInstance.onNext(new Object());
        forwardingInstance.onCompleted();
        var errorToForward = new RuntimeException("Sample exception");
        forwardingInstance.onError(errorToForward);

        assertThat(delegate.getError()).isEqualTo(errorToForward);
        assertThat(delegate.responses()).isEmpty();
        assertThat(delegate.isCompleted()).isFalse();
    }

    @Test
    @DisplayName("create proper memoizing observer")
    void createMemoizingObserver() {
        var observer = memoizingObserver();

        checkFirstResponse(observer);
        checkOnNext(observer);
        checkOnError(observer);
        checkIsCompleted(observer);
    }

    private static void checkFirstResponse(MemoizingObserver<Object> observer) {
        assertIllegalState(observer::firstResponse);
    }

    private static void checkOnNext(MemoizingObserver<Object> observer) {
        assertTrue(observer.responses()
                           .isEmpty());

        var firstResponse = new Object();
        observer.onNext(firstResponse);
        assertEquals(firstResponse, observer.firstResponse());

        var sorted = ContiguousSet.create(Range.closed(1, 20),
                                          DiscreteDomain.integers());
        List<Integer> moreResponses = newArrayList(newHashSet(sorted));

        for (var element : moreResponses) {
            observer.onNext(element);
        }
        var actualResponses = observer.responses();

        assertEquals(firstResponse, actualResponses.get(0));
        assertEquals(moreResponses.size() + 1, // as there was the first response
                     actualResponses.size());
        assertEquals(moreResponses, actualResponses.subList(1, actualResponses.size()));
    }

    private static void checkOnError(MemoizingObserver<Object> observer) {
        assertNull(observer.getError());
        var exception = new RuntimeException("Sample error");
        observer.onError(exception);
        assertEquals(exception, observer.getError());
    }

    private static void checkIsCompleted(MemoizingObserver<Object> observer) {
        assertFalse(observer.isCompleted());
        observer.onCompleted();
        assertTrue(observer.isCompleted());
    }
}
