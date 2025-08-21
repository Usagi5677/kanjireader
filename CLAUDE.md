# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is an Android application called "Kanji Reader" that helps users learn Japanese by:
- Using camera OCR to capture and recognize Japanese text via ML Kit
- Providing comprehensive dictionary lookups for kanji and words
- Handling verb/adjective conjugations and deinflections
- Displaying animated stroke order for kanji characters
- **Managing reading lists, favorites, and saved words with full CRUD operations**
- **Kanji radical lookup and decomposition for finding unknown kanji**
- **Long text reading mode with furigana and word boundary detection**
- **Pitch accent data and visualization**
- **Word variants display and comprehensive tagging system**
- **Highlighted text extraction with synchronized word list scrolling**
- Mixed script parsing (Japanese + romaji input support)

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
   - `DictionaryActivity` - Dictionary search interface with advanced filtering
   - `WordDetailActivity` - Detailed word information with tabbed interface (meanings, forms, variants, pitch accent)
   - `ImageAnnotationActivity` - OCR results overlay with highlighted text extraction
   - `RadicalSearchActivity` - **NEW**: Kanji lookup using radical decomposition
   - `DictionaryTextReaderActivity` - **NEW**: Long text reading mode with furigana
   - `WordListDetailActivity` - **NEW**: Reading list management with favorites
   - `ReadingsListActivity` - **ENHANCED**: Full CRUD operations for saved words and lists

2. **Core Processing** (Business logic):
   - `JapaneseWordExtractor` - Extracts Japanese words from text with improved word boundaries
   - `MorphologicalAnalyzer` - Analyzes word forms
   - `TenTenStyleDeinflectionEngine` - Handles deinflection with particle validation
   - `KuromojiMorphologicalAnalyzer` - Kuromoji-based morphological analysis for paragraph parsing and complex conjugations
   - `ConjugationGenerator` - Generates conjugation forms
   - `FuriganaProcessor` - **ENHANCED**: Improved furigana generation with word boundary fixes
   - `PitchAccent` - **NEW**: Pitch accent pattern processing and visualization
   - `Variant` - **NEW**: Word variant handling and relationship management

3. **Data Storage** (Data layer):
   - `DictionaryDatabase` - SQLite database with FTS5 for all dictionary searches and **fast substring search via n-grams**
   - `DictionaryRepository` - **FULLY MIGRATED**: Uses SQLite FTS5 exclusively with **optimized priority ordering for English phrases**
   - `TagDictSQLiteLoader` - **ENHANCED**: Runtime tag lookup with comprehensive tagging (including rK, rk variants)
   - `AppRoomDatabase` - **NEW**: Room database for user data (saved words, reading lists, favorites)
   - `WordListRepository` - **NEW**: Repository for CRUD operations on reading lists and saved words
   - Pre-built `jmdict_fts5.db` in assets folder (**JMNEDict removed for performance**, built via `build_database.py`)

4. **State Management**:
   - `DictionaryStateManager` - Observable dictionary loading state
   - Various storage singletons for passing data between activities

### Data Flow

1. **OCR Flow**: Camera → ML Kit OCR → Word Extraction → Dictionary Lookup → **Highlighted Text Display with Synchronized Scrolling**
2. **Enhanced Search Flow**: Query → Repository → (Irregular Verb Check | Kuromoji Analysis | **N-gram Substring Search** | FTS5 Search) → **Optimized Priority Sorting** → Results
3. **Reading Mode Flow**: Long Text → **Kuromoji Paragraph Parsing** → Furigana Generation → **Interactive Word Lookup**
4. **Dictionary Loading**: App Start → SQLite database ready immediately → Enable UI
5. **User Data Flow**: Reading Lists ↔ Room Database ↔ **CRUD Operations** ↔ UI

### Key Design Decisions

- **SQLite FTS5 Only**: Complete migration from HashMap to SQLite FTS5 for all searches
- **N-gram Substring Search**: Fast substring matching (e.g., find "もてあそぶ" when searching "あそぶ") with 50-100x performance improvement
- **JMNEDict Removal**: Removed proper name dictionary for improved search performance and relevance
- **Pre-built Database**: Ships with APK, built via Python script (`build_database.py`)
- **Intelligent Deinflection**: Multi-layered approach with irregular verb handling and Kuromoji fallback
- **Optimized Priority Ordering**: Exact matches → Valid deinflections → Others (with enhanced English phrase prioritization)
- **Particle Validation**: Prevents false deinflections (e.g., みまで ≠ みる)
- **User Data Separation**: Dictionary data (read-only SQLite) + User data (Room database with CRUD)
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

1. `DictionaryRepository.kt` - Main interface for all dictionary operations with optimized priority ordering
2. `DictionaryDatabase.kt` - SQLite database with FTS5 and **n-gram substring search implementation**
3. `DictionaryModels.kt` - All data classes (WordResult, EnhancedWordResult, VerbType, etc.)
4. `TagDictSQLiteLoader.kt` - **Enhanced** runtime tag lookup with comprehensive tagging support
5. `UnifiedDictionaryEntry.kt` - Core data model for search results with variant support
6. `WordListRepository.kt` - **NEW**: Repository for reading lists and favorites CRUD operations
7. `AppRoomDatabase.kt` - **NEW**: Room database for user data persistence
8. `PitchAccent.kt` & `PitchAccentView.kt` - **NEW**: Pitch accent data processing and visualization
9. `Variant.kt` & `VariantAdapter.kt` - **NEW**: Word variant handling and display
10. `RomajiConverter.kt` - Comprehensive romaji to hiragana conversion
11. `MixedScriptParser.kt` - Parser for mixed Japanese/romaji sentences
12. `KuromojiMorphologicalAnalyzer.kt` - Advanced morphological analysis for paragraphs and conjugations
13. `RadicalSearchActivity.kt` - **NEW**: Kanji lookup using radical decomposition
14. `DictionaryTextReaderActivity.kt` - **NEW**: Long text reading mode with furigana
15. `build_database.py` - **Enhanced** Python script with n-gram generation for fast substring search

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

## Fast Substring Search (NEW - August 2025)

The app now features lightning-fast substring search using n-gram indexing:

### Implementation:
- **N-gram Generation**: Each Japanese word is broken into 2-, 3-, and 4-character substrings during database building
- **Individual Token Storage**: Each n-gram is stored as a separate FTS5 token for instant matching
- **Dual Search Strategy**: Primary FTS5 search + secondary n-gram substring search
- **Deduplication**: Intelligent result merging without duplicates

### Performance:
```
Before: 500-1100ms (slow LIKE queries)
After:  3ms (FTS5 n-gram matching)
Improvement: 50-100x faster
```

### Example:
```kotlin
// Searching "あそぶ" finds words containing it:
search("あそぶ") 
// Returns: あそぶ (exact), もてあそぶ (contains), むれあそぶ (contains), etc.
```

### Technical Details:
- `build_database.py` generates n-grams and populates `japanese_substring_fts` table
- `DictionaryDatabase.kt` performs dual FTS5 searches (exact + substring)
- Each word like "もてあそぶ" creates 7 n-gram tokens: もて, てあ, あそ, そぶ, もてあ, てあそ, あそぶ

## Recent Feature Additions (2024-2025)

### Word Management & Lists
- **Favorites System**: Heart icon for quick favoriting/unfavoriting words
- **Reading Lists**: Create, rename, delete custom reading lists
- **Full CRUD Operations**: Complete list management with Room database
- **Word List Detail View**: Dedicated activity for managing list contents

### Enhanced UI & UX  
- **Tabbed Word Details**: Organized information in meanings, forms, variants, pitch accent tabs
- **Variants Tab**: Display word variants and relationships (見る, 観る, 診る)
- **Pitch Accent Visualization**: Visual representation of Japanese pitch patterns
- **Highlighted Text Extraction**: OCR results with synchronized word list scrolling
- **Improved Card UI**: Removed accent display from dictionary cards for cleaner design

### Advanced Search & Processing
- **Radical-based Kanji Lookup**: Find kanji by selecting radicals with decomposition support
- **Long Text Reading Mode**: Full-screen text reader with furigana and interactive word lookup
- **Kuromoji Paragraph Parsing**: Advanced text analysis for complex sentences
- **Better Word Boundaries**: Improved furigana and text segmentation
- **English Phrase Priority**: Optimized search ordering for English queries

### Technical Improvements
- **JMNEDict Removal**: Eliminated proper name dictionary for better performance
- **Enhanced Tagging**: Added missing tags (rK, rk) for comprehensive word classification  
- **UI State Management**: Fixed multiple view opening issues in kanji sections
- **Normalize Radical Variants**: Standardized 3-stroke radical forms for consistent lookup

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

