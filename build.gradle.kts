plugins {
    id("com.android.application") version "7.2.1" apply false
    id("com.android.library") version "7.2.1" apply false
    id("org.jetbrains.kotlin.android") version "1.7.22" apply false
}

tasks.register<Delete>("clean").configure {
    delete(rootProject.buildDir)
}
