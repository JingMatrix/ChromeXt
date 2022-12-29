plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
	id("com.ncorti.ktfmt.gradle") version "0.11.0"
	id("com.google.devtools.ksp") version "1.8.0-RC2-1.0.8"
}

android {
    compileSdk = 33
	namespace = "org.matrix.chromext"

    defaultConfig {
        applicationId = "org.matrix.chromext"
        minSdk = 26
        targetSdk = 33
        versionCode = 2
        versionName = "2.0.0"
    }

	// packagingOptions {
        // // Remove terminal-emulator and termux-shared JNI libs added via termux-shared dependency
        // exclude("lib/*/libtermux.so")
        // exclude("lib/*/liblocal-socket.so")
    // }

    buildTypes {
        named("release") {
            isShrinkResources = true
            isMinifyEnabled = true
            proguardFiles("proguard-rules.pro")
        }
    }

    androidResources {
        additionalParameters("--allow-reserved-package-id", "--package-id", "0x45")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.toString()
    }
}

dependencies {
    compileOnly("de.robv.android.xposed:api:82")
}
