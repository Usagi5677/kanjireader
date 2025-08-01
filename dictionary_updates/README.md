# Dictionary Update System

Automated system for updating JMdict and Kanjidic dictionaries while preserving custom modifications.

## Overview

This system automates the process of:
1. Downloading the latest dictionaries from [jmdict-simplified](https://github.com/scriptin/jmdict-simplified)
2. Splitting large JSON files into 4 parts each
3. Preserving custom modifications (like the comprehensive する entry)
4. Updating the app's asset files

## Directory Structure

```
dictionary_updates/
├── downloads/          # Downloaded ZIP files (temporary)
├── extracted/          # Extracted JSON files (temporary)
├── output/             # Split dictionary parts
├── backups/            # Automatic backups of assets
├── download_latest.py  # Downloads from GitHub releases
├── split_kanjidic.py   # Splits Kanjidic into 4 parts
├── preserve_modifications.py  # Handles custom modifications
├── update_dictionaries.py     # Main orchestrator script
└── README.md          # This file
```

## Prerequisites

- Python 3.6+
- `requests` library: `pip install requests`
- Your existing JMdict splitter script (place in this directory as `split_jmdict.py`)

## Quick Start

1. **Full Update (Recommended):**
   ```bash
   cd /path/to/kanjireader
   python dictionary_updates/update_dictionaries.py
   ```

2. **JMdict Only:**
   ```bash
   python dictionary_updates/update_dictionaries.py --jmdict-only
   ```

3. **Update Without Custom Modifications:**
   ```bash
   python dictionary_updates/update_dictionaries.py --no-custom
   ```

## Individual Scripts

### 1. Download Latest Dictionaries

```bash
python download_latest.py [--no-cleanup] [--dir directory]
```

Downloads and extracts the latest jmdict-eng and kanjidic2-en files.

### 2. Split Kanjidic

```bash
python split_kanjidic.py input_file.json [--output-dir directory]
```

Splits a kanjidic2.json file into 4 parts.

### 3. Preserve Modifications

```bash
# Apply modifications to new files
python preserve_modifications.py apply /path/to/new/split/files

# Verify current modifications
python preserve_modifications.py verify

# List available backups
python preserve_modifications.py list

# Restore from backup
python preserve_modifications.py restore 20250105_143022
```

## Custom Modifications

The system automatically preserves these modifications:

### する Entry (jmdict_part1.json, index 13957)
- Kana-only entry: `"する"`
- Common flag: `true`
- 37 comprehensive meanings
- Proper frequency handling (6,636,525)

To add more custom modifications, edit the `custom_modifications` dictionary in `preserve_modifications.py`.

## Workflow

1. **Automatic Backup:** Creates timestamped backup before any changes
2. **Download:** Fetches latest releases from GitHub
3. **Split:** Divides large files into manageable parts
4. **Preserve:** Applies custom modifications to new files
5. **Verify:** Confirms modifications were applied correctly
6. **Cleanup:** Removes temporary files

## Error Recovery

If something goes wrong:

1. **List available backups:**
   ```bash
   python preserve_modifications.py list
   ```

2. **Restore from backup:**
   ```bash
   python preserve_modifications.py restore TIMESTAMP
   ```

3. **Verify current state:**
   ```bash
   python update_dictionaries.py --verify-only
   ```

## After Update

1. **Rebuild Database:** Run `DatabaseBuilderActivity` in your app
2. **Test Search:** Verify "shiteimasu" returns only する
3. **Check Frequency:** Confirm する has frequency 6,636,525

## Advanced Usage

### Custom JMdict Splitter Location

If your JMdict splitter is in a different location:

```bash
python update_dictionaries.py --jmdict-splitter /path/to/your/splitter.py
```

### Different Project Root

If running from outside the project directory:

```bash
python update_dictionaries.py --project-root /path/to/kanjireader
```

## Troubleshooting

### Common Issues

1. **"No module named 'requests'"**
   ```bash
   pip install requests
   ```

2. **"JMdict splitter not found"**
   - Place your existing splitter script as `split_jmdict.py` in this directory
   - Or use `--jmdict-splitter` to specify its location

3. **"Permission denied"**
   - Check file permissions
   - Ensure you have write access to the assets directory

4. **"Verification failed"**
   - Check if custom modifications were applied correctly
   - Use `python preserve_modifications.py verify` for details

### Debug Information

Enable verbose output by editing the scripts to add more logging, or check the backup directory for previous versions.

## File Formats

### JMdict Structure
```json
[
  {
    "kana": [{"text": "する", "common": true}],
    "kanji": [{"text": "為る", "common": false}],
    "meanings": ["to do", "to perform", ...]
  }
]
```

### Kanjidic Structure
```json
[
  {
    "literal": "亜",
    "codepoint": {...},
    "reading_meaning": {...}
  }
]
```

## Contributing

To add new custom modifications:

1. Edit `preserve_modifications.py`
2. Add your modification to the `custom_modifications` dictionary
3. Test with `python preserve_modifications.py verify`

## Version History

- **v1.0** - Initial automated update system
- Handles comprehensive する entry preservation
- Supports both JMdict and Kanjidic updates
- Automatic backup and recovery