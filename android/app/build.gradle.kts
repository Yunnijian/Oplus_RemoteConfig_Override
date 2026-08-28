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
    // 锁定 material3 稳定版 1.4.0（BOM 2026.08.00 也是 1.4.0）。
    // 不锁的话，miuix 0.9.3 的 CMP material3 传递依赖会把 androidx material3 抬到
    // 1.5.0-alpha17 → 编译期(1.4.0)/运行期(1.5.0-alpha17) ABI 不一致 → AbstractMethodError
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")

    // Miuix (HyperOS design) — Android 单平台构件
    val miuix = "0.9.3"
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:$miuix")
    implementation("top.yukonga.miuix.kmp:miuix-preference-android:$miuix")
    implementation("top.yukonga.miuix.kmp:miuix-icons-android:$miuix")

    // AndroidLiquidGlass (backdrop) — 液态玻璃
    implementation("io.github.kyant0:backdrop:2.0.1")
    // Capsule 形状（backdrop 的 POM 把 shapes 声明为 runtime scope，编译期不可见，需显式声明）
    implementation("io.github.kyant0:shapes:1.2.1")

    // Material 取色（PaletteStyle/ColorSpec，供 Material 主题与取色屏使用）
    // 排除 material-kolor 的 CMP material3 传递依赖：它会把 androidx material3 抬到
    // 1.5.0-alpha17（atomic group 强制同版本）→ 编译期(1.4.0)/运行期(1.5.0-alpha17)
    // ABI 不一致 → AbstractMethodError。material-kolor 只用其 PaletteStyle/ColorSpec 枚举。
    implementation("com.materialkolor:material-kolor:5.0.0") {
        exclude(group = "org.jetbrains.compose.material3", module = "material3")
    }

    // 平板/大屏适配（Google 标准 WindowSizeClass，版本由 BOM 管理）
    implementation("androidx.compose.material3:material3-window-size-class")

    // Navigation3
    implementation("androidx.navigation3:navigation3-runtime:1.1.6")
    // NavDisplay（androidx.navigation3.ui）由 Miuix Navigation3 UI 提供（对齐 KernelSU 用法）
    implementation("top.yukonga.miuix.kmp:miuix-navigation3-ui-android:$miuix")

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
    // NOTE: AGP 9 内置 Kotlin 下 `kotlin("test")` 的变体属性（androidJvm/android）与
    // kotlin-test MPP 变体（jvm/standard-jvm）不匹配，junit 传递依赖不会进入测试编译类路径，
    // 导致 `kotlin.test.Test` 无法解析。显式声明 JVM 测试构件作为最小化修复。
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.4.10")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.4.10")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
