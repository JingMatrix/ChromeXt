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
    versionCode = 7
    versionName = "3.1.0"
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_19
    targetCompatibility = JavaVersion.VERSION_19
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

  kotlinOptions { jvmTarget = JavaVersion.VERSION_19.toString() }
}
