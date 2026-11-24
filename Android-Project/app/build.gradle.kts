plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val appVersionCode = 2
val appVersionName = "1.1.0"

android {
    namespace = "com.sumanonline.sumanmovies"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sumanonline.sumanmovies"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "TMDB_API_KEY", "\"\"")
        buildConfigField("String", "TMDB_BASE_URL", "\"https://proxy.tmdb.sumanonline.com/\"")
        buildConfigField("String", "BACKEND_BASE_URL", "\"https://api.moviebox.sumanonline.com\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
    implementation(libs.media3.datasource)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.database)
    implementation(libs.media3.ffmpeg.decoder)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.mockito:mockito-core:5.14.2")

    implementation(libs.coil.compose)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.ui.tooling)
}

tasks.register<Copy>("copyReleaseApk") {
    dependsOn("assembleRelease")
    from("${layout.buildDirectory.get()}/outputs/apk/release/app-release-unsigned.apk")
    into(file("${rootDir}/../apk"))
    rename { "SumanMovies-v${appVersionName}-release-unsigned.apk" }
}

tasks.register<Copy>("copyDebugApk") {
    dependsOn("assembleDebug")
    from("${layout.buildDirectory.get()}/outputs/apk/debug/app-debug.apk")
    into(file("${rootDir}/../apk"))
    rename { "SumanMovies-v${appVersionName}-debug.apk" }
}

tasks.register("copyAllApks") {
    dependsOn("copyReleaseApk", "copyDebugApk")
}




























