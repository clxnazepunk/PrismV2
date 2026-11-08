package com.prism.music.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prism.music.R
import com.prism.music.tr
import com.prism.music.data.ArtistFontMode
import com.prism.music.data.ArtistTypographyConfig
import com.prism.music.ui.components.PrismTextButton
import com.prism.music.ui.components.SignatureSample
import com.prism.music.ui.theme.ArtistTypography
import com.prism.music.ui.theme.LocalContainer

@Composable
internal fun ArtistTypographySettings() {
    val prefs = LocalContainer.current.artistPrefs
    val config by prefs.typography.collectAsState()
    var fontsOpen by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Text(tr(R.string.artist_font_settings), style = MaterialTheme.typography.titleMedium)
        Text(tr(R.string.artist_font_hint), style = MaterialTheme.typography.bodySmall)
        for (mode in listOf(ArtistFontMode.FIXED, ArtistFontMode.RANDOM, ArtistFontMode.PRISM)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(config.mode == mode, { prefs.setTypography(config.copy(mode = mode)) })
                Text(tr(when (mode) {
                    ArtistFontMode.FIXED -> R.string.artist_font_fixed
                    ArtistFontMode.RANDOM -> R.string.artist_font_random
                    else -> R.string.artist_font_original
                }))
            }
        }
        Text(tr(R.string.artist_font_fallback), style = MaterialTheme.typography.labelLarge)
        val chosen = ArtistTypography.byKey(config.fallback) ?: ArtistTypography.standard
        Box {
            PrismTextButton(onClick = { fontsOpen = true }) { Text(chosen.label) }
            DropdownMenu(fontsOpen, { fontsOpen = false }) {
                for (font in ArtistTypography.all) DropdownMenuItem(
                    text = { SignatureSample(font.label, font) },
                    onClick = { prefs.setTypography(config.copy(fallback = font.key)); fontsOpen = false },
                )
            }
        }
        PrismTextButton(onClick = { prefs.setTypography(ArtistTypographyConfig()) }) { Text(tr(R.string.reset)) }
    }
}
