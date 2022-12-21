plugins {
    id("com.android.application") version "8.0.0-alpha09" apply false
    id("com.android.library") version "8.0.0-alpha09" apply false
    id("org.jetbrains.kotlin.android") version "1.8.0-RC2" apply false
}

tasks.register<Delete>("clean").configure {
    delete(rootProject.buildDir)
}
