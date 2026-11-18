plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.sumanonline.sumanmovies"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sumanonline.sumanmovies"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

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

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation("junit:junit:4.13.2")

    implementation(libs.coil.compose)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.ui.tooling)
}

tasks.register<Copy>("copyNewPlayerApk") {
    dependsOn("assembleDebug")
    from(layout.buildDirectory.file("outputs/apk/debug/app-debug.apk"))
    into(rootProject.file("../apk"))
    rename { "SumanMovies-v1.0.0-new-player.apk" }
}

tasks.register<Copy>("copyDebugApk") {
    dependsOn("assembleDebug")
    from(layout.buildDirectory.file("outputs/apk/debug/app-debug.apk"))
    into(rootProject.file("../apk"))
    rename { "SumanMovies-v1.0.0-debug.apk" }
}

tasks.register("deployAndPushNewPlayerApk") {
    dependsOn("assembleDebug")
    doLast {
        val root = rootProject.file("..")
        val apkSrc = layout.buildDirectory.file("outputs/apk/debug/app-debug.apk").get().asFile
        val apkDest = root.resolve("apk/SumanMovies-v1.0.0-new-player.apk")
        val apkDebug = root.resolve("apk/SumanMovies-v1.0.0-debug.apk")
        apkDest.parentFile.mkdirs()
        apkSrc.copyTo(apkDest, overwrite = true)
        apkSrc.copyTo(apkDebug, overwrite = true)

        fun runCmd(vararg args: String): String {
            val p = ProcessBuilder(*args).directory(root).start()
            val out = p.inputStream.bufferedReader().readText()
            val err = p.errorStream.bufferedReader().readText()
            p.waitFor()
            return "$out\n$err".trim()
        }
        val addRes = runCmd("git", "add", "-A")
        val commitRes = runCmd("git", "commit", "-m", "release: add SumanMovies-v1.0.0-new-player.apk")
        val pushRes = runCmd("git", "push", "origin", "main")
        root.resolve("scratch_git.txt").writeText("ADD:\n$addRes\nCOMMIT:\n$commitRes\nPUSH:\n$pushRes")
    }
}













