# Fitness Ark — Enhancement Plan

Review status: **Tier 1 (Q1–Q19) and all of Tier 2 (S1–S9) are complete, committed and pushed to `main`.** Open items: the Tier 1 follow-ups Q20–Q22 and all of Tier 3 (features F1–F10), which still await a decision. See `PROGRESS.md` for the running log and what is verified on a device.

**How to review:** fill in the **Decision** column for each item with `yes`, `no`, `later`, or a note. Items marked `yes` are then done one at a time, in small commits. `yes — done` means implemented and committed.

## Context
This comes from a first-pass architecture review of Fitness Ark: a single-module Kotlin/Compose app of about 35 files, using Room, Koin, Coil and MPAndroidChart, with no networking and no tests. Each item lists its effort (S/M/L) and its risk (low/med/high: how likely it is to break something that works today).

**What shaped the ordering:**
- **Single user (you).** Play Store policy and release-build hygiene matter less. Your data exists only on your phone, so data-loss fixes stay first.
- **Neutral tracking, not a weight-loss or weight-gain goal.** The red/green "good vs bad" coloring is replaced with neutral coloring (Q19), and a goal feature is left out.
- **Metric/Imperial setting wanted.** This is feature #1.
- **Feature focus is consistency and photo quality.** Health Connect only if it's easy. It's moderate effort and can only sync weight, so it's last and optional.

---

## Tier 1 — Quick wins
Ordered from most to least serious: crash, data loss, security, correctness, polish.

| # | What | Why it matters | Effort | Risk | Decision |
|---|------|----------------|--------|------|----------|
| Q1 | **"Take Photo" crash.** The manifest declares the `CAMERA` permission, but the app never asks for it. When an app declares `CAMERA` and the user hasn't granted it, opening the system camera throws `SecurityException`. Fix: remove `CAMERA`, and remove `READ_MEDIA_IMAGES`/`READ_EXTERNAL_STORAGE` too (the photo picker needs no permission). Drop the unused Accompanist library. | The app's core action can crash. | S | low | yes — done |
| Q2 | **Duplicate measurements.** `CheckinViewModel.save()` always creates a new row with a new ID, even though the form is pre-filled with today's existing values. | Saving the same day twice creates two rows. Charts show double points and the entry count is inflated. | S | med (core save path) | yes — done |
| Q3 | **Photo angles get wiped.** `PhotoRepository.savePhoto()` deletes the day's whole photo entry, then inserts only the angles you just added. | Adding only a side photo silently deletes that day's front and back photos. Fix: merge into the existing entry, the way `updatePhotoAngle` already does. | S–M | med | yes — done |
| Q4 | **Wrong pre-fill for past dates.** `CheckinViewModel.init` always loads the latest measurement and compares it to today's date, ignoring the date being edited. | Opening a past day shows today's numbers, and saving writes them into that past day. | S | low | yes — done |
| Q5 | **Backup import can write outside its folder ("zip slip").** `ZipUtils.importData` writes `File(photosDir, name)` for every `photos/…` entry without checking the name. | A crafted backup file could overwrite the database or other app files. Fix: reject names containing `..` or `/` and verify the final path. | S | low | yes — done |
| Q6 | **Heavy work on the main thread.** Photo decoding and compression, ZIP export and import, and the storage-size calculation all run on the UI thread (`viewModelScope.launch` with no dispatcher). | The UI freezes, and Android may show "App not responding" when saving 3 photos or exporting. Fix: move that work to a background thread (`withContext(Dispatchers.IO)`). | S | low | yes — done |
| Q7 | **Out-of-memory risk when loading photos.** `BitmapUtils.decodeUriToBitmap` loads camera photos (12–50 MP) at full resolution, three at a time. | Can crash on mid-range phones. Fix: decode at a reduced size (about 2048 px) using `inSampleSize`. | S | low | yes — done |
| Q8 | **Notes show as literal code.** At `ui/measurements/MeasurementsScreen.kt:338`, the `$` is escaped, so the day card displays the text `${measurement.notes}` instead of your note. | Your notes never appear in the day-detail card. One-character fix. | S | low | yes — done |
| Q9 | **Chart dates are mislabeled.** Points are placed by number of days since the first entry, but the date labels are looked up by position in the list. | After any skipped day, every later date label is wrong. Fix: map each point's day position back to its actual date. | S | low | yes — done |
| Q10 | **Blank values treated as zero.** The dashboard's "last 7 days" change includes check-ins where weight was left blank (stored as 0), giving results like "−80 kg". The table shows "0.0cm" for blank fields. | The headline number on the home screen is wrong. Fix: ignore values of 0 or less and show "—". S3 is the proper long-term fix. | S | low | yes — done |
| Q11 | **Theme resets on every launch.** The dark/light choice is held only in memory in `MainActivity`, and a second copy lives in `SettingsViewModel`. DataStore is already a dependency but unused. | Your theme choice is lost on restart. Fix: save it with DataStore and add a "System" option. F1 reuses the same setup to save the unit choice. | S | low | yes — done |
| Q12 | **Poor contrast in light mode.** Buttons use a hard-coded cyan background with text in the page background color. The chart uses a hard-coded dark color for its point centers and white grid lines. | Button text is hard to read and the chart grid is invisible in light theme. Fix: use the theme's own color pairs (`primary`/`onPrimary`). | S | low | yes — done |
| Q13 | **Photos lost if Android restarts the app.** The camera screen keeps which pose you're capturing, and where the photo will be saved, in state that doesn't survive the app being restarted or the screen rotating (`remember` instead of `rememberSaveable`). | Opening the camera often makes Android close Fitness Ark in the background to free memory. When you return, the photo you took is silently thrown away. | S | low | yes — done |
| Q14 | **Input validation.** In locales that use a comma as the decimal point, "72,5" can't be parsed and is saved as 0. There are no range checks, and the weight dialog shows a text keyboard instead of a number pad. | Bad data is saved without any warning. Fix: accept either separator, validate ranges, and show errors on the field. Needed before F1. | S | low | yes — done |
| Q15 | **Swipe-to-delete has no undo** in the table view. | One accidental swipe permanently deletes an entry. Fix: add an "Undo" snackbar. | S | low | yes — done |
| Q16 | **Storage leaks.** Export ZIPs pile up in the app's internal `exports` folder and are never deleted. Camera temp files are never cleaned up. Deleting a single photo angle leaves its image file on disk and the thumbnail still pointing to it. | "Storage Used" keeps growing, and thumbnails can show deleted photos. | S | low | yes — done |
| Q17 | **Command-line builds don't work.** `gradle/wrapper/gradle-wrapper.jar` is missing and `gradlew` is only a 605-byte stub. | Only Android Studio can build the app, so there can be no CI and I can't compile or verify changes from the terminal. Fix: regenerate the Gradle 8.9 wrapper and commit the jar. | S | low | yes — done |
| Q18 | **Housekeeping.** Remove the stray empty `{app/` folder and a duplicate `Close` import. Show the real version (`BuildConfig.VERSION_NAME`) instead of the hard-coded "1.0.0" in Settings. Fix CLAUDE.md, which wrongly says Accompanist handles permissions. | Tidiness. | S | low | yes — done |
| Q19 | **Neutral change coloring.** The dashboard trend card and the comparison panel show weight gain in red and loss in cyan. | You track without a target, so changes shouldn't look good or bad. Fix: one neutral color with ↑/↓ arrows. | S | low | yes — done |

**Notes from doing Tier 1:**
- Q1 turned out to be already fixed in the code when checked (no camera or storage permissions in the manifest, Accompanist not a dependency); only the stale `CLAUDE.md` text needed correcting.
- Q17 is done: the Gradle wrapper (including the jar) is committed and `./gradlew assembleDebug lintDebug` runs from the terminal. This exposed that the Q13 commits had a missing import, since fixed.
- Q6 and Q7 are done, but there is still no automated test coverage of them (see S2).

**Follow-ups found during Tier 1 (not yet planned):**

| # | What | Why it matters | Effort | Risk | Decision |
|---|------|----------------|--------|------|----------|
| Q20 | **Silent photo skip on check-in save.** In `CheckinViewModel.save()`, a picked photo that can't be decoded is dropped without a message, and the check-in still reports success. | You think the photo saved when it didn't. | S | low | |
| Q21 | **Picked check-in photos lost on a full process kill.** `CheckinViewModel` holds the picked photo `Uri`s only in memory. Q13 saved the camera result, but not photos already added to the form. | Android can still kill the app while you fill in the form, and the added photos vanish. Fix: keep them in `SavedStateHandle`. | S | low | |

| Q22 | **Chart x-offsets can be off by a day in DST time zones.** `MeasurementsViewModel.getChartData()` computes the day offset with `TimeUnit.MILLISECONDS.toDays(date - firstDate)`, which truncates; across a clock change a 23-hour "day" counts as 0 days, so two points can land on the same x or the labels shift. Fix: count calendar days (S8's `java.time` makes this simple). Not an issue in Nepal (no DST). The tests pin UTC to stay stable. | Wrong chart spacing for anyone in a DST zone. | S | low | yes — done with F3 (uncommitted) |

---

## Tier 2 — Structural improvements

| # | What | Why it matters | Effort | Risk | Decision |
|---|------|----------------|--------|------|----------|
| S1 | **Real database migrations.** Export the Room schema (`exportSchema = true`) and commit it. Remove `fallbackToDestructiveMigration()`, which deletes the database whenever its structure changes. Add migration tests. | Today, any change to how data is stored wipes all your data. Must be done before S3 and S4. | S | low now, high if skipped | yes — done |
| S2 | **Test foundation.** Unit tests (JUnit and coroutines-test) plus database tests on an in-memory Room database. Cover: streak calculation, check-in saving, photo merging, backup round-trip including the zip-slip check, chart date labels, and unit conversion (F1). *I'll confirm the test libraries with you before adding them.* | There are no tests today, and Q2–Q5 and F1 all affect your data. | M | low | yes — done (JVM + Robolectric; no unit-conversion tests until F1 exists) |
| S3 | **Blank values stored as "empty", not 0.** Make measurement fields nullable (`Float?` instead of `Float` defaulting to 0). A database migration converts existing 0s to empty. | Removes the "0 means blank" workaround scattered through the ViewModels and screens, which is the root cause of Q10. Also makes unit conversion cleaner (F1). | M | med (changes the database and every screen that reads it) | yes — done |
| S4 | **One entry per day, guaranteed by the database.** Add a local-date column with a unique index so each day can have only one measurement row and one photo entry. Saving then updates that day's row instead of adding another. Ship in the same migration as S3. | Q2 and Q3 fix the code; this makes duplicates impossible even if a future bug slips in. Time zones need care. | M | med | yes — done |
| S5 | **Dashboard updates automatically.** Have the dashboard watch the database for changes, instead of loading once and reloading each time the screen opens (`LaunchedEffect(Unit)`). | Removes stale numbers and a duplicate load on first open. Also lets the dashboard refresh instantly when you change units. | S–M | low | yes — done |
| S6 | **Code structure cleanup.** Move `CheckinViewModel` into its own file (it currently lives in `CheckinScreen.kt`). Stop passing the Android `Context` from screens into ViewModels; repositories take a photo `Uri` and use the app context. Make the background thread configurable so tests can control it. Replace text keys like `"front"` and `"weight"` with enums (a new `PhotoAngle`, plus the existing `Metric`). | Easier to test, and removes typo-prone string matching. `Metric` also becomes the natural home for each measurement's display unit (F1). | S–M | low | yes — done |
| S7 | **Safer backup import and export.** Write ZIP entries to disk as they're read, instead of holding every photo in memory (another out-of-memory risk). Run the import as one database transaction, so it saves all or nothing. Check the backup's format version. Let you choose between merging into or replacing current data. Handle days that have photos in both the backup and the app. | Manual export is currently your only backup, because automatic Android backup is turned off (`allowBackup=false`). | M | med | yes — done |
| S8 | **Modern date handling.** Replace the shared `SimpleDateFormat` objects with `java.time`, which is available on all supported Android versions (API 29+). | `SimpleDateFormat` isn't safe to use from several threads, and its language is fixed at app start. `java.time` also simplifies the day calculations in S4 and F2. | S | low | yes — done |
| S9 | **Automated checks (CI).** A GitHub Actions workflow that builds the app, runs lint and runs the unit tests on every push and pull request. Needs Q17. | Cheap safety net that catches breakage automatically. | S | low | yes — done |

**Notes from doing S6–S9:**
- S6: `SettingsViewModel` still takes the injected *application* context (for file sizes and the SAF streams); the removed pattern was screens passing their own `Context` into ViewModels. `Metric` and `PhotoAngle` now live in `data/model`. The check-in form state is now `Map<Metric, String>` and `Map<PhotoAngle, Uri>`, which also makes Q21 a small change.
- S7: restore is `BackupRepository.restore(staged, ImportMode.MERGE | REPLACE)`. Conflict rule on a day in both: the backup's values win, anything the backup lacks is kept (per field, per photo angle). Photo files are moved into place before the DB transaction (removed again if it fails), so a same-named file already on the phone (restoring your own backup) is overwritten rather than rolled back.
- S8: `DateUtils.getDaysBetween` now counts calendar days. `MeasurementsViewModel.getChartData()` still uses the old `toDays` truncation, so Q22 is now a one-line change (use `DateUtils.getDaysBetween`) but is not done.
- S9: `.github/workflows/ci.yml` runs `assembleDebug lintDebug testDebugUnitTest`. It has not run on GitHub yet; the same Gradle command passes locally. It `chmod +x`'s `gradlew`, which is committed without the executable bit.

**Lower priority for a single-user app:**
- Trimming the release build's over-broad ProGuard `-keep` rules. They currently keep all of Compose, Koin and Coil, which stops R8 from shrinking the app. Effort S, risk med: mistakes show up only in release builds.
- Moving UI text into `strings.xml` for translation.

**Not recommended:**
- **Splitting into modules:** one module is the right size for about 35 files.
- **A networking layer:** the app has no networking.
- **Replacing AsyncTask:** there is none; the app already uses coroutines.
- **Switching from Koin to Hilt:** no benefit at this size.

---

## Tier 3 — New features
Ordered by value to you versus effort.

| # | What | Why it matters | Effort | Risk | Decision |
|---|------|----------------|--------|------|----------|
| F1 | **Metric/Imperial setting** (kg ↔ lb, cm ↔ in) in Settings, saved with DataStore (set up in Q11). Data stays stored in metric and is converted when shown or entered: every screen, chart axis, comparison panel and the dashboard. Existing data needs no migration. Rounding note: the database rounds kg to 2 decimal places, which is accurate enough when values are shown to 1 decimal place. | You asked for it directly. | M | med (touches every screen that shows a number) | yes — done (reviewed, committed, pushed; not yet checked on a phone) |
| F2 | **Log or edit past days.** Add a date picker when starting a check-in. The navigation already supports a date (`checkin?date=`), but nothing in the UI uses it. Needs Q2 and Q4. | Right now a missed day can't be logged afterwards. | S | low | yes — done (reviewed, committed, pushed; not yet checked on a phone) |
| F3 | **Trend line.** Overlay a 7-day moving average on the weight chart (optionally on measurements too), and show the weekly average on the dashboard. | Smooths out day-to-day fluctuation, which suits neutral tracking. | S–M | low | |
| F4 | **Better photo comparison.** Choose which angle to compare (today it always uses the front photo when available), add a side-by-side mode next to the slider, and move the delete buttons out of the before/after pickers. | The core of a progress-photo app. | S–M | low | |
| F5 | **Daily reminder notification** at a time you choose, using WorkManager. Android 13+ requires the `POST_NOTIFICATIONS` permission. Skipped automatically if you've already checked in that day. | Helps consistency and your streak. | M | low | |
| F6 | **Home-screen widget** (built with Jetpack Glance) showing your streak, today's weight, and a quick "log weight" button. | Log without opening the app. | M | low | |
| F7 | **Pose "ghost" overlay camera.** An in-app camera (CameraX) that shows your previous photo of the same angle faintly (about 30% opacity) so you can line up the same pose. *Adds a new library (CameraX), so I'll ask before adding it.* | Consistent framing makes before/after comparisons reliable. | L | med | |
| F8 | **Progress timelapse.** Build a GIF or MP4 from all photos of one angle over time. | Better photos and motivation. | L | low | |
| F9 | **(Optional) Health Connect weight sync.** Automatically copy each weight entry into Health Connect (one-way). It isn't a quick addition: it needs the `connect-client` library, the `WRITE_WEIGHT` permission, and a screen explaining why the app wants the permission, which Health Connect requires. Google Fit's APIs are deprecated, so Fit isn't an option. **Body measurements can't be synced:** Health Connect has no data types for waist, chest, hips, biceps or thighs. | You said only if it's easy. It's moderate, so it's last and optional. | M | low–med | |
| F10 | **Toggle button to view Dashboard Pic** When app is open the dashbaord picutre should be blureed for provacy reasona dn should have toggle button so when tohggle can view pic. The toggle can be eye button with cross on middle of blur pic | For establish provacy if app  mistakenly open other person can see half naked body image that is present in dashboard image| M | low | |
|F7| **front Camera and 5 Sec Timer as take photo default**When User choose take photo option, the as a deafult fronmt camera and 5 sec timer should be apoplied show that user can just click the shtter button and get the phot taken. Should be applied in all 3 poses settings.|It weill save a lot of time ans hassle of  cicking 3 button after user choose take photo button.| M|low||
**Dropped or put off based on your answers:**
- Goal weight and direction (replaced by Q19).
- App lock and blocking screenshots (`FLAG_SECURE`).
- Automatic scheduled backups. Worth revisiting later: if you lose your phone, everything not manually exported is lost.
- BMI and custom measurement fields.

---

## Suggested order of work
1. ~~**Q1, Q17:** stop the camera crash and make command-line builds work.~~ Done.
2. ~~**Q2–Q7:** data-loss, security, and freeze/crash fixes.~~ Done.
3. ~~**S1 + S2:** database migration safety net and tests.~~ Done.
4. ~~**Q8–Q19:** remaining quick wins.~~ Done (optionally Q20, Q21).
5. ~~**S3 + S4**, then S5, S6, S8, S7, S9.~~ Done. **Next:** decide on Q20–Q22, then start Tier 3 (F1 units is the one you asked for directly).
6. **Features F1 → F10** in table order. F1 is unblocked now (Q11 and S6 are done).

## Main files affected
- **Build and manifest:** `app/src/main/AndroidManifest.xml`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, `gradle/wrapper/`
- **Check-in and saving:** `ui/checkin/CheckinScreen.kt` (contains `CheckinViewModel`), `data/repository/PhotoRepository.kt`, `data/repository/MeasurementRepository.kt`
- **Utilities:** `util/ZipUtils.kt`, `util/BitmapUtils.kt`, `util/ImageCompressor.kt`, `util/DateUtils.kt`, `util/FileUtils.kt`
- **Screens:** `ui/measurements/MeasurementsScreen.kt` and `MeasurementsViewModel.kt` (defines the `Metric` enum), `ui/dashboard/*`, `ui/photos/*`, `ui/settings/*`
- **Data and setup:** `data/local/AppDatabase.kt`, `data/local/entity/*`, `di/AppModule.kt`, `MainActivity.kt`

## How changes will be verified
- **Build:** after Q17, `./gradlew assembleDebug lint testDebugUnitTest` passes from the command line.
- **On a phone or emulator** (Android 10 / API 29, and Android 14+ / API 34+):
  - On a fresh install, "Take Photo" works (Q1).
  - Saving the same day twice leaves one entry (Q2).
  - Adding only a side photo keeps that day's front and back photos (Q3).
  - Importing a crafted ZIP containing `../` paths is rejected (Q5).
  - Saving 3 camera photos doesn't freeze or crash the app (Q6, Q7).
  - Theme and unit choices survive a restart (Q11, F1).
  - Switching to Imperial converts every screen, and a weight entered in lb is stored as the correct kg (F1).
- **Database:** a migration test runs against the exported version-1 schema (S1).
- **Process:** one small commit per item. For UI changes, the change is described in words for your approval first (per CLAUDE.md).
