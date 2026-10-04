# Fitness Ark — Progress Log

Last updated: 2026-10-04 (F1–F4 done; F5, F6 built, uncommitted). Read this with `CLAUDE.md` (how the code works and the working rules) and `ENHANCEMENT_PLAN.md` (the item-by-item plan with reasons). Together they give the full context: **CLAUDE.md = how it is built, ENHANCEMENT_PLAN.md = what was planned and why, PROGRESS.md = what has actually been done, verified and what is next.**

## Product direction (from the user)

- Personal app used only by its developer: no Play Store users, so release-policy work is low priority, but their data exists nowhere else, so **avoiding data loss comes first**.
- **Neutral tracking**, not a weight-loss or weight-gain goal: never colour or word a change as good or bad (neutral colour + ↑/↓ arrows). No goal-direction features.
- Wants a **Metric/Imperial setting** (kg/lb, cm/in). Cares most about **consistency** (reminders, trend line, widget) and **photo quality** (comparison, pose overlay, timelapse). Health Connect only if easy (it isn't trivial and can only sync weight). Google Fit is deprecated, so don't suggest it.

## Where things stand (summary)

| Area | Status |
|------|--------|
| Tier 1 quick wins Q1–Q19 | **Done**, committed, pushed |
| Tier 2 structural S1–S9 | **Done**, committed, pushed (last commit `02557e5`) |
| Tier 1 follow-ups Q20, Q21, Q22 | Not started, awaiting a decision |
| Tier 3 features F1–F10 | **F1–F4 done**, reviewed, committed, pushed, not yet checked on a phone; **F5 (daily reminder) and F6 (home-screen widget) built**, 103 tests pass, uncommitted, awaiting review; F7–F10 not started |
| On-phone verification of S6–S9 | **Not finished** (see "Verification") |
| GitHub Actions CI | Pushed, **not seen to pass yet** |
| Uncommitted | This file, plus the doc edits to `CLAUDE.md` and `ENHANCEMENT_PLAN.md` made after `02557e5`. `.claude/` is untracked and intentionally left out. |

## Full history (oldest first)

The git history starts with the app as the user built it ("Build 1.0 Before claude Enhancement"). A first-pass architecture review produced `ENHANCEMENT_PLAN.md`; everything below came from it.

### Baseline — 2026-09-23
- `4ba94b3` Fitness Ark Build 1.0 before any Claude changes: ~35 files, Kotlin + Compose, Room, Koin, Coil, MPAndroidChart, no networking, **no tests**.

### Tier 1 — quick wins (crash, data loss, security, correctness, polish)

| Item | Commit | What was done |
|------|--------|---------------|
| Q1 | `3513ed6` | Removed the unrequested `CAMERA`/`READ_MEDIA_IMAGES`/`READ_EXTERNAL_STORAGE` permissions (declaring `CAMERA` without requesting it crashed the system camera with `SecurityException`) and the unused accompanist dependency. The manifest now declares no permissions. |
| Q2 | `6223422` | `CheckinViewModel.save()` reuses the day's existing row id instead of inserting a duplicate. |
| Q3 | `6017181` | `savePhoto` merges into the day's existing entry instead of wiping angles not passed in. |
| Q4 | `2c068ec` | Check-in pre-fills from the day being edited, not the latest measurement. |
| Q5 | `668e502` | Backup import rejects zip-slip entry names (`/`, `\`, `.`, `..`) and verifies the write path. |
| Q6 | `2206fd3` | Photo compression, ZIP export/import and storage-size work moved off the main thread (`Dispatchers.IO`); the three photo decodes run concurrently. |
| Q7–Q10 | `56cc1dd` | Large camera photos downscaled (`inSampleSize`, max 2048 px) to avoid OOM; notes `$` escape fixed; chart date labels keyed by day offset instead of list position; blank (0) values no longer counted in the dashboard's 7-day change and shown as "—" in the table. |
| Q11, Q12 | `0840c80` | Theme (dark/light/system) persisted with DataStore via the new `PreferencesRepository`; hard-coded cyan/white colours replaced with theme `primary`/`onPrimary` and theme-aware chart colours. |
| Q13 | `89feb46`, `847d0b9` | Pending camera slot/URI/dialog state uses `rememberSaveable` so a photo taken while Android kills the app is not lost; fixed a null-after-clear crash risk and added a "couldn't read photo" snackbar on retake. |
| Q17 | `4af3685` | Regenerated the Gradle 8.9 wrapper (including `gradle-wrapper.jar`) so `./gradlew` works from the terminal; this exposed and fixed a missing `rememberSaveable` import from Q13. |
| Q14 | `4933c53` | `MeasurementInput`: accepts `72,5` and `72.5`, range-checks (weight 20–400 kg, others 10–300 cm); field errors, Save blocked on invalid input, number pad on the Log Weight dialog. |
| Q15 | `4125d53` | Undo snackbar after deleting a measurement. |
| Q16 | `f718ef1` | Export ZIPs deleted after saving, camera temp files older than a day swept, deleting one photo angle deletes its file and rebuilds the thumbnail (new `PhotoRepository.deletePhotoAngle`). |
| Q19 | `5e47ce2` | Weight/measurement changes use one neutral colour with ↑/↓ arrows instead of red/green. |
| Q18 | `dfe6d18` | Real `BuildConfig.VERSION_NAME` in Settings, duplicate import removed (stray `{app/` folder removed locally), `CLAUDE.md` corrected. |
| docs | `3810fc2` | Added `CLAUDE.md` and `ENHANCEMENT_PLAN.md`, updated after Tier 1. |

Note: Q1 was found already fixed in the code when re-checked; only stale `CLAUDE.md` text needed correcting.

### Tier 2 — structural improvements

| Item | Commit | What was done |
|------|--------|---------------|
| S1 | `1290468` | Removed destructive DB fallback; Room schema exported (`exportSchema = true`, committed under `app/schemas/`); real `Migration` mechanism (`AppDatabase.MIGRATIONS`) with migration tests. |
| S2 | `8a9c585`, `2a6ae5d` | Test foundation: JVM tests with Robolectric and an in-memory Room DB (`TestSupport`): streaks, check-in saving, photo merging/deletion, backup round trip + zip-slip, chart labels, dashboard change, input validation, migrations. Docs updated. |
| S3, S4, S5 | `1a63dcf` | DB version 2: measurement fields nullable (`null` = not logged, migration converts old 0s); `localDate` column with a unique index so there is one measurement row and one photo entry per day, upserted by day; dashboard now derives its state from the DB `Flow`s (plus a once-a-minute day rollover) instead of reloading. |
| S8 | `5b88eed` | `DateUtils` on `java.time`: cached locale-aware formatters, real day boundaries, `getDaysBetween` counts calendar days. +7 tests. |
| S6 | `ae319c4` | `PhotoAngle` enum; `Metric` moved to `data/model` (with `valueIn(entity)`); `CheckinViewModel` in its own file with `Map<Metric, String>` / `Map<PhotoAngle, Uri>` state; `PhotoRepository` takes photo `Uri`s and has an injectable `ioDispatcher`; no `Context` passed from screens into ViewModels; unused `PhotoTimelineViewModel.savePhoto` removed. |
| S7 | `e3556ba` | `ZipUtils.stageBackup` streams a backup into `filesDir/import_staging` (no photo held in memory) and refuses newer format versions (`BACKUP_VERSION = 2`); new `BackupRepository.restore` writes all rows in one Room transaction and, on failure, removes the files it placed; Settings shows a **Merge / Replace** dialog after a file is picked. +6 tests. |
| S9 | `02557e5` | `.github/workflows/ci.yml` runs `assembleDebug lintDebug testDebugUnitTest` on pushes to `main` and pull requests; `chmod +x gradlew` first because `gradlew` is committed with mode 100644. |

Merge rule when a day is in both the phone and the backup: the backup's values win, anything the backup lacks is kept (per measurement field and per photo angle). Replace deletes everything first. Photo files are moved into place before the transaction, so a same-named file already on the phone (restoring your own backup) is overwritten and not rolled back on failure.

Order the S-items were actually done: S1, S2 → S3, S4, S5 → S8 → S6 → S7 → S9 (S7 needs S6's helpers).

## Verification

| Check | Result | Notes |
|-------|--------|-------|
| `./gradlew assembleDebug lintDebug testDebugUnitTest` | Pass | **66 JVM tests** (49 before S6–S9). Run on the full set of S6–S9 changes, not at each intermediate commit. |
| Code-reviewer agent on the S6–S9 diff | Approve, no blocking issues | One nitpick: `dialogTargetSlot?.fileKey` in `CheckinScreen.kt` has no explicit null check, but the slot is always set before the dialog opens. Read-through review, not a device test. |
| `installDebug` on the user's Pixel 9a (wireless adb) | Installed OK | Updated in place; existing data kept. |
| Launch on the phone | **Inconclusive** | The app process started and no crash showed in the first log check, but the follow-up screenshot showed the phone's home screen, not the app. I was checking whether the phone was locked or the app was not in the foreground when that was interrupted. |
| GitHub Actions run | **Not checked** | Pushed; nobody has looked at the run. |

**Not verified on a device:** Merge/Replace dialog, check-in with photos, retake/remove a photo angle, export then import in both modes, the measurements chart, theme persistence, the Tier 1 UI changes (Undo snackbar, neutral colours, input errors). Compose screens have no automated tests. Not unit-tested: `PreferencesRepository`, the camera flow, `SettingsViewModel` export/import wiring.

## Open items and next steps

0c. **F5 + F6 built, not committed** (103 tests, lint, assembleDebug pass). F5: daily check-in reminder (`util/ReminderScheduler` + `ReminderWorker`, WorkManager), a time picker and on/off switch in Settings, `POST_NOTIFICATIONS` requested only when turned on, skipped automatically on a day already logged. F6: home-screen widget (Jetpack Glance) with streak, today's weight and a "Log weight" button that opens the app straight to the weight dialog. Two new dependencies (`work-runtime-ktx`, `glance-appwidget`), approved by the user beforehand. Needs review, then checking on a phone: granting/denying the notification permission, the reminder actually firing and being skipped once logged, placing the widget and its button, and that an app update doesn't drop the schedule.

0a–0b. **F1–F4 are done**, reviewed, committed and pushed (commits `d3c609c`, `70c64f6`, `fcd309d`). Not yet checked on a phone: switching to Imperial, logging a past day, the trend line, and the Before/After pose picker / layout toggle.

1. Finish the on-phone check (phone unlocked, app in the foreground) across the list above.
2. Look at the first GitHub Actions run; fix the runner SDK or `gradlew` setup if it fails.
3. Decide on the remaining follow-ups (details in `ENHANCEMENT_PLAN.md`):
   - **Q20** — tell the user when a picked photo can't be decoded on check-in save (currently skipped silently).
   - **Q21** — keep picked photo `Uri`s across a process kill (easier now that the form state is `Map<PhotoAngle, Uri>`).
   - **Q22** — done (fixed with F3): `MeasurementsViewModel`'s chart x-offsets now use `DateUtils.getDaysBetween` instead of a millisecond truncation, so they no longer drift by a day across a DST change.
4. Tier 3 features: F1–F6 done (F5, F6 awaiting review). Remaining, in order: **F7** ghost-overlay camera (adds CameraX, ask first), F8 timelapse, F9 optional Health Connect, F10 blur dashboard photo with an eye toggle, F11 front camera + 5 s timer as take-photo default.
5. Revisit later: automatic scheduled backups (manual export is the only backup because Android auto-backup is off), trimming the over-broad ProGuard keep rules, moving UI text to `strings.xml`.

## Notes and gotchas

- Tooling is Git Bash on Windows. `python` and `bc` are not installed; `sed`/`awk` work. Set `JAVA_HOME` to Android Studio's `jbr` to build. The Android SDK `adb` is at `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`.
- A `sed`/heredoc turned `\\` into `\` once inside a Kotlin test string; re-check escapes after generating code that way.
- `installDebug` keeps the phone's data; never clear data or uninstall on the user's phone without asking.
- Line-ending warnings ("LF will be replaced by CRLF") on commit are harmless.
- Rules from `CLAUDE.md`: don't commit until the user has reviewed with the review agent; don't run the review agent unless asked; small commits; ask before adding a dependency; describe UI changes in words.
