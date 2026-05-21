import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val autoVersionCode = (System.currentTimeMillis() / 1000L).toInt()

val localProps = Properties()
val localPropsFile = rootProject.file("local.properties")
if (localPropsFile.exists()) {
    localPropsFile.inputStream().use { localProps.load(it) }
}

fun propLocal(name: String): String? =
    localProps.getProperty(name)?.trim()?.takeIf { it.isNotEmpty() }

val overrideStoreFile = propLocal("localtts.signingStoreFile")
val overrideStorePassword = propLocal("localtts.signingStorePassword")
val overrideKeyAlias = propLocal("localtts.signingKeyAlias")
val overrideKeyPassword = propLocal("localtts.signingKeyPassword")
val useOverrideSigning = overrideStoreFile != null &&
    overrideStorePassword != null &&
    overrideKeyAlias != null &&
    overrideKeyPassword != null

val sideloadProps = Properties()
val sideloadPropsFile = rootProject.file("sideload-signing.properties")
val sideloadKs = rootProject.file("sideload.keystore")
if (!useOverrideSigning && sideloadPropsFile.exists() && sideloadKs.exists()) {
    sideloadPropsFile.inputStream().use { sideloadProps.load(it) }
}

fun propSideload(name: String): String? =
    sideloadProps.getProperty(name)?.trim()?.takeIf { it.isNotEmpty() }

val useCommittedSideload = !useOverrideSigning &&
    sideloadKs.exists() &&
    propSideload("storeFile") != null &&
    propSideload("storePassword") != null &&
    propSideload("keyAlias") != null &&
    propSideload("keyPassword") != null

val useCustomSigning = useOverrideSigning || useCommittedSideload

android {
    namespace = "com.tepmex.localtts"
    compileSdk = 35

    signingConfigs {
        if (useCustomSigning) {
            create("sideload") {
                if (useOverrideSigning) {
                    storeFile = rootProject.file(overrideStoreFile!!)
                    storePassword = overrideStorePassword!!
                    keyAlias = overrideKeyAlias!!
                    keyPassword = overrideKeyPassword!!
                } else {
                    storeFile = rootProject.file(propSideload("storeFile")!!)
                    storePassword = propSideload("storePassword")!!
                    keyAlias = propSideload("keyAlias")!!
                    keyPassword = propSideload("keyPassword")!!
                }
            }
        }
    }

    defaultConfig {
        applicationId = "com.tepmex.localtts"
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
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.19.2")
}
