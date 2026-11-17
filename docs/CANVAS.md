# Canvas and background

The shape of the exported picture, how clips are placed in it, and what fills the empty bars.

## Settings (`CanvasSettings`, per project, undoable, saved as optional `"canvas"`)
- **Shape** (`CanvasAspect`): *Like the first clip* (default; unchanged behaviour), Wide 16:9, Tall 9:16, Square 1:1, Portrait 4:5. The output keeps the **short side of the first clip** (after its crop/rotation) and the other side follows the shape: 1920×1080 as Tall is 1080×1920, as Square 1080×1080, as Portrait 1080×1350. Sides are even. The *Small file* preset's 720 p cap is applied to this canvas shape, and a fixed export size, when set, still wins.
- **Picture** (`CanvasFit`): *Fit* shows the whole clip and leaves bars; *Fill* covers the canvas and crops what overflows (no bars).
- **Bars** (`CanvasBackground`): a colour (black, white, grey, navy, green, wine) or *Blurred video*.

## Export
- Fit + colour: `scale=…:force_original_aspect_ratio=decrease` then `pad=…:color=0xRRGGBB` (black stays `color=black`, as before).
- Fill: `scale=…:force_original_aspect_ratio=increase,crop=W:H`.
- Fit + blur (only for a clip that really has bars): the transformed, scaled, filtered picture is `split`; the copy is scaled **small** (1/8 of the canvas), enlarged to cover, `gblur`red, scaled back up, and the sharp picture is `overlay`ed centred. The clip's look (filters) is applied before the split, so the blur matches. Retime, `fps`, `format`, transitions' fades and freeze-frame holding come after the overlay.
- Text overlays, picture overlays, subtitles and overlay videos are all drawn on the finished canvas, so they use the canvas size.
- Desktop FFmpeg check: blur/colour/fill graphs give the right size, the bars show the blur or the colour. Device filters needed: `split`, `crop`, `gblur`, `scale`, `overlay`, `pad` (all in `FilterAvailabilityTest`).

## Editor
- **Canvas** tool (opens a bottom sheet, preview stays live): shape chips, Fit/Fill, bar colour swatches and Blur. Each choice is one undo step and is autosaved.
- The preview is framed in the output's shape; the frame is filled with the bar colour (a dark grey stands in for *Blurred video*, which the preview cannot draw). Fill zooms the preview to cover. Text and overlay-video layers use the same frame, so what you place is where it exports.
- New text and picture overlays are sized for the current shape.

## Fixes in this stage
- Reopening a saved project now also loads the sources of overlay videos (picture-in-picture) back into the library (before, only video and audio sources were).

## Limits
- The shape follows the first clip's short side; there is no free size entry. The blur strength is fixed. Preview does not draw the blur. A clip's own crop/rotation happens before it is fitted, as before.
- Overlay clips, text and images keep their positions as fractions of the canvas when the shape changes (they are not re-laid out).
- Tests: `CanvasSettingsTest`, `CanvasRenderTest`, `ProjectSerializerCanvasTest`, additions to `EditorToolsTest` and `EditorViewModelTest`; device `CanvasPanelTest`, `CanvasRenderIntegrationTest`.
