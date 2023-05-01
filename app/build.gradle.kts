plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("com.ncorti.ktfmt.gradle")
}

android {
  compileSdk = 33
  namespace = "org.matrix.chromext"

  defaultConfig {
    applicationId = "org.matrix.chromext"
    minSdk = 21
    targetSdk = 33
    versionCode = 6
    versionName = "3.0.0"
  }

  buildFeatures { buildConfig = true }

  buildTypes {
    release {
      isShrinkResources = true
      isMinifyEnabled = true
      proguardFiles("proguard-rules.pro")
    }
  }

  androidResources { additionalParameters("--allow-reserved-package-id", "--package-id", "0x45") }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_18
    targetCompatibility = JavaVersion.VERSION_18
  }

  lintOptions {
    disable(
        "Internationalization",
        "UnsafeIntentLaunch",
        "UnspecifiedRegisterReceiverFlag",
        "VectorPath",
        "Usability:Icons")
  }

  kotlinOptions { jvmTarget = JavaVersion.VERSION_18.toString() }
}

dependencies { compileOnly("de.robv.android.xposed:api:82") }
