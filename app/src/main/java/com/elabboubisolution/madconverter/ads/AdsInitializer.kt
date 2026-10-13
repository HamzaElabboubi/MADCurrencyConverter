package com.elabboubisolution.madconverter.ads

import android.content.Context
import android.util.Log
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Single entry point for user consent (Google UMP) and for starting the Mobile Ads SDK.
 *
 * One instance per process ([get]). Each Activity creation gathers consent in a [ConsentSession]:
 * the consent information is updated, then the consent form is shown if required. The SDK is
 * initialized at most once, and only when [ConsentPlatform.canRequestAds] allows it: consent
 * stored in an earlier session allows it right away, in parallel with the update.
 *
 * [canLoadAds] is the only signal for loading ads: true once consent allows ads **and** the SDK
 * has finished initializing; false again if a later update or the privacy options form no longer
 * allows ads. Without it the app works normally, without ads.
 *
 * [privacyOptionsRequired] tells the UI to offer the privacy options form
 * ([ConsentSession.showPrivacyOptions]), through which the user can change their choices.
 *
 * Consent callbacks arrive on the main thread; only SDK completion may come from elsewhere.
 */
class AdsInitializer(
    /** Starts the SDK; calls its argument when initialization has completed. */
    private val initializeMobileAds: (onComplete: () -> Unit) -> Unit,
    private val log: (String) -> Unit = {},
) {

    private val _canLoadAds = MutableStateFlow(false)
    val canLoadAds: StateFlow<Boolean> = _canLoadAds.asStateFlow()

    private val _privacyOptionsRequired = MutableStateFlow(false)
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    private var consentAllowsAds = false
    private var sdkInitialized = false
    private var sdkInitializationStarted = false
    private var activeSession: ConsentSession? = null

    /**
     * Gathers consent for a newly created Activity, replacing (cancelling) any session still
     * running for a previous one. The Activity must [cancel][ConsentSession.cancel] the returned
     * session when it is destroyed, so no callback reaches it afterwards.
     */
    fun gatherConsent(platform: ConsentPlatform): ConsentSession {
        activeSession?.cancel()
        return ConsentSession(platform).also {
            activeSession = it
            it.start()
        }
    }

    /** Reads the current answer and starts the SDK the first time ads are allowed. */
    private fun updateConsent(platform: ConsentPlatform) {
        _privacyOptionsRequired.value = platform.privacyOptionsRequired()
        val allowed = platform.canRequestAds()
        val startSdk = synchronized(this) {
            consentAllowsAds = allowed
            publish()
            (allowed && !sdkInitializationStarted).also { if (it) sdkInitializationStarted = true }
        }
        if (startSdk) initializeMobileAds(::onSdkInitialized)
    }

    private fun onSdkInitialized() = synchronized(this) {
        sdkInitialized = true
        publish()
    }

    private fun publish() {
        _canLoadAds.value = consentAllowsAds && sdkInitialized
    }

    /** One consent gathering for one Activity. Callbacks after [cancel] or repeated ones are ignored. */
    inner class ConsentSession internal constructor(platform: ConsentPlatform) {

        /** Dropped on [cancel], so the session no longer refers to the Activity. */
        private var platform: ConsentPlatform? = platform
        private var infoUpdateHandled = false
        private var formHandled = false
        private var consentFormShowing = false

        /** Id of the privacy options form being shown, 0 if none: older callbacks don't match it. */
        private var privacyFormId = 0
        private var lastPrivacyFormId = 0

        val isActive: Boolean get() = platform != null

        /** The Activity is going away: ignore every later callback of this session. */
        fun cancel() {
            platform = null
            if (activeSession === this) activeSession = null
        }

        internal fun start() {
            val current = platform ?: return
            current.requestConsentInfoUpdate(onSuccess = ::onInfoUpdated, onFailure = ::onInfoUpdateFailed)
            // Consent from an earlier session may already allow ads (Google's recommended flow).
            platform?.let(::updateConsent)
        }

        private fun onInfoUpdated() {
            val current = platform ?: return
            if (infoUpdateHandled) return
            infoUpdateHandled = true
            consentFormShowing = true
            current.loadAndShowConsentFormIfRequired(::onFormDismissed)
        }

        /** Offline or UMP error: whatever consent is stored on the device still decides. */
        private fun onInfoUpdateFailed(message: String) {
            val current = platform ?: return
            if (infoUpdateHandled) return
            infoUpdateHandled = true
            log("Consent information update failed ($message)")
            updateConsent(current)
        }

        private fun onFormDismissed(errorMessage: String?) {
            val current = platform ?: return
            if (formHandled) return
            formHandled = true
            consentFormShowing = false
            if (errorMessage != null) log("Consent form failed ($errorMessage)")
            updateConsent(current)
        }

        /**
         * Opens UMP's privacy options form, if UMP requires it and no form of this session is
         * showing (so repeated taps open one form). Returns whether it was requested. Once it
         * closes, consent is read again: if ads are no longer allowed, [canLoadAds] turns false.
         */
        fun showPrivacyOptions(): Boolean {
            val current = platform ?: return false
            if (consentFormShowing || privacyFormId != 0 || !current.privacyOptionsRequired()) return false
            val id = ++lastPrivacyFormId
            privacyFormId = id
            current.showPrivacyOptionsForm { errorMessage -> onPrivacyFormDismissed(id, errorMessage) }
            return true
        }

        private fun onPrivacyFormDismissed(id: Int, errorMessage: String?) {
            val current = platform ?: return
            if (id != privacyFormId) return
            privacyFormId = 0
            if (errorMessage != null) log("Privacy options form failed ($errorMessage)")
            updateConsent(current)
        }
    }

    companion object {
        private const val TAG = "AdsConsent"

        @Volatile
        private var instance: AdsInitializer? = null

        /** The process-wide instance: the SDK is initialized at most once per process. */
        fun get(context: Context): AdsInitializer = instance ?: synchronized(this) {
            instance ?: create(context.applicationContext).also { instance = it }
        }

        private fun create(appContext: Context) = AdsInitializer(
            initializeMobileAds = { onComplete ->
                // On a background thread, as Google recommends to avoid ANRs.
                CoroutineScope(Dispatchers.IO).launch {
                    MobileAds.initialize(appContext) { onComplete() }
                }
            },
            log = { Log.w(TAG, it) },
        )
    }
}
