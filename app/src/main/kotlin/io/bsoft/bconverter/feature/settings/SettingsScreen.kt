package io.bsoft.bconverter.feature.settings

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.bsoft.bconverter.R
import io.bsoft.bconverter.core.common.ByteFormat
import io.bsoft.bconverter.core.ui.BConverterTheme
import io.bsoft.bconverter.core.ui.BackButton
import io.bsoft.bconverter.core.ui.BrandBackdrop
import io.bsoft.bconverter.core.ui.ErrorBanner
import io.bsoft.bconverter.core.ui.IconTile
import io.bsoft.bconverter.core.ui.PremiumCard
import io.bsoft.bconverter.core.ui.QuietButton
import io.bsoft.bconverter.core.ui.readableWidth
import io.bsoft.bconverter.domain.settings.AppLanguage
import io.bsoft.bconverter.domain.settings.ThemeMode

@Composable
fun SettingsRoute(onBack: () -> Unit, onOpenFonts: () -> Unit = {}, viewModel: SettingsViewModel = hiltViewModel(), languageViewModel: LanguageViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val language by languageViewModel.language.collectAsStateWithLifecycle()
    SettingsContent(
        state = state,
        language = language,
        onLanguage = languageViewModel::select,
        dynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
        onBack = onBack,
        onTheme = viewModel::setThemeMode,
        onDynamicColor = viewModel::setDynamicColor,
        onClearCache = viewModel::clearCache,
        onOpenFonts = onOpenFonts,
        onCompositionPreview = viewModel::setCompositionPreview,
    )
}

/**
 * Settings, grouped into tonal cards: Appearance (theme previews you pick by sight, wallpaper colours), Language, Fonts and
 * Storage. Texts, roles and the order of the controls are unchanged from the previous screen, so TalkBack and the UI tests
 * read it the same way.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    state: SettingsUiState,
    dynamicColorSupported: Boolean,
    onBack: () -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onClearCache: () -> Unit,
    language: AppLanguage = AppLanguage.SYSTEM,
    onLanguage: (AppLanguage) -> Unit = {},
    onOpenFonts: () -> Unit = {},
    onCompositionPreview: (Boolean) -> Unit = {},
) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        BrandBackdrop()
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    title = { Text(stringResource(R.string.home_settings)) },
                    navigationIcon = { BackButton(onBack) },
                )
            },
        ) { padding ->
            Column(
                Modifier.padding(padding).readableWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // ---------------- appearance
                GroupTitle(R.string.settings_appearance)
                PremiumCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                    Column {
                        RowHeader(R.drawable.ic_palette, stringResource(R.string.settings_theme))
                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ThemeCard(R.string.settings_theme_system, ThemeMode.SYSTEM, state.settings.themeMode, onTheme, Modifier.weight(1f))
                            ThemeCard(R.string.settings_theme_light, ThemeMode.LIGHT, state.settings.themeMode, onTheme, Modifier.weight(1f))
                            ThemeCard(R.string.settings_theme_dark, ThemeMode.DARK, state.settings.themeMode, onTheme, Modifier.weight(1f))
                        }
                        if (dynamicColorSupported) {
                            HorizontalDivider(Modifier.padding(vertical = 14.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(MaterialTheme.shapes.medium)
                                    .toggleable(state.settings.dynamicColor, role = Role.Switch, onValueChange = onDynamicColor),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(R.string.settings_dynamic_color), style = MaterialTheme.typography.titleSmall)
                                    Text(stringResource(R.string.settings_dynamic_color_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = state.settings.dynamicColor, onCheckedChange = null,
                                    colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
                                )
                            }
                        }
                    }
                }

                // ---------------- language
                GroupTitle(R.string.settings_language)
                PremiumCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 6.dp)) {
                    Column(Modifier.selectableGroup()) {
                        AppLanguage.entries.forEachIndexed { i, option ->
                            if (i > 0) HorizontalDivider(Modifier.padding(start = 64.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                            LanguageRow(option, language, onLanguage)
                        }
                    }
                }

                // ---------------- fonts
                GroupTitle(R.string.home_fonts)
                PremiumCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button, onClick = onOpenFonts).padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        IconTile(R.drawable.ic_fonts, size = 40.dp, container = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.home_fonts), style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(R.string.settings_fonts_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // ---------------- storage
                GroupTitle(R.string.settings_storage)
                PremiumCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            IconTile(R.drawable.ic_storage, size = 40.dp, container = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f), tint = MaterialTheme.colorScheme.tertiary)
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.settings_cache_title), style = MaterialTheme.typography.titleSmall)
                                Text(
                                    stringResource(R.string.settings_cache_size, ByteFormat.format(state.cacheBytes)),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            QuietButton(stringResource(R.string.settings_clear_cache), onClearCache, enabled = !state.clearing && state.cacheBytes > 0)
                        }
                        state.freedBytes?.let {
                            Row(Modifier.padding(top = 12.dp).semantics { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(BConverterTheme.brand.success))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.settings_cache_cleared, ByteFormat.format(it)), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (state.clearFailed) {
                            Spacer(Modifier.height(12.dp))
                            ErrorBanner(stringResource(R.string.settings_cache_clear_failed))
                        }
                    }
                }
                // ---------------- labs
                GroupTitle(R.string.settings_labs)
                PremiumCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(MaterialTheme.shapes.medium)
                            .toggleable(state.settings.compositionPreview, role = Role.Switch, onValueChange = onCompositionPreview),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_composition_preview), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(R.string.settings_composition_preview_hint),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = state.settings.compositionPreview, onCheckedChange = null,
                            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun GroupTitle(@StringRes res: Int) {
    Text(
        stringResource(res),
        Modifier.padding(start = 4.dp, top = 12.dp, bottom = 2.dp).semantics { heading() },
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun RowHeader(@DrawableRes icon: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        IconTile(icon, size = 40.dp, container = MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f), tint = MaterialTheme.colorScheme.secondary)
        Text(text, style = MaterialTheme.typography.titleSmall)
    }
}

/** A theme you choose by its look: a miniature of the app in that theme, its name, and a brand ring when picked. */
@Composable
private fun ThemeCard(@StringRes label: Int, mode: ThemeMode, current: ThemeMode, onSelect: (ThemeMode) -> Unit, modifier: Modifier) {
    val selected = current == mode
    val brand = BConverterTheme.brand
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier
            .clip(shape)
            .selectable(selected, role = Role.RadioButton, onClick = { onSelect(mode) })
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(0.78f)
                .then(
                    if (selected) Modifier.border(2.5.dp, brand.signature, shape)
                    else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
                )
                .padding(5.dp)
                .clip(MaterialTheme.shapes.small),
        ) {
            ThemeMiniature(mode)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selected) {
                Box(Modifier.size(16.dp).clip(CircleShape).background(brand.signature), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_check), contentDescription = null, Modifier.size(11.dp), tint = Color.White)
                }
                Spacer(Modifier.width(6.dp))
            }
            Text(
                stringResource(label), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A tiny drawing of the app: a top bar, a gradient hero card and two tiles; "follow system" is split light / dark. */
@Composable
private fun ThemeMiniature(mode: ThemeMode) {
    val stops = BConverterTheme.brand.signatureStops
    Canvas(Modifier.fillMaxSize()) {
        fun scene(bg: Color, tile: Color, ink: Color) {
            drawRect(bg)
            val pad = size.width * 0.1f
            drawRoundRect(ink.copy(alpha = 0.55f), Offset(pad, pad), Size(size.width * 0.45f, size.height * 0.05f), CornerRadius(8f))
            drawRoundRect(
                androidx.compose.ui.graphics.Brush.linearGradient(stops, Offset(pad, 0f), Offset(size.width - pad, size.height * 0.4f)),
                Offset(pad, size.height * 0.16f), Size(size.width - 2 * pad, size.height * 0.32f), CornerRadius(14f),
            )
            val tileW = (size.width - 3 * pad) / 2
            drawRoundRect(tile, Offset(pad, size.height * 0.56f), Size(tileW, size.height * 0.3f), CornerRadius(12f))
            drawRoundRect(tile, Offset(2 * pad + tileW, size.height * 0.56f), Size(tileW, size.height * 0.3f), CornerRadius(12f))
        }
        val light = Triple(Color(0xFFF7F7FB), Color(0xFFDFDEE9), Color(0xFF14141B))
        val dark = Triple(Color(0xFF0B0B10), Color(0xFF282834), Color(0xFFECECF3))
        when (mode) {
            ThemeMode.LIGHT -> scene(light.first, light.second, light.third)
            ThemeMode.DARK -> scene(dark.first, dark.second, dark.third)
            ThemeMode.SYSTEM -> {
                scene(light.first, light.second, light.third)
                // The dark half, cut on the diagonal.
                val half = Path().apply { moveTo(size.width, 0f); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
                clipPath(half) { scene(dark.first, dark.second, dark.third) }
            }
        }
    }
}

/** One language: its own name (or "System default"), and a brand check when it is the current choice. */
@Composable
private fun LanguageRow(option: AppLanguage, current: AppLanguage, onSelect: (AppLanguage) -> Unit) {
    val selected = current == option
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp)
            .selectable(selected, role = Role.RadioButton, onClick = { onSelect(option) })
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (option == AppLanguage.SYSTEM) {
            IconTile(R.drawable.ic_language, size = 34.dp, container = MaterialTheme.colorScheme.surfaceContainerHighest)
        } else {
            // Decorative language code; the row is read by its full name.
            Box(Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
                Text(option.tag.take(2).uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(
            option.nativeName ?: stringResource(R.string.settings_language_system),
            Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
        )
        if (selected) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(BConverterTheme.brand.signature), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = null, Modifier.size(14.dp), tint = Color.White)
            }
        } else {
            Box(Modifier.size(22.dp).border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape))
        }
    }
}
