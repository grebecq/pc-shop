plugins {
    // Если на компьютере нет JDK 21, Gradle скачает его сам (нужно для toolchain в build.gradle.kts)
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "pc-shop"
