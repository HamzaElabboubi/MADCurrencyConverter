package com.elabboubisolution.madconverter.ads

import org.junit.Assert.assertEquals
import org.junit.Test

/** Guards against shipping or testing with anything but Google's official test banner unit. */
class AdConfigTest {

    @Test
    fun `banner uses Google's official test ad unit`() {
        assertEquals("ca-app-pub-3940256099942544/9214589741", AdConfig.BANNER_AD_UNIT_ID)
    }
}
