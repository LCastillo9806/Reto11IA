import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Leemos la llave de Gemini desde local.properties (archivo que NO se sube a GitHub)
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val geminiApiKey: String = localProperties.getProperty("GEMINI_API_KEY", "").trim()

android {
    namespace = "co.edu.unal.reto11ia"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "co.edu.unal.reto11ia"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // La llave queda disponible en el código como BuildConfig.GEMINI_API_KEY
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true  // necesario para BuildConfig.GEMINI_API_KEY
    }
}

dependencies {

    // ==========================================
    // DEPENDENCIAS BASE DE ANDROID
    // ==========================================
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.material)

    // ==========================================
    // RETO 11: INTEGRACIÓN CON IA GENERATIVA
    // ==========================================

    // Fragments: una pantalla para Chat, otra para Texto y otra para Imagen
    implementation(libs.androidx.fragment.ktx)

    // ViewModel + corrutinas: llamar a Gemini sin congelar la app y
    // conservar la conversación al girar la pantalla
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    // RecyclerView: la lista de mensajes (burbujas) del chat
    implementation(libs.androidx.recyclerview)

    // Ajustes: llave de API, modelo y personalidad del chatbot
    implementation(libs.androidx.preference.ktx)

    // Gemini se llama directamente por su API REST (HttpURLConnection + org.json),
    // así que no hace falta ninguna librería extra de Google.

    // ==========================================
    // PRUEBAS
    // ==========================================
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
