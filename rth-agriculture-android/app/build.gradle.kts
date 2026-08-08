import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

extra["sideloadPropertyPrefix"] = "rthagricultureandroid"
apply(from = rootProject.file("../android/sideload-signing.gradle.kts"))

val autoVersionCode: Int = extra["autoVersionCode"] as Int
val useCustomSigning: Boolean = extra["useCustomSigning"] as Boolean

android {
    namespace = "com.tepmex.rthagriculture"
    compileSdk = 35

    signingConfigs {
        if (useCustomSigning) {
            create("sideload") {
                storeFile = extra["sideloadStoreFile"] as File
                storePassword = extra["sideloadStorePassword"] as String
                keyAlias = extra["sideloadKeyAlias"] as String
                keyPassword = extra["sideloadKeyPassword"] as String
            }
        }
    }

    defaultConfig {
        applicationId = "com.tepmex.rthagriculture"
        minSdk = 34
        targetSdk = 35
        versionCode = autoVersionCode
        versionName = "1.0.$autoVersionCode"
    }

    buildTypes {
        debug {
            signingConfig = if (useCustomSigning) {
                signingConfigs.getByName("sideload")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (useCustomSigning) {
                signingConfigs.getByName("sideload")
            } else {
                signingConfigs.getByName("debug")
            }
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
        viewBinding = true
    }

    // Bundled hanzi JSON + map art can be large; keep packaging quiet.
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("androidx.activity:activity-ktx:1.9.3")
}

val wwwIndex = layout.projectDirectory.file("src/main/assets/www/index.html")
tasks.register("verifyWebAssets") {
    doLast {
        if (!wwwIndex.asFile.isFile) {
            throw GradleException(
                "Missing bundled game at ${wwwIndex.asFile}. Run ../scripts/sync-web-assets.sh first.",
            )
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn("verifyWebAssets")
}
