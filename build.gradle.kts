import com.ncorti.ktfmt.gradle.TrailingCommaManagementStrategy
import com.ncorti.ktfmt.gradle.tasks.KtfmtFormatTask

plugins {
  id("com.android.application") version "9.4.1" apply false
  id("com.ncorti.ktfmt.gradle") version "0.27.0"
}

// Keep the existing meta style: ktfmt 0.27 otherwise defaults to rewriting the whole tree with
// trailing commas. The plugin feeds this to every KtfmtBaseTask, the format task below included.
ktfmt { trailingCommaManagementStrategy.set(TrailingCommaManagementStrategy.NONE) }

// AGP 9 ships built-in Kotlin, so the kotlin-android plugin is gone and with it the per source-set
// ktfmtFormat task the app module used to expose. Format every Kotlin and Gradle source directly.
tasks.register<KtfmtFormatTask>("format") {
  source =
      files(
              fileTree("app/src") { include("**/*.kt") },
              "build.gradle.kts",
              "app/build.gradle.kts",
              "settings.gradle.kts")
          .asFileTree
}
