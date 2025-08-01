# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is an Android application called "Kanji Reader" that helps users learn Japanese by:
- Using camera OCR to capture and recognize Japanese text via ML Kit
- Providing comprehensive dictionary lookups for kanji and words
- Handling verb/adjective conjugations and deinflections
- Displaying animated stroke order for kanji characters
- Managing reading lists and saved words
- **NEW**: Mixed script parsing (Japanese + romaji input support)

## Build Commands

```bash
# Build the project
./gradlew build

# Clean and rebuild
./gradlew clean build

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Install debug APK on connected device
./gradlew installDebug

# Run unit tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest

# Run lint checks
./gradlew lint

# List all available tasks
./gradlew tasks
```

Note: Ensure JAVA_HOME is set and Android SDK is properly configured.

## Architecture Overview

### Core Architecture Pattern
The app uses a hybrid architecture combining:
- Activity-based UI (traditional Android pattern)
- Singleton pattern for shared dictionary resources
- Repository pattern for data access
- Observer pattern for state management

### Key Components

1. **Activities** (UI layer):
   - `MainActivity` - Camera capture and OCR
   - `DictionaryActivity` - Dictionary search interface
   - `WordDetailActivity` - Detailed word information with tabs
   - `ImageAnnotationActivity` - OCR results overlay on images
   - `ReadingsListActivity` - Saved words management

2. **Core Processing** (Business logic):
   - `JapaneseWordExtractor` - Extracts Japanese words from text
   - `MorphologicalAnalyzer` - Analyzes word forms
   - `TenTenStyleDeinflectionEngine` - Handles deinflection
   - `KuromojiMorphologicalAnalyzer` - **NEW**: Kuromoji-based morphological analysis for complex conjugations
   - `ConjugationGenerator` - Generates conjugation forms
   - `FuriganaProcessor` - Adds reading annotations

3. **Data Storage** (Data layer):
   - `DictionaryDatabase` - SQLite database with FTS5 for all dictionary searches
   - `DictionaryDatabaseFTS5` - **NEW**: Enhanced FTS5 implementation with optimized search
   - `DictionaryRepository` - **FULLY MIGRATED**: Now uses SQLite FTS5 exclusively (no HashMap)
   - `TagDictSQLiteLoader` - Runtime tag lookup and word enhancement
   - Pre-built `jmdict.db` in assets folder (built via `build_database.py`)

4. **State Management**:
   - `DictionaryStateManager` - Observable dictionary loading state
   - Various storage singletons for passing data between activities

### Data Flow

1. **OCR Flow**: Camera → ML Kit OCR → Word Extraction → Dictionary Lookup → Display
2. **Enhanced Search Flow**: Query → Repository → (Irregular Verb Check | Kuromoji Analysis | FTS5 Search) → Priority Sorting → Results
3. **Dictionary Loading**: App Start → SQLite database ready immediately → Enable UI

### Key Design Decisions

- **SQLite FTS5 Only**: Complete migration from HashMap to SQLite FTS5 for all searches
- **Pre-built Database**: Ships with APK, built via Python script (`build_database.py`)
- **Intelligent Deinflection**: Multi-layered approach with irregular verb handling and Kuromoji fallback
- **Priority-based Results**: Exact matches → Valid deinflections → Others → Proper nouns
- **Particle Validation**: Prevents false deinflections (e.g., みまで ≠ みる)
- **Instant Loading**: SQLite database ready immediately on app start

## Development Notes

### Package Structure
- Main package: `com.example.kanjireader`
- All Kotlin files in: `/app/src/main/java/com/example/kanjireader/`

### Resources
- Layouts: `/app/src/main/res/layout/`
- Assets: `/app/src/main/assets/` (contains kanji SVGs and dictionary JSON files)
- The app includes thousands of kanji stroke order SVG files

### Dependencies
- ML Kit for Japanese text recognition
- AndroidX libraries (Camera, RecyclerView, etc.)
- Kotlin Coroutines
- AndroidSVG for stroke order display
- Gson for JSON parsing

### Testing
- Unit tests in: `/app/src/test/java/com/example/kanjireader/`
- Instrumented tests in: `/app/src/androidTest/java/com/example/kanjireader/`
- Currently includes tests for the deinflection engine

### Build Configuration
- Target SDK: 35
- Min SDK: 24
- Kotlin JVM target: 17
- ViewBinding enabled
- Using Kotlin DSL for Gradle files

### Common Development Tasks

When adding new features:
1. Dictionary searches now use SQLite FTS5 exclusively via `DictionaryRepository`
2. New word processing features should integrate with `MorphologicalAnalyzer`
3. UI changes should maintain consistency with Material Design theme
4. State passing between activities uses singleton storage pattern
5. Database updates should be done via `build_database.py` Python script

### Important Files to Understand

1. `DictionaryRepository.kt` - Main interface for all dictionary operations (SQLite FTS5 only)
2. `DictionaryModels.kt` - **NEW**: All data classes (WordResult, EnhancedWordResult, VerbType, etc.)
3. `DictionaryDatabase.kt` & `DictionaryDatabaseFTS5.kt` - SQLite database implementations
4. `TagDictSQLiteLoader.kt` - Runtime tag lookup and word enhancement
5. `UnifiedDictionaryEntry.kt` - Core data model for search results
6. `DictionaryStateManager.kt` - Manages dictionary loading state
7. `RomajiConverter.kt` - Comprehensive romaji to hiragana conversion
8. `MixedScriptParser.kt` - Parser for mixed Japanese/romaji sentences
9. `KuromojiMorphologicalAnalyzer.kt` - Advanced morphological analysis for complex conjugations
10. `build_database.py` - Python script for building the SQLite database

## Mixed Script Support (NEW)

The app now supports mixed script input combining Japanese characters and romaji:

### Features:
- **Romaji Input**: `"shiteimasu"` → converts to `"しています"` → deinflects to `"する"`
- **Mixed Sentences**: `"国語woべんきょうshiteimasu"` → parses to `["国語", "を", "勉強", "する"]`
- **Particle Recognition**: `"wo"` → `"を"`, `"ga"` → `"が"`, `"ni"` → `"に"`
- **Deinflection Support**: Romaji conjugated forms are converted and deinflected properly

### Key Components:
- `RomajiConverter` - Handles Hepburn and Kunrei romanization systems
- `TenTenStyleDeinflectionEngine` - Enhanced with romaji preprocessing  
- `JapaneseWordExtractor` - Now extracts both Japanese and romaji words
- `DictionaryRepository` - Intelligent routing based on input type
- `MixedScriptParser` - Tokenizes complex mixed sentences

### Usage Examples:
```kotlin
// Search romaji directly
repository.search("shiteimasu") // Returns results for する

// Search mixed script
repository.search("watashiwa日本語woべんきょうsuru") 
// Returns results for: 私, は, 日本語, を, 勉強, する

// Parse sentences
val parser = MixedScriptParser()
val tokens = parser.parseSentence("国語woべんきょうshiteimasu")
// Returns: [国語, wo→を, べんきょう, shiteimasu→しています→する]
```

## Enhanced Japanese Search (NEW)

The app now features sophisticated Japanese search capabilities with intelligent deinflection:

### Features:
- **Irregular Verb Handling**: Specialized patterns for 来る (kuru) and 行く (iku) that Kuromoji often misparses
- **Multi-layered Deinflection**: TenTen rules + Kuromoji morphological analysis + irregular verb fallbacks
- **Smart Priority Ordering**: Exact matches > Valid deinflections > General results > Proper nouns
- **Particle Validation**: Prevents false deinflections for words ending with particles (みまで, をまで, etc.)
- **Progressive Shortening**: Handles corrupted input by progressively shortening and validating
- **Proper Noun Filtering**: Uses isJMNEDictEntry flag to deprioritize proper nouns in search results

### Key Improvements:
```kotlin
// These now work correctly:
search("こられません") // → 来る (not 凝る)
search("みた")        // → 見る appears first (not みたい)
search("みませんでした") // → 見る (complex conjugation)
search("みまで")      // → No false deinflection (correctly identified as み + まで particle)
```

## Recent Architecture Changes (January 2025)

### Complete Migration to SQLite FTS5
The codebase has been fully migrated from a hybrid HashMap/SQLite approach to using SQLite FTS5 exclusively:

- **Removed Components**:
  - `JMdictKanjiExtractor.kt` - HashMap-based Japanese lookups
  - `ExtractorStorage.kt` - Singleton for HashMap storage
  - `DATManager.kt`, `DATDictionary.kt` - XCDAT implementations
  - `TagDictLoader.kt` - HashMap-based tag loading
  - All HashMap initialization and loading code

- **Key Benefits**:
  - Instant app startup (no dictionary loading time)
  - Reduced memory usage (no in-memory HashMaps)
  - Unified search interface for all languages
  - Consistent search behavior across the app

- **Database Building**:
  - Database is pre-built using `build_database.py`
  - Ships with APK as `jmdict.db` in assets
  - No runtime database building needed

### Components:
- `checkIrregularVerbs()` - Handles 来る/行く conjugation patterns
- `KuromojiMorphologicalAnalyzer` - Advanced morphological analysis
- `isValidConjugationByKuromoji()` - Validates deinflection results
- Enhanced FTS5 queries with proper noun support
- Particle detection and validation

### Search Flow:
1. **Particle Check**: Skip deinflection for words ending with particles
2. **Irregular Verb Check**: Special handling for 来る/行く patterns
3. **Kuromoji Analysis**: Morphological analysis for complex conjugations
4. **Progressive Shortening**: Handle corrupted input with validation
5. **Priority Sorting**: Order results by relevance and proper noun status

## Dictionary Update System

The project includes Python scripts for automatically downloading and updating dictionary files from the jmdict-simplified repository.

### Update Scripts

Located in `dictionary_updates/`:

1. **`download_latest.py`** - Downloads and extracts dictionary files
2. **`preserve_modifications.py`** - Applies custom dictionary entries
3. **`update_dictionaries.py`** - Main orchestrator script

### Quick Update Process

```bash
# Update all dictionaries with custom modifications
python dictionary_updates/update_dictionaries.py

# Update only JMdict (skip Kanjidic)
python dictionary_updates/update_dictionaries.py --jmdict-only

# Update without custom modifications
python dictionary_updates/update_dictionaries.py --no-custom
```

### Update Workflow

1. **Download**: Downloads latest releases from [jmdict-simplified](https://github.com/scriptin/jmdict-simplified)
2. **Extract & Rename**: 
   - `jmdict-eng-3.6.1.json` → `jmdict.json`
   - `kanjidic2-en-3.6.1.json` → `kanjidic.json`
   - `jmnedict-all-3.6.1.json` → `jmnedict.json`
3. **Replace**: Files are placed directly in `app/src/main/assets/`, replacing existing versions
4. **Custom Modifications**: Adds hiragana-only entries for common words (する, みる, いる, etc.)
5. **Cleanup**: Removes temporary download files

### Custom Dictionary Entries

The system automatically adds hiragana-only entries for:
- **する** - comprehensive verb meanings and conjugations
- **いる** - existence/location (animate)  
- **ある** - existence/location (inanimate)
- **できる** - ability/capability
- **みる** - seeing/watching/examining
- **など** - "etc.", "and so on" 
- **だけ** - "only", "just"
- **しまう** - completion/unfortunate occurrence

These entries ensure proper dictionary lookup for both kanji and hiragana-only forms.

### Individual Script Usage

```bash
# Download only (extracts to assets)
python dictionary_updates/download_latest.py

# Apply custom modifications to existing files
python dictionary_updates/preserve_modifications.py apply

# Verify custom modifications are present
python dictionary_updates/preserve_modifications.py verify
```

### Key Features

- **No Backups**: Scripts no longer create backup files (streamlined workflow)
- **Direct Extraction**: Files go directly from downloads to assets folder
- **Automatic Renaming**: Handles version numbers in filenames automatically
- **File Replacement**: Safely replaces existing dictionary files on all platforms
- **Custom Entries**: Preserves and applies custom hiragana-only dictionary entries

### After Updates

After updating dictionary files, rebuild the SQLite database:

```bash
python build_database.py
```

This creates the optimized `jmdict.db` file used by the Android app.

