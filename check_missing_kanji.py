#!/usr/bin/env python3
"""
Check which kanji from kradfile.json are missing in kanjidic.json
"""

import json
import sys
from pathlib import Path
from typing import Set, List, Dict

def load_kradfile(file_path: str) -> Set[str]:
    """Load kanji characters from kradfile.json"""
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            data = json.load(f)
        
        kanji_dict = data.get('kanji', {})
        kanji_set = set(kanji_dict.keys())
        print(f"✅ Loaded {len(kanji_set)} kanji from kradfile.json")
        return kanji_set
    
    except Exception as e:
        print(f"❌ Error loading kradfile: {e}")
        return set()

def load_kanjidic(file_path: str) -> Set[str]:
    """Load kanji characters from kanjidic.json"""
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            data = json.load(f)
        
        characters = data.get('characters', [])
        kanji_set = {char.get('literal', '') for char in characters if char.get('literal')}
        print(f"✅ Loaded {len(kanji_set)} kanji from kanjidic.json")
        return kanji_set
    
    except Exception as e:
        print(f"❌ Error loading kanjidic: {e}")
        return set()

def categorize_missing_kanji(missing_kanji: List[str]) -> Dict[str, List[str]]:
    """Categorize missing kanji by Unicode block"""
    categories = {
        'CJK Unified Ideographs Extension A': [],
        'CJK Unified Ideographs': [],
        'CJK Unified Ideographs Extension B': [],
        'CJK Unified Ideographs Extension C': [],
        'CJK Unified Ideographs Extension D': [],
        'CJK Unified Ideographs Extension E': [],
        'CJK Unified Ideographs Extension F': [],
        'CJK Compatibility Ideographs': [],
        'Other': []
    }
    
    for kanji in missing_kanji:
        code_point = ord(kanji)
        
        if 0x3400 <= code_point <= 0x4DBF:
            categories['CJK Unified Ideographs Extension A'].append(kanji)
        elif 0x4E00 <= code_point <= 0x9FFF:
            categories['CJK Unified Ideographs'].append(kanji)
        elif 0x20000 <= code_point <= 0x2A6DF:
            categories['CJK Unified Ideographs Extension B'].append(kanji)
        elif 0x2A700 <= code_point <= 0x2B73F:
            categories['CJK Unified Ideographs Extension C'].append(kanji)
        elif 0x2B740 <= code_point <= 0x2B81F:
            categories['CJK Unified Ideographs Extension D'].append(kanji)
        elif 0x2B820 <= code_point <= 0x2CEAF:
            categories['CJK Unified Ideographs Extension E'].append(kanji)
        elif 0x2CEB0 <= code_point <= 0x2EBEF:
            categories['CJK Unified Ideographs Extension F'].append(kanji)
        elif 0xF900 <= code_point <= 0xFAFF:
            categories['CJK Compatibility Ideographs'].append(kanji)
        else:
            categories['Other'].append(kanji)
    
    # Remove empty categories
    return {k: v for k, v in categories.items() if v}

def main():
    # Default paths
    kradfile_path = "app/src/main/assets/kradfile.json"
    kanjidic_path = "app/src/main/assets/kanjidic.json"
    
    # Parse command line arguments
    if len(sys.argv) > 1:
        kradfile_path = sys.argv[1]
    if len(sys.argv) > 2:
        kanjidic_path = sys.argv[2]
    
    print("🔍 Checking for kanji in kradfile that are missing from kanjidic...")
    print(f"   kradfile: {kradfile_path}")
    print(f"   kanjidic: {kanjidic_path}")
    print()
    
    # Load kanji from both files
    krad_kanji = load_kradfile(kradfile_path)
    kanjidic_kanji = load_kanjidic(kanjidic_path)
    
    if not krad_kanji or not kanjidic_kanji:
        print("❌ Failed to load one or both files")
        return 1
    
    # Find missing kanji
    missing_kanji = krad_kanji - kanjidic_kanji
    missing_list = sorted(list(missing_kanji))
    
    print(f"\n📊 Summary:")
    print(f"   Kanji in kradfile: {len(krad_kanji)}")
    print(f"   Kanji in kanjidic: {len(kanjidic_kanji)}")
    print(f"   Missing in kanjidic: {len(missing_kanji)}")
    print(f"   Coverage: {(len(kanjidic_kanji) / len(krad_kanji) * 100):.1f}%")
    
    if missing_kanji:
        print(f"\n🚫 Found {len(missing_kanji)} kanji in kradfile that are not in kanjidic:")
        
        # Categorize by Unicode block
        categories = categorize_missing_kanji(missing_list)
        
        for category, kanji_list in categories.items():
            print(f"\n📦 {category} ({len(kanji_list)} kanji):")
            
            # Print in rows of 20 characters for readability
            for i in range(0, len(kanji_list), 20):
                batch = kanji_list[i:i+20]
                print(f"   {''.join(batch)}")
                
                # Show Unicode code points for first few
                if i < 40:  # Show details for first 2 rows
                    codes = [f"U+{ord(k):04X}" for k in batch]
                    print(f"   {' '.join(codes[:10])}")
                    if len(codes) > 10:
                        print(f"   {' '.join(codes[10:])}")
        
        # Save to file
        output_file = "missing_kanji_report.txt"
        with open(output_file, 'w', encoding='utf-8') as f:
            f.write("Kanji in kradfile.json but not in kanjidic.json\n")
            f.write("=" * 50 + "\n\n")
            f.write(f"Total missing: {len(missing_kanji)}\n\n")
            
            for category, kanji_list in categories.items():
                f.write(f"{category} ({len(kanji_list)} kanji):\n")
                f.write('-' * 40 + "\n")
                
                for i in range(0, len(kanji_list), 20):
                    batch = kanji_list[i:i+20]
                    f.write(f"{''.join(batch)}\n")
                    
                    # Write Unicode code points
                    codes = [f"U+{ord(k):04X}" for k in batch]
                    f.write(f"{' '.join(codes)}\n")
                f.write("\n")
        
        print(f"\n💾 Detailed report saved to: {output_file}")
    else:
        print("\n✅ All kanji in kradfile exist in kanjidic!")
    
    # Also check the reverse - kanji in kanjidic but not in kradfile
    extra_in_kanjidic = kanjidic_kanji - krad_kanji
    if extra_in_kanjidic:
        print(f"\n📝 Note: {len(extra_in_kanjidic)} kanji exist in kanjidic but not in kradfile")
        print("   (This is normal - not all kanji have radical decompositions)")
    
    return 0

if __name__ == "__main__":
    sys.exit(main())