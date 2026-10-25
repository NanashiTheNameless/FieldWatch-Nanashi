import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

/**
 * Seconds since 2026-01-01T00:00:00Z, so every build installs over the previous one.
 * Fits in a signed Int until 2094-01-19. A ValueSource is read at execution time,
 * so a reused configuration cache does not freeze the value.
 */
abstract class BuildVersionCode : ValueSource<Int, ValueSourceParameters.None> {
    override fun obtain(): Int {
        val epoch = 1_767_225_600L
        val seconds = System.currentTimeMillis() / 1000L - epoch
        return seconds.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
    }
}

val buildVersionCode = providers.of(BuildVersionCode::class) {}

// CI passes -PversionSuffix=<sha7>; local builds stay "localbuild".
val versionSuffix = providers.gradleProperty("versionSuffix").orNull?.takeIf { it.isNotBlank() } ?: "localbuild"

/**
 * Release signing: environment first (CI), then a properties file next to the repo
 * (never inside it). Missing values build an unsigned release.
 */
val localSigning = Properties().apply {
    val text = providers.fileContents(
        rootProject.layout.projectDirectory.file("../fieldwatch-nanashi-keystore.properties"),
    ).asText.orNull
    if (text != null) load(text.reader())
}

fun signingValue(envName: String, propertyKey: String): String? =
    providers.environmentVariable(envName).orNull?.takeIf { it.isNotBlank() }
        ?: localSigning.getProperty(propertyKey)?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("KEYSTORE_FILE", "storeFile")?.let { file(it) }
val releaseStorePassword = signingValue("KEYSTORE_PASSWORD", "storePassword")
val releaseKeyAlias = signingValue("KEY_ALIAS", "keyAlias")
val releaseKeyPassword = signingValue("KEY_PASSWORD", "keyPassword")
val hasReleaseSigning = releaseStoreFile?.isFile == true &&
    releaseStorePassword != null && releaseKeyAlias != null && releaseKeyPassword != null

android {
    namespace = "dev.namelessnanashi.fieldwatch"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.namelessnanashi.fieldwatch"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.2.0-$versionSuffix"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }
    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { it.versionCode.set(buildVersionCode) }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_25
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-service:2.11.0")
    implementation("androidx.navigation:navigation-compose:2.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
    // Elevated access: Shizuku shell service; the Vector (libxposed) API is provided by the framework.
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    compileOnly("io.github.libxposed:api:102.0.0")
    // App side of the Vector module: connection status, scope requests, per-hook switches.
    implementation("io.github.libxposed:service:102.0.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    // Real org.json on the JVM test classpath; the android.jar bundled JSONObject is a stub.
    testImplementation("org.json:json:20260814")
}
