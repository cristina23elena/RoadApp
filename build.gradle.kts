plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.google.gms.google.services) apply false // Declari plugin-ul fără să îl aplici
}

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.0.2") // Ajustează versiunea dacă e necesar
        classpath("com.google.gms:google-services:4.4.2") // Include Google Services Gradle Plugin
    }
}
