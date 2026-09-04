plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.haramhide.core.overlay"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // MaskStateMachine android.util.Log ishlatadi; unit testda u stub.
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}

dependencies {
    api(project(":core-detect"))
}
