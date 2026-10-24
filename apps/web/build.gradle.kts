import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// The live demo: Atlas Studio (the same Loupe screen as the desktop and Android apps) and, at #material3,
// a Material 3 app built from liquidglass-material3. WebAssembly, drawn by the production shaders.
kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName = "atlas"
        browser {
            commonWebpackConfig { outputFileName = "atlas.js" }
        }
        binaries.executable()
    }
    sourceSets {
        val wasmJsMain by getting {
            dependencies {
                implementation(project(":liquidglass"))
                implementation(project(":liquidglass-material3"))
                implementation(libs.compose.material.icons.core)
                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.compose.ui)
            }
            kotlin.srcDir("../sample/src/main/kotlin/shared")
        }
    }
}

// Fills in the names index.html preloads: both WebAssembly downloads (Skia's runtime and the app) start
// with the page, not after atlas.js has been downloaded and run (#78). The names are content hashes, so
// they are known only after the bundle is written.
tasks.named("wasmJsBrowserDistribution") {
    val dist = layout.buildDirectory.dir("dist/wasmJs/productionExecutable")
    doLast {
        val dir = dist.get().asFile
        val page = dir.resolve("index.html")
        val names = dir.listFiles { f -> f.extension == "wasm" }.orEmpty().sortedByDescending { it.length() }
            .joinToString(", ", "[", "]") { "'${it.name}'" }
        check(names != "[]") { "No .wasm files in $dir to preload" }
        val html = page.readText()
        check("/* wasm-preload */[]" in html) { "index.html has no wasm-preload placeholder" }
        page.writeText(html.replace("/* wasm-preload */[]", names))
    }
}
