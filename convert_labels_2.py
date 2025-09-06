#!/usr/bin/env python3
"""
Convert labels_2.txt (continuous kanji string) to Python list format
for use with the new model_float16.tflite in KanjiReader Android app.
"""

def convert_labels():
    # Read the labels_2.txt file
    input_file = 'app/src/main/assets/labels_2.txt'
    output_file = 'app/src/main/assets/labels_2_python_list.txt'
    
    print(f"Reading labels from: {input_file}")
    
    try:
        with open(input_file, 'r', encoding='utf-8') as f:
            content = f.read().strip()
    except FileNotFoundError:
        print(f"Error: {input_file} not found!")
        return False
    
    # Convert each character to a list element
    kanji_list = []
    for char in content:
        # Skip whitespace characters (newlines, spaces, etc.)
        if char.strip():
            kanji_list.append(f"'{char}'")
    
    print(f"Found {len(kanji_list)} kanji characters")
    print(f"First 20 kanji: {kanji_list[:20]}")
    print(f"Last 20 kanji: {kanji_list[-20:]}")
    
    # Create Python list format
    python_list_content = '[' + ', '.join(kanji_list) + ']'
    
    # Write to output file
    print(f"Writing Python list format to: {output_file}")
    with open(output_file, 'w', encoding='utf-8') as f:
        f.write(python_list_content)
    
    print(f"Successfully converted {len(kanji_list)} kanji to Python list format!")
    print(f"Output file size: {len(python_list_content)} characters")
    
    return True

if __name__ == '__main__':
    success = convert_labels()
    if success:
        print("Conversion completed successfully!")
    else:
        print("Conversion failed!")