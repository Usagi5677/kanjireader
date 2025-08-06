#!/usr/bin/env python3
"""
Test script to verify the Chinese→Japanese radical substitution results.
Tests that the enhanced decomposition system can find target kanji through complex radical chains.
"""

import sqlite3
import os

def test_substitution_results():
    """Test the substitution results in the enhanced database"""
    
    db_path = "app/src/main/assets/databases/jmdict_fts5.db"
    
    if not os.path.exists(db_path):
        print(f"❌ Database not found at {db_path}")
        print("Please run build_database.py with the enhanced substitution logic first.")
        return False
    
    print(f"🔍 Testing substitution results with database: {db_path}")
    
    try:
        conn = sqlite3.connect(db_path)
        cursor = conn.cursor()
        
        # Test 1: Check decomposition table has our target radicals
        print("\n📋 Test 1: Checking enhanced decompositions")
        test_radicals = ['魚', '肉', '鳥', '馬']
        
        for radical in test_radicals:
            cursor.execute("SELECT components FROM radical_decomposition_mapping WHERE radical = ?", (radical,))
            result = cursor.fetchone()
            
            if result:
                components = result[0].split(',')
                print(f"   ✅ {radical} → {components}")
            else:
                print(f"   ❌ {radical}: no decomposition found")
        
        # Test 2: Test hierarchical expansion for ｜ + 人 → should find 肉
        print("\n📋 Test 2: Testing ｜ + 人 → 肉 search")
        selected_radicals = ['｜', '人']
        
        # Simulate expansion logic
        expanded_radicals = set(selected_radicals)
        
        for component in selected_radicals:
            cursor.execute("""
                SELECT radical FROM radical_decomposition_mapping 
                WHERE components LIKE ?
            """, (f'%{component}%',))
            
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
                    if component in components_list:
                        expanded_radicals.add(composite)
                        print(f"   {component} → found in composite {composite} ({components_list})")
        
        print(f"   Original selection: {selected_radicals}")
        print(f"   Expanded selection: {sorted(expanded_radicals)}")
        
        # Check if any expanded radicals contain 肉
        meat_found_through = []
        for radical in expanded_radicals:
            cursor.execute("SELECT kanji_list FROM radical_kanji_mapping WHERE radical = ?", (radical,))
            result = cursor.fetchone()
            
            if result and result[0]:
                kanji_list = [k.strip() for k in result[0].split(",")]
                if '肉' in kanji_list:
                    meat_found_through.append(radical)
                    print(f"   ✅ {radical} contains 肉")
        
        if meat_found_through:
            print(f"\n🎯 SUCCESS: 肉 can be found through: {meat_found_through}")
        else:
            print(f"\n❌ FAILURE: 肉 cannot be found through expanded radicals")
        
        # Test 3: Test fire-related searches (should work through ⺣ substitution)
        print("\n📋 Test 3: Testing fire-related searches")
        
        # Check if ⺣ radical contains our target kanji
        cursor.execute("SELECT kanji_list FROM radical_kanji_mapping WHERE radical = ?", ('⺣',))
        result = cursor.fetchone()
        
        if result and result[0]:
            fire_kanji = [k.strip() for k in result[0].split(",")]
            fire_targets = ['魚', '鳥', '馬']
            
            for target in fire_targets:
                contains_target = target in fire_kanji
                print(f"   ⺣ contains {target}: {'✅' if contains_target else '❌'}")
        
        conn.close()
        return len(meat_found_through) > 0
        
    except Exception as e:
        print(f"❌ Error testing database: {e}")
        return False

def main():
    print("🧪 Chinese→Japanese Radical Substitution Test")
    print("=" * 60)
    
    success = test_substitution_results()
    
    print("\n" + "=" * 60)
    if success:
        print("✅ Substitution system is working correctly!")
        print("Users can now find kanji through enhanced radical decomposition.")
    else:
        print("❌ Substitution system needs fixes.")
        print("Please rebuild the database with enhanced substitution logic.")

if __name__ == "__main__":
    main()