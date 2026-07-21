plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.chrono.ssh"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.chrono.ssh"
        minSdk = 26
        targetSdk = 35
        versionCode = 20260766
        versionName = "0.1.43-dev.20260720"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "IS_LITE", "false")

        externalNativeBuild {
            cmake {}
        }

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        getByName("debug") {
            isDebuggable = true
        }
        create("sidecar") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".sidecar"
            versionNameSuffix = "-sidecar"
            matchingFallbacks += listOf("debug")
        }
    }

    flavorDimensions += "edition"
    productFlavors {
        create("full") {
            dimension = "edition"
        }
        create("lite") {
            dimension = "edition"
            applicationIdSuffix = ".lite"
            versionNameSuffix = "-lite"
            buildConfigField("boolean", "IS_LITE", "true")
            resValue("string", "app_name", "chronoSSH Lite")
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = true
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

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.8.2")

    implementation(platform("androidx.compose:compose-bom:2024.02.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.hierynomus:sshj:0.40.0")
    implementation("com.hierynomus:smbj:0.13.0")
    implementation("com.google.zxing:core:3.5.4")
    implementation("net.java.dev.jna:jna:5.14.0@aar")
    implementation("org.bouncycastle:bcprov-jdk18on:1.83")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.80")
    implementation("org.bouncycastle:bcutil-jdk18on:1.80.2")
    implementation("sh.haven:et-transport:0.1.0")
    implementation("sh.haven:ssp-transport:0.1.0")
    implementation(files("libs/rcbridge-bindings.jar"))
    implementation(files("libs/terminal-emulator-prebuilt.jar"))
    implementation(files("libs/terminal-view-prebuilt.jar"))

    testImplementation("junit:junit:4.13.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

configurations.configureEach {
    resolutionStrategy.eachDependency {
        when (requested.group) {
            "androidx.compose.animation" -> useVersion("1.7.0")
            "androidx.compose.material3" -> useVersion("1.3.0")
            "org.jetbrains.kotlin" -> if (requested.name.startsWith("kotlin-stdlib")) {
                useVersion("1.9.0")
            }
        }
        if (requested.group == "androidx.compose.ui" && requested.name == "ui-unit") {
            useVersion("1.7.0")
        }
        if (requested.group == "androidx.compose.ui" &&
            requested.name in setOf("ui-util", "ui-geometry")
        ) {
            useVersion("1.7.0")
        }
        if (requested.group == "org.jetbrains.kotlinx" &&
            requested.name in setOf("kotlinx-coroutines-core", "kotlinx-coroutines-core-jvm", "kotlinx-coroutines-android")
        ) {
            useVersion("1.8.1")
        }
    }
}
