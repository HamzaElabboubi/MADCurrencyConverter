package com.elabboubisolution.madconverter

import android.os.Bundle
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
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.elabboubisolution.madconverter.ads.AdConfig
import com.elabboubisolution.madconverter.ads.AdsInitializer
import com.elabboubisolution.madconverter.ads.BannerAd
import com.elabboubisolution.madconverter.ui.screens.CurrencyConverterScreen
import com.elabboubisolution.madconverter.ui.theme.MADCurrencyConverterTheme
import com.elabboubisolution.madconverter.viewmodel.CurrencyConverterViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // The same instance the screen gets from viewModel(): same owner, same default key.
    private val viewModel: CurrencyConverterViewModel by viewModels { CurrencyConverterViewModel.Factory }

    private var contentSet = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AdsInitializer.initialize(this, lifecycleScope)
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

    private fun showContent() {
        contentSet = true
        setContent {
            MADCurrencyConverterTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    // No top inset here: the screen's top app bar draws behind the status bar
                    // and pads itself.
                    contentWindowInsets = ScaffoldDefaults.contentWindowInsets
                        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                    // The banner sits above the system navigation bar; Scaffold then pads the
                    // content by the banner's height so nothing is drawn underneath it.
                    bottomBar = {
                        BannerAd(
                            adUnitId = AdConfig.BANNER_AD_UNIT_ID,
                            modifier = Modifier.navigationBarsPadding(),
                        )
                    },
                ) { innerPadding ->
                    CurrencyConverterScreen(
                        viewModel = viewModel,
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
    }
}
