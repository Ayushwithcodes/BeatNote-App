# BeatNote

An offline-first Android workstation designed for songwriters, lyricists, and vocal artists to integrate continuous audio playback with contextual multi-draft text composition.

## Architectural Overview

BeatNote follows modern Android development standards, utilizing an MVVM (Model-View-ViewModel) architecture with Unidirectional Data Flow (UDF). The application is decoupled into distinct data, domain, and presentation layers to ensure reliability, offline persistence, and memory efficiency.

### System Architecture
- **Presentation Layer:** 100% declarative UI built with Jetpack Compose. Utilizes custom layout state management to dynamically collapse playback controls and suppress non-critical UI elements upon keyboard focus, allocating maximum viewport space for text input.
- **Background Media Engine:** AndroidX Media3 (ExoPlayer) embedded inside a bounded Foreground Service. Guarantees uninterrupted playback during background transitions, process lifecycles, and screen-off states. Supports gapless playback, custom seek intervals, and playback queue transformations (shuffle, repeat-one, repeat-all).
- **Data Persistence Layer:** Local-first relational schema orchestrated via Room Database (SQLite abstraction). Implements strict foreign-key relationships with cascading delete logic across audio files, draft records, and playlist cross-references.
- **State & Concurrency Management:** Built entirely on Kotlin Coroutines and asynchronous StateFlow primitives. Includes debounced write operations (500ms threshold) to prevent redundant disk I/O during real-time lyric composition.

## Database Migrations
The relational database utilizes versioned, non-destructive schema migrations to maintain data integrity across releases:
- `MIGRATION_1_2`: Normalized database structure from single-string storage to a dedicated relational multi-draft table with foreign-key constraints.
- `MIGRATION_2_3`: Integrated track metadata persistence, playlist models, and n-to-m associative join tables for audio-playlist relationships without affecting existing lyric data.

## Technology Stack

- **Primary Language:** Kotlin
- **UI Framework:** Jetpack Compose (Material 3)
- **Audio Service:** AndroidX Media3 / ExoPlayer
- **Database Engine:** Room (SQLite)
- **Concurrency:** Kotlin Coroutines, StateFlow
- **Architecture Pattern:** MVVM + Clean Architecture principles
- **Minimum SDK:** API Level 26 (Android 8.0)
- **Target SDK:** API Level 34

## Build & Installation

### Prerequisites
- Android Studio Ladybug (2024.2.1) or higher
- JDK 17
- Android SDK 34

### Compilation
1. Clone the repository:
   ```bash
   git clone [https://github.com/Ayushwithcodes/Beatnote.git](https://github.com/Ayushwithcodes/Beatnote.git)