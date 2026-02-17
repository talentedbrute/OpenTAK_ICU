import java.util.Properties
import java.io.FileInputStream

// Create a variable called keystorePropertiesFile, and initialize it to your
// keystore.properties file, in the rootProject folder.
val keystorePropertiesFile = file("../../keystore.properties")

// Initialize a new Properties() object called keystoreProperties.
val keystoreProperties = Properties()

// Load your keystore.properties file into the keystoreProperties object.
keystoreProperties.load(FileInputStream(keystorePropertiesFile))

plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
//    id("io.github.reactivecircus.app-versioning") version "1.3.2"
}

android {
    namespace = "io.opentakserver.opentakicu"
    compileSdk = 35

    lint {
        abortOnError = false
    }

    defaultConfig {
        applicationId = "io.opentakserver.opentakicu"
        minSdk = 32
        targetSdk = 34
        versionCode = 1
        versionName = "1.5.3.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    packaging {
        resources.excludes.apply {
            add("META-INF/**")
        }
    }

    signingConfigs {
        create("release") {
            keyAlias = keystoreProperties["RELEASE_KEY_ALIAS"] as String
            keyPassword = keystoreProperties["RELEASE_KEY_PASSWORD"] as String
            storeFile = file("../../android_keystore")
            storePassword = keystoreProperties["RELEASE_STORE_PASSWORD"] as String
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }

        getByName("debug") {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
    }
    buildFeatures {
        viewBinding = true
    }

    applicationVariants.all {
        val variant = this
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            output.outputFileName = "OpenTAK-ICU-${variant.versionName}-${variant.buildType.name}.apk"
        }
    }
}

dependencies {

    implementation("androidx.appcompat:appcompat:1.7.1")
    //implementation("com.google.android.material:material:1.12.0")
    // Use 1.13.0-alpha08 because it adds orientation to the Slider
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.navigation:navigation-fragment:2.9.7")
    implementation("androidx.navigation:navigation-ui:2.9.7")
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation("com.github.pedroSG94.RootEncoder:library:2.5.5")
    implementation("com.github.AppIntro:AppIntro:6.3.1")
    implementation("androidx.datastore:datastore-preferences:1.2.0")
    implementation("androidx.datastore:datastore-preferences-rxjava3:1.2.0")
    implementation("androidx.preference:preference:1.2.1")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.21.0")
    implementation("com.fasterxml.woodstox:woodstox-core:7.1.1")
    implementation("javax.xml.stream:stax-api:1.0-2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.sealwu:kscript-tools:1.0.22")
    implementation("com.google.firebase:firebase-analytics:23.0.0")
    implementation(platform("com.google.firebase:firebase-bom:34.9.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.github.topjohnwu.libsu:core:6.0.0")
    implementation("com.github.topjohnwu.libsu:nio:6.0.0")

    implementation("com.github.pedroSG94.RootEncoder:extra-sources:2.5.5")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    implementation("org.bouncycastle:bcprov-jdk15on:1.70")
    implementation("org.bouncycastle:bcpkix-jdk15on:1.70")

}