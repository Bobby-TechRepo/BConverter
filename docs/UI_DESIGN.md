# UI design pass (Material 3)
Order agreed 28 Oct: (1) shared theme + Home, (2) all other screens, (3) release-build check (R8 rules + smoke test).
Decisions: dynamic colour on Android 12+, brand indigo palette as the fallback (`core/ui/Theme.kt`); shapes 4/8/12/16/28 dp; titles/labels one weight heavier;
icons are Glyphs outline vectors (`res/drawable`, fetched by `tools/icons/fetch_glyphs.py`, placeholders until then), tinted by the content colour.
Home: three tool tiles (secondaryContainer, round primary icon badge), then Projects/Settings/About as list rows with a chevron. Text and click behaviour are unchanged.
UI tests are paused until the rework is done.

## Pass 2 (all other screens)
Shared pieces in `core/ui/Components.kt`: `ErrorBanner` (errorContainer surface, replaces red text), `SectionLabel` (Quality / Format / Output headings, marked as headings for TalkBack), `IconBadge`, `PrimaryButton` (56 dp), and the stage panels `SelectInputPanel`, `ProgressPanel`, `CompletedPanel` used by both the Converter and the Audio Extractor (same texts and semantics as before).
Source card on `surfaceContainerHigh`; About has an icon header and notice cards; Settings uses the same section labels. Glyphs added to the notices (MIT).
Advanced settings now sit in a tonal card. `PickInputScreen` is no longer used by navigation (the converter screens embed the picker) and was left unstyled.

## Pass 3 (Editor toolbar, Projects)
Editor tools are icon-over-label buttons (56 dp tall, labels unchanged) using `ic_tool_*` Glyphs icons (placeholders until `fetch_glyphs.py` is run); Projects rows are tonal cards with a folder icon, a proper empty state and an error-banner load failure.
Not restyled: Editor timeline/preview chrome, Pick-input screen, Advanced settings section.

## Editor timeline: fixed playhead (29 Oct)
- The red playhead is fixed in the middle of the timeline view; the timeline scrolls underneath it. Scrolling (drag or fling) is seeking; tapping the timeline scrolls that point to the middle; playback scrolls the timeline along; pinch/zoom keeps the playhead time in the middle. Touching the timeline during playback pauses it.
- `SeekRange` (domain): the playhead can go `BLANK_TAIL_MS` (10 s) past the end of the content. After the last video frame the preview is black (`TimelinePlaybackState.isBlank`); nothing plays there, seeking into it while playing pauses, and Play from there restarts at the beginning. Export is unchanged (it ends at the last clip).
- Add video always appends directly after the last clip, whatever the playhead position.
- Accessibility: the playhead's slider range and the step actions use the same range.
- Next: fades between clips (None / Fade through black / Crossfade, per join), in a separate round (model, preview, export).

## Transitions between clips
- Select any video clip except the first and tap **Transition**: None, Fade through black, or Crossfade, with a length of 0.25–2 s (step 0.25). The setting belongs to the cut *before* the selected clip.
- Timeline: every cut between video clips has a round marker. A plain cut shows a small one; with a transition the marker widens to the length of the transition (animated) and is highlighted. Tap it to edit that transition. A clip with a transition also shows ◐ (fade through black) or ⨯ (crossfade) before its length.
- Preview: fade through black is drawn exactly as a black layer; a crossfade is shown as a cut (the two clips are not decoded at once). Export renders both with FFmpeg (`fade`/`xfade`/`acrossfade`/`afade`).
- A crossfade shortens the timeline by its length; audio overlay clips keep their absolute times, so a crossfade before them shifts picture relative to that audio.

## Detach audio
- Select a video clip at 1× speed and tap **Detach audio**: the clip is muted and its sound appears as an audio clip (same slice, same time) on the audio lanes, where it can be moved, trimmed, split, volume-adjusted or deleted. One undo step reverses it.
- Deleting the detached audio clip leaves the video silent; undo to get its sound back.
- Detached audio keeps absolute timeline times like any audio clip, so later edits that move the video (trim/delete an earlier clip, crossfades) do not move it.
- The audio clip takes the video clip's speed. Not offered for already-detached clips or sources without sound.

## Moving clips and audio speed
- Long-press a clip on the timeline (a haptic tap confirms) and drag. **Audio** clips can be dropped at any start time from 0 on (also past the end of the video) and on any existing lane; dropping onto another clip is refused with a message. **Video** clips stay gap-free, so a dropped clip takes the place whose middle it is nearest (reordering).
- **Speed** now works on audio clips too (0.25×–4×, pitch kept); the clip's length on the timeline changes with it, and slowing it into a neighbour is refused.


## Pass 4 — Premium "Studio" redesign (15 Nov)
- **Identity**: deep graphite canvas, one signature gradient (iris `#6A4CFF` → violet `#8E44E6` → rose `#C93A6E`) used only for the most important thing on a screen (main action, Home hero, Export pill, play button, progress). White text passes ≥ 4.8:1 on every stop.
- **Brand first**: dynamic (wallpaper) colour is now opt-in (`AppSettings.dynamicColor` defaults to false) so the brand look is the default experience.
- **Type**: Plus Jakarta Sans (OFL, `res/font`, 5 weights) across the whole Material type scale; tight tracking on display sizes, tabular figures for timecodes. CJK falls back to the system font per glyph.
- **Shapes**: 6/10/14/20/28 dp.
- **Theme API** (`core/ui/Theme.kt`): `BConverterTheme` (app), `StudioTheme` (editor: always dark, neutral surround for judging colour), `BConverterTheme.brand` for roles Material lacks (`signature`, `signatureWash`, `success`, `stage`, `playhead`).
- **Components** (`core/ui/Components.kt`): `BrandBackdrop`, `PremiumCard`, `GradientButton`/`PrimaryButton`, `QuietButton`, `IconBadge` (gradient squircle + glow), `IconTile`, `GradientProgressRing`, `GradientLinearProgress`; the stage panels (select / progress / completed) use them, so Converter, Audio and Subtitle extractors pick the new look up automatically. Texts and semantics are unchanged.
- **Home**: wordmark top bar, the editor as a gradient hero card with a "New project" pill, three quick-tool tiles with one-line hints, a horizontal strip of recent projects (from `ProjectsViewModel`, refreshed on resume), About as a footer row.
- **Editor**: studio theme with light system-bar icons (`LightSystemBarIcons`), gradient Export pill, rounded black preview stage with an empty-state hint, gradient play button, tools grouped under captions (Create · Edit · Audio · Text & overlays · Effects · Arrange) with icon tiles; selecting a clip scrolls the Edit group into view. White playhead with a cap; clip blocks 8 dp radius.
- **Projects**: monogram tiles on the brand wash, tonal cards with hairline edges.
- **System**: adaptive launcher icon (gradient + play-in-loop mark, themed-icon monochrome layer), Android 12+ splash, window background matches the theme (no white flash), system-bar icons follow the app theme rather than the system's.
- Tests touched: `HomeScreenTest` (scrolls before asserting), `SettingsTest`/`SettingsViewModelTest` (new default). `LocalizationTest` covers the 15 new strings in all five languages.

## Tool panels inside the editor (step 23, batch 8)
- **Where**: phones put the open tool's panel between the timeline and the tools bar (`PANEL_HEIGHT_FRACTION` = 34 % of the screen height at most; the body scrolls). Wide windows put it beside the preview in place of the project panel (`LocalToolPanelBeside`), filling that column, so the timeline keeps its height.
- **Look**: surface-container sheet, 24 dp top corners on phones (all corners on wide windows), title as a heading with the panel's actions on the right (48 dp targets). `paneTitle` makes TalkBack announce a panel as it opens.
- **Tools bar**: stays visible; the open tool's tile is lit in the primary colour and reports "selected". Same tool again closes, another tool swaps the panel (cross-fade).
- **Rules** (`EditorPanels`): a panel shows while its tool applies to the selection; picking several clips hides it; a picked subtitle cue is edited on top. Back closes the panel before leaving the editor. Rename and Export remain dialogs.
