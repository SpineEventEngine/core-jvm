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

package io.spine.server.model

/**
 * Associates a model class of one kind with a Java class.
 *
 * A `ModelClassValue` creates the model class of a Java class with the [create] function
 * when the model class is requested for the first time. Then it keeps the model class
 * as long as the Java class is loaded. All the callers obtain the same instance, even when
 * they request it concurrently. If the function throws, nothing is kept, and the next
 * request calls the function again.
 *
 * Each kind of model classes has its own `ModelClassValue`. So, one Java class may have
 * model classes of several kinds. For example, an aggregate class has both a plain
 * [EntityClass][io.spine.server.entity.model.EntityClass] and
 * an [AggregateClass][io.spine.server.aggregate.model.AggregateClass].
 *
 * @param M The type of the model classes.
 * @param create The function that creates the model class of the passed Java class.
 */
public class ModelClassValue<M : ModelClass<*>>(
    private val create: (Class<*>) -> M
) : ClassValue<M>() {

    override fun computeValue(type: Class<*>): M = create(type)
}
