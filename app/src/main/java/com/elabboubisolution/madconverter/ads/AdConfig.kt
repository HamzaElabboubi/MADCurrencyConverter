package com.elabboubisolution.madconverter.ads

import com.elabboubisolution.madconverter.BuildConfig

/**
 * Ad unit IDs, injected by `app/build.gradle.kts`. They are currently Google's official test IDs
 * for every build; the App ID lives in the manifest via the `admobAppId` placeholder.
 */
object AdConfig {
    const val BANNER_AD_UNIT_ID: String = BuildConfig.ADMOB_BANNER_AD_UNIT_ID
}
