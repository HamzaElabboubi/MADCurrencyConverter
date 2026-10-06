package com.elabboubisolution.madconverter.ads

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

private const val TAG = "BannerAd"

/**
 * Anchored adaptive banner sized to the available width.
 *
 * It takes no space until an ad has loaded, so a failed or missing ad leaves the layout unchanged.
 * The [AdView] is paused/resumed with the lifecycle and destroyed when it leaves the composition.
 */
@Composable
fun BannerAd(adUnitId: String, modifier: Modifier = Modifier) {
    if (LocalInspectionMode.current) {
        BannerPlaceholder(modifier)
        return
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val context = LocalContext.current
        val widthDp = maxWidth.value.toInt()
        val adView = remember(adUnitId, widthDp) { createBannerAdView(context, adUnitId, widthDp) }
            ?: return@BoxWithConstraints
        var isLoaded by remember(adView) { mutableStateOf(false) }

        DisposableEffect(adView) {
            adView.adListener = object : AdListener() {
                override fun onAdLoaded() {
                    isLoaded = true
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoaded = false
                    Log.w(TAG, "Banner failed to load (code ${error.code}): ${error.message}")
                }
            }
            adView.loadAd(AdRequest.Builder().build())
            onDispose { adView.destroy() }
        }

        LifecycleResumeEffect(adView) {
            adView.resume()
            onPauseOrDispose { adView.pause() }
        }

        val height = if (isLoaded) adView.adSize?.height?.dp ?: 0.dp else 0.dp
        AndroidView(
            factory = { adView },
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
        )
    }
}

/** Returns null if the ad view cannot be created (e.g. WebView unavailable), so the app still runs. */
private fun createBannerAdView(context: Context, adUnitId: String, widthDp: Int): AdView? =
    try {
        AdView(context).apply {
            setAdUnitId(adUnitId)
            setAdSize(AdSize.getLargeAnchoredAdaptiveBannerAdSize(context, widthDp))
        }
    } catch (e: RuntimeException) {
        Log.w(TAG, "Banner ad view could not be created", e)
        null
    }

@Composable
private fun BannerPlaceholder(modifier: Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text("Ad banner", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Preview(showBackground = true)
@Composable
private fun BannerAdPreview() {
    BannerAd(adUnitId = AdConfig.BANNER_AD_UNIT_ID)
}
