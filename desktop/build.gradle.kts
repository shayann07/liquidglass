import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvm("desktop") {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }
    sourceSets {
        val desktopMain by getting {
            dependencies {
                implementation(project(":liquidglass"))
                implementation(compose.desktop.currentOs)
            }
            kotlin.srcDirs("src/jvmMain/kotlin", "../sample/src/main/kotlin/shared")
        }
        val desktopTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                implementation(compose.uiTest)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.wexpa.liquidglass.sample.desktop.MainKt"
        providers.gradleProperty("atlas.capture").orNull?.let {
            jvmArgs += "-Datlas.capture=$it"
        }
        providers.gradleProperty("atlas.scene").orNull?.let { jvmArgs += "-Datlas.scene=$it" }
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "LiquidGlassStudio"
            packageVersion = "1.0.0"
        }
    }
}
