import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

dependencies {
    implementation(project(":liquidglass"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    // One screen converts stock Material 3 components. The library itself never needs this.
    implementation(libs.compose.material3)
    // The glass Material 3 components and the showcase screen shared with desktop and web.
    implementation(project(":liquidglass-material3"))
    implementation(libs.compose.material.icons.core)
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation(libs.kotlin.test)
}

android {
    namespace = "com.wexpa.liquidglass.sample"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    // Material 3 1.13 alphas pull in Compose foundation that needs Android API 37.1 to compile.
    compileSdkMinor = libs.versions.android.compileSdkMinor.get().toInt()

    defaultConfig {
        applicationId = "com.wexpa.liquidglass.sample"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = providers.gradleProperty("VERSION_NAME").get()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    // Optional isolated qualification install; leaves the developer's existing sample intact.
    if (providers.gradleProperty("sample.qualification").orNull == "true") {
        buildTypes.getByName("debug").applicationIdSuffix = ".qualification"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    buildTypes {
        // A release build installable from the command line, for frame timing on a phone: debug builds
        // run without ahead-of-time compilation, so their first-use frames say little about an app's.
        release {
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}
