package com.elabboubisolution.madconverter.ads

import com.elabboubisolution.madconverter.ads.UmpDebugSettings.Geography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** UMP testing options: debug builds only, off unless configured in local.properties. */
class UmpDebugSettingsTest {

    private val deviceId = "33BE2250B43518CCDA7DE426D04EE231" // the hash format, not a real device
    private val warnings = mutableListOf<String>()

    private fun parse(debug: Boolean = true, geography: String = "", ids: String = "") =
        UmpDebugSettings.parse(debug, geography, ids) { warnings += it }

    @Test
    fun `disabled by default - nothing configured`() {
        assertNull(parse())
        assertNull(parse(geography = "  ", ids = " , "))
        assertEquals(emptyList<String>(), warnings)
    }

    @Test
    fun `release builds never get a debug geography or test devices`() {
        assertNull(parse(debug = false, geography = "EEA", ids = deviceId))
        assertNull(parse(debug = false, geography = "OTHER"))
    }

    @Test
    fun `EEA debug geography with a test device`() {
        assertEquals(UmpDebugSettings(Geography.EEA, listOf(deviceId)), parse(geography = "EEA", ids = deviceId))
    }

    @Test
    fun `geography ignores case, device IDs are trimmed and may be several`() {
        val other = "0123456789abcdef0123456789ABCDEF"
        assertEquals(
            UmpDebugSettings(Geography.REGULATED_US_STATE, listOf(deviceId, other)),
            parse(geography = " regulated_us_state ", ids = " $deviceId , $other ,"),
        )
    }

    @Test
    fun `test devices alone - no forced geography`() {
        assertEquals(UmpDebugSettings(null, listOf(deviceId)), parse(ids = deviceId))
    }

    @Test
    fun `invalid values are ignored with a warning that does not repeat them`() {
        assertNull(parse(geography = "Europe", ids = "my-phone"))
        assertEquals(2, warnings.size)
        warnings.forEach { assertFalse(it, "Europe" in it || "my-phone" in it) }

        assertEquals(UmpDebugSettings(Geography.OTHER, emptyList()), parse(geography = "other", ids = "1234"))
    }
}
