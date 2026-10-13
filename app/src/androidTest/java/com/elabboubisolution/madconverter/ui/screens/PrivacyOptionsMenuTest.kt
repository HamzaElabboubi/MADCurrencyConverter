package com.elabboubisolution.madconverter.ui.screens

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elabboubisolution.madconverter.R
import com.elabboubisolution.madconverter.ui.theme.MADCurrencyConverterTheme
import com.elabboubisolution.madconverter.viewmodel.ConverterUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The "Privacy settings" entry: present only when UMP requires it, reached through "More options". */
@RunWith(AndroidJUnit4::class)
class PrivacyOptionsMenuTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun s(@StringRes id: Int): String = rule.activity.getString(id)

    private var opened = 0

    private fun show(showPrivacyOptions: Boolean) = rule.setContent {
        MADCurrencyConverterTheme {
            CurrencyConverterContent(
                state = ConverterUiState(isLoading = false),
                onAmountChange = {},
                onFromSelected = {},
                onToSelected = {},
                onSwap = {},
                onRetry = {},
                onToggleFavorite = {},
                onQuickConversionSelected = {},
                showPrivacyOptions = showPrivacyOptions,
                onPrivacyOptions = { opened++ },
            )
        }
    }

    @Test
    fun notRequired_noMenu() {
        show(showPrivacyOptions = false)
        rule.onNodeWithContentDescription(s(R.string.history_open)).assertExists()
        rule.onNodeWithContentDescription(s(R.string.more_options)).assertDoesNotExist()
        rule.onNodeWithText(s(R.string.privacy_settings)).assertDoesNotExist()
    }

    @Test
    fun required_menuOpensPrivacySettingsOnce() {
        show(showPrivacyOptions = true)
        rule.onNodeWithContentDescription(s(R.string.more_options)).assertHasClickAction().performClick()
        rule.onNodeWithText(s(R.string.privacy_settings)).assertHasClickAction().performClick()

        assertEquals(1, opened)
        rule.onNodeWithText(s(R.string.privacy_settings)).assertDoesNotExist() // menu closed
    }
}
