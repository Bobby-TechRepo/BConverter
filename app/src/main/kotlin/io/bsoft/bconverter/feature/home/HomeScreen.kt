package io.bsoft.bconverter.feature.home

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.bsoft.bconverter.R
import io.bsoft.bconverter.core.ui.BConverterTheme
import io.bsoft.bconverter.core.ui.BrandBackdrop
import io.bsoft.bconverter.core.ui.IconTile
import io.bsoft.bconverter.core.ui.PremiumCard
import io.bsoft.bconverter.core.ui.LocalWindowLayout
import io.bsoft.bconverter.core.ui.readableWidth
import io.bsoft.bconverter.domain.project.ProjectSummary
import io.bsoft.bconverter.feature.converter.formatDuration
import java.text.DateFormat
import java.util.Date

@DrawableRes
internal fun HomeDestination.iconRes(): Int = when (this) {
    HomeDestination.CONVERT_VIDEO -> R.drawable.ic_convert_video
    HomeDestination.EXTRACT_AUDIO -> R.drawable.ic_extract_audio
    HomeDestination.EXTRACT_SUBTITLES -> R.drawable.ic_subtitles
    HomeDestination.VIDEO_EDITOR -> R.drawable.ic_video_editor
    HomeDestination.RECENT_PROJECTS -> R.drawable.ic_projects
    HomeDestination.JOBS -> R.drawable.ic_jobs
    HomeDestination.FONTS -> R.drawable.ic_fonts
    HomeDestination.SETTINGS -> R.drawable.ic_settings
    HomeDestination.ABOUT -> R.drawable.ic_about
}

/** One-line description under a tool's name. */
@StringRes
private fun HomeDestination.hintRes(): Int? = when (this) {
    HomeDestination.CONVERT_VIDEO -> R.string.home_convert_hint
    HomeDestination.EXTRACT_AUDIO -> R.string.home_audio_hint
    HomeDestination.EXTRACT_SUBTITLES -> R.string.home_subtitles_hint
    HomeDestination.VIDEO_EDITOR -> R.string.home_editor_hint
    HomeDestination.ABOUT -> R.string.home_about_hint
    else -> null
}

private val quickTools = listOf(HomeDestination.CONVERT_VIDEO, HomeDestination.EXTRACT_AUDIO, HomeDestination.EXTRACT_SUBTITLES)

/**
 * Home: the editor as the hero (it is the app's centrepiece), quick single-purpose tools underneath, then the user's
 * recent work, then About. Settings is a top-bar action. Only destinations in [state] are shown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onDestinationClick: (HomeDestination) -> Unit,
    modifier: Modifier = Modifier,
    recentProjects: List<ProjectSummary> = emptyList(),
    onOpenProject: (String) -> Unit = {},
) {
    val shown = state.destinations.toSet()
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        BrandBackdrop()
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    title = { Wordmark() },
                    actions = {
                        IconButton(onClick = { onDestinationClick(HomeDestination.JOBS) }, Modifier.size(48.dp)) {
                            Icon(painterResource(HomeDestination.JOBS.iconRes()), contentDescription = stringResource(HomeDestination.JOBS.titleRes))
                        }
                        IconButton(onClick = { onDestinationClick(HomeDestination.SETTINGS) }, Modifier.size(48.dp)) {
                            Icon(painterResource(HomeDestination.SETTINGS.iconRes()), contentDescription = stringResource(HomeDestination.SETTINGS.titleRes))
                        }
                    },
                )
            },
        ) { padding ->
            val window = LocalWindowLayout.current
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .readableWidth(window.contentMaxWidth)
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    stringResource(R.string.home_tagline),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val create: @Composable () -> Unit = {
                    if (HomeDestination.VIDEO_EDITOR in shown) EditorHero { onDestinationClick(HomeDestination.VIDEO_EDITOR) }
                    val tools = quickTools.filter { it in shown }
                    if (tools.isNotEmpty()) {
                        SectionHeader(stringResource(R.string.home_quick_tools))
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            tools.forEach { d -> ToolTile(d, Modifier.weight(1f).fillMaxHeight()) { onDestinationClick(d) } }
                        }
                    }
                }
                val continueWork: @Composable () -> Unit = {
                    if (HomeDestination.RECENT_PROJECTS in shown) {
                        RecentHeader { onDestinationClick(HomeDestination.RECENT_PROJECTS) }
                        RecentStrip(recentProjects, onOpenProject)
                    }
                    if (HomeDestination.ABOUT in shown) AboutRow { onDestinationClick(HomeDestination.ABOUT) }
                }
                if (window.twoPane) {
                    // Wide window (step 21): making something new on the left, picking up earlier work on the right.
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Column(Modifier.weight(1.1f), verticalArrangement = Arrangement.spacedBy(16.dp)) { create() }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) { continueWork() }
                    }
                } else {
                    create()
                    continueWork()
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// ---------------------------------------------------------------- pieces

@Composable
private fun Wordmark() {
    val brand = BConverterTheme.brand
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(brand.signature), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_play), contentDescription = null, Modifier.size(16.dp), tint = Color.White)
        }
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, Modifier.padding(top = 4.dp).semantics { heading() }, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun EditorHero(onClick: () -> Unit) {
    val brand = BConverterTheme.brand
    Surface(onClick = onClick, shape = MaterialTheme.shapes.extraLarge, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.background(brand.signature).heightIn(min = 188.dp)) {
            // Depth: two soft light discs and a film-strip motif, purely decorative.
            Canvas(Modifier.matchParentSize()) {
                drawCircle(Color.White.copy(alpha = 0.10f), radius = size.height * 0.9f, center = Offset(size.width * 1.02f, -size.height * 0.1f))
                drawCircle(Color.White.copy(alpha = 0.07f), radius = size.height * 0.55f, center = Offset(size.width * 0.78f, size.height * 1.05f))
                val hole = 7.dp.toPx(); val gap = 9.dp.toPx(); val y = size.height - 16.dp.toPx()
                var x = size.width * 0.52f
                while (x < size.width) { drawRoundRect(Color.White.copy(alpha = 0.10f), Offset(x, y), androidx.compose.ui.geometry.Size(hole * 1.6f, hole), androidx.compose.ui.geometry.CornerRadius(2f)); x += hole * 1.6f + gap }
            }
            Column(Modifier.padding(22.dp)) {
                IconTile(R.drawable.ic_video_editor, size = 48.dp, container = Color.White.copy(alpha = 0.18f), tint = Color.White)
                Spacer(Modifier.height(18.dp))
                Text(stringResource(HomeDestination.VIDEO_EDITOR.titleRes), style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Text(stringResource(R.string.home_editor_hint), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.88f))
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.clip(CircleShape).background(Color.White).padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(R.drawable.ic_tool_add_video), contentDescription = null, Modifier.size(18.dp), tint = brand.signatureStops[0])
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.home_new_project), style = MaterialTheme.typography.labelLarge, color = Color(0xFF1B1060))
                }
            }
        }
    }
}

@Composable
private fun ToolTile(destination: HomeDestination, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val accent = when (destination) {
        HomeDestination.CONVERT_VIDEO -> colors.primary
        HomeDestination.EXTRACT_AUDIO -> colors.tertiary
        else -> colors.secondary
    }
    PremiumCard(modifier = modifier.heightIn(min = 148.dp), onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            IconTile(destination.iconRes(), size = 42.dp, container = accent.copy(alpha = 0.16f), tint = accent)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(destination.titleRes), style = MaterialTheme.typography.titleSmall, maxLines = 2)
            destination.hintRes()?.let {
                Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun RecentHeader(onSeeAll: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(onClick = onSeeAll).heightIn(min = 48.dp).padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(HomeDestination.RECENT_PROJECTS.titleRes), Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.home_see_all), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun RecentStrip(projects: List<ProjectSummary>, onOpen: (String) -> Unit) {
    if (projects.isEmpty()) {
        PremiumCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(HomeDestination.RECENT_PROJECTS.iconRes(), size = 40.dp)
                Spacer(Modifier.width(14.dp))
                Text(stringResource(R.string.projects_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 8.dp)) {
        items(projects.take(MAX_RECENT), key = { it.id }) { p -> RecentCard(p) { onOpen(p.id) } }
    }
}

@Composable
private fun RecentCard(project: ProjectSummary, onClick: () -> Unit) {
    val brand = BConverterTheme.brand
    val modified = remember(project.modifiedAtMs) { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(project.modifiedAtMs)) }
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.width(176.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), MaterialTheme.shapes.large),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(100.dp).background(brand.signatureWash), contentAlignment = Alignment.Center) {
                Text(
                    project.name.trim().take(1).uppercase().ifEmpty { "•" },
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                )
                Text(
                    formatDuration(project.durationMs),
                    Modifier.align(Alignment.BottomEnd).padding(8.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall, color = Color.White,
                )
            }
            Column(Modifier.padding(12.dp)) {
                Text(project.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    pluralStringResource(R.plurals.projects_clip_count, project.clipCount, project.clipCount) + " · " + modified,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AboutRow(onClick: () -> Unit) {
    PremiumCard(Modifier.fillMaxWidth(), onClick = onClick, color = MaterialTheme.colorScheme.surfaceContainerLow, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(HomeDestination.ABOUT.iconRes(), size = 40.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(HomeDestination.ABOUT.titleRes), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.home_about_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private const val MAX_RECENT = 8
