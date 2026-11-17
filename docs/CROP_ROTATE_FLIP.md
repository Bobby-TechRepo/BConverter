# Crop, rotate and flip

Per video clip (like filters), with "Apply to all clips". One undo step per change; saved with the project (`"transform"` on the clip, written only when it differs from the default, so older projects open unchanged).

## Model (`domain/transform`)
- `CropRect(left, top, right, bottom)`: fractions of the upright source picture, at least 10% of each side kept.
- `ClipTransform(crop, rotationDegrees 0/90/180/270 clockwise, flipHorizontal, flipVertical)`. Order of operations: crop → rotate → mirror.
- `CropEditing`: edge sliders ("cut from left/top/right/bottom") and shapes (whole, 1:1, 16:9, 9:16, 4:3, 3:4). A shape describes the *finished* picture, so for a quarter-turned clip it is flipped before it is cut on the picture as shot.

## Export
`ClipTransformFilters` emits `crop=w:h:x:y` (even pixel values, so no odd-size errors), `transpose=1` / `transpose=2`, `hflip`, `vflip` ahead of the scale step. The output canvas follows the **first** clip after its transform (a portrait turn gives a portrait video unless a fixed export size is chosen); later clips are fitted into it with black bars like any other clip. Overlay positions use the same frame, so `frameAspect` in the editor follows the first clip's transform.
`ClipTransformFilters.USED_FILTERS` are checked against the device by `FilterAvailabilityTest`.

## Preview
Media3 `Crop` and `ScaleAndRotateTransformation` (Media3 turns counter-clockwise, so the angle is negated), set before `prepare` and switched when the current clip changes — the same mechanism as filter previews.

## Limits
- Crop is edited with sliders and shapes, not by dragging a frame over the preview.
- Mixed-size clips: the canvas is the first clip's; others are letterboxed.
- Preview and export may differ by a pixel at crop edges (export rounds to even pixels).
