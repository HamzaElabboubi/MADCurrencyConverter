package com.elabboubisolution.madconverter.ui.format

/**
 * Wraps [text] in a Unicode left-to-right isolate (LRI … PDI) so amounts, ISO codes, rates and
 * "source → target" expressions keep their reading order inside right-to-left (Arabic) text.
 * Without it, "1 MAD = 0.1003 USD" in an RTL paragraph renders as "MAD = 0.1003 USD 1".
 * Display only: never put isolates in copied/shared text or stored values. No effect in LTR UIs.
 */
fun ltr(text: String): String = "$LRI$text$PDI"

private const val LRI = '⁦'
private const val PDI = '⁩'

/**
 * Converts digits typed with an Arabic keyboard to ASCII before they reach the amount/fee
 * parsers: Arabic-Indic (٠–٩) and Eastern Arabic-Indic (۰–۹) digits, the Arabic decimal
 * separator (٫) and Arabic comma (،). The domain parsers stay unchanged and ASCII-only.
 */
fun normalizeNumericInput(input: String): String = buildString(input.length) {
    for (c in input) {
        append(
            when (c) {
                in '٠'..'٩' -> '0' + (c - '٠')
                in '۰'..'۹' -> '0' + (c - '۰')
                '٫' -> '.'
                '،' -> ','
                else -> c
            },
        )
    }
}
