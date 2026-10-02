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

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * This script uses two declarations of the constant [licenseReportVersion] because
 * currently there is no way to define a constant _before_ a build script of `buildSrc`.
 * We cannot use imports or do something else before the `buildscript` or `plugin` clauses.
 */

plugins {
    java
    groovy
    `kotlin-dsl`
}

repositories {
    mavenLocal()
    gradlePluginPortal()
    mavenCentral()
}

/**
 * The version of Jackson used by `buildSrc`.
 *
 * This value is deliberately decoupled from [io.spine.dependency.lib.Jackson.version],
 * which now points to Jackson 3.x. The `buildSrc` sources still use the Jackson 2.x API
 * (`com.fasterxml.jackson.*`), so they must stay on a 2.x version until they are migrated
 * to `tools.jackson.*`.
 *
 * The floor is set by the SPDX Gradle Plugin (see [spdxPluginVersion]). Its JSON store
 * depends on `jackson-bom`, which aligns every Jackson 2.x module on the runtime classpath
 * of `buildSrc` with the version it requires. A lower version here would compile `buildSrc`
 * against one Jackson and run it against another. Raise this together with the plugin, or
 * when `buildSrc` itself needs a fix from a later 2.x — not to track the newest one.
 *
 * Whatever the reason for a change, `jackson-core` and `jackson-databind` below `2.18.9`
 * are exposed to published advisories, three of them rated `high` — `GHSA-r7wm-3cxj-wff9`,
 * `GHSA-rmj7-2vxq-3g9f`, and `GHSA-j3rv-43j4-c7qm`.
 */
val jacksonVersion = "2.22.0"

/**
 * The version of Google Artifact Registry used by `buildSrc`.
 *
 * The version `2.1.5` is the latest before `2.2.0`, which introduces breaking changes.
 *
 * @see <a href="https://mvnrepository.com/artifact/com.google.cloud.artifactregistry/artifactregistry-auth-common">
 *     Google Artifact Registry at Maven</a>
 */
val googleAuthToolVersion = "2.1.5"

/**
 * Generates reports about the licenses of the dependencies for a Gradle project.
 *
 * https://github.com/jk1/Gradle-License-Report
 */
val licenseReportVersion = "3.1.4"

val grGitVersion = "5.3.3"

/**
 * The version of the Kotlin Gradle plugin used by the build process.
 *
 * This version may change from the [version of Kotlin][io.spine.dependency.lib.Kotlin.version]
 * used by the project.
 */
val kotlinEmbeddedVersion = "2.4.10"

/**
 * The version of Guava used in `buildSrc`.
 *
 * Always use the same version as the one specified in [io.spine.dependency.lib.Guava].
 * Otherwise, when testing Gradle plugins, clashes may occur.
 */
val guavaVersion = "33.7.1-jre"

/**
 * The version of ErrorProne Gradle plugin.
 *
 * Please keep in sync. with [io.spine.dependency.build.ErrorProne.GradlePlugin.version].
 *
 * @see <a href="https://github.com/tbroyer/gradle-errorprone-plugin/releases">
 *     Error Prone Gradle Plugin Releases</a>
 */
val errorPronePluginVersion = "5.1.1"

/**
 * The version of Protobuf Gradle Plugin.
 *
 * Please keep in sync. with [io.spine.dependency.lib.Protobuf.GradlePlugin.version].
 *
 * @see <a href="https://github.com/google/protobuf-gradle-plugin/releases">
 *     Protobuf Gradle Plugins Releases</a>
 */
val protobufPluginVersion = "0.10.0"

/**
 * The version of Dokka Gradle Plugins.
 *
 * Please keep in sync with [io.spine.dependency.build.Dokka.version].
 *
 * @see <a href="https://github.com/Kotlin/dokka/releases">
 *     Dokka Releases</a>
 */
val dokkaVersion = "2.2.0"

/**
 * The version of Detekt Gradle Plugin.
 *
 * @see <a href="https://github.com/detekt/detekt/releases">Detekt Releases</a>
 */
val detektVersion = "1.23.8"

/**
 * @see [io.spine.dependency.test.Kotest]
 */
val kotestJvmPluginVersion = "0.4.11"

/**
 * @see [io.spine.dependency.test.Kover]
 */
val koverVersion = "0.9.9"

/**
 * The version of the Shadow Plugin.
 *
 * @see <a href="https://github.com/GradleUp/shadow">Shadow Plugin releases</a>
 */
val shadowVersion = "9.6.1"

/**
 * The version of the SPDX Gradle Plugin, which writes the SBOM published with each module.
 *
 * @see <a href="https://plugins.gradle.org/plugin/org.spdx.sbom">SPDX Gradle Plugin</a>
 */
val spdxPluginVersion = "0.12.0"

/**
 * The version of Plexus XML used by `buildSrc`, pinned to the line made for Maven 3.
 *
 * The Shadow plugin brings the 4.x line, made for Maven 4. It merges XML through
 * `maven-xml`, which finds its `XmlService` via the context class loader of the current
 * thread — a loader that does not see `buildSrc` on the threads Gradle runs tasks on.
 * The SPDX Gradle Plugin (see [spdxPluginVersion]) builds effective POMs with the Maven 3
 * model, which merges the plugin configuration of a POM with that of its parent. So with
 * the 4.x line, the SBOM of a module fails for any dependency whose POM does that.
 *
 * The 3.x line implements the same API on its own. Shadow uses only its classic part,
 * in `ComponentsXmlResourceTransformer`. Update this within the 3.x line only.
 */
val plexusXmlVersion = "3.1.0"

/**
 * The version of JUnit used to test the build scripts.
 *
 * @see [io.spine.dependency.test.JUnit]
 */
val junitVersion = "6.1.3"

/**
 * The version of Kotest used to test the build scripts.
 *
 * @see [io.spine.dependency.test.Kotest]
 */
val kotestVersion = "6.2.4"

configurations.all {
    resolutionStrategy {
        force(
            "com.google.guava:guava:${guavaVersion}",
            "com.google.protobuf:protobuf-gradle-plugin:$protobufPluginVersion",
            "org.codehaus.plexus:plexus-xml:$plexusXmlVersion",

            // Force Kotlin lib versions avoiding using those bundled with Gradle.
            "org.jetbrains.kotlin:kotlin-stdlib:$kotlinEmbeddedVersion",
            "org.jetbrains.kotlin:kotlin-stdlib-common:$kotlinEmbeddedVersion",
            "org.jetbrains.kotlin:kotlin-reflect:$kotlinEmbeddedVersion"
        )
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    api("com.github.jk1:gradle-license-report:$licenseReportVersion")
    api(platform("org.jetbrains.kotlin:kotlin-bom:$kotlinEmbeddedVersion"))
    dependOnAuthCommon()

    listOf(
        "com.fasterxml.jackson.core:jackson-databind:$jacksonVersion",
        "com.fasterxml.jackson.dataformat:jackson-dataformat-xml:$jacksonVersion",
        "com.github.jk1:gradle-license-report:$licenseReportVersion",
        "com.google.guava:guava:$guavaVersion",
        "com.google.protobuf:protobuf-gradle-plugin:$protobufPluginVersion",
        "com.gradleup.shadow:shadow-gradle-plugin:$shadowVersion",
        "io.gitlab.arturbosch.detekt:detekt-gradle-plugin:$detektVersion",
        "io.kotest:kotest-gradle-plugin:$kotestJvmPluginVersion",
        // https://github.com/srikanth-lingala/zip4j
        "net.lingala.zip4j:zip4j:2.11.6",
        "net.ltgt.gradle:gradle-errorprone-plugin:$errorPronePluginVersion",
        "org.ajoberstar.grgit:grgit-core:$grGitVersion",
        "org.jetbrains.dokka:dokka-base:$dokkaVersion",
        "org.jetbrains.dokka:dokka-gradle-plugin:$dokkaVersion",
        "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinEmbeddedVersion",
        "org.jetbrains.kotlin:kotlin-reflect:$kotlinEmbeddedVersion",
        "org.jetbrains.kotlinx:kover-gradle-plugin:$koverVersion",
        "org.spdx:spdx-gradle-plugin:$spdxPluginVersion"
    ).forEach {
        implementation(it)
    }

    testImplementation(platform("org.junit:junit-bom:$junitVersion"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation(gradleTestKit())
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()

    // Functional tests run real Gradle builds via TestKit and inject the production
    // classes of `buildSrc` into the build script classpath of those builds.
    // The argument provider defers resolving the classpath to execution time.
    val mainClasspath = sourceSets.main.get().runtimeClasspath
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf("-DbuildSrc.classpath=${mainClasspath.asPath}")
    })
}

dependOnBuildSrcJar()

/**
 * Adds a dependency on a `buildSrc.jar`, iff:
 *  1) the `src` folder is missing, and
 *  2) `buildSrc.jar` is present in the `buildSrc/` folder instead.
 *
 * This approach is used in the scope of integration testing.
 */
fun Project.dependOnBuildSrcJar() {
    val srcFolder = this.rootDir.resolve("src")
    val buildSrcJar = rootDir.resolve("buildSrc.jar")
    if (!srcFolder.exists() && buildSrcJar.exists()) {
        logger.info("Adding the pre-compiled 'buildSrc.jar' to 'implementation' dependencies.")
        dependencies {
            implementation(files("buildSrc.jar"))
        }
    }
}

/**
 * Includes the `implementation` dependency on `artifactregistry-auth-common`,
 * with the version defined in [googleAuthToolVersion].
 *
 * `artifactregistry-auth-common` has a transitive dependency on Gson and Apache `commons-codec`.
 * Gson from version `2.8.6` until `2.8.9` is vulnerable to Deserialization of Untrusted Data
 * (https://devhub.checkmarx.com/cve-details/CVE-2022-25647/).
 *
 *  Apache `commons-codec` before 1.13 is vulnerable to information exposure
 * (https://devhub.checkmarx.com/cve-details/Cxeb68d52e-5509/).
 *
 * We use Gson `2.10.1` and we force it in `forceProductionDependencies()`.
 * We use `commons-code` with version `1.16.0`, forcing it in `forceProductionDependencies()`.
 *
 * So, we should be safe with the current version `artifactregistry-auth-common` until
 * we migrate to a later version.
 */
fun DependencyHandlerScope.dependOnAuthCommon() {
    @Suppress("VulnerableLibrariesLocal", "RedundantSuppression")
    implementation(
        "com.google.cloud.artifactregistry:artifactregistry-auth-common:$googleAuthToolVersion"
    ) {
        exclude(group = "com.google.guava")
    }
}
