plugins {
    // Use version catalog for all plugin versions
    alias(libs.plugins.agp) apply false
    alias(libs.plugins.kotlinGradlePlugin) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinCompose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.ktlintGradle) apply false
    alias(libs.plugins.cyclonedxGradle)
}