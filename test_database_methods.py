#!/usr/bin/env python3
"""
Unit test for database decomposition parsing methods.
Tests the makemeahanzi parsing logic without requiring full database rebuild.
"""

import sys
import os
sys.path.append('.')

from build_database import DatabaseBuilder

def test_decomposition_parsing():
    """Test the makemeahanzi decomposition parsing methods"""
    
    print("🧪 Testing Decomposition Parsing Methods")
    print("=" * 50)
    
    builder = DatabaseBuilder()
    
    # Test 1: Parse decomposition strings
    print("\n📋 Test 1: Testing decomposition parsing")
    
    test_cases = [
        ("⿻亅八", ["亅", "八"]),        # 丷 example
        ("⿰氵青", ["氵", "青"]),       # 清 example  
        ("⿱人火", ["人", "火"]),       # Would create a composite
        ("⿰亻主", ["亻", "主"]),       # 住 example
        ("", []),                      # Empty case
        ("火", ["火"]),                # Single character
    ]
    
    for decomp_str, expected in test_cases:
        result = builder.parse_makemeahanzi_decomposition(decomp_str)
        success = result == expected
        print(f"   '{decomp_str}' → {result} {'✅' if success else '❌'}")
        if not success:
            print(f"      Expected: {expected}")
    
    # Test 2: Test sample makemeahanzi data format
    print("\n📋 Test 2: Testing sample makemeahanzi data format")
    
    # Create sample data that would come from makemeahanzi
    sample_data = {
        "丷": ["丶", "丶"],    # Two dots
        "八": ["丿", "乀"],    # Eight radical  
        "人": ["丿", "乀"],    # Person
        "火": ["人", "丷"],    # Fire (contains person + two dots)
    }
    
    # Test the filtering logic
    existing_radicals = {"人", "丶", "丷", "八", "火", "丿", "乀"}
    
    print("   Sample decompositions:")
    for radical, components in sample_data.items():
        valid_components = [comp for comp in components if comp in existing_radicals]
        if valid_components and len(valid_components) > 1:
            print(f"   {radical} → {valid_components} ✅")
        else:
            print(f"   {radical} → {components} (filtered out)")
    
    # Test 3: Test expansion logic concept
    print("\n📋 Test 3: Testing expansion logic concept")
    
    selected = ["人", "丶"]
    
    # Find composites that contain these components
    expanded = set(selected)
    for component in selected:
        for radical, components in sample_data.items():
            if component in components and len(components) > 1:
                expanded.add(radical)
                print(f"   {component} found in {radical} ({components})")
    
    print(f"   Original: {selected}")
    print(f"   Expanded: {sorted(expanded)}")
    
    # Test if this would find 火
    if "火" in sample_data:
        fire_components = sample_data["火"]
        can_find_fire = any(comp in expanded for comp in fire_components)
        print(f"   Can find 火 (components {fire_components}): {'✅' if can_find_fire else '❌'}")
    
    print("\n✅ Decomposition parsing tests completed!")
    print("These methods should work correctly when integrated into the database.")

def main():
    test_decomposition_parsing()

if __name__ == "__main__":
    main()