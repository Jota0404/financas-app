import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

android {
    namespace = "com.joaobarcelos.financas"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.joaobarcelos.financas"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        // Os testes no aparelho usam o Hilt com banco em memória (HiltTestRunner)
        testInstrumentationRunner = "com.joaobarcelos.financas.HiltTestRunner"
    }

    // Assinatura do APK final. A chave e a senha ficam fora do Git, em keystore.properties (na raiz do
    // projeto, ignorado pelo Git). Sem esse arquivo, como no GitHub Actions, o release sai sem assinatura.
    val assinatura = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { arquivo ->
        Properties().apply { arquivo.inputStream().use(::load) }
    }
    signingConfigs {
        if (assinatura != null) {
            create("release") {
                storeFile = file(assinatura.getProperty("storeFile"))
                storePassword = assinatura.getProperty("storePassword")
                keyAlias = assinatura.getProperty("keyAlias")
                keyPassword = assinatura.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (assinatura != null) signingConfig = signingConfigs.getByName("release")
            // O R8 otimiza o app inteiro. Limitar a alguns pacotes (packageScope) fazia a versão final
            // travar ao abrir (IllegalAccessError numa classe do Kotlin), visto no emulador na Etapa 7.
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        // Robolectric: os testes do Room e do DataStore rodam no computador, sem aparelho
        unitTests.isIncludeAndroidResources = true
        // O Robolectric simula o Android 16+ acessando uma parte interna do Java
        unitTests.all { it.jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED") }
    }
    // Os esquemas do banco servem de base para o teste da migração. Os testes com Robolectric leem os
    // arquivos da versão debug; a versão de publicação (release) não leva os esquemas.
    sourceSets.getByName("debug").assets.directories.add("$projectDir/schemas")
}

room {
    // Esquema do banco versionado no Git, para escrever as migrações das próximas versões
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":domain"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.androidx.room.testing)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    // O Compose traz o Espresso 3.5, que não funciona com o Android 17 simulado pelo Robolectric
    testImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.androidx.test.rules)
    kspAndroidTest(libs.hilt.compiler)
    // O Compose traz o Espresso 3.5, que não funciona no Android 17
    androidTestImplementation(libs.androidx.test.espresso.core)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}