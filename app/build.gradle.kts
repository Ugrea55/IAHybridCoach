plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // CLAUDE CODE: aplica el plugin de Google Services en este modulo. Lee
    // app/google-services.json y inyecta la configuracion de Firebase.
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.garuna.iahybridcoach"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.garuna.iahybridcoach"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // CLAUDE CODE: Firebase BoM. Al usar platform(...) no tenemos que poner
    // version a las librerias individuales de Firebase, la BoM las fija.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.firestore.ktx)

    // CLAUDE CODE: librerias para el login con Google via Credential Manager.
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    // CLAUDE CODE: ViewModel para Compose + corrutinas con Play Services.
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.play.services)

    // CLAUDE CODE: navegacion entre las tabs y set de iconos Material.
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material.icons.extended)

    // CLAUDE CODE: calendario para Compose.
    implementation(libs.kizitonwose.calendar.compose)

    // CLAUDE CODE: Health Connect (Google) para leer entrenos de wearables.
    implementation(libs.androidx.health.connect.client)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}