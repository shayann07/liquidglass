import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    `maven-publish`
}

// Coordinates. The artifact id is deliberately the plain descriptive one for now; if this ever
// carries a product name of its own, change it here before the first publish and nowhere else.
group = "com.wexpa.liquidglass"
version = "0.1.0"

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
        }
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("liquidglass")
            description.set(
                "A refracting glass material for Compose Multiplatform, drawn entirely in a " +
                    "shader. Signed distance geometry, exact Snell refraction, dispersion and " +
                    "rim lighting, with a measured tab bar component.",
            )
            url.set("https://github.com/shayann07/liquidglass")
            licenses {
                license {
                    name.set("The Apache License, Version 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                }
            }
            developers {
                developer {
                    id.set("shayann07")
                    name.set("Shayan")
                }
            }
            scm {
                url.set("https://github.com/shayann07/liquidglass")
                connection.set("scm:git:https://github.com/shayann07/liquidglass.git")
            }
        }
    }
    repositories {
        // A local folder so `publishAllPublicationsToLocalRepository` produces something a host
        // project can consume today, without credentials. Add Maven Central here when the
        // account and signing keys exist.
        maven {
            name = "local"
            url = uri(rootProject.layout.buildDirectory.dir("maven"))
        }
    }
}
