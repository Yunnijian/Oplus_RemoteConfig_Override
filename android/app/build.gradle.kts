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
        // minSdk 33：KernelSU 悬浮底栏/液态玻璃用 miuix-blur（要求 API 33+），完整对齐 KernelSU
        minSdk = 33
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
    // material3 显式声明 1.5.0-alpha26（对齐 KernelSU libs.versions.toml）。
    // 必须显式声明并高于任何传递依赖带来的 alpha 版本：CMP material3（miuix / material-kolor
    // 的传递依赖）会把 androidx material3 抬到 1.5.0-alpha17（atomic group 强制同版本）；
    // 冲突解析取最高版本 → alpha26 同时是编译期与运行期版本，ABI 一致（不再 AbstractMethodError）。
    implementation("androidx.compose.material3:material3:1.5.0-alpha26")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")

    // Miuix (HyperOS design) — Android 单平台构件
    val miuix = "0.9.3"
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:$miuix")
    implementation("top.yukonga.miuix.kmp:miuix-preference-android:$miuix")
    implementation("top.yukonga.miuix.kmp:miuix-icons-android:$miuix")
    // miuix-blur：KernelSU 悬浮底栏/液态玻璃依赖（minSdk 33，R0 已提升）——替代自研 backdrop 方案
    implementation("top.yukonga.miuix.kmp:miuix-blur-android:$miuix")

    // Material 取色（PaletteStyle/ColorSpec，供 Material 主题与取色屏使用）
    // 对齐 KernelSU：不排除任何传递依赖。其 CMP material3 会解析出 androidx material3
    // 1.5.0-alpha17，低于本模块显式声明的 alpha26，冲突解析统一取 alpha26。
    implementation("com.materialkolor:material-kolor:5.0.0")

    // 平板/大屏适配（Google 标准 WindowSizeClass，版本由 BOM 管理）
    implementation("androidx.compose.material3:material3-window-size-class")

    // Navigation3
    implementation("androidx.navigation3:navigation3-runtime:1.1.6")
    // NavDisplay（androidx.navigation3.ui）由 Miuix Navigation3 UI 提供（对齐 KernelSU 用法）
    implementation("top.yukonga.miuix.kmp:miuix-navigation3-ui-android:$miuix")
    // 搜索栏展开时接管系统返回键（KernelSU SuperSearchBar 的 NavigationBackHandler，版本对齐）
    implementation("androidx.navigationevent:navigationevent-compose:1.1.2")

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
