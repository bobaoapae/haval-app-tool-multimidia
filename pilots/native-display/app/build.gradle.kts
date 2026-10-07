plugins { id("com.android.application") }

android {
    namespace = "br.com.redesurftank.displaypilot"
    compileSdk = 36
    defaultConfig {
        applicationId = "br.com.redesurftank.displaypilot"
        minSdk = 28
        targetSdk = 28 // Deliberate Android 9 head-unit pilot, not a Play Store release.
        versionCode = 1
        versionName = "0.1.0-pilot"
    }
    buildTypes {
        named("debug") {
            // Never generate a signing credential implicitly. An unsigned APK is NOT installable.
            signingConfig = null
        }
        named("release") { signingConfig = null }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    sourceSets { getByName("test").java.srcDir("src/robolectric/java") }
    testOptions { unitTests.isIncludeAndroidResources = true }
    lint {
        abortOnError = true
        disable += "ExpiredTargetSdkVersion" // Dedicated API 28 bench/parked-car application.
    }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
}
