#!/usr/bin/env python3
"""
Final test: Verify that ｜ + 人 can find 肉 after the enhanced substitution system.
This simulates the exact Android app logic with the new database.
"""

import sqlite3
import os

def test_final_meat_search():
    """Test the complete ｜ + 人 → 肉 search path"""
    
    db_path = "app/src/main/assets/databases/jmdict_fts5.db"
    
    if not os.path.exists(db_path):
        print(f"❌ Database not found. Please rebuild with: python3 build_database.py")
        return False
    
    print("🔍 Final Test: ｜ + 人 → 肉 Search")
    print("=" * 50)
    
    try:
        conn = sqlite3.connect(db_path)
        cursor = conn.cursor()
        
        selected_radicals = ['｜', '人']
        print(f"Selected radicals: {selected_radicals}")
        
        # Step 1: Check decompositions exist
        print("\nStep 1: Checking key decompositions")
        
        # Check 冂 → ｜
        cursor.execute("SELECT components FROM radical_decomposition_mapping WHERE radical = ?", ('冂',))
        result = cursor.fetchone()
        
        if result and '｜' in result[0]:
            print("  ✅ 冂 → ｜ decomposition exists")
        else:
            print("  ❌ 冂 → ｜ decomposition missing")
            return False
        
        # Check 肉 → 冂,人,人
        cursor.execute("SELECT components FROM radical_decomposition_mapping WHERE radical = ?", ('肉',))
        result = cursor.fetchone()
        
        if result and '冂' in result[0] and '人' in result[0]:
            print("  ✅ 肉 → 冂,人,人 decomposition exists")
        else:
            print("  ❌ 肉 → 冂,人,人 decomposition missing")
            return False
        
        # Step 2: Simulate Android expansion logic
        print("\nStep 2: Simulating Android expansion logic")
        expanded_radicals = set(selected_radicals)
        
        for component in selected_radicals:
            print(f"\n  Finding composites containing '{component}':")
            
            cursor.execute("""
                SELECT radical FROM radical_decomposition_mapping 
                WHERE components LIKE ?
            """, (f'%{component}%',))
            
            for (radical,) in cursor.fetchall():
                # Verify component is actually in the decomposition
                cursor.execute("""
                    SELECT components FROM radical_decomposition_mapping 
                    WHERE radical = ?
                """, (radical,))
                
                comp_result = cursor.fetchone()
                if comp_result:
                    components_list = [c.strip() for c in comp_result[0].split(",")]
                    if component in components_list:
                        expanded_radicals.add(radical)
                        print(f"    ✅ {radical} (contains {component})")
        
        print(f"\n  Expanded radicals: {sorted(expanded_radicals)}")
        
        # Step 3: Check if expanded radicals contain 肉
        print("\nStep 3: Checking if expanded radicals contain 肉")
        
        radicals_with_meat = []
        for radical in expanded_radicals:
            cursor.execute("SELECT kanji_list FROM radical_kanji_mapping WHERE radical = ?", (radical,))
            result = cursor.fetchone()
            
            if result and result[0]:
                kanji_list = [k.strip() for k in result[0].split(",")]
                if '肉' in kanji_list:
                    radicals_with_meat.append(radical)
                    print(f"  ✅ {radical} contains 肉")
                else:
                    print(f"  ❌ {radical} does not contain 肉")
        
        # Step 4: Compute intersection (Android logic)
        print("\nStep 4: Computing intersection (Android logic)")
        
        # For each original radical, get all kanji that satisfy it
        original_radical_satisfied_kanji = []
        
        for original_radical in selected_radicals:
            print(f"\n  Processing original radical: {original_radical}")
            
            # Get expansion for this specific radical
            expansion_for_radical = set([original_radical])
            
            cursor.execute("""
                SELECT radical FROM radical_decomposition_mapping 
                WHERE components LIKE ?
            """, (f'%{original_radical}%',))
            
            for (composite,) in cursor.fetchall():
                cursor.execute("""
                    SELECT components FROM radical_decomposition_mapping 
                    WHERE radical = ?
                """, (composite,))
                
                comp_result = cursor.fetchone()
                if comp_result:
                    components_list = [c.strip() for c in comp_result[0].split(",")]
                    if original_radical in components_list:
                        expansion_for_radical.add(composite)
            
            print(f"    Expansion: {sorted(expansion_for_radical)}")
            
            # Get all kanji for this expansion
            satisfied_kanji = set()
            for expanded_radical in expansion_for_radical:
                cursor.execute("SELECT kanji_list FROM radical_kanji_mapping WHERE radical = ?", (expanded_radical,))
                result = cursor.fetchone()
                
                if result and result[0]:
                    kanji_list = [k.strip() for k in result[0].split(",")]
                    satisfied_kanji.update(kanji_list)
            
            print(f"    Satisfied kanji: {len(satisfied_kanji)}")
            print(f"    Contains 肉: {'✅' if '肉' in satisfied_kanji else '❌'}")
            
            original_radical_satisfied_kanji.append(satisfied_kanji)
        
        # Final intersection
        if len(original_radical_satisfied_kanji) == 2:
            final_result = original_radical_satisfied_kanji[0].intersection(original_radical_satisfied_kanji[1])
            print(f"\nFinal intersection: {len(final_result)} kanji")
            
            if '肉' in final_result:
                print("🎯 SUCCESS: 肉 found through ｜ + 人 search!")
                result_list = sorted(list(final_result))
                print(f"Results (first 20): {result_list[:20]}")
                return True
            else:
                print("❌ FAILURE: 肉 not found in final intersection")
                result_list = sorted(list(final_result))
                print(f"Results (first 20): {result_list[:20]}")
                return False
        else:
            print("❌ Error in intersection logic")
            return False
        
        conn.close()
        
    except Exception as e:
        print(f"❌ Error: {e}")
        import traceback
        traceback.print_exc()
        return False

def main():
    success = test_final_meat_search()
    
    print("\n" + "=" * 50)
    if success:
        print("✅ COMPLETE SUCCESS!")
        print("The enhanced hierarchical radical search system works correctly.")
        print("Users can now find 肉 by selecting ｜ + 人 in the Android app!")
    else:
        print("❌ System needs database rebuild.")
        print("Please run: python3 build_database.py")

if __name__ == "__main__":
    main()