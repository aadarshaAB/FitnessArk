# Fitness Ark 🚢📈

A local-first Android fitness tracking app built with Kotlin + Jetpack Compose.

## Features
- **Daily check-ins** — weight + 5 body measurements + notes
- **Camera capture** — take front/side/back photos directly in-app (or pick from gallery)
- **Progress charts** — MPAndroidChart line charts with 7d/30d/90d/All ranges
- **Before/After slider** — drag to compare any two progress photos
- **Streak tracking** — consecutive daily logging streak
- **Export/Import** — full ZIP backup via Android SAF
- **Dark/Light theme** — defaults to dark

## Tech Stack
- Kotlin 100% · Jetpack Compose + Material 3 · Room · Koin · Coil · MPAndroidChart

## Setup

### 1. Add gradle-wrapper.jar
The `gradle-wrapper.jar` binary cannot be included in source distributions.  
Run this once after cloning:

```bash
# Option A — Android Studio handles it automatically on first sync
# Option B — if using CLI:
gradle wrapper --gradle-version=8.9
```

Or manually download from:
https://raw.githubusercontent.com/gradle/gradle/v8.9.0/gradle/wrapper/gradle-wrapper.jar  
→ place at `gradle/wrapper/gradle-wrapper.jar`

### 2. Open in Android Studio
1. **File → Open** → select the `FitnessArk` folder
2. Android Studio will prompt to sync Gradle — click **Sync Now**
3. If `gradle-wrapper.jar` is missing, Studio will offer to download it automatically

### 3. Run
- Connect an Android 10+ device (API 29+) or start an emulator
- Click **Run ▶**

## Permissions
| Permission | Purpose |
|---|---|
| `CAMERA` | Take progress photos in-app |
| `READ_MEDIA_IMAGES` | Pick photos from gallery (Android 13+) |
| `READ_EXTERNAL_STORAGE` | Pick photos from gallery (Android 10–12) |

## Project Structure
```
app/src/main/kotlin/com/fitnessark/
├── data/
│   ├── local/
│   │   ├── dao/          # Room DAOs
│   │   └── entity/       # Room entities + TypeConverters
│   └── repository/       # MeasurementRepository, PhotoRepository
├── di/                   # Koin AppModule
├── ui/
│   ├── checkin/          # Full check-in screen (camera + measurements)
│   ├── dashboard/        # Home screen
│   ├── measurements/     # Progress charts + table
│   ├── navigation/       # NavHost + bottom nav
│   ├── photos/           # Photo timeline + before/after slider
│   ├── settings/         # Export/Import/Theme
│   └── theme/            # Color, Typography, Theme
└── util/                 # CameraUtils, DateUtils, FileUtils, ImageCompressor, ZipUtils
```
