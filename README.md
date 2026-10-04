# Fitness Ark 🚢📈

A local-first Android fitness tracking app built with Kotlin + Jetpack Compose. Daily weight/body-measurement check-ins, in-app progress photos (front/side/back) with a before/after slider, charts, streak tracking, and full ZIP export/import. No account, no server, no network access — everything stays on the device.

## Download

Prebuilt signed APKs are published on the [Releases page](../../releases). Download the latest `.apk` and sideload it (Android will prompt to allow installs from that source if it's not already enabled). Requires Android 10 (API 29) or newer.

## Features

- **Daily check-ins** — weight + 5 body measurements + notes, logged for today or any past day
- **In-app camera** — front lens + a 5-second timer by default (shutter button skips the wait); switch to the rear lens if needed. Gallery picking is also available
- **Before/After comparison** — pose picker (front/side/back), slider or side-by-side layout
- **Progress charts** — 7-day trailing moving average overlay, weekly average on the dashboard
- **Streak tracking** — consecutive daily logging streak, shown on the dashboard and a home-screen widget
- **Daily reminder** — optional notification at a time you choose, skipped automatically once you've already logged that day
- **Metric/Imperial** — kg/lb, cm/in; data is always stored metric
- **Dashboard photo privacy** — the latest progress photo is blurred by default, with a tap-to-reveal toggle
- **Export/Import** — full ZIP backup via Android's Storage Access Framework, with Merge/Replace on import
- **Dark/Light/System theme**

## Tech stack

Kotlin · Jetpack Compose + Material 3 · Room · Koin · Coil · MPAndroidChart · CameraX · WorkManager · Jetpack Glance (widget) · DataStore

Single Gradle module (`:app`), namespace `com.fitnessark`, minSdk 29 / targetSdk & compileSdk 35, Kotlin/JVM target 17.

## Building from source

```bash
git clone https://github.com/aadarshaAB/FitnessArk.git
cd FitnessArk
# Point sdk.dir at your Android SDK in local.properties, e.g.:
#   sdk.dir=/path/to/Android/Sdk
./gradlew assembleDebug      # or installDebug with a device/emulator connected
```

`gradle/wrapper/gradle-wrapper.jar` is committed, so `./gradlew` (or `gradlew.bat` on Windows) works directly after cloning — no manual wrapper setup needed.

```bash
./gradlew test                # unit tests (JVM, Robolectric)
./gradlew lint                # Android lint
```

A release build (`./gradlew assembleRelease`) needs its own signing config (`RELEASE_STORE_FILE`/`RELEASE_STORE_PASSWORD`/`RELEASE_KEY_ALIAS`/`RELEASE_KEY_PASSWORD` in `local.properties`); without one it produces an unsigned APK.

## Permissions

| Permission | Purpose |
|---|---|
| `CAMERA` | In-app camera for progress photos, requested at runtime the first time it's used |
| `POST_NOTIFICATIONS` (Android 13+) | Daily check-in reminder, requested only when you turn it on |

Gallery photo picking uses Android's system photo picker, which needs no permission.

## Project structure

```
app/src/main/kotlin/com/fitnessark/
├── data/
│   ├── local/
│   │   ├── dao/          # Room DAOs
│   │   └── entity/       # Room entities + TypeConverters
│   ├── model/            # PhotoAngle, Metric, UnitSystem
│   └── repository/       # MeasurementRepository, PhotoRepository, BackupRepository, PreferencesRepository
├── di/                   # Koin AppModule (the entire DI graph)
├── ui/
│   ├── camera/           # In-app camera (CameraX)
│   ├── checkin/          # Full check-in screen + ViewModel
│   ├── dashboard/        # Home screen
│   ├── measurements/     # Progress charts + table
│   ├── navigation/       # NavHost + bottom nav
│   ├── photos/           # Photo timeline + before/after comparison
│   ├── settings/         # Export/Import/Theme/Reminder
│   ├── theme/            # Color, Typography, Theme
│   └── widget/           # Home-screen widget (Jetpack Glance)
└── util/                 # CameraUtils, DateUtils, FileUtils, ImageCompressor, ZipUtils, ReminderScheduler/Worker
```

## License

Personal project, source available for reference. No license has been chosen yet.
