plugins {
    id("org.jetbrains.kotlin.jvm")
    jacoco
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
}
