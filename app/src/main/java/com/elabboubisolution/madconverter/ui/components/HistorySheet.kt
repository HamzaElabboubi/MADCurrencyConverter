package com.elabboubisolution.madconverter.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.elabboubisolution.madconverter.R
import com.elabboubisolution.madconverter.domain.model.Currency
import com.elabboubisolution.madconverter.domain.model.HistoryEntry
import com.elabboubisolution.madconverter.ui.format.ConversionText
import com.elabboubisolution.madconverter.ui.format.formatHistoryTimestamp
import com.elabboubisolution.madconverter.ui.format.ltr
import com.elabboubisolution.madconverter.ui.theme.Dimens
import com.elabboubisolution.madconverter.ui.theme.MADCurrencyConverterTheme
import com.elabboubisolution.madconverter.ui.theme.extendedColors
import com.elabboubisolution.madconverter.ui.theme.tabularFigures
import java.math.BigDecimal
import kotlinx.coroutines.launch

/** Full-height sheet listing copied/shared conversions; selecting one closes it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorySheet(
    entries: List<HistoryEntry>,
    onSelect: (HistoryEntry) -> Unit,
    onDelete: (HistoryEntry) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        HistoryContent(
            entries = entries,
            onSelect = { entry ->
                onSelect(entry)
                scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
            },
            onDelete = onDelete,
            onClearAll = onClearAll,
            modifier = Modifier.fillMaxHeight(),
        )
    }
}

@Composable
fun HistoryContent(
    entries: List<HistoryEntry>,
    onSelect: (HistoryEntry) -> Unit,
    onDelete: (HistoryEntry) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Title on the rows' 16dp edge; "Clear all" text (12dp inside its button) on the 16dp end edge.
                .padding(start = Dimens.EdgePadding, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.history_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            if (entries.isNotEmpty()) {
                TextButton(onClick = { confirmClear = true }) { Text(stringResource(R.string.history_clear_all)) }
            }
        }

        if (entries.isEmpty()) {
            HistoryEmptyState()
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(entries, key = { it.id }) { entry ->
                    HistoryRow(entry = entry, onClick = { onSelect(entry) }, onDelete = { onDelete(entry) })
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.history_clear_title)) },
            text = { Text(stringResource(R.string.history_clear_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClearAll()
                }) { Text(stringResource(R.string.history_clear_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, onClick: () -> Unit, onDelete: () -> Unit) {
    val source = ConversionText.amount(entry.amount, entry.from)
    val target = ConversionText.money(entry.convertedAmount, entry.to)
    val time = formatHistoryTimestamp(entry.timestampMillis)
    val conversionLabel = ltr("$source → $target")
    val description = stringResource(R.string.history_entry_description, source, target, time)
    val clickLabel = stringResource(R.string.action_convert_again)
    ListItem(
        headlineContent = {
            // Never truncated: a cut-off amount would be misleading.
            Text(conversionLabel, style = MaterialTheme.typography.bodyLarge.tabularFigures())
        },
        supportingContent = {
            Column {
                Text(time)
                if (entry.wasStale) {
                    Text(
                        text = stringResource(R.string.history_stale_rate),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.extendedColors.warning,
                    )
                }
            }
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(
                    painterResource(R.drawable.ic_delete),
                    stringResource(R.string.history_delete_entry, "$source → $target"),
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .clickable(onClickLabel = clickLabel, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    )
}

@Composable
private fun HistoryEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_history),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.history_empty_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.history_empty_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@PreviewLightDark
@Composable
private fun HistoryPreview() {
    val now = System.currentTimeMillis()
    MADCurrencyConverterTheme {
        Surface {
            HistoryContent(
                entries = listOf(
                    HistoryEntry("1", Currency.MAD, Currency.USD, BigDecimal("1000"), BigDecimal("100.30"), BigDecimal("0.1003"), now, false),
                    HistoryEntry("2", Currency.EUR, Currency.MAD, BigDecimal("250"), BigDecimal("2795.20"), BigDecimal("11.1808"), now - 86_400_000, true),
                ),
                onSelect = {},
                onDelete = {},
                onClearAll = {},
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun HistoryEmptyPreview() {
    MADCurrencyConverterTheme {
        Surface { HistoryContent(entries = emptyList(), onSelect = {}, onDelete = {}, onClearAll = {}) }
    }
}
