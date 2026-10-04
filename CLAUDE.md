# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Fitness Ark — a local-first Android fitness tracking app (Kotlin + Jetpack Compose). Daily weight/body-measurement check-ins, in-app progress photos (front/side/back) with a before/after slider, MPAndroidChart progress charts, streak tracking, and full ZIP export/import via Android SAF. Single Gradle module (`:app`), namespace `com.fitnessark`, minSdk 29 / targetSdk & compileSdk 35, Kotlin/JVM target 17.

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

`gradle/wrapper/gradle-wrapper.jar` is committed, so `./gradlew` works from the command line (set `JAVA_HOME` to a JDK 17+, e.g. Android Studio's bundled `jbr`). If it ever goes missing, run `gradle wrapper --gradle-version=8.9`.

## Architecture

MVVM with a single Koin DI module, one Room database, and a bottom-nav Compose NavHost — no other architectural layers (no use-case/interactor layer; ViewModels call repositories directly).

- **DI (`di/AppModule.kt`)** — the entire dependency graph (DB, DAOs, utils, repositories, ViewModels) is declared in one Koin module (`appModule`), installed in `FitnessArkApp.onCreate()`. When adding a repository, util, or ViewModel, register it here.
- **Data layer** — `data/local/entity` holds Room entities (`MeasurementEntity`, `PhotoEntity`) and `Converters` (Date↔Long, and float rounding to 2 decimals applied via a `@TypeConverter`, which is a deliberate persistence-layer rounding rule — don't "fix" values that look like they should round differently at read time). `data/local/dao` holds the Room DAOs. `data/local/AppDatabase` is the single Room DB (`fitness_ark.db`, currently version 1 with `fallbackToDestructiveMigration()` — bump the version and add a real migration instead of relying on destructive fallback once the app has real user data). `data/repository` wraps DAOs in `MeasurementRepository` / `PhotoRepository`; repositories expose `Flow` for observed lists and `suspend fun` for one-shot reads/writes.
- **Photo storage** — photos are not stored as blobs in Room. `PhotoRepository` compresses bitmaps via `ImageCompressor`, writes front/side/back/thumbnail JPEGs to internal storage (`util/ImageCompressor`, `util/FileUtils`), and stores only file paths in `PhotoEntity`. Only one photo entry exists per calendar day — `PhotoRepository.savePhoto` merges into that day's existing entry (angles not passed in are kept; a replaced angle's old file is deleted). `deletePhotoAngle` removes one angle's file and rebuilds the thumbnail from the next remaining angle. Camera captures go to a temp file in the cache dir (`util/CameraUtils`), which sweeps ones older than a day.
- **UI** — one package per feature under `ui/` (`checkin`, `dashboard`, `measurements`, `photos`, `settings`, plus shared `theme`), each typically a `XScreen.kt` (Compose) + `XViewModel.kt` (StateFlow-based `UiState` data class, updated via `MutableStateFlow.update {}`). `ui/navigation/Navigation.kt` defines the `Screen` sealed class / routes and the single `NavHost`; the bottom nav bar shows only for the four top-level `Screen` routes, not for detail routes like `checkin?date={date}` or `photo/{id}`.
- **ViewModel args** — most ViewModels take only injected dependencies via Koin `get()`; `CheckinViewModel` additionally takes a runtime `date` param via Koin's `params.get()` (see `AppModule.kt`), because check-in can be opened either for "today" or for a specific past date from the photo/measurement history.
- **Export/Import** — `util/ZipUtils` handles full-backup ZIP creation/extraction (DB + photo files) driven from `ui/settings`, using Android's Storage Access Framework rather than raw file paths, since the app targets scoped storage (API 29+). The export ZIP is built in `filesDir/exports` and deleted once copied to the user's chosen location. Import rejects entry names containing path separators (zip-slip).
- **Preferences** — `data/repository/PreferencesRepository` wraps Jetpack DataStore (currently just the `ThemeMode`: light/dark/system). Add new settings (e.g. the metric/imperial unit choice) there rather than keeping them in memory.
- **Measurement input** — `util/MeasurementInput` parses typed values (accepts `,` or `.`) and range-checks them (weight 20–400 kg, other measurements 10–300 cm). Use it for any new measurement field. Values are always stored metric.
- **Blank = 0** — a blank measurement is stored as `0f`, so every read path must treat values `<= 0` as "not logged" (show "—", skip in charts/averages). This goes away if the planned nullable-fields migration (S3) is done.
- **Colour** — the app tracks without a goal, so weight/measurement changes use a neutral colour with ↑/↓ arrows, never red/green "good vs bad". Use theme colour pairs (`primary`/`onPrimary`, etc.), not hard-coded colours, so light mode stays readable.

## Permissions

None. Photos are taken via the system camera app (`ActivityResultContracts.TakePicture` into a `FileProvider` URI) and picked via the system photo picker (`PickVisualMedia`); neither needs a runtime permission, and the manifest deliberately declares none (declaring `CAMERA` without requesting it makes the system camera throw `SecurityException`). Accompanist is not used.

## Known Issues / Tech Debt

- No automated tests exist yet (no unit or instrumented test sources in the project).
- `CheckinViewModel` keeps the photo `Uri`s you've picked in memory only; a full process kill while the form is open loses them. (The camera result itself is preserved via `rememberSaveable`.)
- In `CheckinViewModel.save()`, a photo that can't be decoded is skipped without telling the user.
- `AppDatabase` is at schema version 1 with `fallbackToDestructiveMigration()` — any entity change will wipe local user data until a real migration path is added.

## Workflow Preferences

- Prefer small, reviewable commits over one giant diff.
- Ask before adding a new third-party dependency.
- When a change touches UI, describe the UX change in words, not just the code.

## Rules to Follow
- Dont auto commit the code after a task is complted first i need code review with Agent then only i will give permission to commit.
- Dont auto run review agent i ask for it and i will give permission.