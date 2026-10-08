# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project status

"MAD Currency Converter" is a single-module Android app (`:app`, package `com.elabboubisolution.madconverter`) built with Jetpack Compose + Material 3. MAD = Moroccan Dirham. V1 (spec phases 1–6) is done: API, conversion logic, UI, offline cache and the AdMob test banner. V1.5 is in progress. Its Phase 7 adds 11 currencies, a searchable picker and favorites.

How the data flows:
- **`data/remote`**: `ErApiRateProvider` calls ExchangeRate-API's open endpoint (`open.er-api.com/v6/latest/MAD`, no key) through Retrofit and turns every failure into a typed `RateFetchError`. Errors arrive as HTTP 200 with `"result":"error"`, so check the body as well as the status. Rates are parsed straight to `BigDecimal`.
- **`data/local/RateCache`**: stores the last successful snapshot in DataStore as JSON, with rates kept as strings. Corrupt data reads as an empty cache.
- **`data/repository/CachingCurrencyRepository`**: serves the cache as current until the provider's `nextUpdateEpochSeconds`, then downloads. It never downloads more than once an hour, which the provider requires. If a download fails, it returns the cache with `isStale = true`, and the UI must then show `StaleRateBanner`.
- **`domain/model/Currency`**: the enum order is the picker order. Names and minor-unit digits come from the platform's ISO/CLDR data via `java.util.Currency`, so there are no string resources for currency names. Only the short `symbol` is curated. To add a currency, add an entry, and only if ExchangeRate-API publishes it.
- **`domain/CurrencySearch`**: pure-JVM picker search over code, localized name and English name. It ignores case and accents and returns favorites first.
- **`data/local/FavoritesStore`**: favorites live in a separate DataStore (`user_preferences`). The defaults are MAD/EUR/USD until the user changes them.
- **Partial rate sets**: the provider omits currencies missing from a response instead of failing. The repository refreshes a cache that lacks supported currencies, still at most once an hour, without marking it stale.
- **`domain/QuickConversions`** (Phase 8): converts the main amount into the user's **favorites only**.
  - The source and the current target are excluded, with at most 3 results, in picker (`Currency` declaration) order.
  - There is deliberately no user-defined order, so don't change favorites persistence to add one.
  - It reuses `CurrencyConverter.convert` with the same snapshot as the main result, and is derived in `withDerivedFields`. It must **never** trigger an exchange-rate request or a DataStore write.
  - The section is hidden when there is no result or no eligible favorite.
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

## Conversion history and Copy/Share (Phase 9)

- History is entirely local, in its own DataStore file (`conversion_history`, a JSON list via `DataStoreHistoryStore`). It is never uploaded, never sent to analytics, AdMob or the rate provider. It only leaves the device when the user shares through Android's share sheet.
- A conversion is recorded **only** by an explicit Copy or Share of the main result (`onResultCopied` / `onResultShared`). Typing, swapping, currency picking, Quick Conversions, favorite toggles, rate refreshes and recomposition must never create history.
- Deduplication rule (`domain/ConversionHistory`): a new entry is skipped when the most recent entry has the same pair, amount, converted amount and rate, and is less than `DEDUP_WINDOW_MILLIS` (10 min) old. History keeps at most `MAX_ENTRIES` (50), dropping the oldest.
- Copy/Share must never trigger an exchange-rate refresh. They work offline, and stale rates are recorded with `wasStale = true`.
- Restoring an entry restores source, target and amount, then recalculates with the current rate snapshot. Stored converted amounts and rates are historical and must never be presented as current rates.
- Copy/Share text comes from `ui/format/ConversionText`, which the screen also uses, so the copied text matches the display.
- On Android 13+ the system shows its own copy confirmation, so the "Conversion copied" Snackbar is only shown below API 33.

## Real Cost (Phase 10)

- Real Cost (`domain/RealCost`, `ui/components/RealCostSheet`) is an **estimate**, not an actual bank/card quotation. The UI says "Estimated bank/card fee" / "Estimated total" and shows a disclaimer.
- The fee is the converted target amount × percentage / 100, rounded to the target currency's minor units (0 for JPY). The total is the converted amount + estimated fee. It uses exact `BigDecimal` arithmetic (never Float/Double), on the displayed converted amount, so the displayed amounts add up.
- Presets are 0%, 1%, 2%, 3% and Custom. Custom accepts `,` or `.` with up to 2 decimals. `RealCost.MAX_FEE_PERCENT` is 20%.
- It always reuses the existing primary conversion result (`ConverterUiState.realCost` is derived in `withDerivedFields`). It must never trigger an exchange-rate refresh or request.
- The fee preference stays local (`FeePreferenceStore`, key `real_cost_fee_percent` in the `user_preferences` DataStore). A preset saves immediately. A custom value is saved only when the sheet closes, never while typing.
- Real Cost does not currently take part in Copy/Share or conversion history. History still records the plain primary conversion.

## Numeric format (Phase 11)

- Every displayed number uses ONE format in English, French and Arabic alike: Western digits 0–9, `.` decimal, `,` thousands. Never use locale digits or separators. This covers the main result, rate line, Quick Conversions, Real Cost, History, Copy/Share, dates/times and error messages.
- Percentages are always the number then `%` with no space, at most 2 decimals: `0%`, `2%`, `2.75%` (never `2,75 %` or `٪`).
- All number formatting goes through `ui/format/Formatting.kt` (`formatDecimal`, `formatTypedAmount`, `formatPercent`, `PERCENT_SIGN`) and `ConversionText`, which take no `Locale`. Platform dates/times keep localized words but pass through `asciiDigits`.
- The source amount keeps exactly the decimals the user entered (`formatTypedAmount` uses the `BigDecimal` scale): `1250.50` → `1,250.50`, `1250.00` → `1,250.00`, `1250.5` → `1,250.5`, `1250` → `1,250`. Parsing, conversion and history storage (`toPlainString`) all keep that scale. Copy/Share and History show the same text.
- Target amounts keep their existing rounding: half-up to the currency's minor units (`fractionDigits`, 0 for JPY), independent of the typed decimals. Rates show 4 decimals.
- Editable fields (amount, custom fee) are never reformatted while typing. They show exactly what was typed. Input accepts `,` or `.` as the decimal separator and Arabic-keyboard digits (`normalizeNumericInput`); only the displayed results use the unified format.
- Arabic is RTL. Wrap displayed amounts, rates and "A → B" expressions in `ltr(...)` (Unicode LTR isolate) so they keep their reading order. Display only: never in Copy/Share text or stored values.

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
- `ui/theme/Theme.kt` applies the brand light/dark schemes from `Color.kt`. Wallpaper-based dynamic color is **off** by default (`dynamicColor = false`), so the app looks the same on every device.

### Branding colors (Phase 12.1)

- Approved references: deep green `#123B35`, teal `#168579`, gold `#D5A64C`, light background `#F5F7F4`. Deep green and the background are used as-is (light `onPrimaryContainer`/`background`, dark `primaryContainer`).
- The raw teal and gold are too light for text on the light background, so text-bearing roles use deeper variants: light `primary` `#0E6B61`, light `tertiary`/`warning` `#7A5A14`. Dark mode uses lighter variants.
- Every color role is set explicitly in both schemes (including surface containers), so no Material template purple can leak through. Don't use raw `Color(...)` in components; use theme roles.
- Contrast (WCAG 2.1 AA): text ≥ 4.5:1, icons/outlines/indicators ≥ 3:1, on the backgrounds the components actually use. `ThemeContrastTest` enforces this. Add a pair there when a component puts a new color on a new background.
- Warning colors: Material 3 has no warning role, so `ExtendedColors` (`MaterialTheme.extendedColors.warning`, `onWarning`, `warningContainer`, `onWarningContainer`, gold) marks stale exchange-rate information (`StaleRateBanner`, History "stale rate" note). Errors use the M3 `error`/`errorContainer` roles. Text buttons on these containers use the matching `on…Container` color, not `primary`.

### Startup window (Phase 12.2)

- The native window theme (`Theme.MADCurrencyConverter`) is shown before Compose draws its first frame and is the Android 12+ splash background. It follows the system dark mode: `values/themes.xml` (parent `android:Theme.Material.Light.NoActionBar`) and `values-night/themes.xml` (parent `android:Theme.Material.NoActionBar`).
- `android:windowBackground` = `@color/window_background`: `#F5F7F4` (`values/colors.xml`) / `#0E1513` (`values-night/colors.xml`). These must equal the Compose `LightSurface`/`DarkSurface`; `WindowThemeTest` checks it. Change both together.
- Startup system bars: light mode `windowLightStatusBar`/`windowLightNavigationBar` = `true` (dark icons), dark mode `false` (light icons). Once running, `MainActivity.enableEdgeToEdge()` manages the bars.
- No splash library or extra splash screen; no new dependencies. Verified with cold starts (process killed, frame captures): no white flash in dark mode (English and Arabic), and startup time is unchanged vs. the previous light-only theme.

## Known issues

- **Intermittent, not reproduced (open):** this happened twice during emulator testing (Phases 4 and 6), each time right after launch while typing into the amount field:
  - the amount unexpectedly became `1001000` after typing `1000`;
  - the currencies unexpectedly switched to USD → MAD.

  In Phase 4 the "To" dropdown arrow was already in its expanded state before any scripted input.

  In Phase 7 the `ExposedDropdownMenuBox` selector was replaced by `CurrencySelectorField` + `CurrencyPickerSheet`. Eight controlled runs, with `getevent` recording and no external input, were clean. Five more controlled runs in each of Phases 8, 9 and 10 were also clean. It remains non-reproducible and must not be marked as fixed.

  Three controlled runs with `adb shell getevent -lt` recording real device input did not reproduce it. Separately, one unexplained real touch on the emulator window was recorded during another test. The user was not intentionally interacting with the emulator. It is **not fixed**. If it recurs in a controlled run with no external input events, investigate it as an app bug. Start with the currency selection/swap logic in `CurrencyConverterViewModel` and the picker sheet.
- **Emulator test scripting:** `adb shell input text` typed right after a tap can drop the first characters, because the IME is not yet connected. Wait for `dumpsys input_method` to report `mInputShown=true` before typing. Also note that `adb input` events never appear in `getevent`; only real device or emulator-window input does.
