# Picture in picture

A second video shown over the main one for a while, with its own position, size, opacity and sound.

## Stage 1 (zip 115): model, undo, save/load, export — no editor screen yet
- `PipClip(id, source, sourceDurationMs, sourceStartMs, sourceEndMs, startTimeMs, layout, volume)` lives in `Timeline.pipClips` (clips listed later are drawn on top). It plays at normal speed for its source span, starting at `startTimeMs`; it is cut when the main video ends and never changes the project length.
- `PipLayout(centerX, centerY, width, opacity)`: fractions of the output picture; width 10–100 % of the output width (height follows the clip's own shape); opacity 10–100 %.
- Operations (all undoable): add, move, trim, set layout, set volume (0–200 %, 0 mutes), bring to front; the ordinary delete removes a PiP clip.
- Saved as an optional `"pipClips"` array (older projects open unchanged; out-of-range values are clamped, a clip that cannot be rebuilt is dropped).
- Export: each PiP clip is an extra FFmpeg input (`-ss/-t` trimmed). Its picture is shifted to its start time (`setpts=PTS-STARTPTS+start/TB`), scaled to its width, given its opacity (`colorchannelmixer=aa`), and laid over the finished video with `overlay … enable='between(t,start,end)':eof_action=pass`; text overlays and burned-in subtitles are drawn above it. Its own sound (if it has any and volume > 0) is mixed in like an audio clip, at its start time. Desktop FFmpeg check: the PiP appears exactly between its start and end.
- Device test: `PipRenderIntegrationTest`.

## Stage 2 (zip 116): editor
- **Add**: the *Overlay video* tool opens the picker (video files only) and puts the clip over the main video from the playhead on, selected. It needs a main video.
- **Timeline lane**: overlay clips sit in their own lane(s) under the video lane, above audio; clips that overlap in time get separate rows (`PipLanes`). Tap to select, long-press and drag to move in time. Missing files show as missing (`MissingMediaChecker` now covers overlay sources).
- **Trim / delete**: the ordinary Trim and Delete tools work on a selected overlay clip; split, speed, filters and the other video tools are off for it.
- **Layout panel** (*Overlay layout*): size, left-right, top-bottom, opacity, volume, bring to front. Sliders apply on release (one undo step each); the sheet does not dim the preview, so gestures work while it is open.
- **Live preview**: each overlay showing at the playhead plays in its own player placed in the output frame (`PipPreviewLayer`), following the main video (re-synced when more than 0.35 s apart). The selected overlay can be dragged and pinched; the result is committed once when the fingers lift, snapping to the centre lines. Preview volume is capped at 100 %.
- Tests: `PipLanesTest`, `PipPreviewTest`, additions to `ProjectOpenerTest`, `EditorToolsTest`, `EditorViewModelTest`; device `PipPanelTest`.

## Stage 3 (zip 118): fades and border
- **Fade in / Fade out** (0 – min(10 s, half the clip)): the picture fades through its own transparency so the main video shows through; the clip's own sound fades on the same times. Export: `fade=t=in|out:st=…:d=…:alpha=1` after `format=rgba` (times on the timeline), then the opacity multiplier. If both fades do not fit the clip, the fade-out is shortened (fade-in first), as for audio clips.
- **Border**: a coloured frame (0 – 10 % of the clip's width; six colours) added *around* the picture (`pad=iw+2b:ih+2b:b:b:color=0xRRGGBB`, b even, at least 2 px), so the clip grows by the frame as in the preview.
- Panel: Border (+ colour chips when on), Fade in, Fade out. Preview shows both (fade as changing opacity, border around the picture). Saved as optional `border`, `borderColor`, `fadeInMs`, `fadeOutMs` (old projects open unchanged).
- Not done: rounded or circular masks (need `geq`/`alphamerge`, which are unverified on the device's FFmpeg), manual row drag in the lane, speed, filters, crop.
- Tests: `PipStyleTest`, `PipStyleRenderTest`, additions to `ProjectSerializerPipTest`, `EditorViewModelTest`; device `PipPanelTest`, `PipRenderIntegrationTest` (border, fades). Desktop FFmpeg check: fade alpha and border pixels as expected.

## Stage 4 (zip 119): chroma key (green screen)
- **Remove a colour** per overlay clip: `ChromaKey(rgb, similarity 1–50 %, blend 0–50 %)`; green and blue screen chips, or tap the still frame to pick any colour. Export: `chromakey=color=0xRRGGBB:similarity=…:blend=…` right after the scale and before the border and `format=rgba` (so the border stays solid). Saved as optional `key`.
- **Still-frame preview**: the live preview cannot remove a colour, so the panel shows a 320 px frame of the clip (at the playhead) with the colour removed on a chequered background, drawn by `ChromaKeyMath`, which uses the same arithmetic as FFmpeg's filter (key → full-range U/V, pixel → limited-range U/V, distance / (255·√2) against similarity). It was checked against FFmpeg's own alpha for solid colours (e.g. a mid-grey 225/255, a dark green 14/255). The live preview shows the clip unkeyed; the export removes the colour.
- Device filter needed: `chromakey` (added to `FilterAvailabilityTest`).
- Desktop FFmpeg check: keyed area shows the main video, the kept colour stays, the border stays solid.
- Tests: `ChromaKeyTest` (maths vs FFmpeg, ops), additions to `PipStyleRenderTest`, `ProjectSerializerPipTest`, `EditorViewModelTest`; device `PipPanelTest`, `PipRenderIntegrationTest`.

## Stage 5 (zip 120): glide
- **Glide to a second position** per overlay clip: `PipMotion(endX, endY, eased)`; the centre moves in a straight line from the layout position to the end point across the clip's time on the timeline, optionally with smoothstep easing. Panel: switch, two end sliders, ease switch. Dragging or setting the layout moves the whole path (the end shifts by the same amount, kept inside the frame).
- Export: the overlay's `x`/`y` become expressions of the progress `clip((t-S)/D,0,1)` (`p*p*(3-2*p)` when eased). Size, opacity and border are not animated (the border pad needs a constant size). Saved as optional `motion`. The live preview follows the same path via `PipPath.centerAt`.
- Desktop FFmpeg check: centres within 1 px of the expected points at four times. Not yet run on the device.
- Tests: `PipMotionTest`, additions to `PipStyleRenderTest`, `ProjectSerializerPipTest`, `EditorViewModelTest`; device `PipPanelTest`.

## Stage 6 (zip 123): speed
- **Speed** per overlay clip, 0.25×–4× (chips 0.25, 0.5, 1, 1.5, 2, 4), the same range as main clips. The clip's length on the timeline is its source span divided by the speed (`PipClip.durationMs`); trimming still works on source times. Export: `setpts=(PTS-STARTPTS)/speed+S/TB`; its sound uses `atempo` (chained for extremes) and is placed at the clip's start. Preview: the second player's playback speed follows, and the source position is start + elapsed × speed. Saved as optional `speed`.
- Fades, glide and border use the clip's length on the timeline. Tests: `PipSpeedTest`, additions to `PipStyleRenderTest`, `ProjectSerializerPipTest`, `EditorViewModelTest`; device `PipPanelTest`. A desktop FFmpeg run of the expression rendered without error.

## Stage 7 (zip 124): shapes
- **Shape** per overlay clip: Rectangle, Rounded (corner radius 15 % of the shorter side) or Circle (cut from the middle of a square). Export: `geq` makes the outside transparent with a one-pixel soft edge (`alpha(X,Y)*clip(R-hypot(…)+0.5,0,1)`), applied after `format=rgba` and before the fades; a circle first gets `crop=min(iw\,ih):min(iw\,ih)`. A shaped picture has no border (the panel hides the border controls; the stored border value is kept for when it is a rectangle again). Preview: clipped to the same outline (circle zooms to fill a square). Saved as optional `shape`.
- Needs the `geq` filter on the device: added to `FilterAvailabilityTest`; **if that test fails, the export of shaped clips would fail too.** Desktop FFmpeg check: corners and circle outside transparent, centre and edge midpoints opaque.
- Tests: `PipShapeTest`, additions to `PipStyleRenderTest`, `ProjectSerializerPipTest`, `EditorViewModelTest`; device `PipPanelTest`, `FilterAvailabilityTest`.

## Stage 8 (zip 125): grow or shrink while gliding
- With a glide on, **Ends at: size** sets the clip's width at the end; the width moves along the same (eased) progress as the centre, which stays the clip's centre. Set it back to the starting size to turn it off. Export: `scale=w='2*trunc((W0+(W1-W0)*progress)/2)':h=-2:eval=frame` (even widths); the overlay position expression already uses the current `w`/`h`, so the centre follows the path. A glide that changes size has **no border** (the border `pad` needs a constant size); the panel hides the border controls then. Rounded and circular masks and fades keep working (the mask is computed per frame). Saved as optional `width` inside `motion`. Preview: the picture's size follows the path.
- Desktop FFmpeg check: widths at 1.2 s / 2 s / 2.9 s were 120 / 200 / ~290 px for a 100→300 px glide, centred. Whether the device's FFmpeg handles `scale … eval=frame` before `overlay` is not yet checked (the device test `PipRenderIntegrationTest` was not extended for it).
- Tests: additions to `PipMotionTest`, `PipStyleRenderTest`, `ProjectSerializerPipTest`; device `PipPanelTest`.

## Stage 9 (zip 126): keyframes
- **Keyframes** per overlay clip (up to 16): each is a moment on the clip's own time (from its start) with a position, width and opacity (0–100 %). Between two keyframes everything changes in a straight line; before the first and after the last it holds. Panel: *Add keyframe at playhead* (takes the look the clip has there; the first one also turns the clip's present layout or glide into keyframes so nothing jumps), chips per keyframe with sliders for the chosen one, *Delete this keyframe*, *Remove all keyframes*. With keyframes, the plain position/size/opacity sliders and the glide are hidden (keyframes replace a glide); dragging in the preview is off for that clip (the sliders edit the keyframe). A trim or speed-up that shortens the clip drops the keyframes that fall past the end.
- Export: each value is the first value plus, for every step, its change times `clip((t-T_i)/D_i,0,1)` — no conditionals, so it also works per frame inside `overlay`, `scale` and `geq`. Position goes into `overlay=x=…:y=…`; opacity into a per-frame `geq` alpha (`T`); a changing width into `scale=w=…:eval=frame`.
- **Order fix (also affects zip 125's glide with size change):** `geq` keeps the size of the first frame, so a size animation placed before a mask or per-frame opacity was frozen at the starting size (found on desktop FFmpeg; the device tests of zip 125 only check the output size, so they did not show it). Now the picture is made at its largest size, keyed, masked and faded, and shrunk per frame as the **last** step of its chain. Verified on desktop: widths 200/300/200/108 px, centres and opacity levels as expected, circle corners transparent.
- A changing size drops the border, as for the glide. Saved as optional `keys` (`t,x,y,w,o`); bad entries are skipped, the rest are sorted and made unique.
- Not done: keyframes for text and image layers, easing per keyframe, gestures that edit a keyframe in the preview, and keyframe markers on the timeline.
- Tests: `PipKeyframesTest`, additions to `PipStyleRenderTest`, `ProjectSerializerPipTest`, `EditorViewModelTest`; device `PipPanelTest`, `PipRenderIntegrationTest`.

## Stage 10 (zip 129): smooth steps between keyframes
- Each keyframe has a **Smooth change to the next keyframe** switch (not on the last one): when on, the step from that keyframe to the next starts and ends slowly (smoothstep `p*p*(3-2p)` of the step's progress); off is the straight line as before. Works the same for overlay videos and for text/picture overlays (preview, export and saving: optional `e`). Export: the step's term uses `(p*p*(3-2*p))` with `p=clip((t-T)/D,0,1)` instead of `p`.
- Tests: additions to `PipKeyframesTest`, `OverlayKeyframesTest`, `PipStyleRenderTest`, `OverlayFiltersTest`, the serializer tests; device `OverlayKeyframeSectionTest`.

## Stage 11 (zip 130): edit keyframes by dragging in the preview
- For an overlay video with keyframes, a drag or pinch in the preview now edits **the keyframe at the playhead**: one within 50 ms of the playhead is changed (keeping its time and smooth/straight setting); if there is none, a new one is added there with the look the clip has at that moment plus what was dragged or pinched. The gesture is one undo step. Without keyframes the drag still moves the whole clip.
- The same for text and picture overlays (`transformOverlayKeyframe`; rotation is not part of keyframes, so twisting does nothing on a keyframed overlay). Tapping a keyframed overlay in the preview now finds it **where it is at that moment**, not where it started (`OverlayHitTest` uses `OverlayKeyframes.positioned`).
- Also: the preview of an overlay video now takes its keyframes, glide, speed and shape live from the edited timeline.
- Tests: additions to `PipKeyframesTest`, `OverlayKeyframesTest`, `OverlayHitTestTest`, `EditorViewModelTest`; device `TextOverlayLayerTest`. The overlay-video drag itself (`PipPreviewLayer`) has no automated test — it needs a playing second player — and is for you to try by hand.

## Limits
No speed change, filters, crop, rotation, fades or animations on an overlay clip; the preview's second player is an approximation (small sync drift, no crossfade of its start/end), the export is exact; volumes above 100 % are heard in the export only; the lane is not yet draggable between rows by hand (rows are automatic).
