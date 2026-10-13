package com.elabboubisolution.madconverter

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.elabboubisolution.madconverter.ads.AdConfig
import com.elabboubisolution.madconverter.ads.AdsInitializer
import com.elabboubisolution.madconverter.ads.BannerAd
import com.elabboubisolution.madconverter.ads.UmpConsentPlatform
import com.elabboubisolution.madconverter.ads.UmpDebugSettings
import com.elabboubisolution.madconverter.ui.screens.CurrencyConverterScreen
import com.elabboubisolution.madconverter.ui.theme.MADCurrencyConverterTheme
import com.elabboubisolution.madconverter.viewmodel.CurrencyConverterViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // The same instance the screen gets from viewModel(): same owner, same default key.
    private val viewModel: CurrencyConverterViewModel by viewModels { CurrencyConverterViewModel.Factory }

    private var contentSet = false

    private val ads by lazy { AdsInitializer.get(this) }

    /** Consent gathering for this Activity instance; cancelled when it is destroyed. */
    private var consentSession: AdsInitializer.ConsentSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Consent is updated on every creation; ads start only once it allows them (canLoadAds).
        // Debug builds may add UMP's testing options from local.properties; never release builds.
        val umpDebugSettings = UmpDebugSettings.fromBuildConfig { Log.w(TAG, it) }
        consentSession = ads.gatherConsent(UmpConsentPlatform(this, umpDebugSettings))
        holdFirstFrameUntilContentIsSet()
        // Composed only once the saved currency pair is known, so its first frame already shows
        // it and the default MAD -> USD never flashes. After a rotation the ViewModel is already
        // restored and this runs synchronously, as before. Waits at most MAX_PAIR_WAIT_MILLIS:
        // after that the default shows and the saved pair follows (unless the user chose one).
        lifecycleScope.launch {
            viewModel.awaitPairRestored(MAX_PAIR_WAIT_MILLIS)
            showContent()
        }
    }

    override fun onDestroy() {
        consentSession?.cancel()
        consentSession = null
        super.onDestroy()
    }

    private fun showContent() {
        contentSet = true
        setContent {
            val canLoadAds by ads.canLoadAds.collectAsStateWithLifecycle()
            val privacyOptionsRequired by ads.privacyOptionsRequired.collectAsStateWithLifecycle()
            MADCurrencyConverterTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    // No top inset here: the screen's top app bar draws behind the status bar
                    // and pads itself.
                    contentWindowInsets = ScaffoldDefaults.contentWindowInsets
                        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                    // The banner sits above the system navigation bar; Scaffold then pads the
                    // content by the banner's height so nothing is drawn underneath it.
                    // Not composed (so no ad request) until consent allows ads and the SDK is ready.
                    bottomBar = {
                        if (canLoadAds) {
                            BannerAd(
                                adUnitId = AdConfig.BANNER_AD_UNIT_ID,
                                modifier = Modifier.navigationBarsPadding(),
                            )
                        }
                    },
                ) { innerPadding ->
                    CurrencyConverterScreen(
                        viewModel = viewModel,
                        // Offered only when UMP requires it; the form is UMP's own.
                        showPrivacyOptions = privacyOptionsRequired,
                        onPrivacyOptions = { consentSession?.showPrivacyOptions() },
                        modifier = Modifier
                            .padding(innerPadding)
                            .consumeWindowInsets(innerPadding),
                    )
                }
            }
        }
    }

    /**
     * Keeps the startup window (and the Android 12+ splash) on screen until the content is set,
     * instead of drawing an empty frame meanwhile.
     */
    private fun holdFirstFrameUntilContentIsSet() {
        val content = findViewById<View>(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (contentSet) content.viewTreeObserver.removeOnPreDrawListener(this)
                return contentSet
            }
        })
    }

    private companion object {
        const val MAX_PAIR_WAIT_MILLIS = 500L
        const val TAG = "AdsConsent"
    }
}
