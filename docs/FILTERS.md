# Clip filters

Each video clip has its own look (`ClipFilter`). Choose a preset or adjust sliders; **Apply to all clips** copies the current look to every video clip.

## What can be set
| Tab | Controls |
|---|---|
| Colour | brightness, contrast, saturation, exposure, temperature, tint, hue |
| Effects | sharpen, blur, vignette, film grain; tone: keep colour / black and white / sepia; invert |
| Curves | highlights, shadows, tone curve (S-curve), fade (lifted blacks) |

15 presets: Original, Vivid, Warm, Cool, Vintage, Noir, Sepia, Matte, Cinematic, Dramatic, Soft, Sharp, Sunset, Moody, Negative. A preset is just a set of slider values; editing any slider turns it into a custom look.

Sliders are applied when released (one undo step per adjustment). Presets, tone, invert, Reset and Apply to all apply at once and are one undo step each. A split clip keeps its look on both halves.

## Export (authoritative)
`ClipFilterFilters` turns the look into FFmpeg filters, in this order: `lutyuv` (brightness, contrast and exposure as one luma table) → `colorbalance` (temperature, tint) → `hue` (hue shift and saturation) → `curves` (fade, shadows, highlights, tone curve) → black and white (`hue=s=0`) or sepia (`colorchannelmixer`) → `negate` → `unsharp` → `gblur` → `vignette` → `noise`. They run after the clip is scaled to the canvas and before it is padded, so letterbox bars stay black. Blur strength is a fraction of the output height, so a look does not change with the resolution.

All filters exist in FFmpeg 6 and the FFmpegKit 8 build (no `drawtext` needed). Every preset and every slider extreme was rendered with FFmpeg 6.1 on a desktop (46 cases) and `ClipFilterRenderIntegrationTest` renders them on the device's own FFmpegKit.

## Preview (approximation)
The preview player gets a colour grade from the stock Media3 effects (`HslAdjustment`, `Contrast`, `RgbAdjustment`), switched when the playing clip changes (`FilterPreview`). It is **not** identical to the export:
- Blur, vignette, grain, sharpen and invert are **not previewed** (the panel says so when one is in use).
- Curves, fade, shadows and highlights are approximated with lightness and contrast.
- Temperature/tint use per-channel scales; the export uses `colorbalance`, so the colours differ slightly.
- A slider changes the preview when it is released, not while it moves.
- The effect pipeline is switched on only for projects that have a previewable look, so unfiltered projects play exactly as before. Changing between clips with different looks may cause a brief hiccup at the cut.

## Saved projects
The look is stored on the clip as an optional `filter` object that only lists values that differ from neutral. Older projects have none and open unfiltered; unknown keys and out-of-range numbers are tolerated when reading.

## Tests
Unit: `ClipFilterTest`, `FilterPresetsTest`, `ClipFilterFiltersTest`, `ClipFilterGraphTest`, `RenderPlannerFilterTest`, `ClipFilterOperationsTest`, `DefaultTimelineEditorFilterTest`, `ClipFilterJsonTest`, `ProjectSerializerFilterTest`, `FilterPreviewTest`, `PreviewMapperFilterTest`, `FilterLabelsTest`, plus filter cases in `EditorToolsTest` and `EditorViewModelTest`.
Device: `ClipFilterPanelTest` (panel), `ClipFilterRenderIntegrationTest` (real FFmpegKit). The live-preview effects themselves have no automated test yet; check them by eye on a device.


## Filters the device build lacks

The FFmpegKit build on test devices has no `eq` and no `drawtext`; asking for a missing filter crashes the process natively. So the builder only uses filters listed in `ClipFilterFilters.USED_FILTERS`, and `FilterAvailabilityTest` checks every one of them (plus scale, pad, overlay, fade, format) against the device's `-filters` output. Add any new filter to that list.
