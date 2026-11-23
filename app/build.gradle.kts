plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// En CI se pasa -PversionCode=<número de ejecución> para que cada APK actualice al anterior.
val ciVersionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
// Fecha (epoch s) del commit compilado: sirve para filtrar las notas de versión.
val ciCommitTime = (project.findProperty("commitTime") as String?)?.toLongOrNull() ?: 0L

android {
    namespace = "com.pobla.cuaderno"
    // Compose 1.13 exige la API menor 37.1.
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.pobla.cuaderno"
        minSdk = 26
        targetSdk = 36
        versionCode = ciVersionCode
        versionName = "0.1.$ciVersionCode"
        buildConfigField("long", "COMMIT_TIME", "${ciCommitTime}L")
    }

    signingConfigs {
        // Keystore de depuración fija (en el repo) para que todas las compilaciones
        // de CI tengan la misma firma y se puedan instalar encima sin perder datos.
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
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
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    androidResources {
        // Genera locales_config para que Android 13+ permita elegir idioma solo para esta app.
        generateLocaleConfig = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.glance.appwidget)

    // Copia en la nube: Firebase (Auth + Firestore) y login con Google (Credential Manager)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
}
