package com.elabboubisolution.madconverter.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.elabboubisolution.madconverter.R
import com.elabboubisolution.madconverter.ui.theme.MADCurrencyConverterTheme
import com.elabboubisolution.madconverter.ui.theme.extendedColors

/**
 * Shown above cached rates that could not be refreshed, so they are never mistaken for live ones.
 * [reason] explains the failed refresh; while [isRefreshing], a progress indicator replaces Retry.
 * Warning style (gold, warning icon), never the error style: the cached rates stay usable.
 */
@Composable
fun StaleRateBanner(
    reason: String?,
    isRefreshing: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StatusCard(
        iconRes = R.drawable.ic_warning,
        title = stringResource(R.string.stale_title),
        message = listOfNotNull(reason, stringResource(R.string.stale_message)).joinToString(" "),
        containerColor = MaterialTheme.extendedColors.warningContainer,
        contentColor = MaterialTheme.extendedColors.onWarningContainer,
        onRetry = onRetry,
        isRetrying = isRefreshing,
        modifier = modifier,
    )
}

@PreviewLightDark
@Composable
private fun StaleRateBannerPreview() {
    MADCurrencyConverterTheme {
        Surface {
            StaleRateBanner(
                reason = stringResource(R.string.error_no_connection),
                isRefreshing = false,
                onRetry = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun StaleRateBannerRefreshingPreview() {
    MADCurrencyConverterTheme {
        Surface {
            StaleRateBanner(reason = null, isRefreshing = true, onRetry = {}, modifier = Modifier.padding(16.dp))
        }
    }
}
