plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.kotlinxCoroutinesTest)
    testImplementation(libs.bundles.mockk)
    testImplementation(libs.turbine)
}
