import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// A fixed signing identity, committed with the project. Android refuses to
// install an update whose certificate differs from the installed app, and a CI
// runner has no ~/.android/debug.keystore, so AGP would mint a NEW key on every
// job. Every CI artifact would then need an uninstall to install, taking the
// Room user list and DataStore settings with it. One shared key, no uninstalls.
val keystoreProps = Properties().apply {
    rootProject.file("keystore/keystore.properties").inputStream().use { load(it) }
}

android {
    namespace = "com.example.hotspotportal"
    // compileSdk 35 (not 34): Google's repository has withdrawn the base
    // `platform-34_r0X.zip` artifacts, so API 34 is no longer obtainable.
    // Compiling against 35 with targetSdk 34 is fully supported and keeps
    // the exact runtime behaviour the spec targets.
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.hotspotportal"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("portal") {
            storeFile = rootProject.file("keystore/${keystoreProps.getProperty("storeFile")}")
            storePassword = keystoreProps.getProperty("storePassword")
            keyAlias = keystoreProps.getProperty("keyAlias")
            keyPassword = keystoreProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("portal")
        }
        debug {
            isMinifyEnabled = false
            // Same key as release so a debug build can always replace a release
            // install (and vice versa) without an uninstall.
            signingConfig = signingConfigs.getByName("portal")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/{AL2.0,LGPL2.1}",
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE*",
        )
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.libsu.core)
    implementation(libs.nanohttpd)
    implementation(libs.jbcrypt)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
