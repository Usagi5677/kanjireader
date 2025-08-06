#!/usr/bin/env python3
"""
Debug script for hierarchical radical search.
Checks every step of the process to find where the issue is.
"""

import sqlite3
import os

def debug_hierarchical_search():
    """Debug the hierarchical radical search step by step"""
    
    # Database path
    db_path = "app/src/main/assets/databases/jmdict_fts5.db"
    
    if not os.path.exists(db_path):
        print(f"❌ Database not found at {db_path}")
        return False
    
    print(f"🔍 Debugging hierarchical radical search with database: {db_path}")
    
    try:
        conn = sqlite3.connect(db_path)
        cursor = conn.cursor()
        
        # Step 1: Check if decomposition table exists and has data
        print("\n📋 Step 1: Checking decomposition table")
        cursor.execute("SELECT COUNT(*) FROM radical_decomposition_mapping")
        decomp_count = cursor.fetchone()[0]
        print(f"   Decomposition mappings: {decomp_count}")
        
        if decomp_count == 0:
            print("❌ No decomposition data found!")
            print("   The makemeahanzi data wasn't loaded during database build.")
            conn.close()
            return False
        
        # Show some examples
        cursor.execute("SELECT radical, components FROM radical_decomposition_mapping LIMIT 10")
        examples = cursor.fetchall()
        print("   Sample decompositions:")
        for radical, components in examples:
            print(f"     {radical} → {components}")
        
        # Step 2: Check specific decomposition for 丷
        print("\n📋 Step 2: Checking 丷 decomposition")
        cursor.execute("SELECT radical, components FROM radical_decomposition_mapping WHERE radical = '丷'")
        result = cursor.fetchone()
        
        if result:
            radical, components = result
            component_list = [c.strip() for c in components.split(",")]
            print(f"   丷 decomposes to: {component_list}")
            has_dot = '丶' in component_list
            print(f"   Contains 丶: {'✅' if has_dot else '❌'}")
        else:
            print("❌ 丷 decomposition not found!")
            print("   This means 丷 wasn't included in the makemeahanzi data or filtered out.")
        
        # Step 3: Check if 火 contains 人 and 丷
        print("\n📋 Step 3: Checking 火 kanji radical composition")
        cursor.execute("SELECT components FROM kanji_radical_mapping WHERE kanji = '火'")
        result = cursor.fetchone()
        
        if result:
            components = result[0]
            component_list = [c.strip() for c in components.split(",")]
            print(f"   火 has components: {component_list}")
            has_person = '人' in component_list
            has_dots = '丷' in component_list
            print(f"   Contains 人: {'✅' if has_person else '❌'}")
            print(f"   Contains 丷: {'✅' if has_dots else '❌'}")
            
            if not (has_person or has_dots):
                print("❌ 火 doesn't contain the expected radicals!")
                print("   This means the radical composition data is incorrect.")
        else:
            print("❌ 火 not found in kanji_radical_mapping!")
        
        # Step 4: Check if 人 radical contains 火
        print("\n📋 Step 4: Checking if 人 radical contains 火")
        cursor.execute("SELECT kanji_list FROM radical_kanji_mapping WHERE radical = '人'")
        result = cursor.fetchone()
        
        if result:
            kanji_list = result[0]
            kanji_chars = [k.strip() for k in kanji_list.split(",")]
            has_fire = '火' in kanji_chars
            print(f"   人 radical has {len(kanji_chars)} kanji")
            print(f"   Contains 火: {'✅' if has_fire else '❌'}")
            if has_fire:
                fire_index = kanji_chars.index('火')
                print(f"   火 is at position {fire_index + 1}")
        else:
            print("❌ 人 radical not found!")
        
        # Step 5: Check if 丷 radical exists and contains 火
        print("\n📋 Step 5: Checking if 丷 radical contains 火")
        cursor.execute("SELECT kanji_list FROM radical_kanji_mapping WHERE radical = '丷'")
        result = cursor.fetchone()
        
        if result:
            kanji_list = result[0]
            kanji_chars = [k.strip() for k in kanji_list.split(",")]
            has_fire = '火' in kanji_chars
            print(f"   丷 radical has {len(kanji_chars)} kanji")
            print(f"   Contains 火: {'✅' if has_fire else '❌'}")
            if len(kanji_chars) > 0:
                print(f"   Sample kanji: {kanji_chars[:10]}")
        else:
            print("❌ 丷 radical not found in radical_kanji_mapping!")
            print("   This means 丷 is not a recognized radical in your system.")
        
        # Step 6: Simulate the expansion logic
        print("\n📋 Step 6: Simulating expansion logic")
        selected_radicals = ['人', '丶']
        
        expanded_radicals = set(selected_radicals)
        print(f"   Starting with: {selected_radicals}")
        
        # For each selected radical, find composites containing it
        for component in selected_radicals:
            cursor.execute("""
                SELECT radical FROM radical_decomposition_mapping 
                WHERE components LIKE ?
            """, (f'%{component}%',))
            
            potential_composites = cursor.fetchall()
            print(f"   Checking composites containing '{component}':")
            
            for (composite,) in potential_composites:
                # Verify the component is actually in the decomposition
                cursor.execute("""
                    SELECT components FROM radical_decomposition_mapping 
                    WHERE radical = ?
                """, (composite,))
                comp_result = cursor.fetchone()
                
                if comp_result:
                    components_list = [c.strip() for c in comp_result[0].split(",")]
                    if component in components_list:
                        expanded_radicals.add(composite)
                        print(f"     ✅ {composite} (contains {component})")
                    else:
                        print(f"     ❌ {composite} (false match)")
        
        print(f"   Final expanded set: {sorted(expanded_radicals)}")
        
        # Step 7: Test if expanded radicals can find 火
        print("\n📋 Step 7: Testing if expanded radicals can find 火")
        
        fire_found_in = []
        for radical in expanded_radicals:
            cursor.execute("SELECT kanji_list FROM radical_kanji_mapping WHERE radical = ?", (radical,))
            result = cursor.fetchone()
            
            if result and result[0]:
                kanji_list = [k.strip() for k in result[0].split(",")]
                if '火' in kanji_list:
                    fire_found_in.append(radical)
                    print(f"   ✅ {radical} contains 火")
                else:
                    print(f"   ❌ {radical} does NOT contain 火")
            else:
                print(f"   ⚠️  {radical} not found in radical_kanji_mapping")
        
        if fire_found_in:
            print(f"\n✅ 火 can be found through: {fire_found_in}")
        else:
            print(f"\n❌ 火 cannot be found through any expanded radical")
        
        # Step 8: Final diagnosis
        print("\n📋 Step 8: Final diagnosis")
        if fire_found_in:
            print("✅ The hierarchical search should work!")
            print("   The issue might be in the Android app implementation.")
        else:
            print("❌ The hierarchical search cannot work with current data.")
            print("   Issues found:")
            
            # Check what's missing
            if decomp_count == 0:
                print("   - No decomposition data")
            else:
                cursor.execute("SELECT COUNT(*) FROM radical_decomposition_mapping WHERE components LIKE '%丶%'")
                dot_composites = cursor.fetchone()[0]
                print(f"   - Composites containing 丶: {dot_composites}")
                
                cursor.execute("SELECT COUNT(*) FROM radical_kanji_mapping WHERE radical = '丷'")
                dots_radical_exists = cursor.fetchone()[0]
                print(f"   - 丷 exists as radical: {dots_radical_exists > 0}")
        
        conn.close()
        return len(fire_found_in) > 0
        
    except Exception as e:
        print(f"❌ Error debugging database: {e}")
        import traceback
        traceback.print_exc()
        return False

def main():
    print("🔧 Hierarchical Radical Search Debug")
    print("=" * 60)
    
    success = debug_hierarchical_search()
    
    print("\n" + "=" * 60)
    if success:
        print("✅ Database structure looks correct!")
        print("The issue might be in the Android app implementation.")
    else:
        print("❌ Found issues in the database structure.")
        print("The database needs to be rebuilt with correct data.")

if __name__ == "__main__":
    main()