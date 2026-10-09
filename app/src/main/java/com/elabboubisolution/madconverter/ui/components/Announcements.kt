package com.elabboubisolution.madconverter.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/** How long a value must stay unchanged before a screen reader announces it. */
internal const val ANNOUNCE_SETTLE_MILLIS = 1_000L

/**
 * Pause after typing before announcing. Screen-reader users type about one key per second or
 * slower, so a shorter pause would still announce intermediate amounts.
 */
internal const val TYPING_SETTLE_MILLIS = 2_500L

/**
 * Whether a node showing [text] should be a polite live region right now. The node always keeps
 * its current text, readable at any time; only the live region is switched:
 * - off in the same frame as any change, so changes made while typing are not announced;
 * - on once [text] has stayed unchanged for the pause. TalkBack announces a live region being
 *   switched on, with its current text: one announcement per settled value.
 *
 * A change that comes with a new [typingKey] (the raw typed amount or fee, so even "1000" ->
 * "1000." counts) waits [TYPING_SETTLE_MILLIS]; any other change (swap, another currency, a
 * preset) waits [ANNOUNCE_SETTLE_MILLIS]. A node appearing is not live yet, so a result card
 * appearing on the first typed digit is silent.
 */
@Composable
internal fun rememberLiveWhenSettled(text: String, typingKey: Any? = null): Boolean {
    val current = text to typingKey
    var settled by remember { mutableStateOf<Pair<String, Any?>?>(null) }
    // Starts empty: appearing because of a typed value (the first digit) also waits for a pause.
    var lastTypingKey by remember { mutableStateOf<Any?>(null) }
    // Keyed on both: a keystroke that leaves the text unchanged ("2" -> "2.") still restarts it.
    LaunchedEffect(text, typingKey) {
        val typed = typingKey != lastTypingKey
        lastTypingKey = typingKey
        delay(if (typed) TYPING_SETTLE_MILLIS else ANNOUNCE_SETTLE_MILLIS)
        settled = current
    }
    // Derived in composition, not in the effect: false in the very frame the value changes.
    return settled == current
}
