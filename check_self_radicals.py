#!/usr/bin/env python3
"""
Check which kanji in kradfile.json contain themselves as a radical
For example: 火 has 火 as one of its radicals
"""

import json
import sys
from pathlib import Path
from typing import Dict, List, Tuple

def load_kradfile(file_path: str) -> Dict[str, List[str]]:
    """Load kanji-to-radicals mapping from kradfile.json"""
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            data = json.load(f)
        
        kanji_dict = data.get('kanji', {})
        print(f"✅ Loaded {len(kanji_dict)} kanji from kradfile.json")
        return kanji_dict
    
    except Exception as e:
        print(f"❌ Error loading kradfile: {e}")
        return {}

def find_self_radicals(kanji_dict: Dict[str, List[str]]) -> List[Tuple[str, List[str]]]:
    """Find kanji that contain themselves as a radical"""
    self_radicals = []
    
    for kanji, radicals in kanji_dict.items():
        if kanji in radicals:
            self_radicals.append((kanji, radicals))
    
    return sorted(self_radicals)

def categorize_by_radical_count(self_radicals: List[Tuple[str, List[str]]]) -> Dict[int, List[Tuple[str, List[str]]]]:
    """Categorize kanji by the number of radicals they have"""
    categories = {}
    
    for kanji, radicals in self_radicals:
        count = len(radicals)
        if count not in categories:
            categories[count] = []
        categories[count].append((kanji, radicals))
    
    return categories

def is_basic_radical(kanji: str, common_radicals: set) -> bool:
    """Check if the kanji is a common basic radical"""
    return kanji in common_radicals

def main():
    # Default path
    kradfile_path = "app/src/main/assets/kradfile.json"
    
    # Parse command line arguments
    if len(sys.argv) > 1:
        kradfile_path = sys.argv[1]
    
    print("🔍 Checking for kanji that contain themselves as a radical...")
    print(f"   File: {kradfile_path}")
    print()
    
    # Load kanji data
    kanji_dict = load_kradfile(kradfile_path)
    
    if not kanji_dict:
        print("❌ Failed to load kradfile")
        return 1
    
    # Find self-radicals
    self_radicals = find_self_radicals(kanji_dict)
    
    print(f"📊 Found {len(self_radicals)} kanji that contain themselves as a radical")
    print()
    
    # Define common basic radicals
    common_radicals = {
        '一', '｜', 'ノ', '丶', '乙', '亅', '二', '亠', '人', '⺅', '儿', '入', '八', 'ハ',
        '冂', '冖', '冫', '几', '凵', '刀', '⺉', '力', '勹', '匕', '匚', '匸', '十', '卜',
        '卩', '厂', '厶', '又', '口', '囗', '土', '士', '夂', '夊', '夕', '大', '女', '子',
        '宀', '寸', '小', '⺌', '尢', '尸', '屮', '山', '巛', '川', '工', '已', '巾', '干',
        '幺', '广', '廴', '廾', '弋', '弓', '彐', '彡', '彳', '心', '⺖', '戈', '戸', '手',
        '支', '攵', '文', '斗', '斤', '方', '无', '日', '曰', '月', '木', '欠', '止', '歹',
        '殳', '毋', '比', '毛', '氏', '气', '水', '⺡', '火', '⺣', '爪', '父', '爻', '爿',
        '片', '牙', '牛', '犬', '⺨', '玄', '玉', '王', '瓜', '瓦', '甘', '生', '用', '田',
        '疋', '⽧', '癶', '白', '皮', '皿', '目', '矛', '矢', '石', '示', '⺭', '禸', '禾',
        '穴', '立', '竹', '米', '糸', '缶', '⺲', '羊', '羽', '⺹', '老', '而', '耒', '耳',
        '聿', '肉', '月', '臣', '自', '至', '臼', '舌', '舛', '舟', '艮', '色', '⺾', '虍',
        '虫', '血', '行', '衣', '⻂', '西', '見', '角', '言', '谷', '豆', '豕', '豸', '貝',
        '赤', '走', '足', '身', '車', '辛', '⻌', '⻏', '酉', '釆', '里', '金', '長', '門',
        '⻖', '隶', '隹', '雨', '青', '非', '面', '革', '韋', '韭', '音', '頁', '風', '飛',
        '食', '首', '香', '馬', '骨', '高', '髟', '鬥', '鬯', '鬲', '鬼', '魚', '鳥', '鹵',
        '鹿', '麦', '麻', '黄', '黍', '黒', '黹', '黽', '鼎', '鼓', '鼠', '鼻', '齊', '歯',
        '龍', '龜', '龠'
    }
    
    # Categorize by radical count
    categories = categorize_by_radical_count(self_radicals)
    
    # Display results organized by radical count
    for count in sorted(categories.keys()):
        entries = categories[count]
        print(f"📦 Kanji with {count} radical{'s' if count > 1 else ''} (including self): {len(entries)} kanji")
        
        # Group by whether they're basic radicals
        basic = []
        complex = []
        
        for kanji, radicals in entries:
            if is_basic_radical(kanji, common_radicals):
                basic.append((kanji, radicals))
            else:
                complex.append((kanji, radicals))
        
        # Show basic radicals first
        if basic:
            print(f"\n   Basic radicals ({len(basic)}):")
            for i, (kanji, radicals) in enumerate(basic):
                if i < 10 or len(basic) <= 15:  # Show first 10 or all if 15 or less
                    other_radicals = [r for r in radicals if r != kanji]
                    if other_radicals:
                        print(f"      {kanji} → {kanji} + {' '.join(other_radicals)}")
                    else:
                        print(f"      {kanji} → {kanji} (only)")
                elif i == 10:
                    print(f"      ... and {len(basic) - 10} more")
        
        # Show complex kanji
        if complex:
            print(f"\n   Complex kanji ({len(complex)}):")
            for i, (kanji, radicals) in enumerate(complex):
                if i < 10:  # Show first 10
                    other_radicals = [r for r in radicals if r != kanji]
                    if other_radicals:
                        print(f"      {kanji} → {kanji} + {' '.join(other_radicals)}")
                    else:
                        print(f"      {kanji} → {kanji} (only)")
                elif i == 10:
                    print(f"      ... and {len(complex) - 10} more")
        
        print()
    
    # Special analysis: kanji that ONLY have themselves as radical
    only_self = [(k, r) for k, r in self_radicals if len(r) == 1 and r[0] == k]
    if only_self:
        print(f"🔍 Special case: {len(only_self)} kanji that ONLY have themselves as radical:")
        print("   ", end="")
        for i, (kanji, _) in enumerate(only_self):
            print(kanji, end="")
            if (i + 1) % 20 == 0:
                print("\n   ", end="")
        print("\n")
    
    # Save detailed report
    output_file = "self_radicals_report.txt"
    with open(output_file, 'w', encoding='utf-8') as f:
        f.write("Kanji that contain themselves as a radical\n")
        f.write("=" * 50 + "\n\n")
        f.write(f"Total: {len(self_radicals)} kanji\n\n")
        
        for count in sorted(categories.keys()):
            entries = categories[count]
            f.write(f"Kanji with {count} radical{'s' if count > 1 else ''}: {len(entries)} kanji\n")
            f.write("-" * 40 + "\n")
            
            for kanji, radicals in sorted(entries):
                f.write(f"{kanji} → {', '.join(radicals)}\n")
            f.write("\n")
        
        # Add section for only-self radicals
        if only_self:
            f.write("Kanji that ONLY have themselves as radical:\n")
            f.write("-" * 40 + "\n")
            for kanji, _ in only_self:
                f.write(f"{kanji}\n")
    
    print(f"💾 Detailed report saved to: {output_file}")
    
    # Summary statistics
    print("\n📈 Summary:")
    print(f"   Total kanji in kradfile: {len(kanji_dict)}")
    print(f"   Kanji with self as radical: {len(self_radicals)} ({len(self_radicals)/len(kanji_dict)*100:.1f}%)")
    print(f"   Kanji with ONLY self as radical: {len(only_self)}")
    
    return 0

if __name__ == "__main__":
    sys.exit(main())