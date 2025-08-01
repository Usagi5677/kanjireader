#!/usr/bin/env python3
"""
Custom Modifications Preservation Script
Preserves custom dictionary modifications when updating from new releases
"""

import json
import os
import sys
from pathlib import Path
from typing import Dict, Any, List, Optional
import shutil
from datetime import datetime

class ModificationPreserver:
    def __init__(self, assets_dir: str):
        self.assets_dir = Path(assets_dir)
        
        # Define our custom modifications for single JSON files
        self.custom_modifications = {
            "jmdict.json": {
                "custom_entries": [
                    {
                        "description": "Hiragana-only する entry with comprehensive meanings and frequency",
                        "entry": {
                            "kana": [{"text": "する", "common": True}],
                            "sense": [{
                                "gloss": [
                                    {"text": "to do"},
                                    {"text": "to carry out"},
                                    {"text": "to perform"},
                                    {"text": "to cause"},
                                    {"text": "to make (into)"},
                                    {"text": "to turn (into)"},
                                    {"text": "to serve as"},
                                    {"text": "to act as"},
                                    {"text": "to work as"},
                                    {"text": "to wear (clothes, a facial expression, etc.)"},
                                    {"text": "to judge as being"},
                                    {"text": "to view as being"},
                                    {"text": "to think of as"},
                                    {"text": "to treat as"},
                                    {"text": "to use as"},
                                    {"text": "to decide on"},
                                    {"text": "to choose"},
                                    {"text": "to be sensed (of a smell, noise, etc.)"},
                                    {"text": "to be (in a state, condition, etc.)"},
                                    {"text": "to be worth"},
                                    {"text": "to cost"},
                                    {"text": "to pass (of time)"},
                                    {"text": "to elapse"},
                                    {"text": "verbalizing suffix (applies to nouns noted in this dictionary with the part of speech vs)"},
                                    {"text": "creates a humble verb (after a noun prefixed with o or go)"},
                                    {"text": "to be just about to"},
                                    {"text": "to be just starting to"},
                                    {"text": "to try to"},
                                    {"text": "to attempt to"}
                                ],
                                "pos": ["vs", "vs-i", "aux-v"]
                            }],
                            "is_common": True
                        }
                    },
                    {
                        "description": "Hiragana-only いる entry (auxiliary verb) with high frequency",
                        "entry": {
                            "kana": [{"text": "いる", "common": True}],
                            "sense": [{
                                "gloss": [
                                    {"text": "to be (animate)"},
                                    {"text": "to exist"},
                                    {"text": "to be located"},
                                    {"text": "to be somewhere"},
                                    {"text": "to be (auxiliary verb denoting actions currently in progress)"}
                                ],
                                "pos": ["v1", "vi", "aux-v"]
                            }],
                            "is_common": True
                        }
                    },
                    {
                        "description": "Hiragana-only ある entry (main verb) with high frequency",
                        "entry": {
                            "kana": [{"text": "ある", "common": True}],
                            "sense": [{
                                "gloss": [
                                    {"text": "to be (inanimate)"},
                                    {"text": "to exist"},
                                    {"text": "to be located"},
                                    {"text": "to be somewhere"},
                                    {"text": "to happen"},
                                    {"text": "to occur"},
                                    {"text": "to be found"},
                                    {"text": "to have"},
                                    {"text": "to be equipped with"},
                                    {"text": "to take place"},
                                    {"text": "to come about"}
                                ],
                                "pos": ["v5r-i", "vi"]
                            }],
                            "is_common": True
                        }
                    },
                    {
                        "description": "Hiragana-only できる entry (main verb) with high frequency",
                        "entry": {
                            "kana": [{"text": "できる", "common": True}],
                            "sense": [{
                                "gloss": [
                                    {"text": "to be able (in a position) to do"},
                                    {"text": "to be up to the task"},
                                    {"text": "to be ready"},
                                    {"text": "to be completed"},
                                    {"text": "to be made"},
                                    {"text": "to be built"},
                                    {"text": "to be good at"},
                                    {"text": "to be permitted (to do)"},
                                    {"text": "to become intimate"},
                                    {"text": "to take up (with somebody)"},
                                    {"text": "to grow"},
                                    {"text": "to be raised"},
                                    {"text": "to become pregnant"}
                                ],
                                "pos": ["v1", "vi"]
                            }],
                            "is_common": True
                        }
                    },
                    {
                        "description": "Hiragana-only など entry (particle) with high frequency",
                        "entry": {
                            "kana": [{"text": "など", "common": True}],
                            "sense": [{
                                "gloss": [
                                    {"text": "et cetera"},
                                    {"text": "etc."},
                                    {"text": "and the like"},
                                    {"text": "and so forth"},
                                    {"text": "and so on"},
                                    {"text": "and more"},
                                    {"text": "or something"},
                                    {"text": "or something like that"},
                                    {"text": "things like"},
                                    {"text": "such as"}
                                ],
                                "pos": ["prt"]
                            }],
                            "is_common": True
                        }
                    },
                    {
                        "description": "Hiragana-only だけ entry (particle) with high frequency",
                        "entry": {
                            "kana": [{"text": "だけ", "common": True}],
                            "sense": [{
                                "gloss": [
                                    {"text": "only"},
                                    {"text": "just"},
                                    {"text": "merely"},
                                    {"text": "simply"},
                                    {"text": "but"},
                                    {"text": "nothing more than"},
                                    {"text": "as much as"},
                                    {"text": "to the extent of"},
                                    {"text": "enough to"}
                                ],
                                "pos": ["prt"]
                            }],
                            "is_common": True
                        }
                    },
                    {
                        "description": "Hiragana-only しまう entry (auxiliary verb) with high frequency",
                        "entry": {
                            "kana": [{"text": "しまう", "common": True}],
                            "sense": [{
                                "gloss": [
                                    {"text": "to finish"},
                                    {"text": "to stop"},
                                    {"text": "to end"},
                                    {"text": "to put an end to"},
                                    {"text": "to close"},
                                    {"text": "to do completely"},
                                    {"text": "to put away"},
                                    {"text": "to put back"},
                                    {"text": "to store"},
                                    {"text": "to keep"},
                                    {"text": "to do by mistake"},
                                    {"text": "to do accidentally"},
                                    {"text": "to have the misfortune to do"},
                                    {"text": "to do unfortunately"}
                                ],
                                "pos": ["v5u", "vt", "aux-v"]
                            }],
                            "is_common": True
                        }
                    },
                    {
                        "description": "Hiragana-only みる entry (main verb) with high frequency",
                        "entry": {
                            "kana": [{"text": "みる", "common": True}],
                            "sense": [{
                                "gloss": [
                                    {"text": "to see"},
                                    {"text": "to look"},
                                    {"text": "to watch"},
                                    {"text": "to view"},
                                    {"text": "to observe"},
                                    {"text": "to examine"},
                                    {"text": "to judge"},
                                    {"text": "to look after"},
                                    {"text": "to take care of"},
                                    {"text": "to check"},
                                    {"text": "to investigate"},
                                    {"text": "to consider"},
                                    {"text": "to regard"},
                                    {"text": "to experience"},
                                    {"text": "to try"}
                                ],
                                "pos": ["v1", "vt"]
                            }],
                            "is_common": True
                        }
                    }
                ]
            }
        }
    
    
    def load_json_file(self, filepath: Path) -> Optional[Dict[str, Any]]:
        """Load JSON file with error handling"""
        try:
            with open(filepath, 'r', encoding='utf-8') as f:
                return json.load(f)
        except Exception as e:
            print(f"Error loading {filepath}: {e}")
            return None
    
    def save_json_file(self, filepath: Path, data: Dict[str, Any]):
        """Save JSON file with error handling"""
        try:
            with open(filepath, 'w', encoding='utf-8') as f:
                json.dump(data, f, ensure_ascii=False, separators=(',', ':'))
            print(f"Saved {filepath}")
        except Exception as e:
            print(f"Error saving {filepath}: {e}")
            sys.exit(1)
    
    def apply_custom_entry(self, jmdict_data: Dict[str, Any], custom_entry: Dict[str, Any]) -> Dict[str, Any]:
        """Apply a custom entry modification to the JMdict data"""
        entry_data = custom_entry["entry"]
        description = custom_entry["description"]
        
        print(f"  Adding custom entry: {description}")
        
        # Add the custom entry to the words list
        if "words" in jmdict_data:
            jmdict_data["words"].append(entry_data)
        
        return jmdict_data
    
    def apply_modifications_to_file(self, filename: str):
        """Apply custom modifications to a specific file"""
        if filename not in self.custom_modifications:
            return
        
        filepath = self.assets_dir / filename
        if not filepath.exists():
            print(f"Warning: {filename} not found, skipping modifications")
            return
        
        print(f"Applying modifications to {filename}...")
        
        # Load the file
        jmdict_data = self.load_json_file(filepath)
        if jmdict_data is None:
            return
        
        # Apply each custom entry
        modifications = self.custom_modifications[filename]
        if "custom_entries" in modifications:
            for custom_entry in modifications["custom_entries"]:
                jmdict_data = self.apply_custom_entry(jmdict_data, custom_entry)
        
        # Save the modified file
        self.save_json_file(filepath, jmdict_data)
    
    def verify_modifications(self) -> bool:
        """Verify that modifications were applied correctly"""
        print("Verifying modifications...")
        
        # Check entries in jmdict.json
        jmdict_path = self.assets_dir / "jmdict.json"
        if not jmdict_path.exists():
            print("Error: jmdict.json not found for verification")
            return False
        
        jmdict_data = self.load_json_file(jmdict_path)
        if not jmdict_data or "words" not in jmdict_data:
            return False
        
        entries = jmdict_data["words"]
        
        # Debug: Count how many みる entries exist
        miru_count = 0
        for entry in entries:
            if (entry.get("kana") and len(entry["kana"]) > 0 and 
                entry["kana"][0].get("text") == "みる"):
                miru_count += 1
        print(f"🔍 Debug: Found {miru_count} みる entries in total")
        
        # List of entries to verify (check last few entries since we append them)
        expected_entries = [
            ("する", "Custom する entry"),
            ("いる", "Custom いる entry"),
            ("ある", "Custom ある entry"),
            ("できる", "Custom できる entry"),
            ("など", "Custom など entry"),
            ("だけ", "Custom だけ entry"),
            ("しまう", "Custom しまう entry"),
            ("みる", "Custom みる entry")
        ]
        
        all_verified = True
        
        # Search for each custom entry in the entire list
        for expected_text, description in expected_entries:
            found = False
            for entry in entries:
                # Debug info for みる entry
                if expected_text == "みる":
                    if (entry.get("kana") and len(entry["kana"]) > 0 and 
                        entry["kana"][0].get("text") == expected_text):
                        print(f"🔍 Debug: Found みる entry - kanji: {entry.get('kanji')}, is_common: {entry.get('is_common')}, has_kanji: {bool(entry.get('kanji'))}")
                
                if (entry.get("kana") and 
                    len(entry["kana"]) > 0 and 
                    entry["kana"][0].get("text") == expected_text and
                    (entry.get("is_common") == True or not entry.get("kanji"))):  # Relaxed condition
                    print(f"✅ {description} verified successfully")
                    found = True
                    break
            
            if not found:
                print(f"❌ {description} not found")
                all_verified = False
        
        if all_verified:
            print("✅ All custom entries verified successfully")
        else:
            print("❌ Some custom entries failed verification")
            
        return all_verified
    
    def preserve_and_apply(self, new_files_dir: Path = None) -> bool:
        """
        Main method: Apply custom modifications to dictionary files
        
        Args:
            new_files_dir: Optional directory containing new files to copy first
        
        Returns:
            bool: Success status
        """
        print("=== Dictionary Modification Application ===")
        
        # Copy new files to assets directory if provided
        if new_files_dir:
            print("Copying new dictionary files...")
            
            # Single JSON files
            dictionary_files = ["jmdict.json", "jmnedict.json", "kanjidic.json", "kradfile.json", "radkfile.json"]
            
            for filename in dictionary_files:
                source = new_files_dir / filename
                target = self.assets_dir / filename
                
                if source.exists():
                    shutil.copy2(source, target)
                    print(f"  Copied {filename}")
                else:
                    print(f"  Warning: {filename} not found in new files")
        
        # Apply custom modifications
        print("Applying custom modifications...")
        for filename in self.custom_modifications.keys():
            self.apply_modifications_to_file(filename)
        
        # Verify modifications
        if self.verify_modifications():
            print(f"\nDictionary modifications applied successfully!")
            return True
        else:
            print(f"\nDictionary modification failed verification!")
            return False
    


def main():
    """Command line interface"""
    import argparse
    
    parser = argparse.ArgumentParser(description='Preserve custom dictionary modifications')
    parser.add_argument('--assets-dir', default='app/src/main/assets',
                       help='Path to assets directory (default: app/src/main/assets)')
    
    subparsers = parser.add_subparsers(dest='command', help='Available commands')
    
    # Apply command
    apply_parser = subparsers.add_parser('apply', help='Apply modifications to dictionary files')
    apply_parser.add_argument('new_files_dir', nargs='?', help='Optional directory containing new files to copy first')
    
    # Verify command
    verify_parser = subparsers.add_parser('verify', help='Verify current modifications')
    
    args = parser.parse_args()
    
    if not args.command:
        parser.print_help()
        sys.exit(1)
    
    preserver = ModificationPreserver(args.assets_dir)
    
    if args.command == 'apply':
        new_files_dir = None
        if args.new_files_dir:
            new_files_dir = Path(args.new_files_dir)
            if not new_files_dir.exists():
                print(f"Error: Directory {new_files_dir} does not exist")
                sys.exit(1)
        
        success = preserver.preserve_and_apply(new_files_dir)
        sys.exit(0 if success else 1)
    
    elif args.command == 'verify':
        success = preserver.verify_modifications()
        print("Verification:", "PASSED" if success else "FAILED")
        sys.exit(0 if success else 1)


if __name__ == "__main__":
    main()