import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.1.10"
    kotlin("plugin.compose") version "2.1.10"
    id("org.jetbrains.compose") version "1.7.3"
    kotlin("plugin.serialization") version "2.1.10"
}

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
}

compose.desktop {
    application {
        mainClass = "com.launcher.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "ApplicationLauncher"
            packageVersion = project.property("app.version") as String
        }
    }
}
