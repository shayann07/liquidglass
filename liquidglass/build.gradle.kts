import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.mavenPublish)
}

kotlin {
    jvm()

    android {
        namespace = "com.wexpa.liquidglass"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(compose.desktop.currentOs)
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
        }
    }
}

// Coordinates and POM come from the root gradle.properties (GROUP, VERSION_NAME, POM_*).
mavenPublishing {
    publishToMavenCentral()

    // Central requires signed artifacts; a local publish does not. Signing is switched on by the
    // presence of a key rather than unconditionally, so that anyone can `publishToMavenLocal` and
    // build an app against it without first generating a GPG key they will never use.
    val hasSigningKey = listOf("signingInMemoryKey", "signing.keyId")
        .any { providers.gradleProperty(it).isPresent }
    if (hasSigningKey) signAllPublications()

    configure(
        KotlinMultiplatform(
            // Central insists on a javadoc jar per artifact and accepts an empty one. The API is
            // documented in docs/api-reference.md, which is where a reader would look anyway.
            javadocJar = JavadocJar.Empty(),
            sourcesJar = true,
        ),
    )
}

// The calibration render (GlassCalibrationRenderTest) is opt-in: pass
// -Pliquidglass.calibration=<dir with calibration-target-*.png> and it writes composites there.
tasks.withType<Test>().configureEach {
    (project.findProperty("liquidglass.calibration") as String?)?.let { systemProperty("liquidglass.calibration", it) }
    (project.findProperty("liquidglass.wideClamp") as String?)?.let { systemProperty("liquidglass.wideClamp", it) }
    (project.findProperty("liquidglass.restMap") as String?)?.let { systemProperty("liquidglass.restMap", it) }
}
