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

// Opt-in local preserva os arquivos no computador; o build remoto não empacota music/.
val useLocalMusic = providers.gradleProperty("useLocalMusic").map { it.toBoolean() }.getOrElse(false)

android {
    namespace = "br.com.radioembarcada"
    compileSdk = 35
    defaultConfig {
        applicationId = "br.com.radioembarcada"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "0.5.0"
        buildConfigField("String", "JAMENDO_CLIENT_ID", JsonOutput.toJson(jamendoClientId))
        buildConfigField("boolean", "USE_LOCAL_MUSIC", useLocalMusic.toString())
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    if (!useLocalMusic) sourceSets.getByName("main").assets.setSrcDirs(
        listOf(layout.buildDirectory.dir("generated/remoteAssets")))
    // Permite ler metadados via AssetFileDescriptor e buscar posições sem descompactar MP3.
    androidResources { noCompress += listOf("mp3", "MP3", "Mp3", "mP3") }
}
if (!useLocalMusic) {
    val remoteAssets = tasks.register<Sync>("prepareRemoteAssets") {
        from("src/main/assets")
        exclude("music/**")
        includeEmptyDirs = false
        into(layout.buildDirectory.dir("generated/remoteAssets"))
    }
    tasks.named("preBuild").configure { dependsOn(remoteAssets) }
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
