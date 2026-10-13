import com.android.build.api.variant.BuildConfigField
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// AdMob IDs. Debug builds always use Google's official *test* IDs
// (https://developers.google.com/admob/android/test-ads). Release builds use the production IDs,
// which are never committed: the ADMOB_APP_ID / ADMOB_BANNER_AD_UNIT_ID environment variables
// (CI), else admob.appId / admob.bannerAdUnitId in local.properties. validateReleaseAdMobIds
// fails any release build whose IDs are missing, malformed or Google's test IDs; its messages
// never contain the IDs themselves.
val admobTestAppId = "ca-app-pub-3940256099942544~3347511713"
val admobTestBannerAdUnitId = "ca-app-pub-3940256099942544/9214589741"

// local.properties is not committed (and absent on CI); read lazily, only when a value is needed.
val localProperties: Provider<Properties> = providers
    .fileContents(rootProject.layout.projectDirectory.file("local.properties")).asText
    .map { text -> Properties().apply { load(text.reader()) } }

fun localProperty(name: String): Provider<String> =
    localProperties.map { it.getProperty(name).orEmpty().trim() }.orElse("")

fun releaseSetting(environmentVariable: String, localPropertyName: String): Provider<String> =
    providers.environmentVariable(environmentVariable).map { it.trim() }
        .orElse(localProperty(localPropertyName))

val releaseAdMobAppId = releaseSetting("ADMOB_APP_ID", "admob.appId")
val releaseAdMobBannerAdUnitId = releaseSetting("ADMOB_BANNER_AD_UNIT_ID", "admob.bannerAdUnitId")

// Google UMP testing options (debug builds only; release builds always get empty values):
// ump.debugGeography = EEA | REGULATED_US_STATE | OTHER, ump.testDeviceIds = hashed IDs, comma-separated.
val umpDebugGeography = localProperty("ump.debugGeography")
val umpTestDeviceIds = localProperty("ump.testDeviceIds")

android {
    namespace = "com.elabboubisolution.madconverter"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "com.elabboubisolution.madconverter"
        minSdk = 24
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
        buildConfig = true
    }
}

// Per-variant AdMob and UMP debug values, as lazy providers: nothing is read or checked while
// configuring, so debug builds and IDE sync never need the production IDs.
androidComponents {
    onVariants { variant ->
        val release = variant.buildType == "release"
        // Values are restricted to ID/name characters, so they can never break the generated Java.
        val javaString = { value: String -> "\"" + value.replace(Regex("[^A-Za-z0-9_~/,.-]"), "") + "\"" }
        val stringField = { value: Provider<String> -> value.map { BuildConfigField("String", javaString(it), null) } }
        val none = provider { "" }

        variant.manifestPlaceholders.put(
            "admobAppId",
            if (release) releaseAdMobAppId else provider { admobTestAppId },
        )
        variant.buildConfigFields?.put(
            "ADMOB_BANNER_AD_UNIT_ID",
            stringField(if (release) releaseAdMobBannerAdUnitId else provider { admobTestBannerAdUnitId }),
        )
        variant.buildConfigFields?.put("UMP_DEBUG_GEOGRAPHY", stringField(if (release) none else umpDebugGeography))
        variant.buildConfigFields?.put("UMP_TEST_DEVICE_IDS", stringField(if (release) none else umpTestDeviceIds))
    }
}

val validateReleaseAdMobIds = tasks.register("validateReleaseAdMobIds") {
    group = "verification"
    description = "Fails if the release AdMob IDs are missing, malformed or Google's test IDs."
    val appId = releaseAdMobAppId
    val bannerAdUnitId = releaseAdMobBannerAdUnitId
    doLast {
        val testPublisher = "3940256099942544"
        val problems = mutableListOf<String>()
        // Returns the publisher (account) part of a valid ID. Messages never include the value.
        fun check(value: String, format: Regex, name: String, source: String, example: String): String? {
            val match = format.matchEntire(value)
            when {
                value.isEmpty() -> problems += "$name is missing. Set $source."
                match == null -> problems += "$name is malformed: expected $example ($source)."
                match.groupValues[1] == testPublisher ->
                    problems += "$name is one of Google's test IDs; release builds need the production ID ($source)."
                else -> return match.groupValues[1]
            }
            return null
        }
        val appPublisher = check(
            appId.get(), Regex("""ca-app-pub-(\d{16})~\d{10}"""), "AdMob App ID",
            "the ADMOB_APP_ID environment variable or admob.appId in local.properties",
            "ca-app-pub-<16 digits>~<10 digits>",
        )
        val bannerPublisher = check(
            bannerAdUnitId.get(), Regex("""ca-app-pub-(\d{16})/\d{10}"""), "AdMob banner ad unit ID",
            "the ADMOB_BANNER_AD_UNIT_ID environment variable or admob.bannerAdUnitId in local.properties",
            "ca-app-pub-<16 digits>/<10 digits>",
        )
        if (appPublisher != null && bannerPublisher != null && appPublisher != bannerPublisher) {
            problems += "The AdMob App ID and the banner ad unit ID belong to different AdMob accounts."
        }
        if (problems.isNotEmpty()) {
            throw GradleException(
                "Release AdMob configuration is invalid:\n" + problems.joinToString("\n") { " - $it" },
            )
        }
    }
}

// Every release build (APK or bundle) goes through preReleaseBuild, before the manifest and
// BuildConfig receive the IDs.
tasks.configureEach {
    if (name == "preReleaseBuild") dependsOn(validateReleaseAdMobIds)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.play.services.ads)
    implementation(libs.ump)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
