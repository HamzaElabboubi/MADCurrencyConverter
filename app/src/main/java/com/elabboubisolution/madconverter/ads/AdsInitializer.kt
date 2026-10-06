package com.elabboubisolution.madconverter.ads

import android.content.Context
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Single entry point for starting the Mobile Ads SDK.
 *
 * Before a production release, user consent (UMP / GDPR, US state privacy laws) must be gathered
 * here, before [MobileAds.initialize] and before any ad request.
 */
object AdsInitializer {

    private val started = AtomicBoolean(false)

    /** Initializes the SDK once, on a background thread as Google recommends to avoid ANRs. */
    fun initialize(context: Context, scope: CoroutineScope) {
        if (!started.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        scope.launch(Dispatchers.IO) {
            MobileAds.initialize(appContext)
        }
    }
}
