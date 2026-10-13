package com.elabboubisolution.madconverter.ads

import com.elabboubisolution.madconverter.BuildConfig

/**
 * Ad unit IDs, injected by `app/build.gradle.kts`: Google's official test IDs in debug builds,
 * the production IDs (never committed, validated at build time) in release builds. The App ID
 * lives in the manifest via the `admobAppId` placeholder.
 */
object AdConfig {
    const val BANNER_AD_UNIT_ID: String = BuildConfig.ADMOB_BANNER_AD_UNIT_ID
}
