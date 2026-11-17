# Image overlays

A picture (PNG, JPEG or WebP; transparency kept) can be placed on the video like a caption. Tap **Add image**, choose a file, and it appears at the playhead, selected, for 3 seconds.

## Same as text
An image overlay *is* an overlay (`TextOverlay` with `image` set), so it shares everything with text:
- drag / pinch / twist in the preview, tap to select, double-tap to open its panel;
- start/end, position, rotation, scale, opacity as numbers; fade, slide and scale animations in and out;
- **one layer order** with text: bring to front / forward / backward / to back, so a picture can sit above or below a caption;
- timeline rows, duplicate, delete, one undo/redo order with text and subtitles;
- saved in the project (`image` key on the overlay: `path`, `w`, `h`, optional `height`); older projects open unchanged.

The panel for a picture hides the text-only settings (text, font, colour, outline, shadow, glow, background).

## Import
`AndroidOverlayImageImporter` copies the chosen file into the app's private `overlay-images` folder as a PNG, so the project keeps working if the original is moved or deleted. It shrinks anything over 2048 px on the longest side (never enlarges), turns it upright using the file's orientation, and refuses sources over 100 megapixels. A very wide picture starts no wider than half the frame. Unreadable files and too-large files give a message and change nothing. The copy is not deleted when the overlay is removed (undo needs it); a clean-up of unused copies is not implemented.

## Size
At scale 1 the picture is `heightFraction` (default 30 %, less for very wide pictures) of the frame height tall, whatever the frame size; its width follows its shape. Scale multiplies both.

## Export
The overlay goes through the same path as text: `AndroidOverlayRasterizer` draws it to a transparent PNG at its size on the frame (scaled, rotated, opacity baked in), `OverlayLayerCache` keeps it (key: file, file stamp, height, scale, rotation, opacity, frame size; not position, time or animation), and FFmpeg's `overlay` filter places, times and animates it (`OverlayFilters`). Limits: 8192 px per side, 24 MP; larger are refused and the export says the overlays could not be drawn. A missing picture file fails the export the same way. Export fades are linear.

## Preview
Drawn by Compose from the stored file, decoded near the size it is shown (24 MB cache). Preview and export are close but not identical (filtering and fades differ slightly). A picture whose file is missing is shown as a red box so it can still be selected and deleted.

## Not included
Crop, rounded corners, border, animated GIFs, and per-picture colour filters.

## Tests
Unit: `OverlayImageTest`, `OverlayImageKeyTest`, `OverlayImageSizingTest`, `OverlayImageInteractionTest`, `CachedOverlayLayerPreparerImageTest`, `TextOverlayJsonImageTest`, picture cases in `EditorViewModelTest`.
Device: `AndroidOverlayPictureRasterizerTest`, `AndroidOverlayImageImporterTest`, `ImageOverlayPanelTest`. A device render of a picture overlay through FFmpegKit is covered by `PictureOverlayRenderIntegrationTest`.
