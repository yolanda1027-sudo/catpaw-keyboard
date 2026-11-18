plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.yolanda1027.catpaw"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.yolanda1027.catpaw"
        minSdk = 31
        targetSdk = 34
        // CI 每次建置的編號都會往上加，手機才能直接「更新」而不用先解除安裝
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "0.44.1"
    }

    signingConfigs {
        // 固定的測試用簽章：每次建出來的 APK 簽章一樣，才能直接覆蓋安裝
        getByName("debug") {
            storeFile = rootProject.file("keystore/catpaw-debug.jks")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

// 開放原始碼授權：打包時把 repo 的 third_party/（授權全文）複製到 App 的 assets/licenses/，
// 設定裡的「開放原始碼授權」頁就是讀這裡。授權檔只要維護 third_party/ 這一份。
val copyLicenses = tasks.register<Copy>("copyLicenses") {
    from(rootProject.file("third_party"))
    into(layout.buildDirectory.dir("generated/licenseAssets/licenses"))
}
android.sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/licenseAssets"))
tasks.named("preBuild") { dependsOn(copyLicenses) }

android {
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty(
                "robolectric.dependency.repo.url",
                "https://maven-central.storage-download.googleapis.com/maven2",
            )
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
