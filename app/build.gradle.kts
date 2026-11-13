plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.tachimetro"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.tachimetro"
        minSdk = 26 // Android 8.0: il minimo per il Picture-in-Picture
        targetSdk = 35
        // Su GitHub Actions ogni compilazione ha un numero più alto,
        // così il telefono accetta l'aggiornamento sopra la versione precedente.
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1.0." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Chiave di firma facoltativa, presa dai "segreti" di GitHub (mai scritta nel codice).
        // Se c'è, ogni nuovo APK ha la stessa firma e si installa sopra quello vecchio.
        val percorsoChiave = System.getenv("TACHIMETRO_KEYSTORE")
        if (!percorsoChiave.isNullOrBlank()) {
            create("tachimetro") {
                storeFile = file(percorsoChiave)
                storePassword = System.getenv("TACHIMETRO_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("TACHIMETRO_KEY_ALIAS")
                keyPassword = System.getenv("TACHIMETRO_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Senza chiave nei segreti si usa la chiave "debug" provvisoria creata da Android.
            signingConfig = signingConfigs.findByName("tachimetro") ?: signingConfigs.getByName("debug")
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
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Servizi di localizzazione di Google (GPS "fuso" con Wi-Fi e rete)
    implementation("com.google.android.gms:play-services-location:21.3.0")

    testImplementation("junit:junit:4.13.2")
    // Nei test sul computer serve la vera libreria JSON (quella di Android lì è vuota)
    testImplementation("org.json:json:20231013")
}
