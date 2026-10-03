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

/**
 * This file provides extensions to `String` and `CharSequence` that wrap
 * analogues from the standard Kotlin runtime.
 *
 * It helps in switching between versions of Gradle that have different versions of
 * the Kotlin runtime. Please see the bodies of the extension functions for details on
 * switching the implementations depending on the Kotlin version at hand.
 *
 * Once we migrate to newer Gradle, these wrappers should be inlined with
 * the subsequent removal of this source file.
 */
@Suppress("unused")
private const val ABOUT = ""

/**
 * Makes the first character come in the title case.
 */
fun String.titleCaseFirstChar(): String = replaceFirstChar { it.titlecase() }

/**
 * Converts this string to lowercase.
 */
@Deprecated(message = "Please use `lowercase()` instead.", replaceWith = ReplaceWith("lowercase"))
fun String.lowercased(): String = lowercase()
