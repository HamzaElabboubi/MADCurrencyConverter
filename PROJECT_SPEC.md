You are a senior Android engineer.

I want you to build a simple, modern, production-ready Android currency converter application focused primarily on the Moroccan Dirham (MAD).

## Project Goal

Create a lightweight Android application that allows users to quickly convert Moroccan Dirham (MAD) to major currencies and vice versa.

The first version should support:

- MAD — Moroccan Dirham
- USD — US Dollar
- EUR — Euro
- GBP — British Pound

The application should be simple enough to publish on Google Play, but its architecture and code quality should be production-ready.

Do NOT create a backend.
Do NOT add authentication.
Do NOT use a database server.

---

## Technology Stack

Use:

- Android Studio
- Kotlin
- Jetpack Compose
- Material 3
- MVVM architecture
- Retrofit for HTTP requests
- Kotlin Coroutines
- StateFlow
- DataStore for lightweight local persistence if needed
- Google Mobile Ads SDK for AdMob

Use the latest stable versions that are compatible with each other.

Before implementing dependencies, verify that they are current and not deprecated.

---

## Core Features

### 1. Currency Conversion

The main screen must contain:

- Amount input
- Source currency selector
- Target currency selector
- Conversion result
- Swap currencies button
- Current exchange rate
- Last update date/time

Example:

1,000 MAD

≈ 108.50 USD

1 MAD = 0.1085 USD

Last updated: Today, 10:30

The result must update automatically when the amount or selected currency changes.

Handle decimal values correctly.

---

### 2. Supported Conversions

Users should be able to convert between:

MAD ↔ USD  
MAD ↔ EUR  
MAD ↔ GBP

The architecture should make it easy to add additional currencies later.

---

### 3. Exchange Rate API

Use a reliable exchange-rate API that supports MAD.

Do NOT hardcode exchange rates.

Create a clean API abstraction so the provider can easily be replaced later.

The application must handle:

- No internet connection
- API errors
- Timeout
- Invalid response
- Rate unavailable

If possible, cache the latest successfully retrieved exchange rates locally.

When the network is unavailable, use the cached rate and clearly tell the user that the displayed rate may not be current.

Never silently present an old cached rate as a live exchange rate.

---

## UI / UX

Create a clean, minimal and professional interface.

The visual identity can subtly reference Morocco without making the interface visually heavy.

Main screen structure:

App name

Amount input

[ MAD ▼ ]  ⇄  [ USD ▼ ]

Converted amount

Exchange rate

Last updated information

AdMob banner area

The interface should:

- Support light mode
- Support dark mode
- Be responsive on different Android screen sizes
- Have good spacing
- Use Material 3 components
- Have accessible text sizes
- Use proper loading indicators
- Display useful error messages
- Avoid unnecessary screens

Do not use excessive animations.

---

## AdMob

Integrate Google AdMob correctly.

For development, ALWAYS use Google's official test ads.

Never use production ads during development.

Implement one banner ad at the bottom of the main screen.

Keep AdMob configuration separated from the currency conversion logic.

Make it easy to replace the test Ad Unit ID with the production Ad Unit ID later.

The application must continue working correctly if an ad fails to load.

Do not use intrusive interstitial ads in V1.

---

## Architecture

Use a clean MVVM structure similar to:

data/
remote/
local/
repository/

domain/
model/

ui/
screens/
components/
theme/

viewmodel/

The exact structure can be adjusted if there is a better Android convention.

Keep responsibilities separated.

For example:

ExchangeRateApi  
CurrencyRepository  
CurrencyConverterViewModel  
CurrencyConverterScreen

Avoid unnecessary abstraction and overengineering.

---

## Security

Do not expose sensitive API keys directly in the Git repository.

If the selected API requires an API key, store it using an appropriate local configuration mechanism such as local.properties / BuildConfig and ensure secrets are excluded from Git.

Create an `.env.example` equivalent or clear documentation explaining which configuration values must be supplied.

Do not commit real API keys.

---

## Privacy

Keep the application privacy-friendly.

Do not request unnecessary Android permissions.

Do not collect personal user information.

Do not add analytics in V1 unless required.

Because AdMob is included, structure the implementation so consent/privacy requirements can be added correctly before production release.

---

## Testing

Add useful tests for the important business logic.

At minimum test:

- MAD → USD calculation
- USD → MAD calculation
- MAD → EUR calculation
- Decimal amount conversion
- Zero amount
- Invalid amount
- Missing exchange rate

Keep currency calculation logic independent enough to test without Android UI dependencies.

---

## Code Quality

Follow modern Android development practices.

Requirements:

- Clear naming
- Small reusable Compose components
- No duplicated business logic
- No deprecated Android APIs
- Proper error handling
- Proper loading states
- Comments only where they add value
- No unnecessary libraries
- No placeholder production logic

---

## README

Create a professional README.md containing:

- Project description
- Features
- Screenshots section placeholder
- Architecture
- Technology stack
- Exchange-rate API used
- API key setup if applicable
- How to run the application
- How AdMob test mode works
- How to configure production AdMob IDs later
- Build instructions
- Privacy notes

---

## Development Workflow

Do not implement everything blindly at once.

First:

1. Inspect the existing repository if one exists.
2. Explain the proposed architecture.
3. Select an appropriate exchange-rate API that supports MAD.
4. Identify whether it requires an API key and what its usage limits are.
5. Define the project structure.
6. Present the implementation plan.

Then implement the project incrementally.

After each major phase:

- Build the project.
- Fix compilation errors.
- Run relevant tests.
- Check for deprecated APIs or obvious warnings.

Suggested phases:

Phase 1 — Project setup and architecture  
Phase 2 — Exchange-rate API integration  
Phase 3 — Currency conversion business logic  
Phase 4 — Main Jetpack Compose UI  
Phase 5 — Local rate caching and offline handling  
Phase 6 — AdMob test banner integration  
Phase 7 — Unit tests  
Phase 8 — Final UI polish  
Phase 9 — README and production checklist

Do not claim a phase works without building or testing it when the environment allows verification.

---

## Final Deliverable

At the end, I want a working Android project that:

- Builds successfully
- Converts MAD/USD/EUR/GBP correctly
- Retrieves real exchange rates
- Handles network failures gracefully
- Uses a modern Material 3 interface
- Contains an AdMob test banner
- Has no unnecessary permissions
- Has basic unit tests
- Is documented
- Is structured for eventual Google Play publication

Keep V1 intentionally small.

The priority is reliability, simplicity, maintainability, and getting a legitimate first version ready for testing and eventual Google Play release.