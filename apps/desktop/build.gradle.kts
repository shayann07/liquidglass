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
                // The Material 3 showcase shared with the web demo and the Android sample.
                implementation(project(":liquidglass-material3"))
                implementation(libs.compose.material.icons.core)
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
        val gpuPriority = providers.gradleProperty("atlas.gpu").getOrElse("discrete")
        require(gpuPriority in setOf("auto", "integrated", "discrete")) {
            "atlas.gpu must be auto, integrated or discrete"
        }
        jvmArgs += "-Dskiko.gpu.priority=$gpuPriority"
        providers.gradleProperty("atlas.capture").orNull?.let {
            jvmArgs += "-Datlas.capture=$it"
        }
        providers.gradleProperty("atlas.scene").orNull?.let { jvmArgs += "-Datlas.scene=$it" }
        providers.gradleProperty("atlas.windowSize").orNull?.let { jvmArgs += "-Datlas.windowSize=$it" }
        providers.gradleProperty("atlas.motionCapture").orNull?.let { jvmArgs += "-Datlas.motionCapture=$it" }
        providers.gradleProperty("atlas.motionReadback").orNull?.let { jvmArgs += "-Datlas.motionReadback=$it" }
        providers.gradleProperty("atlas.motionShortTaps").orNull?.let { jvmArgs += "-Datlas.motionShortTaps=$it" }
        providers.gradleProperty("atlas.motionObserveFrames").orNull?.let { jvmArgs += "-Datlas.motionObserveFrames=$it" }
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

// Deterministic 60 fps GPU replay of PhoneScene for reference comparison (see PhoneReplay.kt).
tasks.register<JavaExec>("phoneReplay") {
    val main = kotlin.targets.getByName("desktop").compilations.getByName("main")
    classpath = files(main.output.allOutputs, main.runtimeDependencyFiles)
    mainClass.set("com.wexpa.liquidglass.sample.desktop.PhoneReplayKt")
    jvmArgs("-Dskiko.gpu.priority=discrete")
    args(providers.gradleProperty("atlas.replay.script").orNull ?: "", providers.gradleProperty("atlas.replay.out").orNull ?: "",
        providers.gradleProperty("atlas.replay.variant").getOrElse("calm"))
}
tasks.withType<Test>().configureEach {
    systemProperty("kotlinx.coroutines.test.default_timeout", "15m")
    // The showcase renders (two phone screens at density 3) run in CI's own showcase job;
    // -Patlas.skipShowcase=true leaves them out of the main build.
    if (providers.gradleProperty("atlas.skipShowcase").getOrElse("false").toBoolean()) {
        filter { excludeTestsMatching("*MaterialShowcaseRenderTest") }
    }
    // The colour-transfer evidence renders (#201) draw only when asked for: -Patlas.colourTransfer=true.
    providers.gradleProperty("atlas.colourTransfer").orNull?.let { systemProperty("atlas.colourTransfer", it) }
}
