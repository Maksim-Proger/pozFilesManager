plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.poz.files"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.poz.files"
        minSdk = 28
        // Почему 28: приложениям, «нацеленным» на Android 9, система по-прежнему
        // даёт обычный доступ ко всем файлам после одного разрешения «Файлы».
        // Для новых версий нужен отдельный «Доступ ко всем файлам», которого
        // на Google TV в настройках может не быть.
        targetSdk = 28
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            // R8 выкидывает неиспользуемый код: из огромного набора значков
            // и библиотек в APK остаётся только то, что реально вызывается
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Подписываем тем же ключом, что и debug: ставится на приставку как есть
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

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            // Служебные файлы из библиотек, которые мешают сборке и не нужны в APK
            excludes += setOf("META-INF/versions/**", "META-INF/*.md", "META-INF/LICENSE*", "META-INF/NOTICE*")
        }
    }

    lint {
        disable += "ExpiredTargetSdkVersion"
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    // Compose for TV: кнопки, строки и переключатели под пульт
    implementation("androidx.tv:tv-material:1.0.0")
    // Набор значков Material (папка, видео, музыка, USB...). R8 оставит только нужные
    implementation("androidx.compose.material:material-icons-extended")

    // Плеер Google Media3: видео и музыка с диска, DLNA и SMB
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")

    // SMB — общие папки Windows и NAS
    implementation("eu.agno3.jcifs:jcifs-ng:2.1.10")

    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
