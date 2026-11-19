import com.google.gms.googleservices.GoogleServicesPlugin.GoogleServicesPluginConfig
import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import com.google.gms.googleservices.GoogleServicesTask

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val appGoogleServices = file("google-services.json")
val mockGoogleServices = file("src/mock/google-services.json")
val mockUsesEmulator = providers.gradleProperty("lorem.mockUseEmulator").orNull?.toBooleanStrict() ?: false
val mockFirebaseConfigured = !mockUsesEmulator && mockGoogleServices.isFile
if (appGoogleServices.isFile || mockFirebaseConfigured) {
    apply(plugin = "com.google.gms.google-services")
    extensions.configure<GoogleServicesPluginConfig> {
        missingGoogleServicesStrategy = MissingGoogleServicesStrategy.IGNORE
    }
    androidComponents.onVariants(androidComponents.selector().withBuildType("mock")) {
        tasks.named<GoogleServicesTask>("processMockGoogleServices") {
            // Keep the importer from falling back to the training app's Firebase project.
            googleServicesJsonFiles.set(if (mockFirebaseConfigured) listOf(mockGoogleServices) else emptyList())
            if (!mockFirebaseConfigured) {
                doFirst {
                    outputDirectory.get().asFile.deleteRecursively()
                    gmpAppId.get().asFile.delete()
                }
            }
        }
    }
}

android {
    namespace = "dev.lorem.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.lorem.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("boolean", "FIREBASE_AUTH_ENABLED",
            providers.gradleProperty("lorem.firebaseAuthEnabled").orNull?.toBooleanStrict()?.toString() ?: "true")

        buildConfigField("boolean", "VIRTUAL_DUEL_ENABLED",
            providers.gradleProperty("lorem.virtualDuelEnabled").orNull?.toBooleanStrict()?.toString() ?: "true")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        create("mock") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".mock"
            versionNameSuffix = "-mock"
            matchingFallbacks += "debug"
            buildConfigField("boolean", "MOCK_USE_EMULATOR", mockUsesEmulator.toString())
        }
    }
    sourceSets.getByName("testMock") {
        resources.srcDir(rootProject.file("tools/ipsum-mock"))
        resources.include("ipsums-200-exemplo.xlsx")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Switching the opt-in must rerun SDK tests rather than reuse a previously skipped result.
            it.inputs.property("loremFirebaseEmulators", providers.environmentVariable("LOREM_FIREBASE_EMULATORS").orElse("false"))
            it.inputs.property("loremAuthEmulatorHost", providers.environmentVariable("FIREBASE_AUTH_EMULATOR_HOST").orElse(""))
            it.inputs.property("loremFirestoreEmulatorHost", providers.environmentVariable("FIRESTORE_EMULATOR_HOST").orElse(""))
            it.inputs.property("loremMockEmulator", providers.environmentVariable("LOREM_MOCK_EMULATOR").orElse("false"))
            it.inputs.property("loremMockCloudEmulators", providers.environmentVariable("LOREM_MOCK_CLOUD_EMULATORS").orElse("false"))
            it.systemProperty(
                "robolectric.dependency.repo.url",
                "https://repo.maven.apache.org/maven2",
            )
        }
    }
}

dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    // Firestore supplies Guava at runtime; WorkManager's ListenableFuture API also needs it at compile time.
    implementation(libs.guava)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.google.id)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.work.testing)
}
