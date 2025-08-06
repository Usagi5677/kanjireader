import json

# Check radkfile for advance radical
with open("app/src/main/assets/radkfile.json", "r", encoding="utf-8") as f:
    radkfile = json.load(f)
    
radicals = radkfile.get("radicals", {})

# Check for advance radical variants
advance_variants = ["⻌", "辶", "⻍", "辵"]
for variant in advance_variants:
    if variant in radicals:
        strokes = radicals[variant]
        print(f"{variant} (U+{ord(variant):04X}): {strokes} strokes")
    else:
        print(f"{variant} (U+{ord(variant):04X}): NOT FOUND")

# Check what stroke count ⻌ actually has
if "⻌" in radicals:
    actual_strokes = radicals["⻌"]
    print(f"\nActual stroke count for ⻌: {actual_strokes}")
    if actual_strokes \!= 3:
        print(f"ERROR: ⻌ should have 3 strokes, not {actual_strokes}\!")
