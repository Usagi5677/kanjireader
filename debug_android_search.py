#!/usr/bin/env python3
"""
Debug script to simulate exactly what the Android app does when selecting ｜ + 人.
This will help identify why 肉 is not being found.
"""

import sqlite3
import os

def debug_android_search():
    """Simulate the Android app's radical search logic exactly"""
    
    db_path = "app/src/main/assets/databases/jmdict_fts5.db"
    
    if not os.path.exists(db_path):
        print(f"❌ Database not found at {db_path}")
        return False
    
    print("🔍 Debugging Android Radical Search Logic")
    print("=" * 60)
    print("Simulating user selection: ｜ + 人")
    
    try:
        conn = sqlite3.connect(db_path)
        cursor = conn.cursor()
        
        selected_radicals = ['｜', '人']
        print(f"Selected radicals: {selected_radicals}")
        
        # Step 1: Expand radical selection (simulate expandRadicalSelection)
        print("\n📋 Step 1: Expanding radical selection")
        expanded_radicals = set(selected_radicals)
        
        for component in selected_radicals:
            print(f"\nLooking for composites containing '{component}':")
            
            # Find composite radicals containing this component
            cursor.execute("""
                SELECT radical
                FROM radical_decomposition_mapping 
                WHERE components LIKE ?
            """, (f'%{component}%',))
            
            potential_composites = cursor.fetchall()
            print(f"  Found {len(potential_composites)} potential composites")
            
            for (radical,) in potential_composites:
                # Verify the component is actually in the decomposition
                cursor.execute("""
                    SELECT components
                    FROM radical_decomposition_mapping 
                    WHERE radical = ?
                """, (radical,))
                
                comp_result = cursor.fetchone()
                if comp_result:
                    components_list = [c.strip() for c in comp_result[0].split(",")]
                    if component in components_list:
                        expanded_radicals.add(radical)
                        print(f"    ✅ {radical} (contains {component})")
                    else:
                        print(f"    ❌ {radical} (false match)")
        
        print(f"\n🎯 Expanded radicals: {sorted(expanded_radicals)}")
        
        # Step 2: Get kanji lists for each expanded radical
        print("\n📋 Step 2: Getting kanji lists for expanded radicals")
        radical_to_kanji_sets = {}
        
        for radical in expanded_radicals:
            cursor.execute("""
                SELECT kanji_list
                FROM radical_kanji_mapping 
                WHERE radical = ?
            """, (radical,))
            
            result = cursor.fetchone()
            if result and result[0]:
                kanji_list = [k.strip() for k in result[0].split(",")]
                radical_to_kanji_sets[radical] = set(kanji_list)
                contains_meat = '肉' in kanji_list
                print(f"  {radical}: {len(kanji_list)} kanji, contains 肉: {'✅' if contains_meat else '❌'}")
            else:
                print(f"  {radical}: no kanji found")
        
        # Step 3: Find intersection logic (exactly like Android)
        print("\n📋 Step 3: Finding kanji intersection")
        
        # For each original radical, find all kanji that satisfy it
        original_radical_satisfied_kanji = []
        
        for original_radical in selected_radicals:
            print(f"\nProcessing original radical: {original_radical}")
            
            # Get expansion for this specific radical
            expansion_for_radical = set([original_radical])
            
            # Find composites for this specific radical
            cursor.execute("""
                SELECT radical
                FROM radical_decomposition_mapping 
                WHERE components LIKE ?
            """, (f'%{original_radical}%',))
            
            for (composite,) in cursor.fetchall():
                cursor.execute("""
                    SELECT components
                    FROM radical_decomposition_mapping 
                    WHERE radical = ?
                """, (composite,))
                
                comp_result = cursor.fetchone()
                if comp_result:
                    components_list = [c.strip() for c in comp_result[0].split(",")]
                    if original_radical in components_list:
                        expansion_for_radical.add(composite)
            
            print(f"  Expansion for {original_radical}: {sorted(expansion_for_radical)}")
            
            # Union all kanji sets for radicals that satisfy this original radical
            satisfied_kanji = set()
            for expanded_radical in expansion_for_radical:
                if expanded_radical in radical_to_kanji_sets:
                    satisfied_kanji.update(radical_to_kanji_sets[expanded_radical])
            
            print(f"  Satisfied kanji count: {len(satisfied_kanji)}")
            print(f"  Contains 肉: {'✅' if '肉' in satisfied_kanji else '❌'}")
            
            original_radical_satisfied_kanji.append(satisfied_kanji)
        
        # Find intersection of all satisfied kanji sets
        print("\n📋 Step 4: Computing final intersection")
        if original_radical_satisfied_kanji:
            final_result = set.intersection(*original_radical_satisfied_kanji)
            print(f"Final intersection: {len(final_result)} kanji")
            
            if '肉' in final_result:
                print("🎯 SUCCESS: 肉 found in final results!")
                # Show first 20 results
                result_list = sorted(list(final_result))
                print(f"First 20 results: {result_list[:20]}")
            else:
                print("❌ FAILURE: 肉 NOT found in final results")
                result_list = sorted(list(final_result))
                print(f"Final results: {result_list[:20]}")
                
                # Debug: show what each original radical contributed
                print("\nDEBUG: Individual contributions:")
                for i, (original, satisfied) in enumerate(zip(selected_radicals, original_radical_satisfied_kanji)):
                    contains_meat = '肉' in satisfied
                    print(f"  {original}: {len(satisfied)} kanji, contains 肉: {'✅' if contains_meat else '❌'}")
            
            return '肉' in final_result
        else:
            print("❌ No satisfied kanji sets found")
            return False
        
        conn.close()
        
    except Exception as e:
        print(f"❌ Error: {e}")
        import traceback
        traceback.print_exc()
        return False

def main():
    success = debug_android_search()
    
    print("\n" + "=" * 60)
    if success:
        print("✅ The Android logic should work correctly!")
    else:
        print("❌ Found issue in the Android logic or database.")

if __name__ == "__main__":
    main()