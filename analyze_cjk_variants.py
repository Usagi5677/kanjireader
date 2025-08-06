#!/usr/bin/env python3
"""
Analyze CJK character variants in makemeahanzi data to identify 
which Chinese characters need Japanese equivalents.
"""

from build_database import DatabaseBuilder
import json

def analyze_cjk_variants():
    """Find CJK variants that need Japanese substitutions"""
    
    print('Analyzing CJK character variants in makemeahanzi data...')
    print('=' * 60)
    
    # Get makemeahanzi data and our Japanese radical system
    builder = DatabaseBuilder()
    decomp_data = builder.load_makemeahanzi_decomposition_data()
    
    # Get our existing Japanese radicals
    with open('app/src/main/assets/radkfile.json', 'r', encoding='utf-8') as f:
        radkfile = json.load(f)
    existing_radicals = set(radkfile['radicals'].keys())
    
    # Find all missing components
    missing_components = {}
    fullwidth_question = '？'  # U+FF1F
    
    for radical, components in decomp_data.items():
        if radical in existing_radicals:
            for comp in components:
                # Skip fullwidth question mark and check if not in our system
                if (comp not in existing_radicals and 
                    len(comp) == 1 and 
                    comp != fullwidth_question):
                    
                    if comp not in missing_components:
                        missing_components[comp] = []
                    missing_components[comp].append(radical)
    
    # Sort by frequency
    sorted_missing = sorted(missing_components.items(), key=lambda x: len(x[1]), reverse=True)
    
    print(f'Found {len(sorted_missing)} unique missing components')
    print('\nMost frequent missing components (potential CJK variants):')
    print('-' * 60)
    
    # Analyze top missing components
    for comp, needing_radicals in sorted_missing[:20]:
        unicode_point = ord(comp)
        frequency = len(needing_radicals)
        
        # Identify Unicode block
        if 0x2E80 <= unicode_point <= 0x2EFF:
            unicode_block = 'CJK Radicals Supplement'
        elif 0x2F00 <= unicode_point <= 0x2FDF:
            unicode_block = 'Kangxi Radicals'
        elif 0x3400 <= unicode_point <= 0x4DBF:
            unicode_block = 'CJK Extension A'
        elif 0x4E00 <= unicode_point <= 0x9FFF:
            unicode_block = 'CJK Unified Ideographs'
        elif 0xFF00 <= unicode_point <= 0xFFEF:
            unicode_block = 'Halfwidth/Fullwidth'
        else:
            unicode_block = 'Other'
        
        print(f'{comp} (U+{unicode_point:04X}) [{unicode_block}]: {frequency} radicals')
        if frequency <= 3:
            print(f'   Used in: {needing_radicals}')
        else:
            print(f'   Examples: {needing_radicals[:3]}...')
        print()
    
    # Look for potential Japanese equivalents
    print('\nLooking for potential Japanese equivalents...')
    print('-' * 60)
    
    # Check some common patterns
    potential_substitutions = {}
    
    for comp, needing_radicals in sorted_missing[:10]:  # Check top 10
        unicode_point = ord(comp)
        
        # Look for similar characters in our Japanese system
        similar_candidates = []
        
        for japanese_radical in existing_radicals:
            if len(japanese_radical) == 1:
                jp_unicode = ord(japanese_radical)
                
                # Check for close Unicode values (variants often have similar codes)
                if abs(jp_unicode - unicode_point) <= 100:
                    similar_candidates.append((japanese_radical, jp_unicode))
        
        if similar_candidates:
            print(f'{comp} (U+{unicode_point:04X}) potential matches:')
            for candidate, candidate_unicode in similar_candidates[:3]:
                print(f'   → {candidate} (U+{candidate_unicode:04X})')
            potential_substitutions[comp] = similar_candidates[0][0]  # Take first match
        else:
            print(f'{comp} (U+{unicode_point:04X}): no obvious Japanese equivalent found')
    
    return potential_substitutions

def main():
    potential_subs = analyze_cjk_variants()
    
    print('\n' + '=' * 60)
    print('RECOMMENDED SUBSTITUTIONS TO ADD:')
    print('=' * 60)
    
    # Print current substitutions
    print('# Current substitutions:')
    print("'灬': '⺣',  # Chinese fire dots → Japanese fire radical")
    print("'丨': '｜',  # CJK radical stick → Japanese fullwidth vertical line")
    print()
    
    # Print potential new ones
    print('# Potential new substitutions:')
    for chinese, japanese in potential_subs.items():
        chinese_unicode = ord(chinese)
        japanese_unicode = ord(japanese)
        print(f"'{chinese}': '{japanese}',  # U+{chinese_unicode:04X} → U+{japanese_unicode:04X}")

if __name__ == "__main__":
    main()