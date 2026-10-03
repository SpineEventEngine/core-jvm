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

package io.spine.gradle.javascript.plugin

import com.google.protobuf.gradle.ProtobufExtension
import com.google.protobuf.gradle.id
import com.google.protobuf.gradle.remove
import io.spine.dependency.lib.Protobuf

/**
 * Applies and configures the `protobuf` plugin to work with a JavaScript module.
 *
 * In particular, this method:
 *
 *  1. Specifies an executable for the `protoc` compiler.
 *  2. Configures `GenerateProtoTask`.
 *
 * @see JsPlugins
 */
fun JsPlugins.protobuf() {

    plugins {
        apply(Protobuf.GradlePlugin.id)
    }

    val protobufExt = project.extensions.getByType(ProtobufExtension::class.java)
    protobufExt.apply {

        protoc {
            artifact = Protobuf.compiler
        }

        generateProtoTasks {
            all().forEach { task ->

                task.builtins {

                    // Do not use java builtin output in this project.

                    remove("java")

                    // For information on JavaScript code generation please see
                    // https://github.com/google/protobuf/blob/master/js/README.md

                    id("js") {
                        option("import_style=commonjs")
                        outputSubDir = genProtoDirName
                    }
                }

                val sourceSet = task.sourceSet.name
                val testClassifier = if (sourceSet == "test") "_test" else ""
                val artifact = "${project.group}_${project.name}_${moduleVersion}"
                val descriptor = "$artifact$testClassifier.desc"

                task.generateDescriptorSet = true
                task.descriptorSetOptions.path =
                    "${projectDir}/build/descriptors/${sourceSet}/${descriptor}"
            }
        }
    }
}
