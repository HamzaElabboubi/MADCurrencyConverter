package com.elabboubisolution.madconverter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AdsInitializer.initialize(this, lifecycleScope)
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
                        modifier = Modifier
                            .padding(innerPadding)
                            .consumeWindowInsets(innerPadding),
                    )
                }
            }
        }
    }
}
