import com.ncorti.ktfmt.gradle.tasks.KtfmtFormatTask

plugins {
  id("com.android.application") version "8.0.0-alpha09" apply false
  id("com.android.library") version "8.0.0-alpha09" apply false
  id("org.jetbrains.kotlin.android") version "1.8.10" apply false
  id("com.ncorti.ktfmt.gradle") version "0.11.0"
}

tasks.register<KtfmtFormatTask>("format") {
  source = project.fileTree(rootDir)
  include("*.gradle.kts", "app/*.gradle.kts")
  dependsOn(":app:ktfmtFormat")
}
