package com.elabboubisolution.madconverter.ads

import com.elabboubisolution.madconverter.BuildConfig

/**
 * Google UMP testing options for debug builds: a forced [geography] (e.g. the EEA, to see the
 * consent form outside Europe) and the hashed IDs of physical test devices (emulators are test
 * devices already). Read from `local.properties` (`ump.debugGeography`, `ump.testDeviceIds`)
 * through BuildConfig; release builds always get empty values, and [parse] also refuses them.
 */
data class UmpDebugSettings(
    val geography: Geography?,
    val testDeviceHashedIds: List<String>,
) {
    enum class Geography { EEA, REGULATED_US_STATE, OTHER }

    companion object {
        private val HASHED_DEVICE_ID = Regex("[0-9A-Fa-f]{32}")

        /** This build's settings: null unless it is a debug build with something configured. */
        fun fromBuildConfig(onWarning: (String) -> Unit = {}): UmpDebugSettings? = parse(
            isDebugBuild = BuildConfig.DEBUG,
            geography = BuildConfig.UMP_DEBUG_GEOGRAPHY,
            testDeviceIds = BuildConfig.UMP_TEST_DEVICE_IDS,
            onWarning = onWarning,
        )

        /**
         * [geography]: one of [Geography]'s names, case ignored, or blank for none.
         * [testDeviceIds]: comma-separated hashed IDs, as UMP prints them in logcat.
         * Invalid entries are ignored with a warning. Null when nothing (valid) is configured,
         * and always null outside debug builds.
         */
        fun parse(
            isDebugBuild: Boolean,
            geography: String,
            testDeviceIds: String,
            onWarning: (String) -> Unit = {},
        ): UmpDebugSettings? {
            if (!isDebugBuild) return null
            val parsedGeography = geography.trim().takeIf { it.isNotEmpty() }?.let { name ->
                Geography.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
                    ?: null.also { onWarning("Unknown ump.debugGeography ignored; use one of ${Geography.entries}") }
            }
            val ids = testDeviceIds.split(',').map(String::trim).filter(String::isNotEmpty).filter { id ->
                HASHED_DEVICE_ID.matches(id).also { valid ->
                    if (!valid) onWarning("Malformed entry in ump.testDeviceIds ignored")
                }
            }
            return if (parsedGeography == null && ids.isEmpty()) null else UmpDebugSettings(parsedGeography, ids)
        }
    }
}
