import java.util.Properties
import groovy.json.JsonOutput

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Valores locais nunca são gravados em fontes versionadas; o campo gerado fica em build/.
val localConfiguration = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val jamendoClientId = providers.environmentVariable("JAMENDO_CLIENT_ID").orNull
    ?.takeIf { it.isNotBlank() } ?: localConfiguration.getProperty("jamendo.clientId", "")


android {
    namespace = "br.com.radioembarcada"
    compileSdk = 35
    defaultConfig {
        applicationId = "br.com.radioembarcada"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.1"
        buildConfigField("String", "JAMENDO_CLIENT_ID", JsonOutput.toJson(jamendoClientId))
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.media3:media3-exoplayer:1.6.1")
    implementation("androidx.media3:media3-session:1.6.1")
    implementation("androidx.media3:media3-datasource:1.6.1")
    implementation("androidx.media3:media3-database:1.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("io.coil-kt:coil-compose:2.7.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
