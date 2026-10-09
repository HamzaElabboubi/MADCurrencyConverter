# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project status

"Currency Converter" (formerly "MAD Currency Converter"; renamed in Phase 12.4) is a single-module Android app (`:app`, package `com.elabboubisolution.madconverter`) built with Jetpack Compose + Material 3. MAD = Moroccan Dirham. V1 (spec phases 1–6) is done: API, conversion logic, UI, offline cache and the AdMob test banner. V1.5 is in progress. Its Phase 7 adds 11 currencies, a searchable picker and favorites.

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

### Typography (Phase 12.3)

- `ui/theme/Type.kt` defines the Material 3 scale, system font only (no font files, so Arabic keeps platform shaping). Hierarchy: `displaySmall` 36sp Medium = main result (the focal point) > `headlineSmall` 24sp = typed amount > `titleLarge` 22sp Medium = screen/sheet titles > `titleMedium` 16sp Medium = result source line, exchange rate > `body*` supporting text. `bodySmall` (12sp) only where space is tight (selector currency names, attribution); disclaimers and warnings use `bodyMedium`. `TypographyTest` guards the hierarchy.
- Numeric values (typed amount, result, rate, Quick Conversions, Real Cost, History) use `style.tabularFigures()` (`fontFeatureSettings = "tnum"`). Roboto's digits are already tabular; it protects against system fonts with proportional digits. `TabularFiguresTest` (instrumented) checks equal digit widths on the device font.
- The main result is one line with `TextAutoSize.StepBased(min = 12.sp, max = displaySmall)`. It shrinks only when it doesn't fit, so a large result is never split inside the number. Ordinary amounts keep full accessibility font scaling (verified unchanged at 200%). The largest case (`≈ 15,851,512,000,000 JPY`) renders around 29dp.
- Real Cost `BreakdownRow`: the amount never wraps. It sits beside its label while the label keeps ≥ 120dp, otherwise it moves below the label, end-aligned (no letter-by-letter label breaks). History rows are never truncated (no `maxLines`/ellipsis), so amounts always show in full.
- Verified at 100/130/150/200% font scale, light/dark, English/French/Arabic RTL. Amounts stay inside `ltr(...)` isolates.

### Application Name and Top App Bar (Phase 12.4)

- Approved name: **Currency Converter**. It's `app_name` in `values/strings.xml`, `translatable="false"`: the English brand name is used unchanged in French and Arabic. `StringResourcesTest` checks it. It's the launcher label via the `<application>` label only (the activity has no label of its own). The application ID, package, namespace, DataStore file names and the Gradle project name (`rootProject.name`) are unchanged.
- `CurrencyConverterContent` starts with a pinned Material 3 small `TopAppBar` (`ConverterTopBar`, 64dp, `TopAppBarDefaults.pinnedScrollBehavior()` connected through `nestedScroll`). It never scrolls away. The content scrolls beneath it, and it tints from `surface` to `surfaceContainer` once content is under it. The title is one line, start-aligned (right in RTL), marked `heading()`.
- History has exactly one entry point: the top bar action (`IconButton`, 48dp touch target, localized `history_open` description). Don't add another. TalkBack order: title → History → amount → rest of the screen.
- Insets: `MainActivity`'s Scaffold uses `contentWindowInsets` without the top side, so the `TopAppBar` draws behind the status bar and applies the status-bar inset itself. Bottom/horizontal insets and the AdMob `bottomBar` are unchanged, and content never draws under the banner.
- Verified: English/French/Arabic, light/dark, 100/130/150/200% font scale (title not truncated at 200%), scrolling with the banner loaded (History stays reachable), launcher label. Existing features and AdMob are unchanged.

### Loading, warning and error states (Phase 12.5)

- Presentation only: the ViewModel, repository, caching, throttling, retry policy and calculations are unchanged.
- `ui/components/RatePanel.kt`: the pure `ratePanel(state, lastError)` picks exactly one rate-area state from `ConverterUiState`, so error and success are never shown together. `RatePanelTest` (JVM) covers it.
  - `InitialLoading`: first load with nothing to show.
  - `Unavailable(error, isRetrying)`: no rates at all (no cache, download failed). It never shows a result.
  - `Available`: live or cached rates, with `isStale`, `refreshError` (only when stale), `isRefreshing`, `missingRate` and `missingRateRetry`.
- Loading placeholder: `LoadingState` is a `surfaceContainerLow` surface about the result card's height (144dp min), so the screen doesn't jump when rates arrive. It is not a live region.
- Stable Retry: `onRetry` clears `error` in the ViewModel, so `ConversionSection` remembers the last error. The error card stays in place while retrying, and a progress indicator replaces the Retry button (48dp minimum area, `loading_rates` content description). The stale banner does the same while refreshing.
- Shared presentation: `StatusCard` (in `StatusMessages.kt`) lays out the icon, optional title, message, then Retry or the spinner. Title and message form one polite live region. The icon means the state never relies on color alone.
  - Error: `ErrorState`, `error`/`errorContainer` roles, `ic_error`, title `rates_unavailable_title` when no rates could be loaded.
  - Warning: `StaleRateBanner`, gold `extendedColors.warning*` roles, `ic_warning`. Cached rates stay usable.
  - Retry buttons and spinners use the container's `on…Container` color.
- Only one Retry on screen: when the stale banner and the missing-rate card both appear, only the banner offers Retry (`missingRateRetry`).
- Strings in English, French and Arabic. In RTL the icon and text mirror, and Retry stays at the end. Spinner wording ("Loading exchange rates…") does not claim a network download.
- Verified: 224 unit tests passing, clean build, lint 0 errors; six UI scenarios checked visually in English, French and Arabic.

### Component Spacing and Visual Consistency (Phase 12.6)

- Presentation only: no changes to calculations, API, caching, DataStore, History recording, favorites or AdMob.
- `ui/theme/Dimens.kt` holds the shared 16dp values: `EdgePadding` (screen and every sheet), `CardPadding` (inside cards) and `SectionSpacing` (between screen sections). Use them instead of new literal edge/padding values.
- One alignment line: main content, cards, the History/picker/Real Cost sheet titles, section headers, the search field and list rows all start at 16dp, matching the top bar title and Material `ListItem`. In the result card the "Real cost" label lines up with the amounts (the actions row starts 12dp further out, to offset the `TextButton`'s own padding). History's "Clear all" text sits on the 16dp end edge.
- Quick Conversions rows end with a decorative chevron (`ic_chevron_right`, `android:autoMirrored="true"`, so it points to the end and flips in RTL). It has no content description: the row still announces its "Make main conversion" action, and its click/semantics are unchanged. The chevron sits in an outer row and is measured first, so it is never squeezed out.
- Quick Conversion amounts never clip: the name gives way first, and only when the amount alone doesn't fit (e.g. `≈ 15,928,259,999,984` JPY at 200%) does it shrink on one line (`TextAutoSize.StepBased`, min 12sp).
- Real Cost total: it sits in a `primaryContainer` box (same pairing as the result card), with a `titleMedium` label and a `titleLarge` amount, baseline-aligned. The converted/fee lines above it stay plain, with `onSurfaceVariant` labels. The Phase 12.3 rule is kept (the amount moves below its label when the label would drop under 120dp). In that stacked layout the amount shrinks on one line if even a full line is too narrow, so the total is never clipped.
- `DirectionalIconsTest` checks that the chevron stays auto-mirrored.
- Verified: clean build, 225 unit tests passing, lint 0 errors (4 existing version warnings), no Kotlin warnings. Before/after checks on the emulator: English (light, 100% and 200%), Arabic RTL (dark, 200%), French (light, 150%), with normal and very large amounts. No clipped labels or amounts, rows keep their 48dp minimum height, nothing draws under the top bar or the AdMob banner. Live TalkBack testing is still deferred to Phase 12.8.

## Currency Pair Persistence (Phase 12.7)

- `data/local/CurrencyPairStore` (`DataStoreCurrencyPairStore`) keeps the last source and target in the existing `user_preferences` DataStore, as `last_from_currency` / `last_to_currency`, always written together in one atomic edit. File name and other keys (favorites, fee) are untouched.
- Fresh installs (nothing saved) start with MAD → USD. A pair is restored only whole: a missing, unsupported or malformed code, or the same code twice, reads as nothing saved, and the default stays. `CurrencyPair` itself rejects identical currencies, so an invalid pair can never be saved.
- Persisted changes: source/target selection (including the swap when picking the other side's currency), Swap, History restore and choosing a Quick Conversion target. All go through `changePair` in `CurrencyConverterViewModel`, applied in one state update so no intermediate pair is shown or saved. Typing, favorites, fees, Retry and the startup restore never write.
- The ViewModel's `StateFlow` is the single source of truth. The store is read once at startup and written by one writer:
  - A late read never overrides the user: the first user change sets `pairChangedByUser`, and a saved pair arriving after that is discarded.
  - Writes are serialized: the latest pair goes into a conflated `StateFlow` (`pairToSave`) collected by one coroutine. A newer pair waits for the running write, so an older pair can never land last. A failed write (`IOException`) keeps the pair in memory; the next change saves again.
  - Startup never writes the default over a saved pair (`pairToSave` starts null).
- Startup: `MainActivity` calls `setContent` only after `awaitPairRestored(500ms)`, so the first frame already shows the saved pair. Until then a pre-draw listener keeps the native branded startup window (Phase 12.2 light/dark background, and the splash on Android 12+) on screen. After a rotation the retained ViewModel is already restored and content is set synchronously. `isPairRestored` is set in a `finally`, so the wait always ends. An `IOException` while reading falls back to the default pair.
- Tests: `CurrencyPairStoreTest` (real DataStore: restart, last write wins, invalid/partial/identical codes, read failure, favorites/fee/unknown keys preserved) and `CurrencyConverterViewModelTest` (every persisted action, no writes from other actions, late read vs user change, rapid changes, slow write vs newer pair, failed write, process recreation, and the startup wait on virtual time: immediate, fast read, exactly 500ms limit then late restore, read failure, rotation). The race tests were checked against broken variants (no guard, fire-and-forget writes, no catch) and fail on them.
- Verified: 254 unit tests passing, clean build, lint 0 errors (4 existing version warnings). On the emulator, the pair is restored after process restarts (new PID) for selection, swap, History restore and Quick Conversion, in English and Arabic RTL, and offline. It is kept across rotation, including rotation during startup. Cold-start frame captures in light and dark mode show the saved pair on the first drawn frame, with no white flash. `am start -W` startup: before 1.16–1.50s (median ≈1.23s), after 1.17–1.37s (median ≈1.24–1.30s), within emulator noise.

## Accessibility and TalkBack Validation (Phase 12.8)

- Presentation and semantics only: no changes to calculations, caching, retry policy, pair persistence, History recording, favorites, Real Cost math, AdMob or the approved visual design.
- Audit findings, each confirmed with real TalkBack speech before fixing:
  1. The result was announced on almost every keystroke (3 times while typing "1000").
  2. Picker: the selected row said "Selected" twice (radio state + check icon description).
  3. Picker: the favorite star's label described an action that flipped with its state ("checked, Remove MAD from favorites").
  4. History: each row was read twice (its description, then its visible text).
  5. History: the delete label contained "→".
  6. Real Cost: swiping skipped the breakdown, total and disclaimer (rows inside `BoxWithConstraints`), and label and amount were separate items.
  7. Retry gave no feedback: offline, the same error silently came back.
- Corrections:
  - Picker: the check icon is decorative; the star is `IconToggleButton` with a fixed name (`favorite_currency`, "Favorite: EUR") and checked / not checked as its state.
  - History: the visible texts are hidden from TalkBack, so each row is read once from its description, which includes the stale-rate note. Delete reads "Delete conversion 100 MAD to 10.05 USD" (`history_conversion`).
  - Real Cost: each line is one item ("label, amount"), with semantics set on the `BoxWithConstraints` node (outside the subcomposition), so it sits in the reading order.
  - Retry: while retrying, the card's live text is "Loading exchange rates…" (held for at least 1s), then the message again, so a failed retry repeats the error once. The spinner has no description of its own, so "Loading" is said once; TalkBack reads it as "In progress, Progress bar".
- Settled-result announcements (`ui/components/Announcements.kt`, `rememberLiveWhenSettled`), used by the result card and the Real Cost total:
  - The node always holds its current text, so the result is readable as soon as it is shown.
  - Only the live region is switched: off in the same frame as any change, and on once the value has been unchanged for the pause. TalkBack announces a live region being switched on, once, with its current text.
  - Pauses: 2.5s (`TYPING_SETTLE_MILLIS`) after typing, keyed on the raw typed text so "1000" → "1000." counts; 1s (`ANNOUNCE_SETTLE_MILLIS`) after other changes (swap, currency, Quick Conversion, History restore, fee preset).
  - Intermediate keystrokes are never announced, and a node appearing is not live yet (the first digit is silent).
  - The result node is full width: a live region whose bounds change is re-read by TalkBack.
  - Rejected approaches: changing the live text after a delay (made the result unreachable during the pause), and a separate zero-size announcer (Compose drops zero-size nodes from the accessibility tree).
- Rate-provider attribution (`RateInfo`): a clickable text, no longer an inline link. Same text, color, underline and destination. TalkBack action label `open_website` ("Double-tap to open website"). Touch target at least 48dp tall (`heightIn`), with the text at the top and the extra height (about 32dp) below. Opening the URL is wrapped so a missing browser cannot crash the app.
- Strings in English, French and Arabic: added `favorite_currency`, `history_conversion` and `open_website`; removed `add_favorite`, `remove_favorite` and `selected_currency`.
- Verification, by method:
  - **Actual TalkBack speech**, captured from TalkBack 16's verbose log (`Actors: act() … SPEAK`). Navigation used explore taps and swipes through the emulator's virtual touchscreen (`adb emu event mouse`). Plain `adb input` taps and keys bypass TalkBack, and `uiautomator dump` restarts it.
    - Checked: main screen order and labels, typing (one announcement), immediate reading of the result after the first digit, swap, Quick Conversions, picker and favorites, History, Real Cost (open, preset, custom fee), attribution, offline Retry and online recovery, 200% font.
    - Languages: English, French and Arabic RTL (From → Swap → To order).
  - **Semantics tests:** `AccessibilitySemanticsTest` (14 instrumented tests) covers names, roles, actions, states, decorative icons, live regions and announcement timing on virtual time, plus the attribution's 48dp height, click label and a click below the visible text.
  - **Direct-tap activation:** controls were activated with direct `adb` taps, not TalkBack double-taps. The attribution's lower touch area was checked this way: it opened the browser.
- Results: 254/254 unit tests, 14/14 instrumented tests in the final round (15/15 in the earlier full run, including the existing ones), clean `assembleDebug`, lint 0 errors (2 version warnings, run offline).
- Testing note: `connectedDebugAndroidTest` uninstalls the app when it finishes, which erases its data on the device (History, favorites, fee, pair, rate cache).

## Known issues

- **Phase 12.8 accessibility limitations (open, not fixed):**
  - A result or Real Cost total that TalkBack is focused on is read again when its live region switches on after the pause. Compose does not expose TalkBack focus, so the app cannot skip it.
  - A successful Retry has no explicit spoken confirmation; focus simply moves on once the card disappears.
  - The attribution is actionable ("Double-tap to open website") but TalkBack does not call it a link: Compose has no link role.
  - TalkBack double-tap activation was not verified; actions were activated with direct taps.
  - French and Arabic pronunciation was not evaluated by listening; the emulator's system language (and TalkBack's own words) was English.
  - Combined phrases ("amount, result", "label, amount") use a Latin comma as the pause, also in Arabic.
  - The AdMob banner's accessibility is controlled by the advertising SDK.
- **Phase 12.7 limitations (open, not fixed):**
  - If restoring the saved pair takes longer than 500ms, the default MAD → USD can briefly appear before the saved pair replaces it (unless the user has chosen a pair meanwhile). Only covered by virtual-time tests; not reproduced on real storage.
  - Emulator startup timing is noisy (about ±150ms), so small timing differences from the startup wait cannot be ruled out.
  - Full reinstall testing (`pm clear`) was not performed, because it would erase existing emulator data. The fresh-install path was checked by deleting only the `user_preferences` file and by unit tests.
- **Rotation during a no-cache Retry (open, not fixed):** `ConversionSection` keeps the last error in `remember`, which a configuration change clears. If the device rotates while a Retry runs with no cached rates, the loading placeholder replaces the error card with its spinner. The network request continues. This is an existing presentation limitation.
- **Cache-read failure during a failed Retry (open, not fixed, investigate before Google Play release):** if rates are already on screen and a Retry then fails while the cache read also returns nothing, the repository returns `Unavailable`. The ViewModel keeps the old snapshot and sets `error`, but leaves `isStale` unchanged, so those rates can stay visible without a stale warning. This is an existing ViewModel/state-management issue.

- **Phase 12.5 limitations (open, not fixed):**
  - A missing-currency Retry while the cache is still valid (last download under an hour ago) can briefly show the spinner while the repository reads DataStore, with no network request. Accepted: the ViewModel load is genuine and the text doesn't promise a download.
  - Rates can stay displayed without a stale warning if the app stays open past the provider's next update: staleness is evaluated only when rates are loaded. Existing behavior.
  - Connectivity is not monitored independently while valid cached rates are used.
  - Live TalkBack verification is deferred to Phase 12.8.

- **Intermittent, not reproduced (open):** this happened twice during emulator testing (Phases 4 and 6), each time right after launch while typing into the amount field:
  - the amount unexpectedly became `1001000` after typing `1000`;
  - the currencies unexpectedly switched to USD → MAD.

  In Phase 4 the "To" dropdown arrow was already in its expanded state before any scripted input.

  In Phase 7 the `ExposedDropdownMenuBox` selector was replaced by `CurrencySelectorField` + `CurrencyPickerSheet`. Eight controlled runs, with `getevent` recording and no external input, were clean. Five more controlled runs in each of Phases 8, 9 and 10 were also clean. It remains non-reproducible and must not be marked as fixed.

  Three controlled runs with `adb shell getevent -lt` recording real device input did not reproduce it. Separately, one unexplained real touch on the emulator window was recorded during another test. The user was not intentionally interacting with the emulator. It is **not fixed**. If it recurs in a controlled run with no external input events, investigate it as an app bug. Start with the currency selection/swap logic in `CurrencyConverterViewModel` and the picker sheet.
- **Unexplained History entry (observation, open):** during Phase 12.4 emulator validation, History contained "200 USD → 1,993.92 MAD" at 12:54 PM, which no test script created. It appeared while the emulator was running and the scripts were idle. The cause is unknown: possibly manual input on the emulator window, not established. No History behavior was changed. If similar entries appear in a controlled run with no external input (check with `getevent`), investigate it together with the intermittent issue above.
  - Second observation (2026-10-09, Phase 12.7): History contained "100 MAD → 10.05 USD" at about 09:45 AM, which no test script created. The scripts were idle at that time. Logcat showed touch and clipboard activity between about 09:45:22 and 09:45:27: a touch gesture, a bottom sheet opening and closing, the amount field taking focus, then a clipboard write (Copy). `getevent` was not recording, so the origin of these interactions is not confirmed. The evidence (History screenshot and the saved logcat) is in the session scratchpad. The entry itself is no longer on the emulator: a later `connectedDebugAndroidTest` run uninstalled the app and erased its data. Not resolved.
- **Emulator test scripting:** `adb shell input text` typed right after a tap can drop the first characters, because the IME is not yet connected. Wait for `dumpsys input_method` to report `mInputShown=true` before typing. Also note that `adb input` events never appear in `getevent`; only real device or emulator-window input does.
