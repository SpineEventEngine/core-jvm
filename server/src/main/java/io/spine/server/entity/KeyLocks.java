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

package io.spine.server.entity;

import io.spine.annotation.VisibleForTesting;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Locks, one per key, that make the actions on the same key mutually exclusive, and let
 * the actions on different keys run in parallel.
 *
 * <p>Two keys are the same if they are {@linkplain Object#equals(Object) equal}.
 *
 * <p>A lock exists only while it is in use. It is created by a call for a key that no other
 * call is using, and is disposed when the last call holding or awaiting it completes.
 * The number of locks is therefore limited by the number of calls in progress at the same
 * time, rather than by the number of keys passed to this object.
 *
 * <p>The locks are reentrant: an action may run another action on the same key. An action
 * may also run an action on a different key. As with any locks, two threads doing so for
 * each other's keys at the same time block each other forever.
 *
 * @param <K>
 *         the type of keys
 */
final class KeyLocks<K> {

    /**
     * The locks in use, by their keys.
     */
    private final ConcurrentMap<K, CountedLock> locks = new ConcurrentHashMap<>();

    /**
     * Runs the passed action holding the lock of the passed key.
     */
    void run(K key, Runnable action) {
        var lock = retain(key);
        lock.lock();
        try {
            action.run();
        } finally {
            lock.unlock();
            release(key);
        }
    }

    /**
     * Evaluates the passed action holding the lock of the passed key.
     *
     * @return the result of the action
     */
    <T> T evaluate(K key, Supplier<T> action) {
        var lock = retain(key);
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
            release(key);
        }
    }

    /**
     * Obtains the number of locks currently in use.
     *
     * <p>Provided only for tests.
     */
    @VisibleForTesting
    int size() {
        return locks.size();
    }

    /**
     * Obtains the lock of the passed key, creating the lock if it is not in use yet.
     *
     * <p>Counts the caller among the users of the lock, so that the lock is not disposed
     * while the caller awaits or holds it. The caller must call {@link #release(Object)
     * release()} when it no longer needs the lock.
     */
    private Lock retain(K key) {
        var counted = locks.compute(key, (k, existing) -> {
            var result = existing != null ? existing : new CountedLock();
            result.users++;
            return result;
        });
        return counted.lock;
    }

    /**
     * Records that the caller no longer uses the lock of the passed key, disposing the lock
     * if no other call awaits or holds it.
     */
    private void release(K key) {
        locks.computeIfPresent(key, (k, counted) -> {
            counted.users--;
            return counted.users == 0 ? null : counted;
        });
    }

    /**
     * A lock that counts the calls using it.
     */
    private static final class CountedLock {

        private final Lock lock = new ReentrantLock();

        /**
         * The number of calls awaiting or holding the lock.
         *
         * <p>Read and written only inside the computations of the {@code locks} map for the key
         * of this lock. The map runs them one at a time, so no other synchronization is needed.
         */
        private int users;
    }
}
