package com.elabboubisolution.madconverter.ui.components

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elabboubisolution.madconverter.R
import com.elabboubisolution.madconverter.domain.Conversion
import com.elabboubisolution.madconverter.domain.RealCost
import com.elabboubisolution.madconverter.domain.model.Currency
import com.elabboubisolution.madconverter.domain.model.HistoryEntry
import com.elabboubisolution.madconverter.ui.format.ConversionText
import com.elabboubisolution.madconverter.ui.format.ltr
import com.elabboubisolution.madconverter.ui.theme.MADCurrencyConverterTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

/**
 * What TalkBack is given: names, roles, actions, states, live regions and announcement timing.
 * Strings come from resources, so the checks follow the device/app language.
 */
@RunWith(AndroidJUnit4::class)
class AccessibilitySemanticsTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun s(@StringRes id: Int, vararg args: Any): String = rule.activity.getString(id, *args)

    private fun show(content: @Composable () -> Unit) = rule.setContent { MADCurrencyConverterTheme { content() } }

    private val conversion =
        Conversion(Currency.MAD, Currency.USD, BigDecimal("1000"), BigDecimal("100.81"), BigDecimal("0.100812"))

    private fun spokenResult(c: Conversion) =
        "${ltr(ConversionText.amount(c.amount, c.from))}, ${ltr(ConversionText.approximate(c.convertedAmount, c.to))}"

    /** Same localized name the app shows (platform ISO/CLDR data, in the activity's locale). */
    private fun name(currency: Currency): String = currency.displayName(rule.activity.resources.configuration.locales[0])

    private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)
    private val isPoliteLiveRegion = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)

    private fun SemanticsNodeInteraction.clickLabel(): String? =
        fetchSemanticsNode().config.getOrNull(SemanticsActions.OnClick)?.label

    private fun SemanticsNodeInteraction.descriptions(): List<String> =
        fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()

    // --- Main screen controls ---

    @Test
    fun currencySelectorStatesItsPurposeCurrencyAndAction() {
        show { CurrencySelectorField(label = s(R.string.from_label), currency = Currency.MAD, onClick = {}) }

        rule.onNodeWithContentDescription(s(R.string.change_currency, s(R.string.from_label), "MAD", name(Currency.MAD)))
            .assert(hasRole(Role.Button))
            .assertHasClickAction()
            .also { assertEquals(s(R.string.action_change_currency), it.clickLabel()) }
    }

    @Test
    fun swapIsANamedButton() {
        show { SwapButton(onClick = {}) }

        rule.onNodeWithContentDescription(s(R.string.swap_currencies))
            .assert(hasRole(Role.Button))
            .assertHasClickAction()
    }

    @Test
    fun copyAndShareAreNamedButtons() {
        show { ResultCard(conversion, onCopy = {}, onShare = {}, onRealCost = {}) }

        rule.onNodeWithContentDescription(s(R.string.copy_conversion)).assertHasClickAction()
        rule.onNodeWithContentDescription(s(R.string.share_conversion)).assertHasClickAction()
        rule.onNodeWithText(s(R.string.real_cost_open)).assertHasClickAction()
    }

    // --- Result: always readable, announced only once settled ---

    /** The result node: the one whose description is the "<source>, ≈ <result>" sentence. */
    private fun resultNode(c: Conversion) = rule.onNode(hasContentDescription(spokenResult(c)))

    private fun SemanticsNodeInteraction.isLive(): Boolean =
        fetchSemanticsNode().config.getOrNull(SemanticsProperties.LiveRegion) == LiveRegionMode.Polite

    @Test
    fun resultIsReadableImmediatelyAndAnnouncedOnlyAfterTypingPauses() {
        rule.mainClock.autoAdvance = false
        var typed by mutableStateOf("1")
        var shown by mutableStateOf(conversion.copy(amount = BigDecimal("1"), convertedAmount = BigDecimal("0.10")))
        show { ResultCard(shown, onCopy = {}, onShare = {}, typedAmount = typed) }

        // Appearing on the first digit: readable at once, not announced.
        rule.mainClock.advanceTimeByFrame()
        assertFalse(resultNode(shown).isLive())

        // Each keystroke: the current result is readable right away, and nothing is live.
        listOf("10" to "1.01", "100" to "10.08", "1000" to "100.81").forEach { (amount, converted) ->
            typed = amount
            shown = conversion.copy(amount = BigDecimal(amount), convertedAmount = BigDecimal(converted))
            rule.mainClock.advanceTimeByFrame()
            resultNode(shown).assertExists()
            rule.mainClock.advanceTimeBy(TYPING_SETTLE_MILLIS - 500)
            assertFalse("no announcement while typing ($amount)", resultNode(shown).isLive())
            rule.onAllNodes(isPoliteLiveRegion).assertCountEquals(0)
        }

        // After the pause, the final result becomes live: announced once.
        rule.mainClock.advanceTimeBy(1_000)
        assertTrue(resultNode(conversion).isLive())
        // A keystroke that leaves the result unchanged ("1000" -> "1000.") silences it again.
        typed = "1000."
        rule.mainClock.advanceTimeByFrame()
        assertFalse(resultNode(conversion).isLive())
    }

    @Test
    fun aSwapIsReadableAtOnceAndAnnouncedAfterAShortPause() {
        rule.mainClock.autoAdvance = false
        var shown by mutableStateOf(conversion)
        show { ResultCard(shown, onCopy = {}, onShare = {}, typedAmount = "1000") }
        rule.mainClock.advanceTimeBy(TYPING_SETTLE_MILLIS + 100)
        assertTrue(resultNode(conversion).isLive())

        val swapped = Conversion(Currency.USD, Currency.MAD, BigDecimal("1000"), BigDecimal("9919.48"), BigDecimal("9.919"))
        shown = swapped
        rule.mainClock.advanceTimeByFrame()
        // Readable with the new value, and not live in that same frame (no premature announcement).
        assertFalse(resultNode(swapped).isLive())
        rule.mainClock.advanceTimeBy(ANNOUNCE_SETTLE_MILLIS - 200)
        assertFalse("not yet", resultNode(swapped).isLive())
        rule.mainClock.advanceTimeBy(400)
        assertTrue(resultNode(swapped).isLive())
    }

    // --- Rate provider attribution ---

    @Test
    fun attributionIsAnActionWithA48dpTargetThatOpensTheProviderSite() {
        val opened = mutableListOf<String>()
        val uriHandler = object : UriHandler {
            override fun openUri(uri: String) {
                opened += uri
            }
        }
        show {
            CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                RateInfo(Currency.MAD, Currency.USD, BigDecimal("0.100812"), lastUpdatedEpochSeconds = 1_791_158_551L)
            }
        }

        val link = rule.onNodeWithText(s(R.string.attribution))
        link.assertHasClickAction().assertHeightIsAtLeast(48.dp)
        assertEquals(s(R.string.open_website), link.clickLabel())

        // Below the visible text line, still inside the 48dp target.
        link.performTouchInput { click(bottomCenter - Offset(0f, 4f)) }
        assertEquals(listOf("https://www.exchangerate-api.com"), opened)
        // The TalkBack action (double-tap) does the same.
        link.performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(2, opened.size)
    }

    // --- Quick Conversions ---

    @Test
    fun quickConversionRowIsOneButtonWithItsActionAndADecorativeChevron() {
        val eur = Conversion(Currency.MAD, Currency.EUR, BigDecimal("1000"), BigDecimal("89.73"), BigDecimal("0.089729"))
        show { QuickConversionsSection(listOf(eur), onSelect = {}) }

        val row = rule.onNodeWithContentDescription(s(R.string.quick_conversion_description, "EUR", name(Currency.EUR), "89.73"))
        row.assert(hasRole(Role.Button)).assertHasClickAction()
        assertEquals(s(R.string.action_make_main), row.clickLabel())
        // Only the row's own description: the chevron adds nothing.
        assertEquals(1, row.descriptions().size)
    }

    // --- Currency picker ---

    @Test
    fun favoritesAreTogglesWithAFixedNameAndTheirState() {
        show {
            CurrencyPickerContent(
                title = "",
                selected = Currency.USD,
                favorites = setOf(Currency.MAD, Currency.EUR, Currency.USD),
                onSelect = {},
                onToggleFavorite = {},
            )
        }

        rule.onNodeWithContentDescription(s(R.string.favorite_currency, "EUR")).assertIsOn()
        rule.onNodeWithContentDescription(s(R.string.favorite_currency, "GBP")).assertIsOff()
    }

    @Test
    fun selectedCurrencyIsASelectedRadioButtonWithoutARepeatedSelectedLabel() {
        show {
            CurrencyPickerContent(
                title = "",
                selected = Currency.USD,
                favorites = setOf(Currency.MAD, Currency.EUR, Currency.USD),
                onSelect = {},
                onToggleFavorite = {},
            )
        }

        val row = rule.onNode(isSelectable() and hasText("USD"))
        row.assertIsSelected().assert(hasRole(Role.RadioButton))
        // The check mark is decorative: the radio button already says "Selected".
        assertNull(row.fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription))
    }

    // --- Status cards ---

    @Test
    fun errorCardIsOnePoliteMessageWithANamedRetryAndADecorativeIcon() {
        show {
            ErrorState(
                title = s(R.string.rates_unavailable_title),
                message = s(R.string.error_no_connection),
                onRetry = {},
            )
        }

        rule.onNode(isPoliteLiveRegion).assert(
            hasContentDescription("${s(R.string.rates_unavailable_title)}, ${s(R.string.error_no_connection)}"),
        )
        rule.onNodeWithText(s(R.string.retry)).assertHasClickAction()
        // The live message is the only described node: the icon is decorative.
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription)).assertCountEquals(1)
    }

    @Test
    fun retryIsAnnouncedAndAFailedRetryRepeatsTheError() {
        rule.mainClock.autoAdvance = false
        var retrying by mutableStateOf(false)
        show {
            ErrorState(message = s(R.string.error_no_connection), onRetry = {}, isRetrying = retrying)
        }
        val live = rule.onNode(isPoliteLiveRegion)
        rule.mainClock.advanceTimeByFrame()
        assertEquals(listOf(s(R.string.error_no_connection)), live.descriptions())

        retrying = true
        // Set by an effect: visible from the next frame.
        rule.mainClock.advanceTimeBy(50)
        assertEquals(listOf(s(R.string.loading_rates)), live.descriptions())
        rule.onAllNodesWithText(s(R.string.retry)).assertCountEquals(0)
        // Retry is replaced by a progress indicator with no description of its own, so
        // "Loading exchange rates" is said once (by the card).
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
        rule.onAllNodes(hasContentDescription(s(R.string.loading_rates))).assertCountEquals(1)

        // An instant failure: "Loading" is held, then the error is spoken again, once.
        retrying = false
        rule.mainClock.advanceTimeBy(ANNOUNCE_SETTLE_MILLIS - 200)
        assertEquals(listOf(s(R.string.loading_rates)), live.descriptions())
        rule.mainClock.advanceTimeBy(400)
        assertEquals(listOf(s(R.string.error_no_connection)), live.descriptions())
    }

    @Test
    fun staleBannerIsAPoliteWarningWithRetry() {
        show { StaleRateBanner(reason = s(R.string.error_no_connection), isRefreshing = false, onRetry = {}) }

        rule.onNode(isPoliteLiveRegion).assert(
            hasContentDescription(
                "${s(R.string.stale_title)}, ${s(R.string.error_no_connection)} ${s(R.string.stale_message)}",
            ),
        )
        rule.onNodeWithText(s(R.string.retry)).assertHasClickAction()
    }

    // --- Real Cost ---

    @Test
    fun realCostLinesAreReadWholeAndOnlyTheTotalIsAnnounced() {
        rule.mainClock.autoAdvance = false
        var fee by mutableStateOf(BigDecimal.ZERO)
        show {
            RealCostContent(conversion, RealCost.estimate(conversion, fee), onFeeChanged = {}, onFeeCommitted = {})
        }
        fun total(f: String) = "${s(R.string.real_cost_total)}, ${ltr(ConversionText.money(RealCost.estimate(conversion, BigDecimal(f)).total, Currency.USD))}"

        // Readable as soon as the sheet shows; announced (live) only after the pause.
        rule.mainClock.advanceTimeByFrame()
        assertFalse(rule.onNode(hasContentDescription(total("0"))).isLive())
        rule.mainClock.advanceTimeBy(ANNOUNCE_SETTLE_MILLIS + 100)
        assertTrue(rule.onNode(hasContentDescription(total("0"))).isLive())

        // Fee line: one item (label + amount), never live.
        val zeroFee = RealCost.estimate(conversion, BigDecimal.ZERO).fee
        val feeLine = s(R.string.real_cost_fee, ltr("0%")) + ", " + ltr(ConversionText.money(zeroFee, Currency.USD))
        rule.onNodeWithContentDescription(feeLine).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion))
        rule.onAllNodes(isPoliteLiveRegion).assertCountEquals(1)

        // A preset: the new total is readable at once and announced once it settles.
        fee = BigDecimal("2")
        rule.mainClock.advanceTimeByFrame()
        assertFalse(rule.onNode(hasContentDescription(total("2"))).isLive())
        rule.mainClock.advanceTimeBy(ANNOUNCE_SETTLE_MILLIS + 100)
        assertTrue(rule.onNode(hasContentDescription(total("2"))).isLive())
    }

    // --- History ---

    @Test
    fun historyRowIsOneSentenceIncludingTheStaleNoteAndDeleteNamesTheConversion() {
        val entry = HistoryEntry(
            "1", Currency.MAD, Currency.USD, BigDecimal("1000"), BigDecimal("100.30"), BigDecimal("0.1003"),
            System.currentTimeMillis(), wasStale = true,
        )
        show { HistoryContent(listOf(entry), onSelect = {}, onDelete = {}, onClearAll = {}) }

        val source = ConversionText.amount(entry.amount, entry.from)
        val target = ConversionText.money(entry.convertedAmount, entry.to)
        val row = rule.onNode(hasClickAction() and hasContentDescription(s(R.string.history_entry_description, source, target, ""), substring = true))
        val spoken = row.descriptions().single()
        assertTrue(spoken, spoken.endsWith(". " + s(R.string.history_stale_rate)))
        assertEquals(s(R.string.action_convert_again), row.clickLabel())
        // The visible "A → B" text is not read a second time.
        assertFalse(row.fetchSemanticsNode().config.contains(SemanticsProperties.Text))

        rule.onNodeWithContentDescription(s(R.string.history_delete_entry, s(R.string.history_conversion, source, target)))
            .assertHasClickAction()
    }
}
