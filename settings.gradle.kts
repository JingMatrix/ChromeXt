pluginManagement {
  repositories {
    gradlePluginPortal()
    google()
    mavenCentral()
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    maven("https://api.xposed.info/")
    // maven("https://jitpack.io")
  }
}

include(":xposed", ":app")

rootProject.name = "ChromeXt"
