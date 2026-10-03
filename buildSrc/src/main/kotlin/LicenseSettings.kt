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
 * The settings of the software license that apply to the code of this project.
 *
 * The constants defined in this object are used by the
 * [PublicationHandler][io.spine.gradle.publish.PublicationHandler] to set up
 * corresponding properties of the published `pom.xml` file of an artifact.
 *
 * So, in order to adapt the license settings to the requirements of a particular project,
 * simply change the values of the constants defined in this object.
 *
 * @see io.spine.gradle.publish.PublicationHandler
 */
@Suppress("ConstPropertyName") // https://bit.ly/kotlin-prop-names
object LicenseSettings {
    const val name = "The Apache License, Version 2.0"
    const val url = "https://www.apache.org/licenses/LICENSE-2.0.txt"

    /**
     * The [SPDX identifier](https://spdx.org/licenses/) of the license, which the SBOM
     * published with an artifact declares for the modules of this project.
     *
     * @see io.spine.gradle.publish.PublicationSbomTask
     */
    const val spdxId = "Apache-2.0"
}
