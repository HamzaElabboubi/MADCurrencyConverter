package com.elabboubisolution.madconverter.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.elabboubisolution.madconverter.R
import com.elabboubisolution.madconverter.ui.theme.MADCurrencyConverterTheme

@Composable
fun SwapButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalIconButton(onClick = onClick, modifier = modifier) {
        Icon(
            painter = painterResource(R.drawable.ic_swap_horiz),
            contentDescription = stringResource(R.string.swap_currencies),
        )
    }
}

@PreviewLightDark
@Composable
private fun SwapButtonPreview() {
    MADCurrencyConverterTheme {
        Surface {
            SwapButton(onClick = {}, modifier = Modifier.padding(16.dp))
        }
    }
}
