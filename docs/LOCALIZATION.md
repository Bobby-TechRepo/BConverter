# Localisation
Languages: English (default, `values/`), French (`values-fr`), Spanish (`values-es`), Simplified Chinese (`values-zh-rCN`), Japanese (`values-ja`).
- The app follows the phone's language, falling back to English. On Android 13+ the user can also pick one per app (Settings → Apps → BConverter → Language) via `res/xml/locales_config.xml`.
- Language-neutral strings (app name, "%1$s / %2$s", unit formats) are `translatable="false"` in the default file.
- `LocalizationTest` fails if a language misses a string, has an extra one, changes a placeholder (`%1$s`, `%d`…) or lacks a plural form (fr/es need one/many/other; zh/ja only other).
- When you add a string: add it to `values/strings.xml`, then to all four translations; the test tells you what is missing.
- The translations are machine-quality first drafts written by Claude; have a native speaker review them before release, especially the editor terms (trim, split, playhead, timeline).
- Not localised: third-party notice descriptions in About (`ThirdPartyNotices`, English technical text), codec/container names, and file sizes use a "." decimal separator (`ByteFormat`, Locale.ROOT).

## Switching language inside the app
Settings → Language lists System default, English, Français, Español, 简体中文 and 日本語 (each in its own language). Picking one recreates the screen immediately and the choice is remembered.
How: `MainActivity` is an `AppCompatActivity` (theme parent `Theme.AppCompat.DayNight.NoActionBar`) and `AppCompatLanguageController` calls `AppCompatDelegate.setApplicationLocales`; on Android 12 and older AppCompat stores the choice itself (`AppLocalesMetadataHolderService` in the manifest). `AppLanguage` (domain) maps tags to choices; `LanguageViewModel` holds the selection.
Known limit: on Android 12 and older, text built from the application context outside the screen (background export notifications) follows the phone language until the app is reopened.
