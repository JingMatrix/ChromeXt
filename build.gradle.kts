import com.ncorti.ktfmt.gradle.tasks.KtfmtFormatTask

plugins {
  id("com.android.application") version "8.2.0-alpha01" apply false
  id("com.android.library") version "8.2.0-alpha01" apply false
  id("org.jetbrains.kotlin.android") version "1.8.21" apply false
  id("com.ncorti.ktfmt.gradle") version "0.12.0"
}

tasks.register<KtfmtFormatTask>("format") {
  source = project.fileTree(rootDir)
  include("*.gradle.kts", "app/*.gradle.kts")
  dependsOn(":app:ktfmtFormat")
}
