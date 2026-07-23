import java.io.File
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

extra["sideloadPropertyPrefix"] = "ankientertainer"
apply(from = rootProject.file("../android/sideload-signing.gradle.kts"))

val autoVersionCode: Int = extra["autoVersionCode"] as Int
val useCustomSigning: Boolean = extra["useCustomSigning"] as Boolean

android {
    namespace = "com.tepmex.ankientertainer"
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
        applicationId = "com.tepmex.ankientertainer"
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

    buildFeatures {
        viewBinding = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val roomVersion = "2.7.1"

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.2.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("androidx.room:room-testing:$roomVersion")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
