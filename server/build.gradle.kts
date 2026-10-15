plugins {
    id(
        libs.plugins.kotlin.jvm
            .get()
            .pluginId,
    )
    id(
        libs.plugins.kotlin.serialization
            .get()
            .pluginId,
    )
    application
    alias(libs.plugins.shadowjar)
}

dependencies {
    // Shared (kotlin, coroutines, serialization, koin, logging, dex2jar, apk-parser, ...)
    implementation(libs.bundles.shared)

    // OkHttp
    implementation(libs.bundles.okhttp)
    implementation(libs.okio)

    // HTTP server
    implementation(libs.javalin.core)

    // Dependencies Mihon extensions expect to find at runtime
    implementation(libs.injekt)
    implementation(libs.rxjava)
    implementation(libs.jsoup)
    implementation(libs.serialization.xml.core)
    implementation(libs.serialization.xml)
    implementation(libs.sort)
    implementation(libs.commonscompress)
    implementation(libs.zip4j)
    // AES/CBC/PKCS7Padding cipher provider used by some extensions
    implementation(libs.bouncycastle)

    // BoringSSL-based TLS (the stack Mihon uses on Android); some sites reject the JDK's TLS handshake
    implementation("org.conscrypt:conscrypt-openjdk-uber:2.5.2")

    // ASM for BytecodeEditor (must match dex2jar version)
    implementation(libs.asm)
    implementation(libs.cache4k)

    // Android compatibility layer
    implementation(projects.androidCompat)
    implementation(projects.androidCompat.config)

    testImplementation(libs.bundles.sharedTest)
}

application {
    mainClass.set(MainClass)
}

tasks {
    shadowJar {
        isZip64 = true
        manifest {
            attributes(
                "Main-Class" to MainClass,
                "Implementation-Title" to rootProject.name,
                "Implementation-Version" to AppVersion,
                "Multi-Release" to true, // needed for polyglot
            )
        }
        archiveBaseName.set(rootProject.name)
        archiveVersion.set(AppVersion)
        archiveClassifier.set("")
        destinationDirectory.set(File("$rootDir/server/build"))
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        mergeServiceFiles("META-INF/services")
        dependencies {
            // GraalVM js-community declares POM-only artifacts that shadow cannot expand
            exclude(dependency("org.graalvm.js:js-community:.*"))
            exclude(dependency("org.graalvm.js:js:.*"))
        }
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile> {
        compilerOptions {
            freeCompilerArgs.add("-opt-in=kotlinx.serialization.ExperimentalSerializationApi")
        }
    }

    test {
        useJUnitPlatform()
        testLogging {
            showStandardStreams = true
            events("passed", "skipped", "failed")
        }
    }
}
