import com.ncorti.ktfmt.gradle.tasks.KtfmtFormatTask

plugins {
  id("com.android.application") version "8.7.2" apply false
  id("org.jetbrains.kotlin.android") version "2.0.10" apply false
  id("com.ncorti.ktfmt.gradle") version "0.19.0"
}

tasks.register<KtfmtFormatTask>("format") {
  source = project.fileTree(rootDir)
  include("*.gradle.kts", "app/*.gradle.kts")
  dependsOn(":app:ktfmtFormat")
}
