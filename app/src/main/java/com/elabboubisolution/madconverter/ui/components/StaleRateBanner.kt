package com.elabboubisolution.madconverter.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.elabboubisolution.madconverter.R
import com.elabboubisolution.madconverter.ui.theme.MADCurrencyConverterTheme

/**
 * Shown above cached rates that could not be refreshed, so they are never mistaken for live ones.
 * [reason] explains the failed refresh; while [isRefreshing], a progress indicator replaces Retry.
 */
@Composable
fun StaleRateBanner(
    reason: String?,
    isRefreshing: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(start = 20.dp, top = 16.dp, end = 12.dp, bottom = 8.dp)) {
            Column(modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
                Text(
                    text = stringResource(R.string.stale_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = listOfNotNull(reason, stringResource(R.string.stale_message)).joinToString(" "),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(end = 8.dp, top = 4.dp),
                )
            }
            Box(modifier = Modifier.align(Alignment.End).padding(vertical = 4.dp)) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(12.dp).size(24.dp),
                        strokeWidth = 3.dp,
                    )
                } else {
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                }
            }
        }
    }
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
