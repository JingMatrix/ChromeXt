plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("com.ncorti.ktfmt.gradle")
}

android {
  compileSdk = 34
  namespace = "org.matrix.chromext"

  defaultConfig {
    applicationId = "org.matrix.chromext"
    minSdk = 21
    targetSdk = 34
    versionCode = 12
    versionName = "3.6.0"
  }

  buildFeatures { buildConfig = true }

  buildTypes {
    release {
      isShrinkResources = true
      isMinifyEnabled = true
      proguardFiles("proguard-rules.pro")
    }
  }

  androidResources {
    additionalParameters += listOf("--allow-reserved-package-id", "--package-id", "0x45")
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_20
    targetCompatibility = JavaVersion.VERSION_20
  }

  lint {
    disable +=
        listOf(
            "Internationalization",
            "UnsafeIntentLaunch",
            "SetJavaScriptEnabled",
            "UnspecifiedRegisterReceiverFlag",
            "Usability:Icons")
  }

  kotlinOptions { jvmTarget = JavaVersion.VERSION_20.toString() }
}

dependencies { compileOnly("de.robv.android.xposed:api:82") }
