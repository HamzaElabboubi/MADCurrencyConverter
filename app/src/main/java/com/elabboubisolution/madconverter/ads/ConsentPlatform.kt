package com.elabboubisolution.madconverter.ads

import android.app.Activity
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentInformation.PrivacyOptionsRequirementStatus
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/**
 * The consent operations the app needs from the User Messaging Platform, for one Activity.
 * [UmpConsentPlatform] is the real one; tests use a fake to drive the callbacks.
 */
interface ConsentPlatform {

    /** Whether ads may be requested, from the consent stored on the device and the latest update. */
    fun canRequestAds(): Boolean

    /**
     * Whether UMP requires a way to change the consent choices (its status is `REQUIRED`).
     * Unknown until a consent information update has succeeded, and then reported as false.
     */
    fun privacyOptionsRequired(): Boolean

    /** Updates the consent information (needs the network); exactly one callback is expected. */
    fun requestConsentInfoUpdate(onSuccess: () -> Unit, onFailure: (message: String) -> Unit)

    /**
     * Shows the consent form only if it is required. [onDismissed] is called either way: right
     * away when no form is needed, with an error message if the form could not be loaded or shown.
     */
    fun loadAndShowConsentFormIfRequired(onDismissed: (errorMessage: String?) -> Unit)

    /** Shows UMP's privacy options form; [onDismissed] gets an error message if it could not be shown. */
    fun showPrivacyOptionsForm(onDismissed: (errorMessage: String?) -> Unit)
}

/**
 * [ConsentPlatform] backed by Google's UMP SDK. UMP calls back on the main thread.
 *
 * [debugSettings] (debug builds only, see [UmpDebugSettings.fromBuildConfig]) adds UMP's own
 * testing options: a debug geography and test devices. Null in release builds.
 */
class UmpConsentPlatform(
    private val activity: Activity,
    private val debugSettings: UmpDebugSettings? = null,
) : ConsentPlatform {

    private val consentInformation: ConsentInformation =
        UserMessagingPlatform.getConsentInformation(activity)

    override fun canRequestAds(): Boolean = consentInformation.canRequestAds()

    override fun privacyOptionsRequired(): Boolean =
        consentInformation.privacyOptionsRequirementStatus == PrivacyOptionsRequirementStatus.REQUIRED

    override fun requestConsentInfoUpdate(onSuccess: () -> Unit, onFailure: (message: String) -> Unit) {
        val parameters = ConsentRequestParameters.Builder()
        debugSettings?.let { parameters.setConsentDebugSettings(it.toUmpSettings()) }
        consentInformation.requestConsentInfoUpdate(
            activity,
            parameters.build(),
            { onSuccess() },
            { error -> onFailure("${error.errorCode}: ${error.message}") },
        )
    }

    override fun loadAndShowConsentFormIfRequired(onDismissed: (errorMessage: String?) -> Unit) {
        UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
            onDismissed(error?.let { "${it.errorCode}: ${it.message}" })
        }
    }

    override fun showPrivacyOptionsForm(onDismissed: (errorMessage: String?) -> Unit) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            onDismissed(error?.let { "${it.errorCode}: ${it.message}" })
        }
    }

    private fun UmpDebugSettings.toUmpSettings(): ConsentDebugSettings =
        ConsentDebugSettings.Builder(activity).apply {
            geography?.let {
                setDebugGeography(
                    when (it) {
                        UmpDebugSettings.Geography.EEA -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA
                        UmpDebugSettings.Geography.REGULATED_US_STATE ->
                            ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_REGULATED_US_STATE
                        UmpDebugSettings.Geography.OTHER -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_OTHER
                    },
                )
            }
            testDeviceHashedIds.forEach(::addTestDeviceHashedId)
        }.build()
}
