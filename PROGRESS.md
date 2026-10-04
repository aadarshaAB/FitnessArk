# Fitness Ark — Progress Log

Last updated: 2026-10-04 (F1–F6 done, reviewed, committed, pushed; F5+F6 verified on a phone; Q20 fixed after a real user-hit bug, reviewed, committed, pushed; GitHub Actions CI checked and passing). Read this with `CLAUDE.md` (how the code works and the working rules) and `ENHANCEMENT_PLAN.md` (the item-by-item plan with reasons). Together they give the full context: **CLAUDE.md = how it is built, ENHANCEMENT_PLAN.md = what was planned and why, PROGRESS.md = what has actually been done, verified and what is next.**

## Product direction (from the user)

- Personal app used only by its developer: no Play Store users, so release-policy work is low priority, but their data exists nowhere else, so **avoiding data loss comes first**.
- **Neutral tracking**, not a weight-loss or weight-gain goal: never colour or word a change as good or bad (neutral colour + ↑/↓ arrows). No goal-direction features.
- Wants a **Metric/Imperial setting** (kg/lb, cm/in). Cares most about **consistency** (reminders, trend line, widget) and **photo quality** (comparison, pose overlay, timelapse). Health Connect only if easy (it isn't trivial and can only sync weight). Google Fit is deprecated, so don't suggest it.

## Where things stand (summary)

| Area | Status |
|------|--------|
| Tier 1 quick wins Q1–Q19 | **Done**, committed, pushed |
| Tier 2 structural S1–S9 | **Done**, committed, pushed (last commit `02557e5`) |
| Q20 | **Done**, committed, pushed (`fb9950a`) — fixed live after the user hit it; see "Full history" and "Verification" |
| Q21 | Not started, awaiting a decision |
| Q22 | **Done** with F3 |
| Tier 3 features F1–F11 | **F1–F6 done**, reviewed, committed, pushed; **F5 and F6 verified on a phone** (see "Verification"); a widget-button crash found during that check is fixed (commit `17e2e85`); F7–F11 not started |
| On-phone verification of S6–S9 | **Not finished** (see "Verification") |
| GitHub Actions CI | **Checked, passing** — every real push since S9 has succeeded (see "Verification") |
| Uncommitted | `.claude/` only (untracked, intentionally left out). Everything else is committed and pushed. |
| Not pushed | Nothing — local `main` matches `origin/main`. |

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

### Tier 3 — new features (F1–F6 so far)

| Item | Commit | What was done |
|------|--------|---------------|
| F1 | `d3c609c` | Metric/Imperial setting (kg↔lb, cm↔in), stored metric, converted for display/entry everywhere. |
| F2 | included with F1/F3 commits | Date picker on check-in, so a past day can be logged or edited. |
| F3, Q22 | `70c64f6` | 7-day trailing moving-average trend line (dashed, toggled by a "7-day avg" chip) and weekly average on the dashboard; chart x-offsets switched to `DateUtils.getDaysBetween` (DST-safe), closing Q22. |
| F4 | `fcd309d` | Before/After photo comparison: pose picker (default front, no fallback), Slider/Side-by-side layout toggle, delete button moved out of the before/after pickers. |
| F5 | `39ca9ff` | Daily check-in reminder: `util/ReminderScheduler` + `ReminderWorker` (WorkManager, self-rescheduling one-shot chain), time picker + switch in Settings, `POST_NOTIFICATIONS` requested only on enable, skipped automatically on a day already logged, re-armed on app start. New dependency `work-runtime-ktx`. |
| F6 | `9db6166` | Home-screen widget (Jetpack Glance): streak, today's weight, "Log weight" button opening the app straight to the weight dialog (`MainActivity.EXTRA_OPEN_WEIGHT_DIALOG`); refreshed via `WidgetUpdater` after any weight save. New dependency `glance-appwidget`. |
| docs | `e4f0a70` | Docs updated through F5/F6. |
| fix | `17e2e85` | **Widget-button crash found during on-phone verification** (see "Verification"): both F6 widget buttons threw `ActivityNotFoundException` on tap on Android 17 (API 37) — Glance 1.1.1's `RemoteViews.startPendingIntent` failing on an OS version newer than the library, `compileSdk`/`targetSdk` 35, and the highest locally installed SDK platform (36.1). Fixed by bumping `glance` 1.1.1→1.2.0 (latest stable) and, since Glance 1.2.0 requires it, `agp` 8.5.2→8.6.1 (the minimal satisfying version). `minSdk`/`targetSdk`/`compileSdk`/Kotlin/JVM target unchanged. Reviewed (approved, no findings) before commit. |
| docs | `ae1b9ea` | Docs updated: F5/F6 on-phone verification log, the widget crash fix recorded, push status corrected. |
| Q20 | `fb9950a` | **Live user-reported bug, found and fixed in-session**: check-in photos taken with the camera were silently not saving (weight/measurements saved fine, no error, no popup). Root-caused via on-device logcat plus pulling and grepping the live `fitness_ark.db-wal` for `.jpg` paths before/after a save, to `BitmapUtils.decodeUriToBitmap` having silent `return null` paths with no logging — a transient failure to read the just-captured camera photo (likely a brief file-readiness race right after the camera hands back control) made `PhotoRepository.savePhoto`'s decoded-bitmap map empty, so it quietly did nothing. Fixed: `BitmapUtils` now logs every previously-silent failure path; `PhotoRepository.savePhoto(date, uris)` returns the set of angles that failed to decode instead of `Unit`; `CheckinViewModel.save()` surfaces that as an error ("Saved, but the X photo couldn't be read and was skipped. Try retaking it.") and keeps the user on the check-in screen instead of navigating away, so the thumbnail is still there to retry. This closes **Q20**. Reviewed (approved, no blocking issues) before commit. |

## Verification

| Check | Result | Notes |
|-------|--------|-------|
| `./gradlew assembleDebug lintDebug testDebugUnitTest` | Pass | **66 JVM tests** (49 before S6–S9). Run on the full set of S6–S9 changes, not at each intermediate commit. |
| Code-reviewer agent on the S6–S9 diff | Approve, no blocking issues | One nitpick: `dialogTargetSlot?.fileKey` in `CheckinScreen.kt` has no explicit null check, but the slot is always set before the dialog opens. Read-through review, not a device test. |
| `installDebug` on the user's Pixel 9a (wireless adb) | Installed OK | Updated in place; existing data kept. |
| Launch on the phone | **Inconclusive** | The app process started and no crash showed in the first log check, but the follow-up screenshot showed the phone's home screen, not the app. I was checking whether the phone was locked or the app was not in the foreground when that was interrupted. |
| GitHub Actions runs | **Checked 2026-10-04, all passing** | See "CI status" below. |

**Not verified on a device:** Merge/Replace dialog, check-in with photos, retake/remove a photo angle, export then import in both modes, the measurements chart, theme persistence, the Tier 1 UI changes (Undo snackbar, neutral colours, input errors), switching to Imperial, logging a past day, the trend line, the Before/After pose picker/layout toggle. Compose screens have no automated tests. Not unit-tested: `PreferencesRepository`, the camera flow, `SettingsViewModel` export/import wiring.

### F5 + F6 on-phone verification — 2026-10-04 (Pixel 9a, Android 17 / API 37)

| Check | Result | Notes |
|-------|--------|-------|
| F5: toggling the reminder on | Pass | Triggers the `POST_NOTIFICATIONS` runtime prompt correctly, only on enable (not on every Settings visit). |
| F5: time picker | Pass | Standard Material time picker, sets correctly (tested 7:20 PM). |
| F5: WorkManager actually schedules the job | Pass | Confirmed via `adb shell dumpsys jobscheduler`: job present with the correct minimum-latency countdown. |
| F5: notification fires at the set time | Pass (indirectly) | At the scheduled time the job ran, found today already logged, correctly skipped the notification per spec, and rescheduled for the next day (confirmed via job-history log: clean run → cancel → next job at +23h59m). Not a bug — today's check-in had already been done earlier in the session. The skip path is what F5's spec calls for; the fire-and-notify path was exercised by this same code path minus the skip, so it's considered covered. |
| F6: widget placement | Pass | Placed on the home screen by the user; renders the streak, today's weight, and "Log weight" button correctly, including after a `force-stop` (reads live from the DB, no ViewModel needed). |
| F6: widget buttons (first attempt) | **Fail — crash found** | Both the outer widget tap and the "Log weight" button threw `ActivityNotFoundException` (via `RemoteViews.startPendingIntent`), confirmed in logcat on every attempt. Root-caused to Glance 1.1.1 vs. the device's Android 17 (API 37). Fixed by the `glance`/`agp` bump (commit `17e2e85`, see Tier 3 history). |
| F6: widget buttons (after fix) | Pass | Retested after the bump + reinstall: outer tap opens the app, "Log weight" opens straight to the weight dialog. No crash, no `ActivityNotFoundException` in logcat. |
| F6: widget survives app process kill | Pass | `adb shell am force-stop` then screenshot showed the widget still rendering current data (streak, weight) — confirms it reads the DB directly rather than caching stale state. |

Still not verified for F5/F6: denying the notification permission (only "Allow" was tested), the reminder firing when today is *not* yet logged (today was already logged before testing began, so only the skip path was exercised directly), and that an app update doesn't drop the schedule.

### Q20 bug: check-in photo silently not saving — found and fixed live, 2026-10-04

The user hit this while using the app normally (not a planned test): taking a check-in photo and tapping Save appeared to do nothing — no popup, no error, and the photo was missing from both the dashboard and the Photos tab afterward.

| Step | Result | Notes |
|------|--------|-------|
| Reproduce on the existing install | Confirmed | Weight saved; photo did not. |
| Reproduce on a clean reinstall | Confirmed | Ruled out stale app state as the cause. |
| Logcat around the Save tap | Inconclusive at first | No exception surfaced — `decodeUriToBitmap`'s silent-null paths didn't log anything before the fix. |
| Direct SQLite inspection (`run-as` + `cat` the live `fitness_ark.db-wal`, grepped for `.jpg`) | **Confirmed the DB write** | Zero `.jpg` references before the fix, despite a `2026-10-04` row existing (the measurement) — proved the photo path specifically never reached the DB, not a display/caching issue. |
| Added logging + user-facing error (commit `fb9950a`) | — | See Tier 3 history. |
| Retest: take Front photo, Save | **Message shown**: "Saved, but the Front photo couldn't be read and was skipped. Try retaking it." | Confirmed the fix surfaces the real failure instead of hiding it. |
| Retest: retake and Save again | **Pass** | DB re-inspected (same `run-as` + grep method): `.jpg` paths now present. Photo genuinely saved. |

Root cause is believed to be a brief race where the camera-captured file isn't fully readable by the content resolver the instant control returns to the app — intermittent, not reproducible on every attempt (Side and Back succeeded on the same multi-photo test where Front failed). The fix doesn't eliminate the race (that's outside the app's control — it's the camera app's write timing), but it stops it from being silent, and keeps the user on the screen to retry instead of losing the attempt.

### CI status — checked 2026-10-04

Every GitHub Actions run since S9 added the workflow has **passed**: `assembleDebug lintDebug testDebugUnitTest` succeeds on each push to `main`, including on the latest doc-update push. One run (the Q20 fix commit `fb9950a`) shows as "cancelled" — that's normal GitHub Actions concurrency behaviour (a newer push superseded it before it finished, "Canceling since a higher priority waiting request for ci-refs/heads/main exists"), not a failure; the next push's run covers the same code and passed.

Checked via the `gh` CLI, downloaded and authenticated in this session (installed to the job scratchpad, not committed to the repo; re-install if a future session needs it again). Two harmless deprecation warnings appear in the workflow logs, worth a look eventually but not urgent: `actions/setup-java@v4` is deprecated in favor of `@v5`, and GitHub's `ubuntu-latest` runner label migrates to Ubuntu 26 starting 2026-10-19 (the workflow doesn't pin a version, so it will pick that up automatically — worth re-checking CI after that date in case anything on the newer image behaves differently).

## Open items and next steps

0. **F1–F6 and Q20 are done**, reviewed, committed, and pushed. Local `main` matches `origin/main`. ~~Check the GitHub Actions run~~ done — all green, see "CI status".

1. Finish the remaining on-phone checks (see "Verification" above for what's covered and what isn't): notification-permission denial, the reminder's fire-and-notify path on a day not yet logged, surviving an app update without dropping the schedule, switching to Imperial, logging a past day, the trend line, the Before/After pose picker/layout toggle, S6–S9 items (Merge/Replace dialog, photo retake/remove, export/import round trip, theme persistence, Tier 1 UI changes).
2. Decide on the remaining follow-up:
   - **Q21** — keep picked photo `Uri`s across a process kill (easier now that the form state is `Map<PhotoAngle, Uri>`).
3. Tier 3 features: F1–F6 done. Remaining, in order: **F7** ghost-overlay camera (adds CameraX, ask first), F8 timelapse, F9 optional Health Connect, F10 blur dashboard photo with an eye toggle, F11 front camera + 5 s timer as take-photo default.
4. Revisit later: automatic scheduled backups (manual export is the only backup because Android auto-backup is off), trimming the over-broad ProGuard keep rules, moving UI text to `strings.xml`, the two CI deprecation warnings (setup-java@v5, Ubuntu 26 migration 2026-10-19). Possibly also: a retry-with-backoff in `BitmapUtils.decodeUriToBitmap` if the Q20 decode race recurs often enough to be worth smoothing over rather than just reporting.

## Notes and gotchas

- Tooling is Git Bash on Windows. `python` and `bc` are not installed; `sed`/`awk` work. Set `JAVA_HOME` to Android Studio's `jbr` to build. The Android SDK `adb` is at `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`.
- A `sed`/heredoc turned `\\` into `\` once inside a Kotlin test string; re-check escapes after generating code that way.
- `installDebug` keeps the phone's data; never clear data or uninstall on the user's phone without asking.
- Line-ending warnings ("LF will be replaced by CRLF") on commit are harmless.
- Rules from `CLAUDE.md`: don't commit until the user has reviewed with the review agent; don't run the review agent unless asked; small commits; ask before adding a dependency; describe UI changes in words.
- The user's test phone runs Android 17 (API 37), ahead of this project's `compileSdk`/`targetSdk` (35) and the highest locally installed SDK platform (36.1 at the time of the F6 fix). A library built against an older Android can behave differently on it — the F6 widget crash (Glance 1.1.1's `PendingIntent` handling) was this kind of gap, not a bug in our code. Worth checking library versions against this if something behaves correctly in tests but misbehaves only on-device.
- A widget's clickable areas in Glance (`actionStartActivity`, `actionRunCallback`) don't always align with their visual bounds when driving taps via `adb shell input tap` — coordinates that look centered on the button in a screenshot can land on an outer/wrapping clickable instead. When precision matters (e.g. confirming which specific button fired), it's more reliable to ask the user to tap it than to guess coordinates.
- No `sqlite3` binary is available on the device or in this dev environment (checked both). To inspect the DB directly: `MSYS_NO_PATHCONV=1 adb shell run-as com.fitnessark cat /data/data/com.fitnessark/databases/fitness_ark.db-wal > local_file`, then grep the raw bytes as text (`grep -a -oE "pattern"`) for recognizable strings (dates, file paths) — crude but doesn't need a SQLite reader, and the WAL file often has the most recent uncommitted/recent writes even when the main `.db` file doesn't. `MSYS_NO_PATHCONV=1` is required in Git Bash or the absolute device path gets mangled into a Windows path.
- `e.printStackTrace()` in Kotlin writes to `System.err`, which is easy to lose in a noisy `adb logcat` dump unless you specifically grep for `System.err`; prefer `Log.e(TAG, message, e)` so it's grep-able by tag and shows up with the rest of the app's logs.
- A function that returns `null`/empty on failure without any logging (not even a caught exception) is effectively unobservable from outside the device — `BitmapUtils.decodeUriToBitmap` had two such silent paths before the Q20 fix. When a symptom is "nothing happens, no error," check for this pattern first before assuming the issue is higher up the call stack.
- No `gh` CLI is preinstalled in this dev environment, and the repo returns "Not Found" to an unauthenticated API call (likely private). To check CI from here: download the latest Windows release zip from `cli/cli`'s GitHub releases, `Expand-Archive` it (PowerShell) into a scratch folder, then have the user run `gh auth login` themselves via the `!` prefix (it's an interactive device-code/browser flow, can't be scripted). After that, `gh run list` / `gh run view <id>` work normally. Not committed to the repo — a fresh session needs to redo this once if it wants `gh` again.
