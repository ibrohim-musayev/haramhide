plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.haramhide.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.haramhide.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0-F0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    /**
     * ABI ajratish.
     *
     * ONNX Runtime har bir ABI uchun ~32-38 MB native kutubxona olib keladi.
     * To'rttasi birga 131 MB — universal APK 145 MB bo'lib chiqadi va bu
     * TZ 6.2 dagi chegaradan (45 MB) uch barobar oshadi.
     *
     * x86 va x86_64 faqat emulyatorda kerak, haqiqiy Android telefonlarda
     * amalda uchramaydi. Shuning uchun faqat ARM qoladi va har bir ABI uchun
     * alohida APK yig'iladi. F-Droid ham, GitHub Releases ham bunday
     * tarqatishni qo'llab-quvvatlaydi.
     *
     * DIQQAT: bu sozlama BARCHA variantlarga tegishli, debug ham ajratiladi.
     * Ya'ni `app-debug.apk` yo'q — `app-arm64-v8a-debug.apk` bor.
     * Emulyator arm64 bo'lgani uchun aynan shu o'rnatiladi.
     *
     * x86_64 emulyatorda ishlash kerak bo'lsa, `include` ga uni qo'shing.
     */
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }

    // F-Droid reproducible build uchun
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core-capture"))
    implementation(project(":core-detect"))
    implementation(project(":core-overlay"))
    implementation(project(":core-context"))
    implementation(project(":core-data"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}

// TZ 10.2 — maxfiylik tekshiruvi `check` ga ulanadi
apply(from = rootProject.file("gradle/privacy-check.gradle.kts"))
