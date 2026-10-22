plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.appsharepc.scanner"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.appsharepc.scanner"
        minSdk = 26
        targetSdk = 35
        versionCode = 19
        versionName = "1.16.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    applicationVariants.all {
        val variant = this
        outputs.all {
            val output = this as? com.android.build.gradle.internal.api.BaseVariantOutputImpl
            output?.outputFileName = "APPSharePC-v${variant.versionName}-${variant.buildType.name}.apk"
        }
        assembleProvider.configure {
            doLast {
                val apkName = "APPSharePC-v${variant.versionName}-${variant.buildType.name}.apk"
                val builtApk = File(layout.buildDirectory.asFile.get(), "outputs/apk/${variant.buildType.name}/$apkName")
                if (builtApk.exists()) {
                    try {
                        builtApk.copyTo(File(rootDir, apkName), overwrite = true)
                        // Luôn copy sang D:/Apps/Filein/InPGH/PDFs
                        val appsFolder = File("D:/Apps/Filein/InPGH/PDFs")
                        if (!appsFolder.exists()) {
                            appsFolder.mkdirs()
                        }
                        builtApk.copyTo(File(appsFolder, apkName), overwrite = true)

                        val dropboxFolder = File("D:/Dropbox/Filein/InPGH/PDFs")
                        if (dropboxFolder.exists()) {
                            builtApk.copyTo(File(dropboxFolder, apkName), overwrite = true)
                        }
                    } catch (e: Exception) {
                        println("Warning copying APK: ${e.message}")
                    }
                }
            }
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/license.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
            excludes += "META-INF/notice.txt"
            excludes += "META-INF/ASL2.0"
            excludes += "META-INF/*.kotlin_module"
        }
    }
}

dependencies {
    // AndroidX & Lifecycle
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Jetpack Compose & Material 3
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // CameraX
    val cameraxVersion = "1.4.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // Coil for Compose image loading
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // SMB Client (SMBv2/v3 support)
    implementation("com.hierynomus:smbj:0.13.0")

    // DocumentFile for directory selection (OpenDocumentTree)
    implementation("androidx.documentfile:documentfile:1.0.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
