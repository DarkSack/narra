import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Llave de subida a Play: keystore.properties (fuera de git) apunta al .jks. Sin ese archivo, el
// bundle de Play sale sin firmar y Play Console lo rechaza.
val playKey: Properties? = rootProject.file("keystore.properties").takeIf { it.exists() }
    ?.let { file -> Properties().apply { file.inputStream().use(::load) } }

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "app.narra"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.narra"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "3.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (playKey != null) {
            create("play") {
                storeFile = file(playKey.getProperty("storeFile"))
                storePassword = playKey.getProperty("storePassword")
                keyAlias = playKey.getProperty("keyAlias")
                keyPassword = playKey.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    // Cada canal trae su propio AppUpdater: Play prohíbe que una app suya se actualice por fuera
    // de la tienda (y el permiso para instalar APK), así que el autoactualizador va solo en "interna".
    flavorDimensions += "canal"
    productFlavors {
        create("interna") {
            dimension = "canal"
            // Misma llave que firmó la 3.0.0 repartida a mano: si cambia, la actualización no se instala.
            signingConfig = signingConfigs.getByName("debug")
        }
        create("play") {
            dimension = "canal"
            signingConfig = signingConfigs.findByName("play")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        // Robolectric necesita los recursos de la app (fuentes, colores) para dibujar pantallas.
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        // Tablas de algoritmos post-cuánticos de Bouncy Castle (unos 8 MB). PdfBox usa Bouncy
        // Castle para descifrar PDF protegidos, que nunca emplean estos algoritmos.
        resources.excludes += "org/bouncycastle/pqc/crypto/picnic/*.properties"
        resources.excludes += "org/bouncycastle/pqc/crypto/sike/*.properties"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.animation.ExperimentalSharedTransitionApi",
            "androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "kotlinx.coroutines.ExperimentalCoroutinesApi",
        )
        // Las anotaciones de inyección en parámetros de constructor se aplican también al campo.
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.navigation.suite)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.adaptive)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.coil.compose)
    implementation(libs.androidx.palette)
    implementation(libs.androidx.documentfile)

    implementation(libs.pdfbox.android)
    implementation(libs.mlkit.text.recognition)

    "playImplementation"(libs.play.app.update)
    "playImplementation"(libs.play.billing)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Capturas para la ficha de Play (StoreScreenshots): pantallas reales dibujadas con Robolectric.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.compose.ui.test.accessibility)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

apply(from = rootProject.file("gradle/robolectric-offline.gradle.kts"))

// Capturas para la ficha de Play: solo corren con -Pcapturas=<carpeta>.
tasks.withType<Test>().configureEach {
    systemProperty("narra.capturas", providers.gradleProperty("capturas").getOrElse(""))
    systemProperty("narra.tema", providers.gradleProperty("tema").getOrElse(""))
}
