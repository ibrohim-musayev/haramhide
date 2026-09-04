plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.haramhide.core.detect"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // NudeNet .onnx formatida keladi — konversiya qilmaslik F1 ni bloklamaslikning
    // eng ishonchli yo'li (ADR-001).
    api(libs.onnxruntime.android)

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}

dependencies {
    api(project(":core-capture"))
}
