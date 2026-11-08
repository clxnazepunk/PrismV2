package com.prism.music.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prism.music.R
import com.prism.music.tr
import com.prism.music.data.*
import com.prism.music.data.meta.Genres
import com.prism.music.data.meta.MusicTags
import com.prism.music.ui.components.PrismTextButton
import com.prism.music.ui.components.SignatureSample
import com.prism.music.ui.theme.ArtistTypography
import com.prism.music.ui.theme.LocalContainer
import java.util.UUID

@Composable
internal fun ArtistTypographySettings() {
    val prefs = LocalContainer.current.artistPrefs
    val config by prefs.typography.collectAsState()
    var editing by remember { mutableStateOf<ArtistFontGroup?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Text(tr(R.string.artist_font_settings), style = MaterialTheme.typography.titleMedium)
        Text(tr(R.string.artist_font_hint), style = MaterialTheme.typography.bodySmall)
        for (mode in ArtistFontMode.entries) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(config.mode == mode, { prefs.setTypography(config.copy(mode = mode)) })
                Text(tr(when (mode) {
                    ArtistFontMode.FIXED -> R.string.artist_font_fixed
                    ArtistFontMode.RANDOM -> R.string.artist_font_random
                    ArtistFontMode.SMART -> R.string.artist_font_smart
                    ArtistFontMode.PRISM -> R.string.artist_font_original
                }))
            }
        }
        Text(tr(R.string.artist_font_fallback), style = MaterialTheme.typography.labelLarge)
        ArtistFontPicker(config.fallback) { prefs.setTypography(config.copy(fallback = it)) }
        if (config.mode == ArtistFontMode.SMART) {
            Text(tr(R.string.artist_font_genres_hint), style = MaterialTheme.typography.bodySmall)
            for (group in config.groups) {
                Column {
                    Text(group.genres.joinToString { MusicTags.shown(it) }, style = MaterialTheme.typography.bodyMedium)
                    Text(ArtistTypography.byKey(group.font)?.label ?: ArtistTypography.standard.label,
                        style = MaterialTheme.typography.bodySmall)
                    Row {
                        PrismTextButton(onClick = { editing = group }) { Text(tr(R.string.edit)) }
                        PrismTextButton(onClick = { prefs.setTypography(config.copy(groups = config.groups.filterNot { it.id == group.id })) }) { Text(tr(R.string.remove)) }
                    }
                }
            }
            PrismTextButton(onClick = { editing = ArtistFontGroup(UUID.randomUUID().toString(), emptySet(), config.fallback) },
                enabled = config.groups.flatMap { it.genres }.toSet().size < MusicTags.typographyGenres.size) {
                Text(tr(R.string.artist_font_add_group))
            }
        }
        PrismTextButton(onClick = { prefs.setTypography(ArtistTypographyConfig()) }) { Text(tr(R.string.reset)) }
    }
    editing?.let { group ->
        ArtistFontGroupEditor(group, config, onDismiss = { editing = null }, onSave = { next ->
            // Re-read current settings so another update cannot erase other groups.
            val current = prefs.typography.value
            val groups = current.groups.toMutableList()
            val index = groups.indexOfFirst { it.id == next.id }
            if (index >= 0) groups[index] = next else groups += next
            prefs.setTypography(current.copy(groups = groups))
            editing = null
        })
    }
}

@Composable
private fun ArtistFontPicker(key: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val chosen = ArtistTypography.byKey(key) ?: ArtistTypography.standard
    Box {
        PrismTextButton(onClick = { open = true }) { Text(chosen.label) }
        DropdownMenu(open, { open = false }) {
            for (font in ArtistTypography.all) DropdownMenuItem(
                text = { SignatureSample(font.label, font) },
                onClick = { onPick(font.key); open = false },
            )
        }
    }
}

@Composable
private fun ArtistFontGroupEditor(
    initial: ArtistFontGroup, config: ArtistTypographyConfig,
    onDismiss: () -> Unit, onSave: (ArtistFontGroup) -> Unit,
) {
    var font by remember(initial.id) { mutableStateOf(initial.font) }
    var genres by remember(initial.id) { mutableStateOf(initial.genres) }
    val used = config.groups.filterNot { it.id == initial.id }.flatMap { it.genres }.toSet()
    AlertDialog(onDismissRequest = onDismiss, title = { Text(tr(R.string.artist_font_smart)) },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                ArtistFontPicker(font) { font = it }
                Text(tr(R.string.artist_font_genres_hint), style = MaterialTheme.typography.bodySmall)
                for (genre in Genres.all) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(genre in genres, { selected -> genres = if (selected) genres + genre else genres - genre },
                        enabled = genre !in used)
                    Column {
                        Text(MusicTags.shown(genre), color = if (genre in used) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                        if (genre in used) Text(tr(R.string.artist_font_used), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        },
        confirmButton = { PrismTextButton(onClick = { onSave(initial.copy(font = font, genres = genres - used)) },
            enabled = (genres - used).isNotEmpty()) { Text(tr(R.string.save)) } },
        dismissButton = { PrismTextButton(onClick = onDismiss) { Text(tr(R.string.cancel)) } },
    )
}
