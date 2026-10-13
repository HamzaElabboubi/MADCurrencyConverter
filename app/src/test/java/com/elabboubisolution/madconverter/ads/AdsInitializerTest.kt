package com.elabboubisolution.madconverter.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Consent flow and ad gating, with a fake UMP whose callbacks the tests fire (in any order,
 * repeated, or after the Activity is gone). [AdsInitializer.canLoadAds] is the only thing that
 * lets the banner be composed, i.e. request an ad.
 */
class AdsInitializerTest {

    private var sdkStarts = 0
    private var completeSdk: (() -> Unit)? = null
    private val logged = mutableListOf<String>()

    private val ads = AdsInitializer(
        initializeMobileAds = { onComplete ->
            sdkStarts++
            completeSdk = onComplete
        },
        log = { logged += it },
    )

    private val canLoadAds get() = ads.canLoadAds.value

    @Test
    fun `consent required - no SDK and no ads until the form grants consent`() {
        val ump = FakeConsentPlatform(canRequestAds = false)
        ads.gatherConsent(ump)
        assertEquals(1, ump.infoRequests)
        assertEquals(0, sdkStarts)

        ump.succeedInfoUpdate()
        assertEquals(1, ump.formRequests)
        assertEquals("form showing: nothing started", 0, sdkStarts)
        assertFalse(canLoadAds)

        ump.canRequestAds = true
        ump.dismissForm()
        assertEquals(1, sdkStarts)
        assertFalse("SDK not initialized yet: still no ad request", canLoadAds)

        completeSdk!!()
        assertTrue(canLoadAds)
    }

    @Test
    fun `consent not required - ads start once the info update confirms it`() {
        val ump = FakeConsentPlatform(canRequestAds = false)
        ads.gatherConsent(ump)

        ump.canRequestAds = true // e.g. outside the regulated regions
        ump.succeedInfoUpdate()
        ump.dismissForm() // UMP calls back at once when no form is required
        completeSdk!!()

        assertEquals(1, sdkStarts)
        assertTrue(canLoadAds)
    }

    @Test
    fun `update failure with consent from an earlier session - ads still allowed`() {
        val ump = FakeConsentPlatform(canRequestAds = true)
        ads.gatherConsent(ump)
        assertEquals("started in parallel with the update", 1, sdkStarts)

        ump.failInfoUpdate("offline")
        completeSdk!!()

        assertEquals(1, sdkStarts)
        assertEquals(0, ump.formRequests)
        assertTrue(canLoadAds)
        assertEquals(1, logged.size)
    }

    @Test
    fun `update failure without consent - no SDK, no ads, nothing else attempted`() {
        val ump = FakeConsentPlatform(canRequestAds = false)
        ads.gatherConsent(ump)

        ump.failInfoUpdate("offline")

        assertEquals(0, sdkStarts)
        assertEquals(0, ump.formRequests)
        assertFalse(canLoadAds)
    }

    @Test
    fun `form dismissed without consent - no ads`() {
        val ump = FakeConsentPlatform(canRequestAds = false)
        ads.gatherConsent(ump)
        ump.succeedInfoUpdate()

        ump.dismissForm()

        assertEquals(0, sdkStarts)
        assertFalse(canLoadAds)
    }

    @Test
    fun `form error - the stored consent decides`() {
        val denied = FakeConsentPlatform(canRequestAds = false)
        ads.gatherConsent(denied)
        denied.succeedInfoUpdate()
        denied.dismissForm(error = "form unavailable")
        assertEquals(0, sdkStarts)
        assertFalse(canLoadAds)
        assertEquals(1, logged.size)

        // A later launch, with consent stored meanwhile and the form failing again.
        val allowed = FakeConsentPlatform(canRequestAds = false)
        ads.gatherConsent(allowed)
        allowed.succeedInfoUpdate()
        allowed.canRequestAds = true
        allowed.dismissForm(error = "form unavailable")
        completeSdk!!()
        assertEquals(1, sdkStarts)
        assertTrue(canLoadAds)
    }

    @Test
    fun `duplicate callbacks show the form once and start the SDK once`() {
        val ump = FakeConsentPlatform(canRequestAds = false)
        ads.gatherConsent(ump)

        ump.succeedInfoUpdate()
        ump.succeedInfoUpdate()
        ump.failInfoUpdate("late failure")
        assertEquals(1, ump.formRequests)

        ump.canRequestAds = true
        ump.dismissForm()
        ump.dismissForm()
        ump.dismissForm(error = "late error")
        completeSdk!!()
        completeSdk!!()

        assertEquals(1, sdkStarts)
        assertTrue(canLoadAds)
        assertEquals("ignored callbacks are not logged", 0, logged.size)
    }

    @Test
    fun `every launch updates consent but the SDK starts only once per process`() {
        repeat(3) {
            val ump = FakeConsentPlatform(canRequestAds = true)
            val session = ads.gatherConsent(ump)
            assertEquals(1, ump.infoRequests)
            ump.succeedInfoUpdate()
            ump.dismissForm()
            session.cancel()
        }
        completeSdk!!()

        assertEquals(1, sdkStarts)
        assertTrue(canLoadAds)
    }

    @Test
    fun `callbacks reaching a destroyed Activity are ignored`() {
        val first = FakeConsentPlatform(canRequestAds = false)
        val session = ads.gatherConsent(first)
        session.cancel() // destroyed (e.g. rotation) before the update answered
        assertFalse(session.isActive)

        first.canRequestAds = true
        first.succeedInfoUpdate()
        first.failInfoUpdate("late failure")

        assertEquals("no form shown on a destroyed Activity", 0, first.formRequests)
        assertEquals(0, sdkStarts)
        assertFalse(canLoadAds)
    }

    @Test
    fun `recreation during the form - the new Activity shows it and decides`() {
        val first = FakeConsentPlatform(canRequestAds = false)
        val firstSession = ads.gatherConsent(first)
        first.succeedInfoUpdate()
        assertEquals(1, first.formRequests)

        // Rotation: the old Activity (and its form) goes away, a new one gathers consent.
        val second = FakeConsentPlatform(canRequestAds = false)
        ads.gatherConsent(second)
        firstSession.cancel()
        first.dismissForm()
        assertEquals(0, sdkStarts)

        second.succeedInfoUpdate()
        assertEquals(1, second.formRequests)
        second.canRequestAds = true
        second.dismissForm()
        completeSdk!!()

        assertEquals(1, sdkStarts)
        assertTrue(canLoadAds)
    }

    @Test
    fun `a new session replaces one that was not cancelled`() {
        val first = FakeConsentPlatform(canRequestAds = false)
        val firstSession = ads.gatherConsent(first)
        ads.gatherConsent(FakeConsentPlatform(canRequestAds = false))

        assertFalse(firstSession.isActive)
        first.succeedInfoUpdate()
        assertEquals(0, first.formRequests)
    }

    @Test
    fun `no ad request before permission, at any step`() {
        val ump = FakeConsentPlatform(canRequestAds = false)
        val seen = mutableListOf<Boolean>()
        fun record() {
            seen += canLoadAds
        }

        record()
        ads.gatherConsent(ump)
        record()
        ump.succeedInfoUpdate()
        record()
        ump.dismissForm()
        record()

        assertEquals(listOf(false, false, false, false), seen)
        assertEquals(0, sdkStarts)
    }

    @Test
    fun `ads stop when a later update no longer allows them`() {
        val first = FakeConsentPlatform(canRequestAds = true)
        ads.gatherConsent(first)
        completeSdk!!()
        assertTrue(canLoadAds)

        // A later launch: the stored consent is no longer valid and the user declines.
        val second = FakeConsentPlatform(canRequestAds = true)
        ads.gatherConsent(second)
        second.canRequestAds = false
        second.succeedInfoUpdate()
        second.dismissForm()

        assertFalse(canLoadAds)
        assertEquals(1, sdkStarts)
    }

    @Test
    fun `can load ads only changes on real transitions`() {
        val ump = FakeConsentPlatform(canRequestAds = true)
        ads.gatherConsent(ump)
        val values = mutableListOf(canLoadAds)
        completeSdk!!()
        values += canLoadAds
        ump.succeedInfoUpdate()
        values += canLoadAds
        ump.dismissForm()
        values += canLoadAds

        // A StateFlow only emits changes: collectors (the banner) see false then true, once.
        assertEquals(listOf(false, true, true, true), values)
    }

    // --- Privacy options (Phase 14.2B) ---

    private val privacyOptionsRequired get() = ads.privacyOptionsRequired.value

    /** A launch in a regulated region where the user granted consent; privacy options required. */
    private fun consentedSession(ump: FakeConsentPlatform): AdsInitializer.ConsentSession {
        val session = ads.gatherConsent(ump)
        ump.privacyOptionsRequired = true
        ump.succeedInfoUpdate()
        ump.dismissForm()
        completeSdk?.invoke()
        assertTrue(canLoadAds)
        return session
    }

    @Test
    fun `privacy options required - offered once the update reports it, also after recreation`() {
        val ump = FakeConsentPlatform(canRequestAds = true)
        ads.gatherConsent(ump)
        assertFalse("unknown before the update", privacyOptionsRequired)

        ump.privacyOptionsRequired = true
        ump.succeedInfoUpdate()
        ump.dismissForm()
        assertTrue(privacyOptionsRequired)

        // Rotation: the new Activity's session reads the status again and keeps it.
        ads.gatherConsent(FakeConsentPlatform(canRequestAds = true, privacyOptionsRequired = true))
        assertTrue(privacyOptionsRequired)
    }

    @Test
    fun `privacy options not required - not offered and never opened`() {
        val ump = FakeConsentPlatform(canRequestAds = true)
        val session = ads.gatherConsent(ump)
        ump.succeedInfoUpdate()
        ump.dismissForm()

        assertFalse(privacyOptionsRequired)
        assertFalse(session.showPrivacyOptions())
        assertEquals(0, ump.privacyRequests)
    }

    @Test
    fun `opening and closing the privacy form re-reads consent and allows reopening`() {
        val ump = FakeConsentPlatform(canRequestAds = true)
        val session = consentedSession(ump)

        assertTrue(session.showPrivacyOptions())
        assertEquals(1, ump.privacyRequests)
        val readsBefore = ump.canRequestAdsReads
        ump.dismissPrivacyForm() // choices kept

        assertTrue("consent read again after the form", ump.canRequestAdsReads > readsBefore)
        assertTrue(canLoadAds)
        assertTrue("closed: it can be opened again", session.showPrivacyOptions())
        assertEquals(2, ump.privacyRequests)
        assertEquals(1, sdkStarts)
    }

    @Test
    fun `consent withdrawn in the privacy form removes the banner, granting it again restores it`() {
        val ump = FakeConsentPlatform(canRequestAds = true)
        val session = consentedSession(ump)

        session.showPrivacyOptions()
        ump.canRequestAds = false
        ump.dismissPrivacyForm()
        assertFalse("banner removed at once", canLoadAds)

        session.showPrivacyOptions()
        ump.canRequestAds = true
        ump.dismissPrivacyForm()
        assertTrue(canLoadAds)
        assertEquals("the SDK is never started twice", 1, sdkStarts)
    }

    @Test
    fun `repeated taps and duplicate callbacks - one form, handled once`() {
        val ump = FakeConsentPlatform(canRequestAds = true)
        val session = consentedSession(ump)

        assertTrue(session.showPrivacyOptions())
        assertFalse("already showing", session.showPrivacyOptions())
        assertFalse(session.showPrivacyOptions())
        assertEquals(1, ump.privacyRequests)

        ump.canRequestAds = false
        ump.dismissPrivacyForm(error = "first")
        ump.canRequestAds = true // a late duplicate must not re-read (and re-allow) anything
        ump.dismissPrivacyForm(error = "duplicate")
        assertFalse(canLoadAds)
        assertEquals(listOf("Privacy options form failed (first)"), logged)
    }

    @Test
    fun `a late callback of an earlier privacy form does not close the current one`() {
        val ump = FakeConsentPlatform(canRequestAds = true)
        val session = consentedSession(ump)
        session.showPrivacyOptions()
        ump.dismissPrivacyForm()
        session.showPrivacyOptions()

        ump.canRequestAds = false
        ump.dismissPrivacyForm(index = 0) // stale callback of the first form
        assertTrue("the second form is still open", canLoadAds)
        assertFalse(session.showPrivacyOptions())

        ump.dismissPrivacyForm(index = 1)
        assertFalse(canLoadAds)
    }

    @Test
    fun `privacy form error - logged, and the stored consent decides`() {
        val ump = FakeConsentPlatform(canRequestAds = true)
        val session = consentedSession(ump)
        session.showPrivacyOptions()

        ump.dismissPrivacyForm(error = "offline")

        assertTrue(canLoadAds)
        assertEquals(listOf("Privacy options form failed (offline)"), logged)
        assertTrue("can be tried again", session.showPrivacyOptions())
    }

    @Test
    fun `Activity destroyed during the privacy form - callback ignored, next Activity re-reads consent`() {
        val first = FakeConsentPlatform(canRequestAds = true)
        val session = consentedSession(first)
        session.showPrivacyOptions()

        session.cancel() // e.g. rotation while the form is open
        first.canRequestAds = false // the user withdrew consent before the form went away
        first.dismissPrivacyForm()
        assertTrue("the destroyed Activity's callback is ignored", canLoadAds)
        assertFalse(session.showPrivacyOptions())
        assertEquals(1, first.privacyRequests)

        // The recreated Activity reads the stored choice at once.
        ads.gatherConsent(FakeConsentPlatform(canRequestAds = false, privacyOptionsRequired = true))
        assertFalse(canLoadAds)
        assertTrue(privacyOptionsRequired)
    }

    @Test
    fun `privacy form is not opened while the consent form is showing`() {
        val ump = FakeConsentPlatform(canRequestAds = false, privacyOptionsRequired = true)
        val session = ads.gatherConsent(ump)
        ump.succeedInfoUpdate()

        assertFalse(session.showPrivacyOptions())
        ump.dismissForm()
        assertTrue(session.showPrivacyOptions())
        assertEquals(1, ump.privacyRequests)
    }

    /** Fake UMP: records requests and keeps the callbacks so tests can fire them. */
    private class FakeConsentPlatform(
        var canRequestAds: Boolean,
        var privacyOptionsRequired: Boolean = false,
    ) : ConsentPlatform {
        var infoRequests = 0
            private set
        var formRequests = 0
            private set
        var canRequestAdsReads = 0
            private set
        private var onSuccess: (() -> Unit)? = null
        private var onFailure: ((String) -> Unit)? = null
        private var onDismissed: ((String?) -> Unit)? = null
        private val privacyCallbacks = mutableListOf<(String?) -> Unit>()
        val privacyRequests get() = privacyCallbacks.size

        override fun canRequestAds() = canRequestAds.also { canRequestAdsReads++ }

        override fun privacyOptionsRequired() = privacyOptionsRequired

        override fun showPrivacyOptionsForm(onDismissed: (errorMessage: String?) -> Unit) {
            privacyCallbacks += onDismissed
        }

        /** Fires the callback of the [index]th privacy form (the latest by default). */
        fun dismissPrivacyForm(error: String? = null, index: Int = privacyCallbacks.lastIndex) =
            privacyCallbacks[index](error)

        override fun requestConsentInfoUpdate(onSuccess: () -> Unit, onFailure: (message: String) -> Unit) {
            infoRequests++
            this.onSuccess = onSuccess
            this.onFailure = onFailure
        }

        override fun loadAndShowConsentFormIfRequired(onDismissed: (errorMessage: String?) -> Unit) {
            formRequests++
            this.onDismissed = onDismissed
        }

        fun succeedInfoUpdate() = checkNotNull(onSuccess)()

        fun failInfoUpdate(message: String) = checkNotNull(onFailure)(message)

        fun dismissForm(error: String? = null) = checkNotNull(onDismissed) { "no form requested" }(error)
    }
}
