// Hermes Agent for Android — Build System
// Target: Android 16 (API 36) per 2026 Google Play Console requirements
// Gradle: 8.9+ | AGP: 8.8+ | Kotlin: 2.0+

plugins {
    id("com.android.application") version "8.8.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
    id("com.chaquo.python") version "16.0.0" apply false
}
