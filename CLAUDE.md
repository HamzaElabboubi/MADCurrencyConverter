# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project status

"MAD Currency Converter" is a single-module Android app (`:app`, package `com.elabboubisolution.madconverter`) built with Jetpack Compose + Material 3. MAD = Moroccan Dirham. The project is currently the stock Android Studio "Empty Activity" template: `MainActivity` renders a placeholder `Greeting` composable, and there is no converter logic, networking, persistence, ViewModel, or DI yet. The only tests are the template `ExampleUnitTest` / `ExampleInstrumentedTest`.

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

- **AGP 9.x with built-in Kotlin**: `app/build.gradle.kts` applies only `com.android.application` and `org.jetbrains.kotlin.plugin.compose` — there is no separate `org.jetbrains.kotlin.android` plugin. Don't add one.
- `compileSdk` uses the new AGP DSL (`release(36) { minorApiLevel = 1 }`); `minSdk 24`, `targetSdk 36`, Java/Kotlin target 11. Gradle daemon JVM is pinned to 21 via `gradle/gradle-daemon-jvm.properties` (foojay toolchain resolver).
- All dependency/plugin versions live in the version catalog `gradle/libs.versions.toml`; reference them as `libs.*` in build scripts. Compose library versions come from the Compose BOM (no per-artifact versions).
- `settings.gradle.kts` uses `RepositoriesMode.FAIL_ON_PROJECT_REPOS` — add repositories there, not in module build files.
- Adding network access (e.g. for exchange-rate APIs) will require the `INTERNET` permission in `app/src/main/AndroidManifest.xml`, which is not declared yet.

## UI / theming

- Single-activity Compose app: `MainActivity` calls `enableEdgeToEdge()` and wraps content in `MADCurrencyConverterTheme` + `Scaffold`; pass `innerPadding` to content.
- `ui/theme/Theme.kt` uses dynamic color on Android 12+ and falls back to the static `Color.kt` schemes otherwise, so custom palette changes in `Color.kt` won't be visible on 12+ devices unless `dynamicColor = false`.
