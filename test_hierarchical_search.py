#!/usr/bin/env python3
"""
Test script for hierarchical radical search functionality.
Tests that selecting 人 + 丶 can find 火 through the 丷 decomposition.
"""

import sqlite3
import os
import sys

def test_hierarchical_search():
    """Test the hierarchical radical search functionality"""
    
    # Database path
    db_path = "app/src/main/assets/databases/jmdict_fts5.db"
    
    if not os.path.exists(db_path):
        print(f"❌ Database not found at {db_path}")
        print("Please run build_database.py first to create the database with hierarchical search support.")
        return False
    
    print(f"🔍 Testing hierarchical radical search with database: {db_path}")
    
    try:
        conn = sqlite3.connect(db_path)
        cursor = conn.cursor()
        
        # Test 1: Check if radical_decomposition_mapping table exists
        print("\n📋 Test 1: Checking if radical_decomposition_mapping table exists")
        cursor.execute("""
            SELECT name FROM sqlite_master 
            WHERE type='table' AND name='radical_decomposition_mapping'
        """)
        table_exists = cursor.fetchone()
        
        if table_exists:
            print("✅ radical_decomposition_mapping table exists")
        else:
            print("❌ radical_decomposition_mapping table NOT found")
            print("   Please rebuild the database with the updated build_database.py")
            conn.close()
            return False
        
        # Test 2: Check decomposition data
        print("\n📋 Test 2: Checking decomposition data")
        cursor.execute("SELECT COUNT(*) FROM radical_decomposition_mapping")
        decomp_count = cursor.fetchone()[0]
        print(f"   Found {decomp_count} radical decompositions")
        
        # Test 3: Check specific decomposition (丷 → 丶,丶)
        print("\n📋 Test 3: Checking 丷 decomposition")
        cursor.execute("""
            SELECT radical, components FROM radical_decomposition_mapping 
            WHERE radical = '丷'
        """)
        result = cursor.fetchone()
        
        if result:
            radical, components = result
            component_list = [c.strip() for c in components.split(",")]
            print(f"   丷 decomposes to: {component_list}")
            
            if '丶' in component_list:
                print("✅ 丷 correctly contains 丶 component")
            else:
                print("❌ 丷 does NOT contain 丶 component")
                conn.close()
                return False
        else:
            print("⚠️  丷 decomposition not found - this is expected if makemeahanzi data wasn't available")
        
        # Test 4: Test the expansion logic simulation
        print("\n📋 Test 4: Testing expansion logic simulation")
        selected_radicals = ['人', '丶']
        
        # Simulate the expansion for each radical
        expanded_radicals = set(selected_radicals)
        
        for radical in selected_radicals:
            # Find composite radicals that contain this component
            cursor.execute("""
                SELECT radical FROM radical_decomposition_mapping 
                WHERE components LIKE ?
            """, (f'%{radical}%',))
            
            composites = cursor.fetchall()
            for (composite,) in composites:
                # Verify the component is actually in the decomposition
                cursor.execute("""
                    SELECT components FROM radical_decomposition_mapping 
                    WHERE radical = ?
                """, (composite,))
                comp_result = cursor.fetchone()
                if comp_result:
                    components_list = [c.strip() for c in comp_result[0].split(",")]
                    if radical in components_list:
                        expanded_radicals.add(composite)
                        print(f"   {radical} → found in composite {composite} ({components_list})")
        
        print(f"   Original selection: {selected_radicals}")
        print(f"   Expanded selection: {sorted(expanded_radicals)}")
        
        # Test 5: Check if expanded radicals can find 火
        print("\n📋 Test 5: Testing if expanded radicals can find 火")
        
        # Get kanji lists for all expanded radicals
        radical_kanji_sets = {}
        for radical in expanded_radicals:
            cursor.execute("""
                SELECT kanji_list FROM radical_kanji_mapping 
                WHERE radical = ?
            """, (radical,))
            result = cursor.fetchone()
            
            if result and result[0]:
                kanji_list = [k.strip() for k in result[0].split(",")]
                radical_kanji_sets[radical] = set(kanji_list)
                has_fire = '火' in kanji_list
                print(f"   {radical}: {len(kanji_list)} kanji, contains 火: {'✅' if has_fire else '❌'}")
        
        # Find intersection (kanji that appear in at least one radical satisfying each original selection)
        if len(radical_kanji_sets) >= len(selected_radicals):
            # Group radicals by original selection
            original_satisfaction = {}
            for original in selected_radicals:
                satisfying_radicals = []
                for expanded in expanded_radicals:
                    if expanded == original:
                        satisfying_radicals.append(expanded)
                    else:
                        # Check if this expanded radical contains the original as component
                        cursor.execute("""
                            SELECT components FROM radical_decomposition_mapping 
                            WHERE radical = ?
                        """, (expanded,))
                        comp_result = cursor.fetchone()
                        if comp_result:
                            components_list = [c.strip() for c in comp_result[0].split(",")]
                            if original in components_list:
                                satisfying_radicals.append(expanded)
                
                # Union all kanji from satisfying radicals
                satisfied_kanji = set()
                for radical in satisfying_radicals:
                    if radical in radical_kanji_sets:
                        satisfied_kanji.update(radical_kanji_sets[radical])
                
                original_satisfaction[original] = satisfied_kanji
                print(f"   {original} satisfied by {len(satisfied_kanji)} kanji through {satisfying_radicals}")
            
            # Find intersection
            if len(original_satisfaction) == len(selected_radicals):
                final_result = set.intersection(*original_satisfaction.values())
                print(f"\n🎯 Final results: {len(final_result)} kanji satisfy all conditions")
                
                if '火' in final_result:
                    print("✅ SUCCESS: 火 found through hierarchical search!")
                    print(f"   First 10 results: {sorted(list(final_result))[:10]}")
                    conn.close()
                    return True
                else:
                    print("❌ FAILURE: 火 NOT found in final results")
                    print(f"   Results: {sorted(list(final_result))[:20]}")
            else:
                print("❌ FAILURE: Could not satisfy all original radical selections")
        else:
            print("❌ FAILURE: Not enough expanded radicals found")
        
        conn.close()
        return False
        
    except Exception as e:
        print(f"❌ Error testing database: {e}")
        return False

def main():
    print("🧪 Hierarchical Radical Search Test")
    print("=" * 50)
    print("Testing if selecting 人 + 丶 can find 火 through 丷 decomposition...")
    
    success = test_hierarchical_search()
    
    print("\n" + "=" * 50)
    if success:
        print("✅ Hierarchical search test PASSED!")
        print("The system can now find kanji through component decomposition.")
    else:
        print("❌ Hierarchical search test FAILED!")
        print("Please check the database and implementation.")
        sys.exit(1)

if __name__ == "__main__":
    main()