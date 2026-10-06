# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project status

"MAD Currency Converter" is a single-module Android app (`:app`, package `com.elabboubisolution.madconverter`) built with Jetpack Compose + Material 3. MAD = Moroccan Dirham. Phases 1–5 of the spec are done: API, conversion logic, UI and offline cache. AdMob, polish and the README are still to come.

How the data flows:
- **`data/remote`**: `ErApiRateProvider` calls ExchangeRate-API's open endpoint (`open.er-api.com/v6/latest/MAD`, no key) through Retrofit and turns every failure into a typed `RateFetchError`. Errors arrive as HTTP 200 with `"result":"error"`, so check the body as well as the status. Rates are parsed straight to `BigDecimal`.
- **`data/local/RateCache`**: stores the last successful snapshot in DataStore as JSON, with rates kept as strings. Corrupt data reads as an empty cache.
- **`data/repository/CachingCurrencyRepository`**: serves the cache as current until the provider's `nextUpdateEpochSeconds`, then downloads. It never downloads more than once an hour, which the provider requires. If a download fails, it returns the cache with `isStale = true`, and the UI must then show `StaleRateBanner`.
- **`domain/CurrencyConverter`**: pure-JVM `BigDecimal` math. Cross rates are computed from the single MAD-based snapshot. It also parses amounts with `,` or `.` as the decimal separator.
- **`viewmodel/CurrencyConverterViewModel`**: exposes one `StateFlow<ConverterUiState>`. All derived fields are recomputed in `withDerivedFields`.
- **`ads/`**: holds all AdMob code and is referenced only from `MainActivity`.
  - The anchored adaptive `BannerAd` is the Scaffold `bottomBar`, so the content is padded by its height and never drawn under it. It takes no space until an ad loads.
  - `AdsInitializer` is the single place where UMP consent must be added before release.
  - IDs come from `app/build.gradle.kts`. They are Google's test IDs, and `AdConfigTest` fails if the banner ID changes.
- **UI**: lives in `ui/screens` and `ui/components`. Strings come in English (default) and French (`values-fr`). ExchangeRate-API's terms require the "Rates By Exchange Rate API" attribution link in `RateInfo`.

**`PROJECT_SPEC.md` is the source of truth for what to build.** Read it before starting work. Key constraints from it:

- V1 scope: convert MAD ↔ USD/EUR/GBP on one screen (amount, source/target selectors, swap, result, rate, last-updated time, AdMob banner). Keep adding currencies easy. Keep V1 small, with no backend, auth, analytics, or extra screens.
- Stack: MVVM with Retrofit, Coroutines/StateFlow, DataStore for caching, and Google Mobile Ads. Packages are roughly `data/{remote,local,repository}`, `domain/model`, `ui/{screens,components,theme}`, `viewmodel`. Avoid overengineering.
- Never hardcode rates. Put the rate provider behind an abstraction so it can be swapped. Handle offline, timeouts, API errors, invalid responses, and missing rates. Cached rates must be visibly labelled as possibly stale, never shown as live.
- AdMob: during development, always use Google's official **test** ad unit/app IDs. Keep ad config out of the conversion logic, and the app must keep working when an ad fails to load. Structure the code so UMP consent can be added before release. Don't use interstitials.
- Secrets go in `local.properties` → `BuildConfig`, never committed. Request no unnecessary permissions.
- Conversion math must be testable as plain JVM code without Android dependencies. Required tests: MAD→USD, USD→MAD, MAD→EUR, decimals, zero, invalid amount, missing rate.
- Work in the spec's phases (setup → API → logic → UI → caching → ads → tests → polish → README). After each phase, build, run tests, and check for deprecations. Don't claim a phase works without verifying it.

## Commands

Run from the repo root. On Windows use `./gradlew.bat` (or `./gradlew` from Git Bash).

```bash
./gradlew assembleDebug                 # build debug APK
./gradlew installDebug                  # install on connected device/emulator
./gradlew lint                          # Android lint
./gradlew testDebugUnitTest             # JVM unit tests (app/src/test)
./gradlew connectedDebugAndroidTest     # instrumented tests (app/src/androidTest), needs device/emulator

# Single unit test class / method
./gradlew testDebugUnitTest --tests "com.elabboubisolution.madconverter.ExampleUnitTest"
./gradlew testDebugUnitTest --tests "com.elabboubisolution.madconverter.ExampleUnitTest.addition_isCorrect"

# Single instrumented test class
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.elabboubisolution.madconverter.ExampleInstrumentedTest
```

## Build setup notes

- **AGP 9.x with built-in Kotlin**: `app/build.gradle.kts` applies `com.android.application` plus the Kotlin `compose` and `serialization` compiler plugins only — there is no separate `org.jetbrains.kotlin.android` plugin. Don't add one.
- AGP 9.4 requires Gradle ≥ 9.6 (wrapper is on 9.8.0). Current AndroidX/OkHttp releases require `compileSdk` ≥ 37, so it is `release(37) { minorApiLevel = 2 }`, while `targetSdk` stays 36 and `minSdk` is 24, with a Java/Kotlin target of 11. The Gradle daemon JVM is pinned to 21 via `gradle/gradle-daemon-jvm.properties`. From a shell, point `JAVA_HOME` at Android Studio's `jbr`.
- The AdMob App ID is injected into the manifest via `manifestPlaceholders["admobAppId"]` (Google's test ID by default). The Ads SDK crashes at startup if that meta-data is missing.
- DI is manual: `MadConverterApplication` creates one `AppContainer` (shared `Json`, `OkHttpClient` with timeouts). Wire new dependencies there, not through Hilt.
- All dependency/plugin versions live in the version catalog `gradle/libs.versions.toml`; reference them as `libs.*` in build scripts. Compose library versions come from the Compose BOM (no per-artifact versions).
- `settings.gradle.kts` uses `RepositoriesMode.FAIL_ON_PROJECT_REPOS` — add repositories there, not in module build files.
- The app itself declares only `INTERNET`. The Ads SDK merges in `ACCESS_NETWORK_STATE`, `AD_ID`, `ACCESS_ADSERVICES_*`, `WAKE_LOCK` and `FOREGROUND_SERVICE`. Check `app/build/intermediates/merged_manifest/` before claiming what the app requests. `AD_ID` must be declared in the Play Console.

## UI / theming

- Single-activity Compose app: `MainActivity` calls `enableEdgeToEdge()` and wraps content in `MADCurrencyConverterTheme` + `Scaffold`; pass `innerPadding` to content.
- `ui/theme/Theme.kt` uses dynamic color on Android 12+ and falls back to the static `Color.kt` schemes otherwise, so custom palette changes in `Color.kt` won't be visible on 12+ devices unless `dynamicColor = false`.
