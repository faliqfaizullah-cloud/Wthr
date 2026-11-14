plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.wthr.app"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.wthr.app"
        minSdk = 26; targetSdk = 34
        versionCode = (project.findProperty("wthrVersionCode") as String?)?.toInt() ?: 1
        versionName = (project.findProperty("wthrVersionName") as String?) ?: "1.0.0"
    }
    signingConfigs {
        create("release") {
            storeFile = rootProject.file("wthr-release.p12"); storePassword = "wthrRelease2026"
            keyAlias = "wthr"; keyPassword = "wthrRelease2026"; storeType = "pkcs12"
        }
    }
    buildTypes { release { isMinifyEnabled = false; signingConfig = signingConfigs.getByName("release") } }
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
    implementation("androidx.work:work-runtime-ktx:2.9.0")
}
