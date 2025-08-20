#!/usr/bin/env python3
"""
Analyze kanji variants in JMdict to understand the scope of variant data
"""
import json
from collections import Counter
from typing import Dict, List, Tuple

def analyze_variants(json_path: str = 'app/src/main/assets/jmdict.json'):
    """Analyze variant patterns in JMdict"""
    
    print("Loading JMdict data...")
    with open(json_path, 'r', encoding='utf-8') as f:
        data = json.load(f)
    
    # Statistics
    total_entries = len(data['words'])
    entries_with_multiple_kanji = 0
    variant_count_distribution = Counter()
    max_variants_entry = None
    max_variants_count = 0
    
    # Sample entries with many variants
    high_variant_samples = []
    
    # All variant groups (for understanding relationships)
    variant_groups = []
    
    print(f"Analyzing {total_entries:,} entries...")
    
    for entry in data['words']:
        if 'kanji' in entry and len(entry['kanji']) > 1:
            entries_with_multiple_kanji += 1
            num_variants = len(entry['kanji'])
            variant_count_distribution[num_variants] += 1
            
            # Track the entry with most variants
            if num_variants > max_variants_count:
                max_variants_count = num_variants
                max_variants_entry = entry
            
            # Collect samples with 4+ variants
            if num_variants >= 4 and len(high_variant_samples) < 20:
                kanji_forms = [k['text'] for k in entry['kanji']]
                reading = entry['kana'][0]['text'] if 'kana' in entry and entry['kana'] else 'N/A'
                meanings = []
                if 'sense' in entry and entry['sense']:
                    for sense in entry['sense']:
                        if 'gloss' in sense:
                            for gloss in sense['gloss']:
                                if 'text' in gloss:
                                    meanings.append(gloss['text'])
                                    break
                            if meanings:
                                break
                
                high_variant_samples.append({
                    'id': entry.get('id', 'unknown'),
                    'kanji': kanji_forms,
                    'reading': reading,
                    'meaning': meanings[0] if meanings else 'N/A',
                    'count': num_variants
                })
            
            # Store variant groups
            if num_variants >= 2:
                kanji_forms = [k['text'] for k in entry['kanji']]
                reading = entry['kana'][0]['text'] if 'kana' in entry and entry['kana'] else 'N/A'
                variant_groups.append({
                    'kanji_forms': kanji_forms,
                    'reading': reading,
                    'id': entry.get('id', 'unknown')
                })
    
    # Print analysis results
    print("\n" + "="*60)
    print("VARIANT ANALYSIS RESULTS")
    print("="*60)
    
    print(f"\nTotal entries in JMdict: {total_entries:,}")
    print(f"Entries with multiple kanji forms: {entries_with_multiple_kanji:,}")
    print(f"Percentage with variants: {(entries_with_multiple_kanji/total_entries)*100:.2f}%")
    
    print("\n" + "-"*40)
    print("VARIANT COUNT DISTRIBUTION")
    print("-"*40)
    print("Number of variants | Number of entries")
    print("-"*40)
    for num_variants in sorted(variant_count_distribution.keys()):
        count = variant_count_distribution[num_variants]
        bar = '█' * min(50, count // 10)
        print(f"{num_variants:17d} | {count:6d} {bar}")
    
    print("\n" + "-"*40)
    print("ENTRY WITH MOST VARIANTS")
    print("-"*40)
    if max_variants_entry:
        kanji_forms = [k['text'] for k in max_variants_entry['kanji']]
        reading = max_variants_entry['kana'][0]['text'] if 'kana' in max_variants_entry and max_variants_entry['kana'] else 'N/A'
        print(f"ID: {max_variants_entry.get('id', 'unknown')}")
        print(f"Reading: {reading}")
        print(f"Number of variants: {max_variants_count}")
        print(f"Kanji forms: {', '.join(kanji_forms)}")
    
    print("\n" + "-"*40)
    print("SAMPLE ENTRIES WITH MANY VARIANTS (4+)")
    print("-"*40)
    for sample in high_variant_samples[:15]:
        print(f"\nID: {sample['id']}")
        print(f"Reading: {sample['reading']}")
        print(f"Meaning: {sample['meaning']}")
        print(f"Variants ({sample['count']}): {', '.join(sample['kanji'])}")
    
    # Check specific common words
    print("\n" + "-"*40)
    print("CHECKING SPECIFIC COMMON WORDS")
    print("-"*40)
    
    common_words_to_check = ['みる', 'きく', 'いう', 'かく', 'よむ', 'たべる', 'のむ', 'いく', 'くる', 'する']
    
    for reading_to_check in common_words_to_check:
        variants = []
        for entry in data['words']:
            if 'kana' in entry:
                for kana in entry['kana']:
                    if kana['text'] == reading_to_check:
                        if 'kanji' in entry and len(entry['kanji']) > 1:
                            kanji_forms = [k['text'] for k in entry['kanji']]
                            variants.append({
                                'id': entry.get('id', 'unknown'),
                                'kanji': kanji_forms
                            })
                        break
        
        if variants:
            print(f"\n{reading_to_check}:")
            for v in variants:
                print(f"  ID {v['id']}: {', '.join(v['kanji'])}")
    
    # Summary statistics
    print("\n" + "="*60)
    print("SUMMARY")
    print("="*60)
    total_variants = sum(count * num for num, count in variant_count_distribution.items())
    avg_variants = total_variants / entries_with_multiple_kanji if entries_with_multiple_kanji > 0 else 0
    
    print(f"Total variant relationships: {total_variants:,}")
    print(f"Average variants per entry (when present): {avg_variants:.2f}")
    print(f"Most common variant count: {variant_count_distribution.most_common(1)[0][0]} variants " +
          f"({variant_count_distribution.most_common(1)[0][1]} entries)")
    
    # Save a sample of variant groups to file for reference
    print("\n" + "-"*40)
    print("SAVING VARIANT DATA")
    print("-"*40)
    
    output_file = 'variant_groups_sample.json'
    sample_groups = variant_groups[:1000]  # Save first 1000 groups
    
    with open(output_file, 'w', encoding='utf-8') as f:
        json.dump({
            'total_groups': len(variant_groups),
            'sample_size': len(sample_groups),
            'groups': sample_groups
        }, f, ensure_ascii=False, indent=2)
    
    print(f"Saved sample of {len(sample_groups)} variant groups to {output_file}")
    
    return {
        'total_entries': total_entries,
        'entries_with_variants': entries_with_multiple_kanji,
        'distribution': dict(variant_count_distribution),
        'max_variants': max_variants_count
    }

if __name__ == "__main__":
    results = analyze_variants()
    
    print("\n" + "="*60)
    print("FEASIBILITY ASSESSMENT")
    print("="*60)
    
    if results['entries_with_variants'] < 100:
        print("✅ HARDCODING FEASIBLE: Very few entries with variants")
    elif results['entries_with_variants'] < 1000:
        print("⚠️  HARDCODING POSSIBLE: Moderate number of entries with variants")
    else:
        print("❌ HARDCODING NOT RECOMMENDED: Too many entries with variants")
        print("   Recommend dynamic approach using database or runtime lookup")
    
    print(f"\nTotal entries to handle: {results['entries_with_variants']:,}")