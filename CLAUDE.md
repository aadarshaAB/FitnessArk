# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Fitness Ark — a local-first Android fitness tracking app (Kotlin + Jetpack Compose). Daily weight/body-measurement check-ins, in-app progress photos (front/side/back) with a before/after slider, MPAndroidChart progress charts, streak tracking, and full ZIP export/import via Android SAF. Single Gradle module (`:app`), namespace `com.fitnessark`, minSdk 29 / targetSdk & compileSdk 35, Kotlin/JVM target 17.

**Getting context fast:** read this file (how it's built and the working rules), then `PROGRESS.md` (everything done since the start, how it was verified, what's next), then `ENHANCEMENT_PLAN.md` (the item-by-item plan and the reasons). Keep all three up to date when work lands.

## Commands

Build and run from the project root (Windows: use `gradlew.bat`, otherwise `./gradlew`).

```bash
./gradlew assembleDebug          # build debug APK
./gradlew installDebug           # build + install on connected device/emulator
./gradlew test                   # unit tests (all modules)
./gradlew testDebugUnitTest      # unit tests, debug variant only
./gradlew connectedAndroidTest   # instrumented tests on a connected device/emulator
./gradlew lint                   # Android lint
```

Lint needs network access the first time (it downloads its tooling), so don't pass `--offline` for it. `local.properties` (not committed) must point `sdk.dir` at the Android SDK.

To run a single test class/method with Gradle: `./gradlew testDebugUnitTest --tests "com.fitnessark.SomeTest"`.

Tests are JVM unit tests run with Robolectric (no emulator needed), under `app/src/test/kotlin`; `TestSupport` has the in-memory Room DB and date helpers. Robolectric downloads its Android jars on first run, so that needs network once. There are no instrumented tests, so `connectedAndroidTest` has nothing to run. Tests run against a plain `Application` (`robolectric.properties`), not `FitnessArkApp`, so Koin is not started.

`gradle/wrapper/gradle-wrapper.jar` is committed, so `./gradlew` works from the command line (set `JAVA_HOME` to a JDK 17+, e.g. Android Studio's bundled `jbr`). If it ever goes missing, run `gradle wrapper --gradle-version=8.9`.

Device testing (Windows): `adb` is at `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`; `./gradlew installDebug` updates the app in place and keeps its data, so never clear app data or uninstall on the user's phone without asking. Save screenshots (`adb exec-out screencap -p`) under the session scratchpad, since reads outside the project are blocked. `PROGRESS.md` records what has and hasn't been checked on a device.

CI: `.github/workflows/ci.yml` runs `assembleDebug lintDebug testDebugUnitTest` on every push to `main` and every pull request.

## Architecture

MVVM with a single Koin DI module, one Room database, and a bottom-nav Compose NavHost — no other architectural layers (no use-case/interactor layer; ViewModels call repositories directly).

- **DI (`di/AppModule.kt`)** — the entire dependency graph (DB, DAOs, utils, repositories, ViewModels) is declared in one Koin module (`appModule`), installed in `FitnessArkApp.onCreate()`. When adding a repository, util, or ViewModel, register it here.
- **Data layer** — `data/local/entity` holds Room entities (`MeasurementEntity`, `PhotoEntity`) and `Converters` (Date↔Long, and float rounding to 2 decimals applied via a `@TypeConverter`, which is a deliberate persistence-layer rounding rule — don't "fix" values that look like they should round differently at read time). `data/local/dao` holds the Room DAOs. `data/local/AppDatabase` is the single Room DB (`fitness_ark.db`, currently version 2, `exportSchema = true` with schemas committed under `app/schemas/`). There is deliberately no destructive fallback: to change an entity, bump `version`, add a `Migration` to `AppDatabase.MIGRATIONS`, and add a data-preserving test in `AppDatabaseMigrationTest`. `data/repository` wraps DAOs in `MeasurementRepository` / `PhotoRepository`; repositories expose `Flow` for observed lists and `suspend fun` for one-shot reads/writes.
- **Photo storage** — photos are not stored as blobs in Room. `PhotoRepository` compresses bitmaps via `ImageCompressor`, writes front/side/back/thumbnail JPEGs to internal storage (`util/ImageCompressor`, `util/FileUtils`), and stores only file paths in `PhotoEntity`. Only one photo entry exists per calendar day — `PhotoRepository.savePhoto` merges into that day's existing entry (angles not passed in are kept; a replaced angle's old file is deleted). Screens and ViewModels pass photo `Uri`s (or a `Map<PhotoAngle, Uri>`) to the repository, which decodes them with the application context on its injectable `ioDispatcher`; never pass a `Context` from a screen into a ViewModel. Angles are the `PhotoAngle` enum (`data/model`), not strings. `deletePhotoAngle` removes one angle's file and rebuilds the thumbnail from the next remaining angle. Camera captures go to a temp file in the cache dir (`util/CameraUtils`), which sweeps ones older than a day.
- **UI** — one package per feature under `ui/` (`checkin`, `dashboard`, `measurements`, `photos`, `settings`, plus shared `theme`), each typically a `XScreen.kt` (Compose) + `XViewModel.kt` (StateFlow-based `UiState` data class, updated via `MutableStateFlow.update {}`). `ui/navigation/Navigation.kt` defines the `Screen` sealed class / routes and the single `NavHost`; the bottom nav bar shows only for the four top-level `Screen` routes, not for detail routes like `checkin?date={date}` or `photo/{id}`.
- **ViewModel args** — most ViewModels take only injected dependencies via Koin `get()`; `CheckinViewModel` additionally takes a runtime `date` param via Koin's `params.get()` (see `AppModule.kt`), because check-in can be opened either for "today" or for a specific past date from the photo/measurement history.
- **Export/Import** — `util/ZipUtils` writes the full-backup ZIP (format `BACKUP_VERSION`, currently 2; a newer backup is refused) and reads one by streaming it into `filesDir/import_staging` (no photo is held in memory; zip-slip entry names are rejected). `data/repository/BackupRepository.restore` then moves the staged photos into place and writes all rows in one Room transaction (all or nothing; a failure removes the files it placed). `ImportMode.MERGE` keeps current data and, for a day in both, takes the backup's values but keeps anything the backup lacks (per field, per photo angle); `REPLACE` wipes first. The UI (`ui/settings`) asks which mode after the file is picked. Driven through Android's Storage Access Framework (scoped storage, API 29+). The export ZIP is built in `filesDir/exports` and deleted once copied to the user's chosen location.
- **Preferences** — `data/repository/PreferencesRepository` wraps Jetpack DataStore (the `ThemeMode`, the `UnitSystem`, and `ReminderSettings`). Add new settings there rather than keeping them in memory.
- **Daily reminder (F5)** — `util/ReminderScheduler` and `util/ReminderWorker` (WorkManager) fire a notification once a day at the time set in Settings, skip it if that day is already logged, then reschedule themselves for the next day — a chain of one-shot work, not a single periodic request, so a changed time takes effect on the very next firing. `ReminderWorker` resolves `MeasurementRepository` via Koin's `KoinComponent`, not constructor injection (WorkManager constructs workers itself). Settings asks for `POST_NOTIFICATIONS` (API 33+) only when turning the reminder on; `FitnessArkApp.onCreate()` re-arms it if left on (e.g. after an app update). Needs `androidx.work:work-runtime-ktx`.
- **Home-screen widget (F6)** — `ui/widget/FitnessArkWidget` (Jetpack Glance) shows the streak, today's weight and a "Log weight" button that opens `MainActivity` straight to its weight dialog (`MainActivity.EXTRA_OPEN_WEIGHT_DIALOG` → `DashboardScreen(openWeightDialogOnStart = true)`), since the widget itself can't take typed input. It reads the repositories directly (a widget composes outside any screen, so there's no ViewModel) and is refreshed by `util/WidgetUpdater.refresh()`, called from `DashboardViewModel.updateWeight` and `CheckinViewModel.save` after a measurement is saved. Needs `androidx.glance:glance-appwidget`.
- **Units (metric/imperial)** — data is always stored metric; `UnitSystem` (`data/model`) only changes display and typing. `Metric.toDisplay/toMetric/unit(system)/format` do the conversion, screens read the choice from `LocalUnitSystem` (provided in `MainActivity`), and `MeasurementInput.validate/parseMetric` take the system and range-check in metric. `CheckinViewModel` gets the unit `Flow` injected; a field left as pre-filled keeps its stored value, so re-saving in lb/in does not drift kg/cm. Backups are unaffected.
- **Trend line** — `util/TrendLine` does the 7-day trailing moving average (calendar days; unlogged days are skipped, not zero). `MeasurementsViewModel.getTrendData()` averages over all logged days and returns the visible ones on the chart's x-axis; the chart draws it as a dashed line toggled by the "7-day avg" chip (`showTrend`). The dashboard card shows `weeklyAverageWeight`. Chart x-offsets now use `DateUtils.getDaysBetween` (DST-safe).
- **Photo comparison** — Before/After mode on the Photos tab has a pose picker (`PhotoTimelineUiState.compareAngle`, default front; both photos use it, with no fallback to another pose) and a Slider / Side by side layout (`ComparisonLayout`). A photo without the chosen pose shows a "No <pose> photo" message instead. Deleting a day is only offered from the normal photo view, not the Before/After pickers.
- **Check-in date** — `CheckinViewModel.setDate` (driven by the date badge/picker on the check-in screen; no future days) reloads that day's entry, or blanks the form; picked photos are kept. Dashboard passes an explicit `checkin?date=<now>` so an app left open past midnight doesn't log under yesterday.
- **Measurement input** — `util/MeasurementInput` parses typed values (accepts `,` or `.`) and range-checks them (weight 20–400 kg, other measurements 10–300 cm). Use it for any new measurement field. Values are always stored metric. Measurements are identified by the `Metric` enum (`data/model`, which also holds each one's label/unit and `valueIn(entity)`), not by string keys. Dates go through `util/DateUtils` (`java.time`; no `SimpleDateFormat`).
- **Blank = null** — measurement fields are nullable (`Float?`); `null` means "not logged" (show "—", skip in charts/averages). Never store 0 for blank. Old v1 data and v1 backups used 0, which the v2 migration and the import convert to null.
- **One row per day** — `MeasurementEntity` and `PhotoEntity` each carry a `localDate` (ISO `yyyy-MM-dd`, device zone at write time) with a unique index. `MeasurementRepository.saveMeasurement` and `PhotoRepository.savePhoto`/`insertPhotoEntity` upsert by day (the day's existing id is kept), and the DB rejects duplicates even if a caller bypasses them. Don't query days by millisecond ranges; use `DateUtils.localDateKey`.
- **Dashboard** — `DashboardViewModel` derives its state from the measurements and photos `Flow`s (plus a once-a-minute day-rollover check), so screens never need to call a reload.
- **Colour** — the app tracks without a goal, so weight/measurement changes use a neutral colour with ↑/↓ arrows, never red/green "good vs bad". Use theme colour pairs (`primary`/`onPrimary`, etc.), not hard-coded colours, so light mode stays readable.

## Permissions

`POST_NOTIFICATIONS` (API 33+), requested at runtime from Settings only when the daily reminder (F5) is turned on — the app's only permission. Photos are taken via the system camera app (`ActivityResultContracts.TakePicture` into a `FileProvider` URI) and picked via the system photo picker (`PickVisualMedia`); neither needs a runtime permission, and the manifest declares neither `CAMERA` nor storage permissions (declaring `CAMERA` without requesting it makes the system camera throw `SecurityException`). Accompanist is not used.

## Known Issues / Tech Debt

- Test coverage is a start, not complete: 103 JVM tests cover streak, check-in saving, photo merging/deletion, backup staging + zip-slip + version check, merge/replace restore and rollback, date helpers, chart labels, dashboard change, input validation, DB migrations, and `PreferencesRepository` (theme/units/reminder settings). Not covered: UI/Compose screens (including the Merge/Replace dialog), the camera flow, `SettingsViewModel` export/import wiring, `ReminderWorker`'s notification path, and the Glance widget (none of these run under Robolectric's plain `Application`).
- S6–S9 are built, unit-tested and pushed, but not yet confirmed working on a phone, and the GitHub Actions workflow has not been seen to pass. See `PROGRESS.md`.
- `CheckinViewModel` keeps the photo `Uri`s you've picked in memory only; a full process kill while the form is open loses them. (The camera result itself is preserved via `rememberSaveable`.)
- In `CheckinViewModel.save()`, a photo that can't be decoded is skipped without telling the user.

## Workflow Preferences

- Prefer small, reviewable commits over one giant diff.
- Ask before adding a new third-party dependency.
- When a change touches UI, describe the UX change in words, not just the code.

## Rules to Follow
- Dont auto commit the code after a task is complted first i need code review with Agent then only i will give permission to commit.
- Dont auto run review agent i ask for it and i will give permission.