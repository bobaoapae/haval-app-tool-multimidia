import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val appVersionCode = providers.gradleProperty("appVersionCode")
    .map(String::toInt)
    .orElse(1)
val appVersionName = providers.gradleProperty("appVersionName")
    .orElse("0.0.1")
val impulseReportSupabaseUrl =
    providers.gradleProperty("impulseReportSupabaseUrl")
        .orElse("https://eymyarugcfcwkezqjiba.supabase.co")
val impulseReportSupabasePublishableKey =
    providers.gradleProperty("impulseReportSupabasePublishableKey")
        .orElse("sb_publishable_2fp6Nav76kCNXklyKtCnYA_ZiM3z5l8")
val impulseReportFunctionName =
    providers.gradleProperty("impulseReportFunctionName").orElse("impulse-report-problem")

fun buildConfigString(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "br.com.redesurftank.havalshisuku"
    compileSdk = 36

    defaultConfig {
        applicationId = "br.com.redesurftank.havalshisuku"
        minSdk = 28
        //noinspection ExpiredTargetSdkVersion
        targetSdk = 28
        versionCode = appVersionCode.get()
        versionName = appVersionName.get()
        buildConfigField("boolean", "EMBED_FRIDA_TOOLS", "true")
        buildConfigField(
            "String",
            "IMPULSE_REPORT_SUPABASE_URL",
            buildConfigString(impulseReportSupabaseUrl.get())
        )
        buildConfigField(
            "String",
            "IMPULSE_REPORT_SUPABASE_PUBLISHABLE_KEY",
            buildConfigString(impulseReportSupabasePublishableKey.get())
        )
        buildConfigField(
            "String",
            "IMPULSE_REPORT_FUNCTION_NAME",
            buildConfigString(impulseReportFunctionName.get())
        )
    }

    signingConfigs {
        create("release") {
            storeFile = file("release.keystore")
            storePassword = System.getenv("SIGNING_STORE_PASSWORD")
            keyAlias = System.getenv("SIGNING_KEY_ALIAS")
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
        }
    }

    buildTypes {
        named("debug") {
            buildConfigField("boolean", "EMBED_FRIDA_TOOLS", "true")
        }
        create("leanDebug") {
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug")
            buildConfigField("boolean", "EMBED_FRIDA_TOOLS", "false")
        }
        named("release") {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources  = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("boolean", "EMBED_FRIDA_TOOLS", "true")
        }
    }

    sourceSets {
        getByName("debug") {
            java.srcDir("src/internalDebug/java")
        }
        getByName("leanDebug") {
            java.srcDir("src/internalDebug/java")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        aidl = true
        buildConfig = true
        compose = true
    }
}

kotlin {
    jvmToolchain(11)
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.core.ktx)
    implementation(libs.material)
    implementation(libs.shizuku)
    implementation(libs.shizuku.provider)
    implementation(libs.hiddenapibypass)
    implementation(libs.commons.net)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.service)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.material3)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.gson)
    implementation(libs.coil.compose)
    implementation(libs.material.icons.extended)
    annotationProcessor(libs.annotation.processor)
    compileOnly(libs.annotation)
    testImplementation(libs.junit)
    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.ui.test.manifest)
}
