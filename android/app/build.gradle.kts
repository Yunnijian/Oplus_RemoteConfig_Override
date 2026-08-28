plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.parcelize")
}

android {
    namespace = "com.remoteconfig.override"
    compileSdk {
        // Compose BOM 2026.08.00 (ui 1.12.0) / material-kolor 5.0.0 require minCompileSdk 37.
        version = release(37) {}
    }

    defaultConfig {
        applicationId = "com.remoteconfig.override"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.2.1"
        // The target system SQLite library is the 64-bit /system/lib64/libsqlite.so.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs {
            // libcosa.so is an executable command launched through the root shell.
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/*"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")
    implementation(composeBom)

    // Compose UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")

    // Miuix (HyperOS design) — Android 单平台构件
    val miuix = "0.9.3"
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:$miuix")
    implementation("top.yukonga.miuix.kmp:miuix-preference-android:$miuix")
    implementation("top.yukonga.miuix.kmp:miuix-icons-android:$miuix")

    // AndroidLiquidGlass (backdrop) — 液态玻璃
    implementation("io.github.kyant0:backdrop:2.0.1")

    // Material 取色（PaletteStyle/ColorSpec，供 Material 主题与取色屏使用）
    implementation("com.materialkolor:material-kolor:5.0.0")

    // 平板/大屏适配（Google 标准 WindowSizeClass）
    implementation("androidx.compose.material3:material3-window-size-class")

    // Navigation3
    implementation("androidx.navigation3:navigation3-runtime:1.1.6")

    // TODO(Task 4): 移除 —— 旧 NavGraph.kt 仍使用 Navigation Compose，
    // 在迁移到 Navigation3 (NavDisplay + NavKey) 前暂时保留以维持可编译基线。
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Activity & Lifecycle
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")

    // Kotlinx Serialization (JSON parsing)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    // libsu (Root access)
    implementation("com.github.topjohnwu.libsu:core:6.0.0")

    // Core
    implementation("androidx.core:core-ktx:1.12.0")

    // Unit tests
    testImplementation(kotlin("test"))

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
