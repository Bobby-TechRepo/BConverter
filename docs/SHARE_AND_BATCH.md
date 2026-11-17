# Share to BConverter and batch conversion (step 25)

## Sharing a video to BConverter
- Manifest: `MainActivity` accepts `android.intent.action.SEND` and `SEND_MULTIPLE` for `video/*`. Mixed shares (photos and videos) are not offered BConverter by the system.
- `MainActivity.receiveShare` reads `EXTRA_STREAM` (one Uri or a list) and falls back to `ClipData`; `ShareIntents.items` keeps the order, drops repeats and blanks, and takes at most 50. Only a fresh activity (or `onNewIntent`) reads the intent, so a rotation does not deliver it twice.
- `ShareInbox` (singleton, in memory) holds the share; `AppNavHost` opens the `share` screen whenever a new one arrives. The tool picked there is opened on the `shared/{tool}` route, which takes the share exactly once; Back from the share screen drops it.
- What is offered (`ShareIntents.actionsFor`): one video → edit in a new project, convert, extract audio, extract subtitles; several → edit in a new project, convert all.

## Batch conversion
- Entry: Converter's first screen ("Convert several videos"), or "Convert all" on a share. The picker is the document picker (multi-select); the list keeps its order, has no repeats, and survives the process being killed (`SavedStateHandle`).
- One preset and one container for every file; codecs are automatic per file (`ConversionPresetFactory`). The folder is chosen with the folder picker; outputs are `BatchNames.fileName`: "name-converted.ext", then "name-converted (2).ext", case-insensitively unique within the batch; forbidden characters become "_".
- `BatchConverter` runs on the app scope: for each file it opens and probes it, checks for a picture, builds and validates settings, checks free space, creates the output document, and launches an ordinary export job, waiting for it before opening the next file. Skips: UNREADABLE, NO_VIDEO, NOT_CONVERTIBLE, NO_SPACE, CANNOT_SAVE. Failures carry the job's `JobFailure`. Stop cancels the running job and marks the rest as stopped. Only one batch runs at a time.

## Checks on a device
1. From Google Photos, share one video to BConverter; try each of the four tools. Then share three videos and choose "Edit in a new project": they are on the timeline in the order shared.
2. Share three videos and choose "Convert all"; pick MKV and a folder. Leave the screen during the second file and come back: the batch is still shown and finishes. Each output is in the folder and in Exports.
3. Include an audio file (from Files) in a batch: it is skipped with "No video in this file".
4. Stop a batch during the second file: the first stays saved, the rest say "Stopped".
5. Rotate a tablet on the share screen and in a shared tool: nothing is opened twice.
