import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

val runNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0

kotlin {
    jvmToolchain(17)
    // Same code as the Android app (data, sync, screens)
    sourceSets["main"].kotlin.srcDirs("src/main/kotlin", "../common/src")
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.compose.material:material-icons-core:1.7.3")
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
    implementation("org.json:json:20240303")
}

compose.desktop {
    application {
        mainClass = "com.rady.worktracker.desktop.MainKt"
        jvmArgs += listOf("-Dfile.encoding=UTF-8")
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Ydar Track"
            packageVersion = "5.0.$runNumber"
            description = "Build yourself, one hour at a time."
            vendor = "Rady"
            copyright = "Created by Claude · Inspired by the ideas of Rady"
            includeAllModules = true
            windows {
                iconFile.set(project.file("icon.ico"))
                menuGroup = "Ydar Track"
                shortcut = true
                menu = true
                perUserInstall = true
                dirChooser = true
                // Keeps the same id so new versions install over old ones.
                upgradeUuid = "6f1d2c8e-7b4a-4f0e-9a3d-5b8c1e2f4a71"
            }
        }
    }
}
