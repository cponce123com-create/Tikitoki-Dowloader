import com.android.build.api.variant.FilterConfiguration.FilterType.ABI
import org.gradle.process.ExecOperations
import javax.inject.Inject

plugins {
    alias(libs.plugins.agp)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinCompose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktlintGradle)
    alias(libs.plugins.detektGradle)
    alias(libs.plugins.dokkaGradle)
    jacoco
}

android {
    namespace = "com.tikitoki.downloader"
    compileSdk = Integer.parseInt(libs.versions.compileSdk.get())

    defaultConfig {
        applicationId = "com.tikitoki.downloader"
        minSdk = Integer.parseInt(libs.versions.minSdk.get())
        targetSdk = Integer.parseInt(libs.versions.targetSdk.get())
        versionCode = Integer.parseInt(libs.versions.appVersionCode.get())
        versionName = libs.versions.appVersion.get()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val releaseSecrets =
        listOf("KEYSTORE_PATH", "KEYSTORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD")
            .associateWith { providers.environmentVariable(it).orNull?.takeIf(String::isNotBlank) }
    val hasSigning = releaseSecrets.values.all { it != null }
    if (!hasSigning && releaseSecrets.values.any { it != null }) {
        throw GradleException(
            "Para firmar release se requieren las cuatro variables KEYSTORE_PATH, KEYSTORE_PASSWORD, KEY_ALIAS y KEY_PASSWORD.",
        )
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
    packaging {
        jniLibs {
            useLegacyPackaging = true
            pickFirsts.add("lib/arm64-v8a/libffmpeg.zip.so")
        }
    }
}

ktlint {
    version.set(libs.versions.ktlint.get())
    baseline.set(file("ktlint-baseline.xml"))
}

detekt {
    config.setFrom(rootProject.file("detekt.yml"))
    baseline = file("detekt-baseline.xml")
    buildUponDefaultConfig = true
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

abstract class RebuildFfmpegWebp
    @Inject
    constructor(
        private val processes: ExecOperations,
    ) : DefaultTask() {
        @get:InputFiles
        @get:PathSensitive(PathSensitivity.RELATIVE)
        abstract val scripts: ConfigurableFileCollection

        @get:InputFile
        @get:PathSensitive(PathSensitivity.NONE)
        abstract val ffmpegAar: RegularFileProperty

        @get:Internal
        abstract val ndk: DirectoryProperty

        @get:Input
        abstract val ndkVersion: Property<String>

        @get:OutputDirectory
        abstract val nativeLibraries: DirectoryProperty

        @TaskAction
        fun rebuild() {
            processes.exec {
                commandLine(
                    "python3",
                    scripts.files.single { it.name == "rebuild_ffmpeg_webp.py" }.absolutePath,
                    "--aar",
                    ffmpegAar.get().asFile.absolutePath,
                    "--ndk",
                    ndk.get().asFile.absolutePath,
                    "--output",
                    nativeLibraries.get().asFile.absolutePath,
                )
            }
        }
    }

val ffmpegSourceAar by configurations.creating {
    isCanBeConsumed = false
    isTransitive = false
}
val ffmpegNdkVersion = "28.2.13676358"
val rebuildFfmpegWebp by tasks.registering(RebuildFfmpegWebp::class) {
    scripts.from(rootProject.file("scripts/rebuild_ffmpeg_webp.py"), rootProject.file("scripts/verify_native_pages.py"))
    ffmpegAar.fileProvider(ffmpegSourceAar.elements.map { it.single().asFile })
    ndkVersion.set(ffmpegNdkVersion)
    ndk.set(androidComponents.sdkComponents.sdkDirectory.map { it.dir("ndk/$ffmpegNdkVersion") })
    nativeLibraries.set(layout.buildDirectory.dir("generated/ffmpeg-webp"))
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.sources.jniLibs?.addGeneratedSourceDirectory(rebuildFfmpegWebp, RebuildFfmpegWebp::nativeLibraries)
        variant.outputs.forEach { output ->
            val abi = output.filters.find { it.filterType == ABI }?.identifier
            // Exclude x86_64: virtually no production Android devices use x86_64
            // (only emulators). Reduces universal APK size by ~25% and avoids
            // shipping native libs for an architecture with zero real users.
            // See: https://developer.android.com/ndk/guides/abis#x86_64
            if (abi == null || abi == "x86_64") output.enabled.set(false)
        }
    }
}

dependencies {
    implementation(project(":core:security"))
    coreLibraryDesugaring(libs.coreLibraryDesugaring)
    implementation(libs.newPipeExtractor)
    implementation(libs.androidxCoreKtx)
    implementation(libs.androidxCoreSplashscreen)
    implementation(libs.androidxDocumentfile)
    implementation(libs.androidxLifecycleRuntimeKtx)
    implementation(libs.androidxLifecycleViewmodelCompose)
    implementation(libs.androidxActivityCompose)
    implementation(libs.kotlinxCoroutinesAndroid)
    implementation(libs.androidxWorkRuntimeKtx)
    implementation(libs.androidxMedia3Exoplayer)
    implementation(libs.androidxMedia3Session)
    implementation(libs.androidxMedia3Ui)

    implementation(platform(libs.androidxComposeBom))
    implementation(libs.androidxComposeUi)
    implementation(libs.androidxComposeUiToolingPreview)
    implementation(libs.androidxComposeFoundation)
    implementation(libs.androidxComposeMaterial3)
    implementation(libs.androidxComposeMaterial)
    implementation(libs.androidxComposeMaterialIconsExtended)
    implementation(libs.coilCompose)
    implementation(libs.coilVideo)

    implementation(libs.androidxRoomRuntime)
    implementation(libs.androidxRoomKtx)
    ksp(libs.androidxRoomCompiler)

    // GPL-3.0: consult redistribution obligations before publishing.
    implementation(libs.youtubedlAndroidLibrary)
    implementation(libs.youtubedlAndroidFfmpeg)
    ffmpegSourceAar("io.github.junkfood02.youtubedl-android:ffmpeg:${libs.versions.youtubedlAndroid.get()}@aar")

    debugImplementation(libs.androidxComposeUiTooling)
    testImplementation(libs.junit)
    testImplementation(libs.jsonJvm)
    testImplementation(libs.kotlinxCoroutinesTest)
    androidTestImplementation(libs.androidxTestJunit)
    androidTestImplementation(libs.androidxTestRunner)
    androidTestImplementation(libs.androidxRoomTesting)
    androidTestImplementation(platform(libs.androidxComposeBom))
    androidTestImplementation(libs.androidxComposeUiTestJunit4)
    debugImplementation(libs.androidxComposeUiTestManifest)
}

// M1 coverage gate: pure URL parsers, input policy, and metadata sanitization.
// IncomingIntentReader behavior is covered separately by instrumented androidTest.
tasks.register<JacocoReport>("m1CoverageReport") {
    dependsOn("testDebugUnitTest", project(":core:security").tasks.named("test"))
    classDirectories.setFrom(
        fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/debug")) {
            include("**/domain/TikTokLinkParser*")
        },
        fileTree(project(":core:security").layout.buildDirectory.dir("classes/kotlin/main")) {
            include("**/domain/IncomingLinkValidator*")
            include("**/domain/MediaLinkParser*")
            include("**/domain/DownloadInputPolicy*")
            include("**/domain/MetadataSanitizer*")
        },
    )
    sourceDirectories.setFrom(files("src/main/java", project(":core:security").file("src/main/kotlin")))
    executionData.setFrom(
        fileTree(layout.buildDirectory) {
            include("jacoco/testDebugUnitTest.exec")
            include("outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec")
        },
        fileTree(project(":core:security").layout.buildDirectory) {
            include("jacoco/test.exec")
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
        fileTree(project(":core:security").layout.buildDirectory) {
            include("jacoco/test.exec")
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
    dependsOn("testDebugUnitTest", project(":core:security").tasks.named("test"))
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
        fileTree(project(":core:security").layout.buildDirectory) {
            include("jacoco/test.exec")
        },
    )
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

/**
 * Global unit-test coverage gate. CI measured 10.15% in Android CI #485.
 * Keep a 10% floor while coverage grows toward the 70% goal.
 */
tasks.register<JacocoCoverageVerification>("verifyGlobalCoverage") {
    dependsOn("globalCoverageReport")
    classDirectories.setFrom(tasks.named<JacocoReport>("globalCoverageReport").map { it.classDirectories })
    executionData.setFrom(
        fileTree(layout.buildDirectory) {
            include("jacoco/testDebugUnitTest.exec")
            include("outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec")
        },
        fileTree(project(":core:security").layout.buildDirectory) {
            include("jacoco/test.exec")
        },
    )
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.10".toBigDecimal()
            }
        }
    }
}
