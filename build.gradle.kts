import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.shadowjar) apply false
}

allprojects {
    group = "mangareader"

    version = "0.1.0"

    repositories {
        mavenCentral()
        google()
        // Android stub jar published by the Suwayomi project
        maven("https://github.com/Suwayomi/Suwayomi-Server/raw/android-jar/")
        maven("https://jitpack.io")
        maven("https://jogamp.org/deployment/maven")
        maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
    }
}

subprojects {
    plugins.withType<JavaPlugin> {
        extensions.configure<JavaPluginExtension> {
            val javaVersion = JavaVersion.toVersion(libs.versions.jvmTarget.get())
            sourceCompatibility = javaVersion
            targetCompatibility = javaVersion
        }
    }

    tasks {
        withType<KotlinJvmCompile> {
            compilerOptions {
                jvmTarget = JvmTarget.fromTarget(libs.versions.jvmTarget.get())
                freeCompilerArgs.add("-Xcontext-parameters")
            }
        }
    }
}
