plugins {
    id("com.android.application")
}

// -----------------------------------------------------------------------------
// Release signing
// -----------------------------------------------------------------------------

private val signingKeystorePath =
    System.getenv("AUTO_SCROLL_KEYSTORE")
        ?.takeIf { it.isNotBlank() }
        ?: "${System.getProperty("user.home")}/.local/share/android-signing/personal-apps.p12"

private val signingStorePassword =
    System.getenv("ANDROID_KEYSTORE_PASSWORD")
        ?.takeIf { it.isNotBlank() }

private val signingKeyPassword =
    System.getenv("ANDROID_KEY_PASSWORD")
        ?.takeIf { it.isNotBlank() }
        ?: signingStorePassword

private val signingKeystoreFile = file(signingKeystorePath)

private val releaseRequested =
    gradle.startParameter.taskNames.any { requestedTask ->
        val taskName = requestedTask
            .substringAfterLast(":")
            .lowercase()

        taskName.contains("release") ||
            taskName in setOf(
                "build",
                "assemble",
                "bundle",
                "install",
                "publish"
            )
    }

// Fail immediately if release signing prerequisites are missing.
if (releaseRequested) {
    check(signingKeystoreFile.isFile) {
        "Release signing failed: keystore not found at $signingKeystorePath"
    }

    check(signingStorePassword != null) {
        "Release signing failed: ANDROID_KEYSTORE_PASSWORD is missing"
    }

    check(signingKeyPassword != null) {
        "Release signing failed: ANDROID_KEY_PASSWORD is missing"
    }
}

// -----------------------------------------------------------------------------
// Android configuration
// -----------------------------------------------------------------------------

android {
    namespace = "com.adampirog.autoscroll"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.adampirog.autoscroll"

        minSdk = 24
        targetSdk = 35

        versionCode = 2
        versionName = "0.2.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // -------------------------------------------------------------------------
    // Signing configurations
    // -------------------------------------------------------------------------

    signingConfigs {
        create("release") {
            storeFile = signingKeystoreFile
            storeType = "PKCS12"

            storePassword = signingStorePassword ?: ""

            // Explicit alias from the original pyproject.toml.
            keyAlias = "personal-apps"

            keyPassword = signingKeyPassword ?: ""
        }
    }

    // Validate that Gradle actually registered the expected alias.
    check(signingConfigs.getByName("release").keyAlias == "personal-apps") {
        "Release signing configuration has an incorrect or missing key alias"
    }

    // -------------------------------------------------------------------------
    // Build types
    // -------------------------------------------------------------------------

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
        }

        getByName("release") {
            isMinifyEnabled = false

            signingConfig = signingConfigs.getByName("release")
        }
    }
}
