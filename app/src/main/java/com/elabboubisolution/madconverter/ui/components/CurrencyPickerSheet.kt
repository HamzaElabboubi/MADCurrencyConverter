package com.elabboubisolution.madconverter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.elabboubisolution.madconverter.R
import com.elabboubisolution.madconverter.domain.CurrencySearch
import com.elabboubisolution.madconverter.domain.model.Currency
import com.elabboubisolution.madconverter.ui.format.currentLocale
import com.elabboubisolution.madconverter.ui.format.localizedName
import com.elabboubisolution.madconverter.ui.theme.Dimens
import com.elabboubisolution.madconverter.ui.theme.MADCurrencyConverterTheme
import kotlinx.coroutines.launch

/** Full-height bottom sheet to pick a currency; closes itself after a selection. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyPickerSheet(
    title: String,
    selected: Currency,
    favorites: Set<Currency>,
    onSelect: (Currency) -> Unit,
    onToggleFavorite: (Currency) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        CurrencyPickerContent(
            title = title,
            selected = selected,
            favorites = favorites,
            onSelect = { currency ->
                onSelect(currency)
                scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
            },
            onToggleFavorite = onToggleFavorite,
            // Fixed full height: the sheet must not jump while search results change.
            modifier = Modifier.fillMaxHeight(),
        )
    }
}

@Composable
fun CurrencyPickerContent(
    title: String,
    selected: Currency,
    favorites: Set<Currency>,
    onSelect: (Currency) -> Unit,
    onToggleFavorite: (Currency) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val locale = currentLocale()
    val listing = remember(query, locale, favorites) { CurrencySearch.search(query, locale, favorites) }
    val focusManager = LocalFocusManager.current

    Column(modifier = modifier.imePadding()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .padding(horizontal = Dimens.EdgePadding)
                .semantics { heading() },
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.EdgePadding, vertical = 12.dp),
            placeholder = { Text(stringResource(R.string.search_currency_hint)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { query = "" }) {
                        Icon(painterResource(R.drawable.ic_close), stringResource(R.string.clear_search))
                    }
                }
            } else {
                null
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        )

        if (listing.isEmpty) {
            Text(
                text = stringResource(R.string.no_currency_found, query.trim()),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Dimens.EdgePadding, vertical = 16.dp),
            )
        }

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            fun LazyListScope.section(headerRes: Int, currencies: List<Currency>, keyPrefix: String) {
                if (currencies.isEmpty()) return
                item(key = "$keyPrefix-header") { SectionHeader(stringResource(headerRes)) }
                items(currencies, key = { "$keyPrefix-${it.code}" }) { currency ->
                    CurrencyRow(
                        currency = currency,
                        isSelected = currency == selected,
                        isFavorite = currency in favorites,
                        onClick = { onSelect(currency) },
                        onToggleFavorite = { onToggleFavorite(currency) },
                    )
                }
            }
            section(R.string.favorites_header, listing.favorites, "fav")
            section(
                if (listing.favorites.isEmpty()) R.string.all_currencies_header else R.string.other_currencies_header,
                listing.others,
                "all",
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = Dimens.EdgePadding, end = Dimens.EdgePadding, top = 12.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

@Composable
private fun CurrencyRow(
    currency: Currency,
    isSelected: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(currency.code, fontWeight = FontWeight.SemiBold) },
        supportingContent = {
            Text(currency.localizedName(), maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = { CurrencyBadge(currency) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSelected) {
                    // Decorative: the row is a selectable radio button that already says "Selected".
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                // Fixed name ("Favorite: EUR"); the toggle itself announces checked / not checked.
                IconToggleButton(checked = isFavorite, onCheckedChange = { onToggleFavorite() }) {
                    Icon(
                        painter = painterResource(if (isFavorite) R.drawable.ic_star else R.drawable.ic_star_border),
                        contentDescription = stringResource(R.string.favorite_currency, currency.code),
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(
            // Unselected rows blend with the sheet; the selected one is highlighted.
            containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        ),
        modifier = Modifier.selectable(selected = isSelected, onClick = onClick, role = Role.RadioButton),
    )
}

/** Symbol (or code) in a circle: a visual cue that does not rely on emoji flags. */
@Composable
private fun CurrencyBadge(currency: Currency) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = currency.symbol ?: currency.code,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@PreviewLightDark
@Composable
private fun CurrencyPickerPreview() {
    MADCurrencyConverterTheme {
        Surface {
            CurrencyPickerContent(
                title = "Convert to",
                selected = Currency.USD,
                favorites = setOf(Currency.MAD, Currency.EUR, Currency.USD),
                onSelect = {},
                onToggleFavorite = {},
            )
        }
    }
}
