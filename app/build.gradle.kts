import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

android {
    namespace = "com.edgegesture.evilgodxu"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.edgegesture.evilgodxu"
        minSdk = 34
        targetSdk = 37
        versionCode = 18
        versionName = "5.1.1"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("../jh.keystore")
            storePassword = localProperties.getProperty("KEYSTORE_PASSWORD", "")
            keyAlias = localProperties.getProperty("KEY_ALIAS", "jh")
            keyPassword = localProperties.getProperty("KEY_PASSWORD", "")
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
            signingConfig = signingConfigs.getByName("release")
        }
    }
    androidResources {
        // 保留打包语言，支持应用内语言切换
        localeFilters += listOf("zh", "en")
    }

    packaging {
        resources {
            excludes += setOf(
                // Kotlin 模块元数据文件（每个 Kotlin 库都会生成，必然重复）
                "META-INF/*.kotlin_module",
                // Kotlin 协程调试探针
                "META-INF/DebugProbesKt.bin",
                "DebugProbesKt.bin",
                // 常见的重复许可证文件
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/LICENSE.md",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/NOTICE.md",
                // 版本控制索引文件
                "META-INF/INDEX.LIST",
                // 第三方库签名文件
                "META-INF/*.SF",
                "META-INF/*.DSA",
                "META-INF/*.RSA",
                // AndroidX 版本属性文件（各库独立包含，无需打包）
                "META-INF/*.version",
                "META-INF/androidx/**",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        aidl = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

// 构建产物统一命名为 EdgeGesture-<versionName>-arm64.apk
val apkVersionName = android.defaultConfig.versionName ?: "0.0.0"

androidComponents {
    onVariants(selector().all()) { variant ->
        variant.outputs.forEach { output ->
            output.outputFileName.set("EdgeGesture-$apkVersionName-arm64.apk")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.datastore.core)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.window)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.layout)

    // Koin 依赖注入
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.core)
    implementation(libs.kotlinx.coroutines.guava)

    // Kotlin Serialization
    implementation(libs.kotlinx.serialization.json)

    // Shizuku
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    // Hidden API Bypass（用于调用 setLaunchWindowingMode 等小窗隐藏 API）
    implementation(libs.hidden.api.bypass)

    // Media3 ExoPlayer — 多格式音频播放引擎
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)

    // 屏幕翻译网络请求
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
