import com.android.build.api.variant.FilterConfiguration.FilterType.ABI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("org.jlleitschuh.gradle.ktlint")
    jacoco
}

android {
    namespace = "com.tikitoki.downloader"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tikitoki.downloader"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "0.5.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    }

    val releaseSecrets = listOf("KEYSTORE_PATH", "KEYSTORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD")
        .associateWith { providers.environmentVariable(it).orNull?.takeIf(String::isNotBlank) }
    val hasSigning = releaseSecrets.values.all { it != null }
    if (!hasSigning && releaseSecrets.values.any { it != null }) {
        throw GradleException("Para firmar release se requieren las cuatro variables KEYSTORE_PATH, KEYSTORE_PASSWORD, KEY_ALIAS y KEY_PASSWORD.")
    }
    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = file(requireNotNull(releaseSecrets.getValue("KEYSTORE_PATH")))
                storePassword = releaseSecrets.getValue("KEYSTORE_PASSWORD")
                keyAlias = releaseSecrets.getValue("KEY_ALIAS")
                keyPassword = releaseSecrets.getValue("KEY_PASSWORD")
            }
        }
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasSigning) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
    packaging { jniLibs { useLegacyPackaging = true } }
}

ktlint {
    version.set("1.5.0")
    baseline.set(file("ktlint-baseline.xml"))
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.outputs.forEach { output ->
            val abi = output.filters.find { it.filterType == ABI }?.identifier
            if (abi == null || abi == "x86_64") output.enabled.set(false)
        }
    }
}

dependencies {
    implementation(project(":core:security"))
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.27.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-video:2.7.0")

    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")

    // GPL-3.0: consult redistribution obligations before publishing.
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.room:room-testing:2.7.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}


// M1 coverage gate: pure URL and Intent-boundary validation logic.
// Android Intent behavior is also covered by androidTest on an emulator.
tasks.register<JacocoReport>("m1CoverageReport") {
    dependsOn("testDebugUnitTest")
    classDirectories.setFrom(
        fileTree(project(":core:security").layout.buildDirectory.dir("classes/kotlin/main")) {
            include("**/domain/IncomingLinkValidator*")
            include("**/domain/MediaLinkParser*")
        },
    )
    sourceDirectories.setFrom(files("src/main/java", project(":core:security").file("src/main/kotlin")))
    executionData.setFrom(
        fileTree(layout.buildDirectory) {
            include("jacoco/testDebugUnitTest.exec")
            include("outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec")
        },
    )
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.register<JacocoCoverageVerification>("verifyM1Coverage") {
    dependsOn("m1CoverageReport")
    classDirectories.setFrom(tasks.named<JacocoReport>("m1CoverageReport").map { it.classDirectories })
    executionData.setFrom(
        fileTree(layout.buildDirectory) {
            include("jacoco/testDebugUnitTest.exec")
            include("outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec")
        },
    )
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.95".toBigDecimal()
            }
        }
    }
}

/** Coverage of compiled application classes; reports only, with no threshold. */
tasks.register<JacocoReport>("globalCoverageReport") {
    dependsOn("testDebugUnitTest")
    classDirectories.setFrom(
        fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/debug")) {
            exclude("**/R.class", "**/R$*.class", "**/BuildConfig.*", "**/*_Impl*")
        },
        fileTree(project(":core:security").layout.buildDirectory.dir("classes/kotlin/main")),
    )
    sourceDirectories.setFrom(files("src/main/java", project(":core:security").file("src/main/kotlin")))
    executionData.setFrom(
        fileTree(layout.buildDirectory) {
            include("jacoco/testDebugUnitTest.exec")
            include("outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec")
        },
    )
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}
