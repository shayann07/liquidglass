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
                // Already supplied at runtime by Compose; needed directly for non-reentrant diagnostics.
                implementation(libs.kotlinx.coroutines.swing)
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
        providers.gradleProperty("atlas.motionCapture").orNull?.let { jvmArgs += "-Datlas.motionCapture=$it" }
        providers.gradleProperty("atlas.motionReadback").orNull?.let { jvmArgs += "-Datlas.motionReadback=$it" }
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "LiquidGlassStudio"
            packageVersion = "1.0.0"
        }
    }
}

// Keep renderer evidence alongside cached test results on CI.
tasks.withType<Test>().configureEach {
    outputs.dir(layout.buildDirectory.dir("reports/atlas"))
    // Both suites render real scenes on the CPU. Concurrent workers exhausted Atlas's
    // six-minute budget; the same assertions finish in3m28s alone. Keep their deadlines
    // and serialize only when both tasks are requested (do not force library tests here).
    mustRunAfter(":liquidglass:jvmTest")
}
