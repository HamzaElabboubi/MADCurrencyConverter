package com.elabboubisolution.madconverter.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.elabboubisolution.madconverter.R
import com.elabboubisolution.madconverter.ui.theme.MADCurrencyConverterTheme

/** About the height of the result card, so the screen does not jump when rates arrive. */
private val LOADING_PLACEHOLDER_MIN_HEIGHT = 144.dp

/**
 * First load, nothing to show yet: a quiet placeholder in the result's place. It is not a live
 * region, so TalkBack does not interrupt; the text and the progress indicator are read together.
 */
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = LOADING_PLACEHOLDER_MIN_HEIGHT),
        shape = CardDefaults.shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
            Text(stringResource(R.string.loading_rates), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/**
 * Error card. [title] states the problem, [message] the reason. With [onRetry], a Retry button;
 * while [isRetrying], a progress indicator replaces it so the card stays in place.
 */
@Composable
fun ErrorState(
    message: String,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
    title: String? = null,
    isRetrying: Boolean = false,
) {
    StatusCard(
        iconRes = R.drawable.ic_error,
        title = title,
        message = message,
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        onRetry = onRetry,
        isRetrying = isRetrying,
        modifier = modifier,
    )
}

/**
 * Shared layout of the error and stale-rate cards: an icon (so the meaning never relies on
 * color alone), an optional title, the message, then Retry or a progress indicator. Title and
 * message form one polite live region: announced when the card appears, not on every retry.
 */
@Composable
internal fun StatusCard(
    @DrawableRes iconRes: Int,
    title: String?,
    message: String,
    containerColor: Color,
    contentColor: Color,
    onRetry: (() -> Unit)?,
    isRetrying: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 12.dp, bottom = 4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                        .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (title != null) {
                        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        text = message,
                        style = if (title != null) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            if (onRetry != null) {
                val loading = stringResource(R.string.loading_rates)
                Box(
                    modifier = Modifier
                        .align(Alignment.End)
                        .heightIn(min = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isRetrying) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .size(24.dp)
                                .semantics { contentDescription = loading },
                            color = contentColor,
                            strokeWidth = 3.dp,
                        )
                    } else {
                        // Same color as the text: the default primary color lacks contrast on
                        // the error and warning containers.
                        TextButton(
                            onClick = onRetry,
                            colors = ButtonDefaults.textButtonColors(contentColor = contentColor),
                        ) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun HintMessage(message: String, modifier: Modifier = Modifier) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(vertical = 16.dp),
    )
}

@PreviewLightDark
@Composable
private fun LoadingStatePreview() {
    MADCurrencyConverterTheme {
        Surface { LoadingState(Modifier.padding(16.dp)) }
    }
}

@PreviewLightDark
@Composable
private fun ErrorStatePreview() {
    MADCurrencyConverterTheme {
        Surface {
            ErrorState(
                message = stringResource(R.string.error_no_connection),
                onRetry = {},
                modifier = Modifier.padding(16.dp),
                title = stringResource(R.string.rates_unavailable_title),
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun HintMessagePreview() {
    MADCurrencyConverterTheme {
        Surface { HintMessage(stringResource(R.string.enter_amount_hint), Modifier.padding(16.dp)) }
    }
}
