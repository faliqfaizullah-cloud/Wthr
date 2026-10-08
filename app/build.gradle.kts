plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.wthr.app"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.wthr.app"
        minSdk = 26; targetSdk = 34
        versionCode = 1; versionName = "1.0.0"
    }
    buildTypes { release { isMinifyEnabled = false; signingConfig = signingConfigs.getByName("debug") } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17"; freeCompilerArgs += listOf("-opt-in=androidx.compose.foundation.ExperimentalFoundationApi", "-opt-in=androidx.compose.animation.ExperimentalAnimationApi", "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi") }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
}
dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(bom)
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
}
