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

package io.spine.gradle

/**
 * Compares dependency version strings by their meaning rather than lexicographically.
 *
 * Numeric segments are ordered as numbers, so `10.0.0` is newer than `9.2.0`, and
 * `2.0.0-SNAPSHOT.100` is newer than `2.0.0-SNAPSHOT.99`. A plain `String` comparison
 * would order both pairs the other way around.
 *
 * The rules follow Semantic Versioning where it applies:
 *
 *  1. A version consists of a release part and an optional qualifier, separated by
 *     the first `-`: for `2.0.0-SNAPSHOT.100` these are `2.0.0` and `SNAPSHOT.100`.
 *  2. Both parts are compared segment by segment, as split by `.`, and also by `-`
 *     within a qualifier. Two numeric segments are compared as numbers, two textual
 *     ones as case-insensitive text, and a numeric segment is older than a textual one.
 *  3. When one version runs out of segments, it is the older one: `1.0.1` is newer
 *     than `1.0`, and `1.0.0-RC.1` is newer than `1.0.0-RC`.
 *  4. When the release parts are equal, a version without a qualifier is newer than
 *     a version with one: `2.0.0` is newer than `2.0.0-SNAPSHOT.100`.
 *
 * Unlike full Maven semantics, qualifiers carry no special meaning: `RC`, `SNAPSHOT`,
 * and the like are ordered as plain text. This keeps the comparison simple and
 * predictable for the report, where only the relative recency of the versions
 * of the same artifact matters.
 */
internal object VersionComparator : Comparator<String> {

    override fun compare(left: String, right: String): Int {
        val (leftRelease, leftQualifier) = left.parse()
        val (rightRelease, rightQualifier) = right.parse()
        val byRelease = compareSegments(leftRelease, rightRelease)
        if (byRelease != 0) {
            return byRelease
        }
        return when {
            leftQualifier == null && rightQualifier == null -> 0
            leftQualifier == null -> 1
            rightQualifier == null -> -1
            else -> compareSegments(leftQualifier, rightQualifier)
        }
    }

    /**
     * Splits this version into the segments of its release part and the segments
     * of its qualifier, the latter being `null` when the version has no qualifier.
     */
    private fun String.parse(): Pair<List<String>, List<String>?> {
        val release = substringBefore('-')
        val qualifier = if ('-' in this) substringAfter('-') else null
        return release.split('.') to qualifier?.split('.', '-')
    }

    private fun compareSegments(left: List<String>, right: List<String>): Int {
        for (index in 0 until maxOf(left.size, right.size)) {
            val bySegment = compareSegment(
                left.getOrElse(index) { "" },
                right.getOrElse(index) { "" }
            )
            if (bySegment != 0) {
                return bySegment
            }
        }
        return 0
    }

    /**
     * Compares single segments, ordering an absent (empty) segment below any present
     * one, a numeric segment below a textual one, numbers by their value, and text
     * case-insensitively.
     *
     * Keeping the empty, numeric, and textual segments in distinct buckets makes
     * the order transitive: comparing a numeric pair as numbers, but a mixed pair
     * as text, would order `2` < `10` < `1a` < `2`.
     */
    private fun compareSegment(left: String, right: String): Int {
        if (left.isEmpty() || right.isEmpty()) {
            return left.length.compareTo(right.length)
        }
        val leftNumber = left.toLongOrNull()
        val rightNumber = right.toLongOrNull()
        return when {
            leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
            leftNumber != null -> -1
            rightNumber != null -> 1
            else -> left.compareTo(right, ignoreCase = true)
        }
    }
}
