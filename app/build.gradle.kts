plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val uploadKeystore: File? = System.getenv("CATPAW_UPLOAD_KEYSTORE")?.let { file(it) }?.takeIf { it.isFile }

android {
    namespace = "io.github.yolanda1027.catpaw"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.yolanda1027.catpaw"
        minSdk = 31
        targetSdk = 36
        // CI 每次建置的編號都會往上加，手機才能直接「更新」而不用先解除安裝
        // 正式版（release.yml）用 CATPAW_VERSION_CODE，每次上傳 Google Play 都要比上一次大
        versionCode = (System.getenv("CATPAW_VERSION_CODE") ?: System.getenv("GITHUB_RUN_NUMBER"))?.toIntOrNull() ?: 1
        versionName = "0.49.2"
    }

    signingConfigs {
        // 固定的測試用簽章：每次建出來的 APK 簽章一樣，才能直接覆蓋安裝
        getByName("debug") {
            storeFile = rootProject.file("keystore/catpaw-debug.jks")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // 正式版：上傳 Google Play 用的「上傳金鑰」。金鑰不放進 repo，從環境變數讀：
        //   CATPAW_UPLOAD_KEYSTORE = .p12 檔的路徑
        //   CATPAW_UPLOAD_PASSWORD = 密碼（金鑰庫和金鑰共用同一組）
        // GitHub Actions 的 release.yml 會從 repo 的 Secrets 拿出來放好。
        if (uploadKeystore != null) create("upload") {
            storeFile = uploadKeystore
            storeType = "pkcs12"
            storePassword = System.getenv("CATPAW_UPLOAD_PASSWORD")
            keyAlias = "upload"
            keyPassword = System.getenv("CATPAW_UPLOAD_PASSWORD")
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
            // 測試版是另一個 App（io.github.yolanda1027.catpaw.dev）：可以跟 Google Play 的正式版同時安裝。
            // 名字、圖示顏色在 src/debug/res 裡另外設定。
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
        }
        release {
            isMinifyEnabled = false
            // 沒有上傳金鑰就不簽（建出來的檔案 Google Play 不收，見下面的檢查）。
            // 絕對不要用公開的測試簽章簽正式版。
            signingConfig = signingConfigs.findByName("upload")
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

// 打正式版（bundleRelease／assembleRelease）卻沒有上傳金鑰：直接停下來說清楚，不要產出沒簽章的檔案
gradle.taskGraph.whenReady {
    val wantsRelease = allTasks.any { it.path == ":app:bundleRelease" || it.path == ":app:assembleRelease" }
    if (wantsRelease && (uploadKeystore == null || System.getenv("CATPAW_UPLOAD_PASSWORD").isNullOrEmpty())) {
        throw GradleException("正式版需要上傳金鑰：請設定 CATPAW_UPLOAD_KEYSTORE（.p12 路徑）和 CATPAW_UPLOAD_PASSWORD")
    }
}

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
