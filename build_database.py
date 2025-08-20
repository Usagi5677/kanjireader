#!/usr/bin/env python3
"""
Unified Database Builder for Kanji Reader
Creates a complete FTS5-enabled database ready for Android app deployment

This script combines all the successful database building steps:
1. Creates main dictionary_entries table with FTS5 search
2. Creates kanji_entries table for KanjiDic data
3. Populates with JMdict and KanjiDic data
4. Tokenizes Japanese text properly for search
5. Creates all necessary indexes
6. Saves to app/src/main/assets/databases/
"""

import sqlite3
import json
import os
import sys
import shutil
from typing import List, Dict, Tuple, Optional
from datetime import datetime

# Try to import MeCab if available
try:
    import MeCab
    MECAB_AVAILABLE = True
except ImportError:
    MECAB_AVAILABLE = False
    print("⚠️  MeCab not available - will use basic tokenization")

def get_radical_name(radical_number: int) -> str:
    """
    Map Kangxi radical numbers to their Japanese names.
    This is a subset of the most common radicals.
    """
    radical_names = {
        1: "一", 2: "丨", 3: "丶", 4: "丿", 5: "乙", 6: "亅", 7: "二", 8: "亠", 9: "人", 10: "儿",
        11: "入", 12: "八", 13: "冂", 14: "冖", 15: "冫", 16: "几", 17: "凵", 18: "刀", 19: "力", 20: "勹",
        21: "匕", 22: "匚", 23: "匸", 24: "十", 25: "卜", 26: "卩", 27: "厂", 28: "厶", 29: "又", 30: "口",
        31: "囗", 32: "土", 33: "士", 34: "夂", 35: "夊", 36: "夕", 37: "大", 38: "女", 39: "子", 40: "宀",
        41: "寸", 42: "小", 43: "尢", 44: "尸", 45: "屮", 46: "山", 47: "川", 48: "工", 49: "己", 50: "巾",
        51: "干", 52: "幺", 53: "广", 54: "廴", 55: "廾", 56: "弋", 57: "弓", 58: "彐", 59: "彡", 60: "彳",
        61: "心", 62: "戈", 63: "戸", 64: "手", 65: "支", 66: "攴", 67: "文", 68: "斗", 69: "斤", 70: "方",
        71: "无", 72: "日", 73: "曰", 74: "月", 75: "木", 76: "欠", 77: "止", 78: "歹", 79: "殳", 80: "毋",
        81: "比", 82: "毛", 83: "氏", 84: "气", 85: "水", 86: "火", 87: "爪", 88: "父", 89: "爻", 90: "爿",
        91: "片", 92: "牙", 93: "牛", 94: "犬", 95: "玄", 96: "玉", 97: "瓜", 98: "瓦", 99: "甘", 100: "生",
        101: "用", 102: "田", 103: "疋", 104: "疒", 105: "癶", 106: "白", 107: "皮", 108: "皿", 109: "目", 110: "矛",
        111: "矢", 112: "石", 113: "示", 114: "禸", 115: "禾", 116: "穴", 117: "立", 118: "竹", 119: "米", 120: "糸",
        121: "缶", 122: "网", 123: "羊", 124: "羽", 125: "老", 126: "而", 127: "耒", 128: "耳", 129: "聿", 130: "肉",
        131: "臣", 132: "自", 133: "至", 134: "臼", 135: "舌", 136: "舛", 137: "舟", 138: "艮", 139: "色", 140: "艸",
        141: "虍", 142: "虫", 143: "血", 144: "行", 145: "衣", 146: "襾", 147: "見", 148: "角", 149: "言", 150: "谷",
        151: "豆", 152: "豕", 153: "豸", 154: "貝", 155: "赤", 156: "走", 157: "足", 158: "身", 159: "車", 160: "辛",
        161: "辰", 162: "辵", 163: "邑", 164: "酉", 165: "釆", 166: "里", 167: "金", 168: "長", 169: "門", 170: "阜",
        171: "隶", 172: "隹", 173: "雨", 174: "青", 175: "非", 176: "面", 177: "革", 178: "韋", 179: "韭", 180: "音",
        181: "頁", 182: "風", 183: "飛", 184: "食", 185: "首", 186: "香", 187: "馬", 188: "骨", 189: "高", 190: "髟",
        191: "鬥", 192: "鬯", 193: "鬲", 194: "鬼", 195: "魚", 196: "鳥", 197: "鹵", 198: "鹿", 199: "麦", 200: "麻",
        201: "黄", 202: "黍", 203: "黒", 204: "黹", 205: "黽", 206: "鼎", 207: "鼓", 208: "鼠", 209: "鼻", 210: "齊",
        211: "齒", 212: "竜", 213: "亀", 214: "龠"
    }
    return radical_names.get(radical_number, "")

class DatabaseBuilder:
    def __init__(self, output_path: str = "app/src/main/assets/databases/jmdict_fts5.db"):
        self.output_path = output_path
        self.mecab_tokenizer = None
        self.frequency_data = {}
        
        # Ensure output directory exists
        os.makedirs(os.path.dirname(output_path), exist_ok=True)
        
        # Load frequency data
        self.load_frequency_data()
        
        # Initialize MeCab if available
        if MECAB_AVAILABLE:
            try:
                # IMPORTANT: For Windows, you might need to specify the path to mecabrc
                # For WSL/Linux, MeCab should usually be found if installed via package manager
                if sys.platform == "win32":
                    # Adjust this path if your MeCab installation is elsewhere
                    mecabrc_path = os.path.join(os.getenv('MECAB_PATH', r"C:\Program Files\MeCab"), "etc", "mecabrc")
                    if os.path.exists(mecabrc_path):
                        os.environ["MECABRC"] = mecabrc_path
                    else:
                        print(f"⚠️  MECABRC not found at {mecabrc_path}. MeCab might not initialize correctly.")
                self.mecab_tokenizer = MeCab.Tagger()
                print("✅ MeCab tokenizer initialized")
            except Exception as e:
                print(f"⚠️  MeCab initialization failed: {e}")
                print("   Falling back to basic tokenization")

    def load_frequency_data(self) -> None:
        """Load frequency data from CSV file"""
        import csv
        frequency_file = "app/src/main/assets/frequency_data.csv"

        if not os.path.exists(frequency_file):
            print(f"⚠️  Frequency file not found: {frequency_file}")
            return

        try:
            with open(frequency_file, 'r', encoding='utf-8-sig') as f:
                # Skip BOM if present
                reader = csv.reader(f)
                next(reader)  # Skip header

                count = 0
                duplicates_found = 0
                skipped_corrupted = 0

                for row in reader:
                    if len(row) >= 4:
                        word = row[0].strip()
                        pos = row[1].strip() if len(row) > 1 else ""
                        reading = row[2].strip() if len(row) > 2 else ""
                        frequency_str = row[3].strip()

                        # Skip genuinely corrupted data (empty words or missing frequencies)
                        if not word or not frequency_str or frequency_str.strip() == '':
                            skipped_corrupted += 1
                            continue

                        # Parse frequency: remove commas and spaces, convert to int
                        try:
                            frequency = int(frequency_str.replace(',', '').replace(' ', ''))

                            # Create a unique key for true duplicates (same word, same POS, same reading)
                            unique_key = f"{word}|{pos}|{reading}"

                            # Keep the highest frequency for true duplicates
                            if unique_key in self.frequency_data:
                                existing_freq = self.frequency_data[unique_key]
                                if frequency > existing_freq:
                                    print(f"   🔄 Duplicate: '{word}' ({pos}|{reading}) - replacing freq {existing_freq:,} with {frequency:,}")
                                    self.frequency_data[unique_key] = frequency
                                    # Also update the simple word key for lookup
                                    if word not in self.frequency_data or frequency > self.frequency_data[word]:
                                        self.frequency_data[word] = frequency
                                else:
                                    print(f"   ❌ Duplicate: '{word}' ({pos}|{reading}) - keeping existing freq {existing_freq:,}, discarding {frequency:,}")
                                duplicates_found += 1
                            else:
                                self.frequency_data[unique_key] = frequency
                                # Also store by simple word key, keeping highest frequency
                                if word not in self.frequency_data or frequency > self.frequency_data[word]:
                                    self.frequency_data[word] = frequency
                                count += 1
                        except ValueError:
                            skipped_corrupted += 1
                            continue

                print(f"✅ Loaded {count} frequency entries from CSV")
                if duplicates_found > 0:
                    print(f"⚠️  Found {duplicates_found} true duplicate entries - kept highest frequencies")
                if skipped_corrupted > 0:
                    print(f"⚠️  Skipped {skipped_corrupted} corrupted/empty entries")

        except Exception as e:
            print(f"⚠️  Error loading frequency data: {e}")

    def create_database_schema(self, conn: sqlite3.Connection) -> None:
        """Create all necessary tables and indexes"""
        cursor = conn.cursor()

        print("📋 Creating database schema...")

        # Main dictionary entries table
        # CHANGES: Removed 'jmnedict_type', added 'is_jmnedict_entry'
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS dictionary_entries (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                kanji TEXT,
                reading TEXT NOT NULL,
                meanings TEXT NOT NULL,
                parts_of_speech TEXT,
                is_common INTEGER DEFAULT 0,
                frequency INTEGER DEFAULT 0,
                tokenized_kanji TEXT,
                tokenized_reading TEXT,
                is_jmnedict_entry INTEGER DEFAULT 0,  -- Only this flag for JMnedict source
                form_is_common INTEGER DEFAULT 0,  -- Common flag specific to this kanji-kana combination
                UNIQUE(kanji, reading, is_jmnedict_entry)
            )
        """)

        # Create indexes for performance
        cursor.execute("CREATE INDEX IF NOT EXISTS idx_reading ON dictionary_entries(reading)")
        cursor.execute("CREATE INDEX IF NOT EXISTS idx_kanji ON dictionary_entries(kanji)")
        cursor.execute("CREATE INDEX IF NOT EXISTS idx_common ON dictionary_entries(is_common)")
        cursor.execute("CREATE INDEX IF NOT EXISTS idx_frequency ON dictionary_entries(frequency)")
        # NEW INDEX for new column
        cursor.execute("CREATE INDEX IF NOT EXISTS idx_is_jmnedict_entry ON dictionary_entries(is_jmnedict_entry)")

        # Create FTS5 virtual tables
        print("📊 Creating FTS5 virtual tables...")

        # Japanese text search table (entries_fts5)
        # CHANGES: Removed jmnedict_type
        cursor.execute("""
            CREATE VIRTUAL TABLE IF NOT EXISTS entries_fts5 USING fts5(
                kanji,
                reading,
                meanings,
                tokenized_kanji,
                tokenized_reading,
                parts_of_speech,
                is_common UNINDEXED,
                frequency UNINDEXED,
                is_jmnedict_entry UNINDEXED, -- Only this flag for FTS
                form_is_common UNINDEXED,
                content='dictionary_entries',
                content_rowid='id'
            )
        """)

        # English search table
        cursor.execute("""
            CREATE VIRTUAL TABLE IF NOT EXISTS english_fts USING fts5(
                entry_id UNINDEXED,
                meanings,
                parts_of_speech,
                tokenize='unicode61',
                prefix='1 2 3'
            )
        """)

        # Tag definitions and word tags
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS tag_definitions (
                tag TEXT PRIMARY KEY,
                description TEXT
            )
        """)

        cursor.execute("""
            CREATE TABLE IF NOT EXISTS word_tags (
                entry_id INTEGER,
                tag TEXT,
                FOREIGN KEY(entry_id) REFERENCES dictionary_entries(id) ON DELETE CASCADE,
                FOREIGN KEY(tag) REFERENCES tag_definitions(tag) ON DELETE CASCADE
            )
        """)

        # Create indexes for tag tables
        cursor.execute("CREATE INDEX IF NOT EXISTS idx_word_tags_entry ON word_tags(entry_id)")
        cursor.execute("CREATE INDEX IF NOT EXISTS idx_word_tags_tag ON word_tags(tag)")

        # Pitch accent table
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS pitch_accents (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                kanji_form TEXT NOT NULL,
                reading TEXT NOT NULL,
                accent_pattern TEXT NOT NULL,
                UNIQUE(kanji_form, reading)
            )
        """)

        # Create indexes for pitch accent table
        cursor.execute("CREATE INDEX IF NOT EXISTS idx_pitch_kanji ON pitch_accents(kanji_form)")
        cursor.execute("CREATE INDEX IF NOT EXISTS idx_pitch_reading ON pitch_accents(reading)")

        print("✅ Dictionary database schema created")

        # Create KanjiDic tables in the same database
        self.create_kanjidic_schema(conn)
        conn.commit()

    def populate_tag_definitions(self, conn: sqlite3.Connection) -> None:
        """Populate tag definitions table with JMdict POS tags"""
        cursor = conn.cursor()

        print("🏷️  Populating tag definitions...")

        # Complete tag definitions from jmdict.json
        tag_definitions = {
            # Basic parts of speech
            'n': 'noun (common) (futsuumeishi)',
            'n-adv': 'adverbial noun (fukushitekimeishi)', 
            'n-t': 'noun (temporal) (jisoumeishi)',
            'n-pr': 'proper noun',
            'n-pref': 'noun, used as a prefix',
            'n-suf': 'noun, used as a suffix',
            'adj-i': 'adjective (keiyoushi)',
            'adj-na': 'adjectival nouns or quasi-adjectives (keiyodoshi)',
            'adj-no': 'nouns which may take the genitive case particle \'no\'',
            'adj-pn': 'pre-noun adjectival (rentaishi)',
            'adj-t': '\'taru\' adjective',
            'adj-f': 'noun or verb acting prenominally',
            'adj-ix': 'adjective (keiyoushi) - yoi/ii class',
            'adj-ku': '\'ku\' adjective (archaic)',
            'adj-shiku': '\'shiku\' adjective (archaic)',
            'adj-nari': 'archaic/formal form of na-adjective',
            'adj-kari': '\'kari\' adjective (archaic)',
            'adv': 'adverb (fukushi)',
            'adv-to': 'adverb taking the \'to\' particle',
            'aux': 'auxiliary',
            'aux-v': 'auxiliary verb',
            'aux-adj': 'auxiliary adjective',
            'conj': 'conjunction',
            'cop': 'copula',
            'ctr': 'counter',
            'exp': 'expressions (phrases, clauses, etc.)',
            'id': 'idiomatic expression',
            'int': 'interjection (kandoushi)',
            'num': 'numeric',
            'pn': 'pronoun',
            'prt': 'particle',
            'pref': 'prefix',
            'suf': 'suffix',

            # Verbs - Regular
            'v1': 'Ichidan verb',
            'v1-s': 'Ichidan verb - kureru special class',
            'v5b': 'Godan verb with \'bu\' ending',
            'v5g': 'Godan verb with \'gu\' ending',
            'v5k': 'Godan verb with \'ku\' ending',
            'v5k-s': 'Godan verb - Iku/Yuku special class',
            'v5m': 'Godan verb with \'mu\' ending',
            'v5n': 'Godan verb with \'nu\' ending',
            'v5r': 'Godan verb with \'ru\' ending',
            'v5r-i': 'Godan verb with \'ru\' ending (irregular verb)',
            'v5s': 'Godan verb with \'su\' ending',
            'v5t': 'Godan verb with \'tsu\' ending',
            'v5u': 'Godan verb with \'u\' ending',
            'v5u-s': 'Godan verb with \'u\' ending (special class)',
            'v5uru': 'Godan verb - Uru old class verb (old form of Eru)',
            'v5aru': 'Godan verb - -aru special class',
            'vk': 'Kuru verb - special class',
            'vn': 'irregular nu verb',
            'vr': 'irregular ru verb, plain form ends with -ri',
            'vs': 'noun or participle which takes the aux. verb suru',
            'vs-c': 'su verb - precursor to the modern suru',
            'vs-s': 'suru verb - special class',
            'vs-i': 'suru verb - included',
            'vz': 'Ichidan verb - zuru verb (alternative form of -jiru verbs)',
            'vt': 'transitive verb',
            'vi': 'intransitive verb',
            'v-unspec': 'verb unspecified',

            # Archaic verbs
            'v2a-s': 'Nidan verb with \'u\' ending (archaic)',
            'v2b-k': 'Nidan verb (upper class) with \'bu\' ending (archaic)',
            'v2b-s': 'Nidan verb (lower class) with \'bu\' ending (archaic)',
            'v2d-k': 'Nidan verb (upper class) with \'dzu\' ending (archaic)',
            'v2d-s': 'Nidan verb (lower class) with \'dzu\' ending (archaic)',
            'v2g-k': 'Nidan verb (upper class) with \'gu\' ending (archaic)',
            'v2g-s': 'Nidan verb (lower class) with \'gu\' ending (archaic)',
            'v2h-k': 'Nidan verb (upper class) with \'hu/fu\' ending (archaic)',
            'v2h-s': 'Nidan verb (lower class) with \'hu/fu\' ending (archaic)',
            'v2k-k': 'Nidan verb (upper class) with \'ku\' ending (archaic)',
            'v2k-s': 'Nidan verb (lower class) with \'ku\' ending (archaic)',
            'v2m-k': 'Nidan verb (upper class) with \'mu\' ending (archaic)',
            'v2m-s': 'Nidan verb (lower class) with \'mu\' ending (archaic)',
            'v2n-s': 'Nidan verb (lower class) with \'nu\' ending (archaic)',
            'v2r-k': 'Nidan verb (upper class) with \'ru\' ending (archaic)',
            'v2r-s': 'Nidan verb (lower class) with \'ru\' ending (archaic)',
            'v2s-s': 'Nidan verb (lower class) with \'su\' ending (archaic)',
            'v2t-k': 'Nidan verb (upper class) with \'tsu\' ending (archaic)',
            'v2t-s': 'Nidan verb (lower class) with \'tsu\' ending (archaic)',
            'v2w-s': 'Nidan verb (lower class) with \'u\' ending and \'we\' conjugation (archaic)',
            'v2y-k': 'Nidan verb (upper class) with \'yu\' ending (archaic)',
            'v2y-s': 'Nidan verb (lower class) with \'yu\' ending (archaic)',
            'v2z-s': 'Nidan verb (lower class) with \'zu\' ending (archaic)',
            'v4b': 'Yodan verb with \'bu\' ending (archaic)',
            'v4g': 'Yodan verb with \'gu\' ending (archaic)',
            'v4h': 'Yodan verb with \'hu/fu\' ending (archaic)',
            'v4k': 'Yodan verb with \'ku\' ending (archaic)',
            'v4m': 'Yodan verb with \'mu\' ending (archaic)',
            'v4n': 'Yodan verb with \'nu\' ending (archaic)',
            'v4r': 'Yodan verb with \'ru\' ending (archaic)',
            'v4s': 'Yodan verb with \'su\' ending (archaic)',
            'v4t': 'Yodan verb with \'tsu\' ending (archaic)',

            # Usage and style tags
            'arch': 'archaic',
            'dated': 'dated term',
            'obs': 'obsolete term',
            'rare': 'rare term',
            'form': 'formal or literary term',
            'col': 'colloquial',
            'fam': 'familiar language',
            'sl': 'slang',
            'm-sl': 'manga slang',
            'net-sl': 'Internet slang',
            'hon': 'honorific or respectful (sonkeigo) language',
            'hum': 'humble (kenjougo) language',
            'pol': 'polite (teineigo) language',
            'male': 'male term or language',
            'fem': 'female term or language',
            'chn': 'children\'s language',
            'joc': 'jocular, humorous term',
            'vulg': 'vulgar expression or word',
            'derog': 'derogatory',
            'X': 'rude or X-rated term (not displayed in educational software)',
            'sens': 'sensitive',
            'euph': 'euphemistic',

            # Kanji and kana usage
            'sK': 'search-only kanji form',
            'rK': 'rarely used kanji form',
            'iK': 'word containing irregular kanji usage',
            'oK': 'word containing out-dated kanji or kanji usage',
            'sk': 'search-only kana form',
            'rk': 'rarely used kana form',
            'ik': 'word containing irregular kana usage',
            'ok': 'out-dated or obsolete kana usage',
            'uk': 'word usually written using kana alone',
            'io': 'irregular okurigana usage',
            'ateji': 'ateji (phonetic) reading',
            'gikun': 'gikun (meaning as reading) or jukujikun (special kanji reading)',

            # Specialized fields
            'abbr': 'abbreviation',
            'anat': 'anatomy',
            'agric': 'agriculture',
            'archeol': 'archeology',
            'archit': 'architecture',
            'art': 'art, aesthetics',
            'astron': 'astronomy',
            'audvid': 'audiovisual',
            'aviat': 'aviation',
            'baseb': 'baseball',
            'biochem': 'biochemistry',
            'biol': 'biology',
            'bot': 'botany',
            'boxing': 'boxing',
            'Buddh': 'Buddhism',
            'bus': 'business',
            'cards': 'card games',
            'chem': 'chemistry',
            'Christn': 'Christianity',
            'civeng': 'civil engineering',
            'cloth': 'clothing',
            'comp': 'computing',
            'cryst': 'crystallography',
            'dent': 'dentistry',
            'ecol': 'ecology',
            'econ': 'economics',
            'elec': 'electricity, elec. eng.',
            'electr': 'electronics',
            'embryo': 'embryology',
            'engr': 'engineering',
            'ent': 'entomology',
            'figskt': 'figure skating',
            'film': 'film',
            'finc': 'finance',
            'fish': 'fishing',
            'food': 'food, cooking',
            'gardn': 'gardening, horticulture',
            'genet': 'genetics',
            'geogr': 'geography',
            'geol': 'geology',
            'geom': 'geometry',
            'go': 'go (game)',
            'golf': 'golf',
            'gramm': 'grammar',
            'hanaf': 'hanafuda',
            'hist': 'historical term',
            'horse': 'horse racing',
            'internet': 'Internet',
            'kabuki': 'kabuki',
            'law': 'law',
            'ling': 'linguistics',
            'logic': 'logic',
            'MA': 'martial arts',
            'mahj': 'mahjong',
            'manga': 'manga',
            'math': 'mathematics',
            'mech': 'mechanical engineering',
            'med': 'medicine',
            'met': 'meteorology',
            'mil': 'military',
            'min': 'mineralogy',
            'mining': 'mining',
            'motor': 'motorsport',
            'music': 'music',
            'myth': 'mythology',
            'jpmyth': 'Japanese mythology',
            'grmyth': 'Greek mythology',
            'rommyth': 'Roman mythology',
            'chmyth': 'Chinese mythology',
            'noh': 'noh',
            'ornith': 'ornithology',
            'paleo': 'paleontology',
            'pathol': 'pathology',
            'pharm': 'pharmacology',
            'phil': 'philosophy',
            'photo': 'photography',
            'physics': 'physics',
            'physiol': 'physiology',
            'poet': 'poetical term',
            'politics': 'politics',
            'print': 'printing',
            'prowres': 'professional wrestling',
            'psych': 'psychology',
            'psyanal': 'psychoanalysis',
            'psy': 'psychiatry',
            'rail': 'railway',
            'relig': 'religion',
            'shogi': 'shogi',
            'Shinto': 'Shinto',
            'ski': 'skiing',
            'sports': 'sports',
            'stat': 'statistics',
            'stockm': 'stock market',
            'sumo': 'sumo',
            'surg': 'surgery',
            'telec': 'telecommunications',
            'tv': 'television',
            'vet': 'veterinary terms',
            'vidg': 'video games',
            'zool': 'zoology',

            # Mimetics and expressions
            'on-mim': 'onomatopoeic or mimetic word',
            'yoji': 'yojijukugo',
            'proverb': 'proverb',
            'quote': 'quotation',

            # Proper noun types
            'person': 'full name of a particular person',
            'given': 'given name or forename, gender not specified',
            'surname': 'family or surname',
            'place': 'place name',
            'station': 'railway station',
            'company': 'company name',
            'organization': 'organization name',
            'product': 'product name',
            'work': 'work of art, literature, music, etc. name',
            'char': 'character',
            'creat': 'creature',
            'dei': 'deity',
            'ev': 'event',
            'fict': 'fiction',
            'group': 'group',
            'leg': 'legend',
            'obj': 'object',
            'oth': 'other',
            'serv': 'service',
            'ship': 'ship name',
            'unc': 'unclassified',
            'unclass': 'unclassified name',
            'doc': 'document',
            'tradem': 'trademark',

            # Dialectal
            'hob': 'Hokkaido-ben',
            'thb': 'Touhoku-ben',
            'ktb': 'Kantou-ben',
            'kyb': 'Kyoto-ben',
            'ksb': 'Kansai-ben',
            'osb': 'Osaka-ben',
            'tsb': 'Tosa-ben',
            'tsug': 'Tsugaru-ben',
            'kyu': 'Kyuushuu-ben',
            'rkb': 'Ryuukyuu-ben',
            'nab': 'Nagano-ben',
            'bra': 'Brazilian',
            'masc': 'male name',
        }

        for tag, description in tag_definitions.items():
            cursor.execute("""
                INSERT OR REPLACE INTO tag_definitions (tag, description)
                VALUES (?, ?)
            """, (tag, description))

        conn.commit()
        print(f"✅ Added {len(tag_definitions)} tag definitions")

    def populate_pitch_accents(self, conn: sqlite3.Connection) -> None:
        """Populate pitch accent table from accents.json"""
        cursor = conn.cursor()
        
        accents_file = os.path.join(os.path.dirname(__file__), 'app', 'src', 'main', 'assets', 'accents.json')
        
        if not os.path.exists(accents_file):
            print("⚠️  accents.json not found, skipping pitch accent data")
            return
            
        print("🎵 Populating pitch accent data...")
        
        try:
            with open(accents_file, 'r', encoding='utf-8') as f:
                accent_data = json.load(f)
            
            if 'accents' not in accent_data:
                print("❌ Invalid accent data format")
                return
                
            count = 0
            skipped = 0
            
            for kanji_form, readings_dict in accent_data['accents'].items():
                for reading, accent_numbers in readings_dict.items():
                    try:
                        # Create accent pattern string (e.g., "1,2" for multiple patterns)
                        accent_pattern = ','.join(map(str, accent_numbers))
                        
                        cursor.execute("""
                            INSERT OR REPLACE INTO pitch_accents 
                            (kanji_form, reading, accent_pattern)
                            VALUES (?, ?, ?)
                        """, (kanji_form, reading, accent_pattern))
                        
                        count += 1
                        
                        if count % 10000 == 0:
                            print(f"   📊 Processed {count:,} accent entries...")
                            
                    except Exception as e:
                        print(f"   ⚠️  Skipping accent entry {kanji_form}:{reading} - {e}")
                        skipped += 1
                        continue
            
            conn.commit()
            print(f"✅ Added {count:,} pitch accent entries")
            if skipped > 0:
                print(f"   ⚠️  Skipped {skipped} invalid entries")
                
        except Exception as e:
            print(f"❌ Error loading pitch accent data: {e}")

    def normalize_romaji(self, text: str) -> str:
        """
        Optional: Normalize Romaji characters like 'ō' (U+014D) to simpler ASCII 'o'.
        Add this function to fields that might contain such characters if desired.
        """
        if not text:
            return ""
        # Example: Replace macrons with their base ASCII equivalents
        text = text.replace('ā', 'a').replace('ī', 'i').replace('ū', 'u').replace('ē', 'e').replace('ō', 'o')
        text = text.replace('Ā', 'A').replace('Ī', 'I').replace('Ū', 'U').replace('Ē', 'E').replace('Ō', 'O')
        # Add any other specific character replacements here if needed (e.g., apostrophes if they are mis-parsed)
        # text = text.replace("'", "") # Example: To remove straight apostrophes if undesired
        # text = text.replace("’", "") # Example: To remove curly apostrophes if undesired
        return text

    def tokenize_japanese(self, text: str) -> str:
        """
        Tokenize Japanese text for FTS5 search (removes spacing after tokenization)

        This function:
        1. Uses MeCab to properly tokenize compound words (e.g., "みるべき" -> "みる" + "べき")
        2. Removes spaces between tokens so FTS5 can find substring matches
        """
        if not text:
            return ""

        if self.mecab_tokenizer:
            try:
                node = self.mecab_tokenizer.parseToNode(text)
                tokens = []

                while node:
                    if node.surface:
                        tokens.append(node.surface)
                    node = node.next

                # IMPORTANT: Remove spaces after tokenization
                # Example: "みるべき" -> ["みる", "べき"] -> "みるべき" (no spaces)
                return "".join(tokens)
            except Exception as e:
                print(f"⚠️  MeCab tokenization failed for '{text}': {e}. Falling back to simple text.")

        # Fallback: basic character-level tokenization (no spaces needed as it's not tokenized)
        return text

    def calculate_frequency(self, word: str, is_common: bool, kana_reading: str = None, parts_of_speech: list = None) -> int:
        """
        Calculate frequency score for word.
        Assigns 0 frequency to known "pure proper noun" types to avoid false high rankings.
        """
        if parts_of_speech:
            # These are common JMnedict tags that indicate a proper name, not a dictionary word.
            pure_proper_noun_name_types = {
                'fem', 'masc', 'given', 'surname', 'person', 'char', 'place', 'station',
                'company', 'organization', 'group', 'product', 'serv', 'work', 'ev', 'obj',
                'creat', 'dei', 'myth', 'leg', 'ship', 'doc', 'relig', 'fict', 'oth', 'unclass'
            }

            # Check if any of the entry's POS/name tags match our pure proper noun types.
            if any(tag in pure_proper_noun_name_types for tag in parts_of_speech):
                return 0 # Assign 0 frequency to avoid contaminating general word frequencies.

        # Use exact matches from loaded frequency data.
        if word in self.frequency_data:
            return self.frequency_data[word]

        # No exact match found - return 0.
        return 0

    def load_jmdict_data(self, file_path: str) -> List[Dict]:
        """Load JMdict data from JSON file"""
        print(f"📖 Loading JMdict data from {file_path}...")

        if os.path.exists(file_path):
            try:
                with open(file_path, 'r', encoding='utf-8') as f:
                    data = json.load(f)
                
                # Handle new JMdict format with metadata and words array
                if isinstance(data, dict) and 'words' in data:
                    words = data['words']
                    print(f"✅ Loaded {len(words)} entries from JMdict file (new format)")
                    return words
                elif isinstance(data, list):
                    # Old format - direct array of entries
                    print(f"✅ Loaded {len(data)} entries from JMdict file (old format)")
                    return data
                else:
                    print(f"❌ Unexpected JMdict format")
                    return []
            except Exception as e:
                print(f"❌ Failed to load JMdict data from {file_path}: {e}")
                return []
        else:
            print(f"❌ JMdict file not found at {file_path}")
            print("Please ensure you have jmdict.json file in the assets directory")
            return []

    def load_tags_data(self, tags_file_path: str) -> Dict[str, List[str]]:
        """Load tags data from tags.json file (JMdict POS tags)"""
        print(f"🏷️  Loading tags data from {tags_file_path}...")

        if not os.path.exists(tags_file_path):
            print(f"⚠️  No JMdict tags file found at {tags_file_path}")
            return {}

        try:
            with open(tags_file_path, 'r', encoding='utf-8') as f:
                tag_data = json.load(f)

            # Process the tags data (JMdict structure)
            all_tags = {}
            for word, tag_info in tag_data.items():
                if isinstance(tag_info, dict) and 'senses' in tag_info:
                    pos_tags = []
                    for sense in tag_info['senses']:
                        if 'pos' in sense:
                            pos_tags.extend(sense['pos'])

                    if pos_tags:
                        all_tags[word] = pos_tags

            print(f"✅ Loaded tags for {len(all_tags)} words from JMdict tags file")
            return all_tags
        except Exception as e:
            print(f"❌ Failed to load tags file: {e}")
            return {}

    # MODIFIED: populate_dictionary_entries now takes 'is_jmnedict_source'
    def populate_dictionary_entries(self, conn: sqlite3.Connection, entries_data: List[Dict], tags_data: Dict[str, List[str]], is_jmnedict_source: bool = False) -> None:
        """Populate the main dictionary_entries table and word_tags table"""
        cursor = conn.cursor()

        print(f"📝 Populating dictionary entries (Source: {'JMnedict' if is_jmnedict_source else 'JMdict'})...")
        entries_added = 0
        tags_added = 0
        error_count = 0  # Track SQL errors
        max_errors = 100  # Maximum allowed errors before stopping

        # NOTE: jmnedict_type_priority_order and related logic for jmnedict_specific_type are REMOVED.

        batch_size = 1000  # Smaller batch size to prevent corruption
        
        for i, entry in enumerate(entries_data):
            # Debug: Track みる entries specifically
            kana_forms = [k['text'] for k in entry.get('kana', [])]
            if 'みる' in kana_forms:
                kanji_forms = [k['text'] for k in entry.get('kanji', [])]
                print(f"   🔍 Processing みる entry #{i}: kanji={kanji_forms}, kana={kana_forms}")
            
            # Use smaller batches to prevent database corruption
            if i > 0 and i % batch_size == 0:
                try:
                    conn.commit()
                    # Perform integrity check periodically
                    if i % 10000 == 0:
                        print(f"   Progress: {i}/{len(entries_data)} entries...")
                        check_cursor = conn.execute("PRAGMA integrity_check")
                        result = check_cursor.fetchone()[0]
                        if result != "ok":
                            print(f"   ⚠️  Database integrity issue detected: {result}")
                            raise sqlite3.DatabaseError("Database integrity check failed")
                except sqlite3.DatabaseError as e:
                    print(f"   ❌ Database error during commit: {e}")
                    if "database disk image is malformed" in str(e):
                        print("   🚨 CRITICAL: Database corruption detected. Stopping build.")
                        raise
                    # Try to recover by rolling back
                    try:
                        conn.rollback()
                        print("   🔄 Rolled back transaction, continuing...")
                    except:
                        raise

            # Handle new format with 'sense' array (JMdict) or 'translation' array (JMnedict)
            meanings = []
            parts_of_speech_list = []
            
            if 'sense' in entry:
                # JMdict format
                for sense in entry.get('sense', []):
                    # Extract meanings from gloss array
                    for gloss in sense.get('gloss', []):
                        if isinstance(gloss, dict):
                            # Standard JMdict format with lang field, or our custom format without lang
                            if gloss.get('lang') == 'eng' or not gloss.get('lang'):
                                text = gloss.get('text', '')
                                if text:
                                    meanings.append(text)
                    
                    # Extract parts of speech from each sense
                    # Support both 'partOfSpeech' (standard JMdict) and 'pos' (our custom entries)
                    pos_tags = sense.get('partOfSpeech', []) or sense.get('pos', [])
                    parts_of_speech_list.extend(pos_tags)
            elif 'translation' in entry:
                # JMnedict format
                for trans in entry.get('translation', []):
                    # Extract meanings from translation array
                    for t in trans.get('translation', []):
                        if isinstance(t, dict) and t.get('lang') == 'eng':
                            text = t.get('text', '')
                            if text:
                                meanings.append(text)
                    
                    # Extract name types as parts of speech
                    name_types = trans.get('type', [])
                    parts_of_speech_list.extend(name_types)
                    
                    # Debug logging for JMnedict tag extraction
                    if is_jmnedict_source:
                        kanji_forms_debug = [k['text'] for k in entry.get('kanji', [])]
                        if '上' in kanji_forms_debug:
                            print(f"   🔍 JMnedict tag extraction for 上: name_types={name_types}")
            else:
                # Old format fallback
                meanings = entry.get('meanings', [])
            
            if not meanings:
                continue

            meanings_json = json.dumps(meanings)

            kanji_forms = [k['text'] for k in entry.get('kanji', [])]
            kana_forms = [k['text'] for k in entry.get('kana', [])]

            # Collect additional POS tags from tags file (if not already populated from sense)
            if not parts_of_speech_list:
                for word in kanji_forms + kana_forms:
                    if word in tags_data:
                        parts_of_speech_list.extend(tags_data[word])
            
            # Remove duplicates, preserve order
            parts_of_speech_list = list(dict.fromkeys(parts_of_speech_list))
            parts_of_speech_json = json.dumps(parts_of_speech_list)

            is_common = any(form.get('common', False) for form in entry.get('kanji', []) + entry.get('kana', []))

            # --- UPDATED LOGIC FOR FLAGS ---
            is_entry_from_jmnedict = 1 if is_jmnedict_source else 0
            # jmnedict_specific_type is REMOVED from here.

            # Build forms with their metadata
            forms_to_insert = []
            kanji_entries = entry.get('kanji', [])
            kana_entries = entry.get('kana', [])
            
            if kanji_forms:
                for kanji_text in kanji_forms:
                    # Find the kanji entry object for this text
                    kanji_entry = next((k for k in kanji_entries if k['text'] == kanji_text), {})
                    kanji_common = kanji_entry.get('common', False)
                    
                    for kana_text in kana_forms:
                        # Find the kana entry object for this text
                        kana_entry = next((k for k in kana_entries if k['text'] == kana_text), {})
                        kana_common = kana_entry.get('common', False)
                        
                        # Form is common if both kanji and kana are common
                        form_common = kanji_common and kana_common
                        
                        forms_to_insert.append((kanji_text, kana_text, form_common))
            elif kana_forms:
                for kana_text in kana_forms:
                    # Find the kana entry object for this text
                    kana_entry = next((k for k in kana_entries if k['text'] == kana_text), {})
                    kana_common = kana_entry.get('common', False)
                    
                    forms_to_insert.append((None, kana_text, kana_common))

            for kanji_form, kana_form, form_is_common in forms_to_insert:
                # OPTIONAL: Apply Romaji normalization here if desired
                # normalized_kana_form = self.normalize_romaji(kana_form)
                # normalized_kanji_form = self.normalize_romaji(kanji_form) if kanji_form else None

                tokenized_kanji = self.tokenize_japanese(kanji_form) if kanji_form else None
                tokenized_reading = self.tokenize_japanese(kana_form)

                frequency = self.calculate_frequency(kanji_form if kanji_form else kana_form, is_common, kana_form, parts_of_speech_list)

                try:
                    # Both JMdict and JMNEdict entries should be inserted - they serve different purposes
                    # JMdict = dictionary words, JMNEdict = proper nouns/names
                    # The UNIQUE constraint is on (kanji, reading) but we want both types when they exist
                    
                    # Debug logging for みる entries
                    if kana_form == 'みる':
                        print(f"   🔍 DEBUG: Inserting みる entry:")
                        print(f"      kanji_form: {repr(kanji_form)}")
                        print(f"      kana_form: {repr(kana_form)}")
                        print(f"      frequency: {frequency}")
                        print(f"      is_common: {is_common}")
                        print(f"      is_entry_from_jmnedict: {is_entry_from_jmnedict}")
                        print(f"      parts_of_speech: {parts_of_speech_list}")
                    
                    cursor.execute("""
                        INSERT OR IGNORE INTO dictionary_entries
                        (kanji, reading, meanings, parts_of_speech, is_common,
                         frequency, tokenized_kanji, tokenized_reading,
                         is_jmnedict_entry, form_is_common)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, (kanji_form, kana_form, meanings_json, parts_of_speech_json,
                          1 if is_common else 0, frequency, tokenized_kanji, tokenized_reading,
                          is_entry_from_jmnedict, 1 if form_is_common else 0))

                    # Get the entry ID - check if entry was actually inserted
                    if cursor.rowcount > 0:
                        # Entry was inserted successfully
                        entry_id = cursor.lastrowid
                    else:
                        # INSERT was ignored (duplicate), fetch the existing entry's ID
                        cursor.execute("""
                            SELECT id FROM dictionary_entries 
                            WHERE reading = ? AND (kanji IS ? OR (kanji IS NULL AND ? IS NULL)) AND is_jmnedict_entry = ?
                        """, (kana_form, kanji_form, kanji_form, is_entry_from_jmnedict))
                        result = cursor.fetchone()
                        entry_id = result[0] if result else None
                    
                    # Debug logging for みる entries - check if insertion was successful
                    if kana_form == 'みる':
                        if entry_id:
                            print(f"   ✅ DEBUG: みる entry inserted successfully with ID: {entry_id}")
                        else:
                            print(f"   ❌ DEBUG: みる entry insertion was ignored (likely duplicate)")
                            # Check what existing entry conflicts
                            cursor.execute("""
                                SELECT id, kanji, reading, frequency, is_common, is_jmnedict_entry 
                                FROM dictionary_entries 
                                WHERE reading = ? AND (kanji IS ? OR (kanji IS NULL AND ? IS NULL)) AND is_jmnedict_entry = ?
                            """, (kana_form, kanji_form, kanji_form, is_entry_from_jmnedict))
                            existing = cursor.fetchone()
                            if existing:
                                print(f"      Existing entry: ID={existing[0]}, kanji={repr(existing[1])}, freq={existing[3]}, is_jmnedict={existing[5]}")
                            else:
                                print(f"      No conflicting entry found - insertion failed for other reason")

                    # Debug logging for JMnedict entries
                    if is_entry_from_jmnedict and kanji_form == '上':
                        print(f"   🔍 DEBUG JMnedict: {kanji_form}/{kana_form}")
                        print(f"      entry_id: {entry_id}")
                        print(f"      parts_of_speech_list: {parts_of_speech_list}")
                        print(f"      tags to insert: {len(parts_of_speech_list)}")

                    if entry_id:
                        for tag in parts_of_speech_list:
                            try:
                                cursor.execute("""
                                    INSERT OR REPLACE INTO word_tags (entry_id, tag)
                                    VALUES (?, ?)
                                """, (entry_id, tag))
                                tags_added += 1
                                
                                # Debug logging for JMnedict entries
                                if is_entry_from_jmnedict and kanji_form == '上':
                                    print(f"         ✅ Inserted tag: {tag}")
                            except sqlite3.Error as e:
                                if is_entry_from_jmnedict and kanji_form == '上':
                                    print(f"         ❌ Failed to insert tag: {tag}, error: {e}")
                                pass
                    else:
                        if is_entry_from_jmnedict and kanji_form == '上':
                            print(f"   ❌ No valid entry_id for {kanji_form}/{kana_form}")
                    entries_added += 1
                except sqlite3.Error as e:
                    error_count += 1
                    print(f"   ⚠️  SQL Error inserting {kanji_form}/{kana_form}: {e}")
                    if "database disk image is malformed" in str(e):
                        print(f"   🚨 CRITICAL: Database corruption detected after {i} entries")
                        raise
                    if error_count > max_errors:
                        print(f"   🚨 Too many errors ({error_count}), stopping build")
                        raise RuntimeError(f"Too many SQL errors: {error_count}")
                    continue

        # Final commit
        try:
            conn.commit()
            print(f"✅ Added {entries_added} dictionary entries from this source")
            print(f"✅ Added {tags_added} word tags from this source")
            if error_count > 0:
                print(f"   ⚠️  Encountered {error_count} errors during insertion")
        except sqlite3.DatabaseError as e:
            print(f"   ❌ Final commit failed: {e}")
            raise

    def populate_fts_tables(self, conn: sqlite3.Connection) -> None:
        """Populate FTS5 virtual tables"""
        cursor = conn.cursor()

        print("🔍 Populating FTS5 tables...")

        # Populate entries_fts5 table
        # This will automatically pull the new columns (is_common, frequency, is_jmnedict_entry)
        # because they are specified in the CREATE VIRTUAL TABLE DDL.
        # FTS5 'rebuild' command ensures all data from the content table is re-indexed.
        cursor.execute("""
            INSERT INTO entries_fts5(entries_fts5) VALUES('rebuild')
        """)

        # Populate english_fts table (no changes needed here as it doesn't use the new columns)
        cursor.execute("""
            INSERT INTO english_fts (entry_id, meanings, parts_of_speech)
            SELECT id, meanings, parts_of_speech FROM dictionary_entries
        """)

        conn.commit()
        print("✅ FTS5 tables populated")

    def create_triggers(self, conn: sqlite3.Connection) -> None:
        """Create triggers to keep FTS5 tables in sync"""
        cursor = conn.cursor()

        print("🔧 Creating triggers...")

        # Trigger for entries_fts5 (AFTER INSERT)
        # CHANGES: Removed jmnedict_type from VALUES list
        cursor.execute("""
            CREATE TRIGGER IF NOT EXISTS entries_fts5_ai AFTER INSERT ON dictionary_entries
            BEGIN
                INSERT INTO entries_fts5(rowid, kanji, reading, meanings, tokenized_kanji,
                                        tokenized_reading, parts_of_speech,
                                        is_common, frequency, is_jmnedict_entry, form_is_common)
                VALUES (new.id, new.kanji, new.reading, new.meanings, new.tokenized_kanji,
                        new.tokenized_reading, new.parts_of_speech,
                        new.is_common, new.frequency, new.is_jmnedict_entry, new.form_is_common);
            END
        """)

        # Trigger for entries_fts5 (AFTER DELETE)
        # CHANGES: Removed jmnedict_type from VALUES list
        cursor.execute("""
            CREATE TRIGGER IF NOT EXISTS entries_fts5_ad AFTER DELETE ON dictionary_entries
            BEGIN
                INSERT INTO entries_fts5(entries_fts5, rowid, kanji, reading, meanings, tokenized_kanji,
                                        tokenized_reading, parts_of_speech,
                                        is_common, frequency, is_jmnedict_entry, form_is_common)
                VALUES ('delete', old.id, old.kanji, old.reading, old.meanings, old.tokenized_kanji,
                        old.tokenized_reading, old.parts_of_speech,
                        old.is_common, old.frequency, old.is_jmnedict_entry, old.form_is_common);
            END
        """)

        # Trigger for entries_fts5 (AFTER UPDATE)
        # CHANGES: Removed jmnedict_type from VALUES list in both delete and insert parts
        cursor.execute("""
            CREATE TRIGGER IF NOT EXISTS entries_fts5_au AFTER UPDATE ON dictionary_entries
            BEGIN
                INSERT INTO entries_fts5(entries_fts5, rowid, kanji, reading, meanings, tokenized_kanji,
                                        tokenized_reading, parts_of_speech,
                                        is_common, frequency, is_jmnedict_entry, form_is_common)
                VALUES ('delete', old.id, old.kanji, old.reading, old.meanings, old.tokenized_kanji,
                        old.tokenized_reading, old.parts_of_speech,
                        old.is_common, old.frequency, old.is_jmnedict_entry, old.form_is_common);
                INSERT INTO entries_fts5(rowid, kanji, reading, meanings, tokenized_kanji,
                                        tokenized_reading, parts_of_speech,
                                        is_common, frequency, is_jmnedict_entry, form_is_common)
                VALUES (new.id, new.kanji, new.reading, new.meanings, new.tokenized_kanji,
                        new.tokenized_reading, new.parts_of_speech,
                        new.is_common, new.frequency, new.is_jmnedict_entry, new.form_is_common);
            END
        """)

        # english_fts triggers remain the same as they don't use these new columns
        cursor.execute("""
            CREATE TRIGGER IF NOT EXISTS english_fts_ai AFTER INSERT ON dictionary_entries
            BEGIN
                INSERT INTO english_fts(entry_id, meanings, parts_of_speech)
                VALUES (new.id, new.meanings, new.parts_of_speech);
            END
        """)

        cursor.execute("""
            CREATE TRIGGER IF NOT EXISTS english_fts_ad AFTER DELETE ON dictionary_entries
            BEGIN
                DELETE FROM english_fts WHERE entry_id = old.id;
            END
        """)

        cursor.execute("""
            CREATE TRIGGER IF NOT EXISTS english_fts_au AFTER UPDATE ON dictionary_entries
            BEGIN
                UPDATE english_fts SET meanings = new.meanings, parts_of_speech = new.parts_of_speech
                WHERE entry_id = old.id;
            END
        """)

        print("✅ Triggers created")

    def verify_database(self, conn: sqlite3.Connection) -> bool:
        """Verify the database was created correctly"""
        cursor = conn.cursor()

        print("🔍 Verifying database...")

        # Check table counts
        cursor.execute("SELECT COUNT(*) FROM dictionary_entries")
        entry_count = cursor.fetchone()[0]

        cursor.execute("SELECT COUNT(*) FROM entries_fts5")
        fts5_count = cursor.fetchone()[0]

        cursor.execute("SELECT COUNT(*) FROM english_fts")
        english_fts_count = cursor.fetchone()[0]

        cursor.execute("SELECT COUNT(*) FROM tag_definitions")
        tag_defs_count = cursor.fetchone()[0]

        cursor.execute("SELECT COUNT(*) FROM word_tags")
        word_tags_count = cursor.fetchone()[0]

        # Check kanji tables too
        cursor.execute("SELECT COUNT(*) FROM kanji_entries")
        kanji_count = cursor.fetchone()[0]

        print(f"   Dictionary entries: {entry_count}")
        print(f"   FTS5 entries: {fts5_count}")
        print(f"   English FTS entries: {english_fts_count}")
        print(f"   Tag definitions: {tag_defs_count}")
        print(f"   Word tags: {word_tags_count}")
        print(f"   Kanji entries: {kanji_count}")

        # Test search functionality
        cursor.execute("""
            SELECT COUNT(*) FROM dictionary_entries
            WHERE id IN (
                SELECT rowid FROM entries_fts5
                WHERE reading MATCH ? OR tokenized_reading MATCH ?
            )
        """, ('みる', 'みる'))

        search_results = cursor.fetchone()[0]
        print(f"   Search test (みる): {search_results} results")

        # Test tag functionality
        cursor.execute("""
            SELECT COUNT(*) FROM word_tags wt
            JOIN tag_definitions td ON wt.tag = td.tag
            WHERE wt.tag = 'v1'
        """)
        tag_test = cursor.fetchone()[0]
        print(f"   Tag test (v1 verbs): {tag_test} results")

        # Test kanji lookup
        cursor.execute("""
            SELECT COUNT(*) FROM kanji_entries
            WHERE kanji = '水'
        """)
        kanji_test = cursor.fetchone()[0]
        print(f"   Kanji test ('水'): {kanji_test} results")

        # Check if we have reasonable data (FIXED: tag_defs_count >= 50)
        if (entry_count > 100000 and fts5_count == entry_count and
            search_results > 0 and tag_defs_count >= 50 and word_tags_count > 100000):
            print("✅ Database verification passed")
            return True
        else:
            print("❌ Database verification failed")
            return False

    def create_kanjidic_schema(self, conn: sqlite3.Connection) -> None:
        """Create schema for KanjiDic database"""
        cursor = conn.cursor()

        print("📋 Creating KanjiDic database schema...")

        # Main kanji entries table
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS kanji_entries (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                kanji TEXT NOT NULL UNIQUE,
                jlpt_level INTEGER,
                grade INTEGER,
                stroke_count INTEGER,
                frequency INTEGER,
                meanings TEXT,
                kun_readings TEXT,
                on_readings TEXT,
                nanori_readings TEXT,
                radical_names TEXT,
                heisig_number INTEGER,
                heisig_keyword TEXT,
                components TEXT,
                radical TEXT,
                radical_number INTEGER
            )
        """)

        # Create indexes
        cursor.execute("CREATE INDEX IF NOT EXISTS idx_kanji ON kanji_entries(kanji)")
        cursor.execute("CREATE INDEX IF NOT EXISTS idx_jlpt ON kanji_entries(jlpt_level)")
        cursor.execute("CREATE INDEX IF NOT EXISTS idx_grade ON kanji_entries(grade)")
        cursor.execute("CREATE INDEX IF NOT EXISTS idx_frequency ON kanji_entries(frequency)")
        cursor.execute("CREATE INDEX IF NOT EXISTS idx_stroke_count ON kanji_entries(stroke_count)")

        # Kanji radical mapping tables (from kradfile.json)
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS kanji_radical_mapping (
                kanji TEXT PRIMARY KEY,
                components TEXT NOT NULL
            )
        """)

        cursor.execute("""
            CREATE TABLE IF NOT EXISTS radical_kanji_mapping (
                radical TEXT PRIMARY KEY,
                stroke_count INTEGER,
                kanji_list TEXT NOT NULL
            )
        """)

        cursor.execute("""
            CREATE TABLE IF NOT EXISTS radical_decomposition_mapping (
                radical TEXT PRIMARY KEY,
                components TEXT NOT NULL,
                component_count INTEGER NOT NULL
            )
        """)

        # Create indexes for radical tables
        cursor.execute("CREATE INDEX IF NOT EXISTS idx_radical_stroke_count ON radical_kanji_mapping(stroke_count)")
        cursor.execute("CREATE INDEX IF NOT EXISTS idx_decomposition_component_count ON radical_decomposition_mapping(component_count)")

        # No FTS5 tables needed for kanji - direct lookup is sufficient

        print("✅ KanjiDic database schema created")

    def load_kradfile_data(self, file_path: str) -> Optional[Dict]:
        """Load kradfile data from JSON file"""
        print(f"📖 Loading kradfile data from {file_path}...")
        
        if not os.path.exists(file_path):
            print(f"⚠️  kradfile not found: {file_path}")
            return None
            
        try:
            with open(file_path, 'r', encoding='utf-8') as f:
                data = json.load(f)
            
            kanji_data = data.get('kanji', {})
            print(f"✅ Loaded {len(kanji_data)} kanji entries from kradfile")
            return kanji_data
            
        except Exception as e:
            print(f"🚨 Error loading kradfile data: {e}")
            return None

    def load_radkfile_data(self, file_path: str) -> Optional[Dict]:
        """Load radkfile data from JSON file"""
        print(f"📖 Loading radkfile data from {file_path}...")
        
        if not os.path.exists(file_path):
            print(f"⚠️  radkfile not found: {file_path}")
            return None
            
        try:
            with open(file_path, 'r', encoding='utf-8') as f:
                data = json.load(f)
            
            # Extract the radicals data
            radicals_data = data.get('radicals', {})
            print(f"✅ Loaded {len(radicals_data)} radical entries from radkfile")
            return radicals_data
            
        except Exception as e:
            print(f"🚨 Error loading radkfile data: {e}")
            return None

    def populate_kanji_radical_mapping(self, conn: sqlite3.Connection, kradfile_data: Dict) -> None:
        """Populate kanji_radical_mapping table from kradfile data"""
        cursor = conn.cursor()
        
        print("🔧 Populating kanji radical mapping...")
        
        for kanji, components in kradfile_data.items():
            # Convert list of components to comma-separated string
            components_str = ", ".join(components) if isinstance(components, list) else str(components)
            
            cursor.execute("""
                INSERT OR REPLACE INTO kanji_radical_mapping (kanji, components)
                VALUES (?, ?)
            """, (kanji, components_str))
        
        conn.commit()
        print(f"✅ Added {len(kradfile_data)} kanji component mappings")

    def populate_radical_kanji_mapping_from_kradfile(self, conn: sqlite3.Connection, kradfile_data: Dict, radkfile_data: Dict = None) -> None:
        """Build radical_kanji_mapping table from kradfile data (primary) with radkfile fallback"""
        cursor = conn.cursor()
        
        print("🔧 Building radical kanji mapping from kradfile data...")
        
        # Build radical -> kanji mapping from kradfile
        radical_to_kanji = {}
        
        print("  📝 Inverting kradfile (kanji → components) to (radical → kanji list)...")
        for kanji, components in kradfile_data.items():
            if isinstance(components, list):
                for radical in components:
                    if radical not in radical_to_kanji:
                        radical_to_kanji[radical] = []
                    radical_to_kanji[radical].append(kanji)
                    
                    # Debug 人 radical specifically
                    if radical == '人' and kanji == '火':
                        print(f"    🎯 Found 火 with 人 component in kradfile!")
        
        print(f"  📊 Built mapping for {len(radical_to_kanji)} unique radicals from kradfile")
        
        # Check 人 radical from kradfile inversion
        if '人' in radical_to_kanji:
            person_kanji_from_krad = radical_to_kanji['人']
            has_fire = '火' in person_kanji_from_krad
            print(f"  🎯 人 radical from kradfile inversion: {len(person_kanji_from_krad)} kanji, contains 火: {has_fire}")
        else:
            print("  ❌ 人 radical not found in kradfile inversion!")
        
        # Get stroke counts from radkfile if available
        radical_stroke_counts = {}
        if radkfile_data:
            print("  📏 Getting stroke counts from radkfile...")
            for radical, info in radkfile_data.items():
                stroke_count = info.get('strokeCount', 0)
                radical_stroke_counts[radical] = stroke_count
        
        # Insert or update radical mappings
        for radical, kanji_list in radical_to_kanji.items():
            # Get stroke count from radkfile, default to 1 if not found
            stroke_count = radical_stroke_counts.get(radical, 1)
            
            # Convert list of kanji to comma-separated string
            kanji_str = ", ".join(sorted(kanji_list))  # Sort for consistency
            
            cursor.execute("""
                INSERT OR REPLACE INTO radical_kanji_mapping (radical, stroke_count, kanji_list)
                VALUES (?, ?, ?)
            """, (radical, stroke_count, kanji_str))
        
        conn.commit()
        print(f"✅ Built radical mappings for {len(radical_to_kanji)} radicals from kradfile data")
        
        # Add any radicals from radkfile that weren't in kradfile
        if radkfile_data:
            radkfile_only_count = 0
            for radical, info in radkfile_data.items():
                if radical not in radical_to_kanji:
                    stroke_count = info.get('strokeCount', 0)
                    kanji_list = info.get('kanji', [])
                    kanji_str = ", ".join(kanji_list) if isinstance(kanji_list, list) else str(kanji_list)
                    
                    cursor.execute("""
                        INSERT OR REPLACE INTO radical_kanji_mapping (radical, stroke_count, kanji_list)
                        VALUES (?, ?, ?)
                    """, (radical, stroke_count, kanji_str))
                    radkfile_only_count += 1
            
            if radkfile_only_count > 0:
                conn.commit()
                print(f"✅ Added {radkfile_only_count} additional radicals from radkfile")

    def populate_radical_kanji_mapping(self, conn: sqlite3.Connection, radkfile_data: Dict) -> None:
        """Populate radical_kanji_mapping table from radkfile data (legacy method)"""
        cursor = conn.cursor()
        
        print("🔧 Populating radical kanji mapping...")
        
        for radical, info in radkfile_data.items():
            stroke_count = info.get('strokeCount', 0)
            kanji_list = info.get('kanji', [])
            
            # Convert list of kanji to comma-separated string
            kanji_str = ", ".join(kanji_list) if isinstance(kanji_list, list) else str(kanji_list)
            
            cursor.execute("""
                INSERT OR REPLACE INTO radical_kanji_mapping (radical, stroke_count, kanji_list)
                VALUES (?, ?, ?)
            """, (radical, stroke_count, kanji_str))
        
        conn.commit()
        print(f"✅ Added {len(radkfile_data)} radical kanji mappings")

    def parse_makemeahanzi_decomposition(self, decomposition: str) -> List[str]:
        """
        Parse makemeahanzi decomposition field, extracting component radicals
        
        Args:
            decomposition: The decomposition string like "⿻亅八" or "⿰氵青"
            
        Returns:
            List of component radicals (without IDC symbols)
        """
        if not decomposition:
            return []

        # IDC (Ideographic Description Characters) to ignore
        idc_chars = {
            '⿰', '⿱', '⿲', '⿳', '⿴', '⿵', '⿶', '⿷', '⿸', '⿹', '⿺', '⿻'
        }

        # Extract all characters except IDC symbols
        components = []
        for char in decomposition:
            if char not in idc_chars and char.strip():
                components.append(char)

        return components

    def load_makemeahanzi_decomposition_data(self, makemeahanzi_path: str = None) -> Dict[str, List[str]]:
        """
        Load makemeahanzi decomposition data from downloaded dictionary.txt
        
        Args:
            makemeahanzi_path: Optional path to makemeahanzi file, if None will try to download
            
        Returns:
            Dictionary mapping radicals to their component radicals
        """
        print("📖 Loading makemeahanzi decomposition data...")
        
        # If no path provided, try to download makemeahanzi data
        temp_dir = None
        if not makemeahanzi_path:
            try:
                # Try to use the same download logic from preserve_modifications.py
                import tempfile
                import urllib.request
                import zipfile
                
                temp_dir = tempfile.mkdtemp()
                makemeahanzi_zip = os.path.join(temp_dir, "makemeahanzi.zip")
                makemeahanzi_path = os.path.join(temp_dir, "dictionary.txt")
                
                print("⬇️  Downloading makemeahanzi data...")
                urllib.request.urlretrieve(
                    "https://github.com/skishore/makemeahanzi/archive/refs/heads/master.zip",
                    makemeahanzi_zip
                )
                
                with zipfile.ZipFile(makemeahanzi_zip, 'r') as zip_ref:
                    zip_ref.extract("makemeahanzi-master/dictionary.txt", temp_dir)
                    
                # Move the extracted file to the expected location
                extracted_path = os.path.join(temp_dir, "makemeahanzi-master", "dictionary.txt")
                if os.path.exists(extracted_path):
                    shutil.move(extracted_path, makemeahanzi_path)
                
                print(f"✅ Downloaded makemeahanzi to {makemeahanzi_path}")
                
            except Exception as e:
                print(f"⚠️  Could not download makemeahanzi data: {e}")
                return {}
        
        if not os.path.exists(makemeahanzi_path):
            print(f"⚠️  Makemeahanzi file not found at {makemeahanzi_path}")
            return {}
        
        radical_decompositions = {}
        
        try:
            with open(makemeahanzi_path, 'r', encoding='utf-8') as f:
                for line_num, line in enumerate(f, 1):
                    line = line.strip()
                    if not line:
                        continue
                    
                    try:
                        entry = json.loads(line)
                        character = entry.get("character")
                        decomposition = entry.get("decomposition")
                        
                        if character and decomposition:
                            components = self.parse_makemeahanzi_decomposition(decomposition)
                            if components and len(components) > 1:  # Only include composites
                                radical_decompositions[character] = components
                    
                    except json.JSONDecodeError:
                        continue
                        
        except Exception as e:
            print(f"❌ Error parsing makemeahanzi data: {e}")
            return {}
        
        print(f"✅ Parsed {len(radical_decompositions)} radical decompositions from makemeahanzi")
        
        # Clean up temporary files if we downloaded them
        if temp_dir and os.path.exists(temp_dir):
            try:
                shutil.rmtree(temp_dir)
            except:
                pass
        
        return radical_decompositions

    def populate_radical_decomposition_mapping(self, conn: sqlite3.Connection, decomposition_data: Dict[str, List[str]], existing_radicals: set) -> None:
        """
        Populate radical_decomposition_mapping table with makemeahanzi decomposition data
        
        Args:
            conn: Database connection
            decomposition_data: Dictionary mapping radicals to component lists
            existing_radicals: Set of radicals that exist in the radical system
        """
        cursor = conn.cursor()
        
        print("🔧 Populating radical decomposition mapping...")
        
        # Chinese → Japanese radical substitutions
        chinese_to_japanese_substitutions = {
            # Fire and line radicals
            '灬': '⺣',  # Chinese fire dots → Japanese fire radical
            '丨': '｜',  # CJK radical stick → Japanese fullwidth vertical line
            
            # Common stroke/shape radicals
            '丿': 'ノ',  # Left-falling stroke → katakana no (similar shape/meaning)
            '乚': '乙',  # Hook stroke → second/hook radical
            '亻': '人',  # Person radical variant → person radical
            
            # Note: 廿 and 卄 should decompose naturally:
            # 廿 → ['廾', '一'] and 卄 → ['十', '丨'] → ['十', '｜']
            # So no direct substitution needed for these
            
            # Add more substitutions as we research them
        }
        
        # Manual corrections for known issues in makemeahanzi data
        manual_corrections = {
            '丷': ['丶', '丶'],  # Two dots - makemeahanzi has corrupted data with '？'
            '肉': ['冂', '人', '人'],  # Meat - flattened from 冂,仌 → 冂,人,人
            # Add more corrections here if needed
        }
        
        # Apply manual corrections first
        corrected_count = 0
        for radical, components in manual_corrections.items():
            if radical in existing_radicals:
                valid_components = [comp for comp in components if comp in existing_radicals]
                if valid_components and len(valid_components) > 1:
                    components_str = ",".join(valid_components)
                    component_count = len(valid_components)
                    
                    cursor.execute("""
                        INSERT OR REPLACE INTO radical_decomposition_mapping (radical, components, component_count)
                        VALUES (?, ?, ?)
                    """, (radical, components_str, component_count))
                    
                    corrected_count += 1
                    print(f"    🔧 Manual correction: {radical} → {valid_components}")
        
        def apply_substitutions_and_expansion(components):
            """Apply Chinese→Japanese substitutions and recursive expansion"""
            expanded_components = []
            
            for comp in components:
                # Apply Chinese → Japanese substitutions first
                if comp in chinese_to_japanese_substitutions:
                    substitute = chinese_to_japanese_substitutions[comp]
                    expanded_components.append(substitute)
                    print(f"      🔄 Substituted: {comp} → {substitute}")
                # Recursive expansion for missing components that have decompositions
                elif comp not in existing_radicals and comp in decomposition_data:
                    # Recursively expand missing component
                    sub_components = decomposition_data[comp]
                    # Apply substitutions recursively (but avoid infinite loops)
                    if comp != '仌':  # Prevent infinite recursion for ice radical
                        recursive_expansion = apply_substitutions_and_expansion(sub_components)
                        expanded_components.extend(recursive_expansion)
                        print(f"      🔄 Expanded: {comp} → {recursive_expansion}")
                    else:
                        # Special case for ice radical: 仌 → ['人', '人']
                        expanded_components.extend(['人', '人'])
                        print(f"      🔄 Expanded ice: {comp} → ['人', '人']")
                else:
                    # Keep component as-is
                    expanded_components.append(comp)
            
            return expanded_components
        
        valid_decompositions = 0
        substitution_count = 0
        for radical, components in decomposition_data.items():
            # Skip if we already applied a manual correction
            if radical in manual_corrections:
                continue
                
            # Only include decompositions where the radical exists in our radical system
            if radical in existing_radicals:
                # Apply substitutions and recursive expansion
                expanded_components = apply_substitutions_and_expansion(components)
                
                # Filter to only include components that exist as radicals after expansion
                valid_components = [comp for comp in expanded_components if comp in existing_radicals]
                
                # Allow single components for important hierarchical radicals, otherwise require 2+
                important_single_decompositions = {'冂', '⺣'}  # Box and fire radical create useful hierarchical paths
                min_components = 1 if radical in important_single_decompositions else 2
                
                if valid_components and len(valid_components) >= min_components:
                    components_str = ",".join(valid_components)
                    component_count = len(valid_components)
                    
                    cursor.execute("""
                        INSERT OR REPLACE INTO radical_decomposition_mapping (radical, components, component_count)
                        VALUES (?, ?, ?)
                    """, (radical, components_str, component_count))
                    
                    valid_decompositions += 1
                    
                    # Track substitutions made
                    if len(expanded_components) != len(components):
                        substitution_count += 1
                    
                    # Debug specific examples
                    if radical in ['魚', '肉', '鳥', '馬', '丷'] or len(expanded_components) != len(components):
                        print(f"    🎯 Added decomposition: {radical} → {valid_components} (from {components})")
        
        conn.commit()
        print(f"✅ Added {valid_decompositions} radical decompositions from makemeahanzi")
        print(f"✅ Added {corrected_count} manual corrections")
        print(f"🔄 Applied substitutions/expansions to {substitution_count} radicals")

    def load_kanjidic_data(self, file_path: str) -> List[Dict]:
        """Load KanjiDic data from JSON file"""
        print(f"📖 Loading KanjiDic data from {file_path}...")

        if os.path.exists(file_path):
            try:
                with open(file_path, 'r', encoding='utf-8') as f:
                    data = json.load(f)
                
                # Extract characters array from KanjiDic JSON structure
                characters = data.get('characters', [])
                print(f"✅ Loaded {len(characters)} kanji entries from KanjiDic file")
                return characters
            except Exception as e:
                print(f"❌ Failed to load KanjiDic data from {file_path}: {e}")
                return []
        else:
            print(f"⚠️  KanjiDic file not found at {file_path}")
            print("Please ensure you have kanjidic.json file in the assets directory")
            return []

    def populate_kanji_entries(self, conn: sqlite3.Connection, kanjidic_data: List[Dict], debug_mode: bool = False) -> None:
        """Populate the kanji_entries table"""
        cursor = conn.cursor()

        print(f"📝 Populating kanji entries... (debug_mode={debug_mode})")
        entries_added = 0

        for i, entry in enumerate(kanjidic_data):
            if i % 50 == 0 and i > 0:
                print(f"   Progress: {i}/{len(kanjidic_data)} kanji...")
                conn.commit()

            # KanjiDic2 format uses 'literal' for the kanji character
            kanji = entry.get('literal', entry.get('kanji', entry.get('character', '')))
            if not kanji:
                continue

            # Extract meanings from KanjiDic2 structure
            meanings = []
            reading_meaning = entry.get('readingMeaning', {})
            if 'groups' in reading_meaning:
                for group in reading_meaning['groups']:
                    if 'meanings' in group:
                        for meaning in group['meanings']:
                            if meaning.get('lang') == 'en':
                                meanings.append(meaning['value'])

            if not meanings and 'meanings' in entry: # Fallback for older formats
                meanings = entry['meanings']

            meanings_json = json.dumps(meanings, ensure_ascii=False)

            # Extract readings from KanjiDic2 structure
            kun_readings = []
            on_readings = []
            nanori_readings = []

            if 'groups' in reading_meaning:
                for group in reading_meaning['groups']:
                    if 'readings' in group:
                        for reading in group['readings']:
                            reading_type = reading.get('type', '')
                            reading_value = reading.get('value', '')

                            if reading_type == 'ja_kun':
                                kun_readings.append(reading_value)
                            elif reading_type == 'ja_on':
                                on_readings.append(reading_value)

            # Get nanori readings
            if 'nanori' in reading_meaning:
                nanori_readings = reading_meaning['nanori']

            # Fallback to old format (if 'groups' not used or empty)
            if not kun_readings and entry.get('kun_readings'):
                kun_readings = entry['kun_readings']
            if not on_readings and entry.get('on_readings'):
                on_readings = entry['on_readings']
            if not nanori_readings and entry.get('nanori_readings'):
                nanori_readings = entry['nanori_readings']

            kun_json = json.dumps(kun_readings if isinstance(kun_readings, list) else [kun_readings], ensure_ascii=False)
            on_json = json.dumps(on_readings if isinstance(on_readings, list) else [on_readings], ensure_ascii=False)
            nanori_json = json.dumps(nanori_readings if isinstance(nanori_readings, list) else [nanori_readings], ensure_ascii=False)

            # Extract metadata from KanjiDic2 structure
            misc = entry.get('misc', {})
            jlpt_level = misc.get('jlptLevel', entry.get('jlpt_level', entry.get('jlpt')))
            grade = misc.get('grade', entry.get('grade', entry.get('grade_level')))
            stroke_counts = misc.get('strokeCounts', [])
            stroke_count = stroke_counts[0] if stroke_counts else entry.get('stroke_count', entry.get('strokes'))
            frequency = misc.get('frequency', entry.get('frequency', entry.get('freq')))

            heisig_number = entry.get('heisig_number', entry.get('heisig'))
            heisig_keyword = entry.get('heisig_keyword', '')

            # Extract radical information from the radicals array
            radicals_list = entry.get('radicals', [])
            radical_number = None
            radical = ''
            
            # Find the classical radical
            for rad in radicals_list:
                if rad.get('type') == 'classical':
                    radical_number = rad.get('value')
                    break
            
            # Get radical name using a lookup table
            radical_names = []
            if radical_number:
                radical_name = get_radical_name(radical_number)
                if radical_name:
                    radical_names = [radical_name]
                    radical = radical_name
            
            radical_names_json = json.dumps(radical_names, ensure_ascii=False)

            components = entry.get('components', [])
            components_json = json.dumps(components if isinstance(components, list) else [components], ensure_ascii=False)

            try:
                cursor.execute("""
                    INSERT OR REPLACE INTO kanji_entries
                    (kanji, jlpt_level, grade, stroke_count, frequency,
                     meanings, kun_readings, on_readings, nanori_readings,
                     radical_names, heisig_number, heisig_keyword,
                     components, radical, radical_number)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, (kanji, jlpt_level, grade, stroke_count, frequency,
                      meanings_json, kun_json, on_json, nanori_json,
                      radical_names_json, heisig_number, heisig_keyword,
                      components_json, radical, radical_number))

                entries_added += 1
                
                # In debug mode, commit after every successful insertion
                if debug_mode:
                    conn.commit()
                    if entries_added % 100 == 0:
                        print(f"   🐛 Debug: Successfully added {entries_added} kanji so far")
                        
            except sqlite3.Error as e:
                print(f"   ❌ Error inserting kanji '{kanji}': {e}")
                # Only suppress UNIQUE constraint errors, not other issues
                if "UNIQUE constraint failed" not in str(e):
                    print(f"   🔍 Non-unique error for '{kanji}': {e}")
                    # Try to commit what we have so far to avoid losing progress
                    try:
                        conn.commit()
                        print(f"   💾 Committed {entries_added} entries so far due to error")
                    except:
                        pass
                continue

        # Final commit
        conn.commit()
        print(f"✅ Added {entries_added} kanji entries total")

    def load_jmnedict_data(self, file_path: str) -> List[Dict]:
        """Load JMnedict data from JSON file"""
        print(f"📛 Loading JMnedict data from {file_path}...")

        if os.path.exists(file_path):
            try:
                with open(file_path, 'r', encoding='utf-8') as f:
                    data = json.load(f)
                
                # Handle new JMnedict format with metadata and words array
                if isinstance(data, dict) and 'words' in data:
                    words = data['words']
                    print(f"✅ Loaded {len(words)} entries from JMnedict file (new format)")
                    return words
                elif isinstance(data, list):
                    # Old format - direct array of entries
                    print(f"✅ Loaded {len(data)} entries from JMnedict file (old format)")
                    return data
                else:
                    print(f"❌ Unexpected JMnedict format")
                    return []
            except Exception as e:
                print(f"❌ Failed to load JMnedict data from {file_path}: {e}")
                return []
        else:
            print(f"⚠️  JMnedict file not found at {file_path}")
            print("Please ensure you have jmnedict.json file in the assets directory")
            return []


    def rebuild_kanji_only(self) -> bool:
        """Rebuild only the kanji_entries table in existing database"""
        print(f"🔄 Rebuilding kanji data in: {self.output_path}")
        
        if not os.path.exists(self.output_path):
            print(f"❌ Database not found: {self.output_path}")
            return False
        
        try:
            conn = sqlite3.connect(self.output_path)
            
            # Clear existing kanji data
            print("🗑️  Clearing existing kanji data...")
            conn.execute("DELETE FROM kanji_entries")
            conn.commit()
            
            # Load KanjiDic data from single file
            print("📖 Loading KanjiDic data...")
            kanjidic_file = "app/src/main/assets/kanjidic.json"
            kanjidic_data = self.load_kanjidic_data(kanjidic_file)
            
            if not kanjidic_data:
                print("❌ No KanjiDic data found!")
                conn.close()
                return False
            
            # Populate kanji entries
            print(f"📝 Populating {len(kanjidic_data)} kanji entries...")
            self.populate_kanji_entries(conn, kanjidic_data)
            
            # Commit and close
            conn.commit()
            conn.close()
            
            print("✅ Kanji rebuild completed successfully!")
            return True
            
        except Exception as e:
            print(f"❌ Kanji rebuild failed: {e}")
            return False

    def build_database(self, jmdict_path: str = "app/src/main/assets/jmdict.json", kanjidic_path: str = "app/src/main/assets/kanjidic.json", jmnedict_path: str = "app/src/main/assets/jmnedict.json", kradfile_path: str = "app/src/main/assets/kradfile.json", radkfile_path: str = "app/src/main/assets/radkfile.json") -> bool:
        """Main method to build the complete database"""
        print(f"🚀 Building database: {self.output_path}")
        print(f"📅 Started at: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")

        # Remove existing database
        if os.path.exists(self.output_path):
            print("🗑️  Removing existing database...")
            os.remove(self.output_path)

        try:
            # Create new database
            conn = sqlite3.connect(self.output_path)
            conn.execute("PRAGMA journal_mode = WAL;") # Enable WAL for better concurrency
            conn.execute("PRAGMA synchronous = NORMAL;") # Optimize write performance

            # Create schema
            print("📋 Creating database schema...")
            self.create_database_schema(conn)

            # Populate tag definitions (JMdict POS tags and JMnedict types)
            print("🏷️  Populating tag definitions...")
            self.populate_tag_definitions(conn)

            # Populate pitch accent data
            print("🎵 Populating pitch accent data...")
            self.populate_pitch_accents(conn)

            # Process KANJI and RADICAL data FIRST (faster to test)
            print("\n" + "="*50)
            print("🔧 PROCESSING KANJI & RADICAL DATA (FAST)")
            print("="*50)
            
            # Load and populate kradfile data (kanji → components)
            print("\n🔄 Processing kradfile data...")
            kradfile_data = self.load_kradfile_data(kradfile_path)
            if kradfile_data:
                print(f"📊 Loaded {len(kradfile_data)} kanji entries from kradfile")
                self.populate_kanji_radical_mapping(conn, kradfile_data)
            else:
                print("⚠️  No kradfile data found, skipping kanji radical mapping.")
            
            # Load radkfile data (for stroke counts and additional radicals)
            print("\n🔄 Loading radkfile data...")
            radkfile_data = self.load_radkfile_data(radkfile_path)
            if radkfile_data:
                print(f"📊 Loaded {len(radkfile_data)} radicals from radkfile")
                
                # Show sample radical data for debugging
                sample_radicals = list(radkfile_data.keys())[:5]
                for radical in sample_radicals:
                    kanji_count = len(radkfile_data[radical].get('kanji', []))
                    stroke_count = radkfile_data[radical].get('strokeCount', 0)
                    print(f"  🔍 {radical}: {kanji_count} kanji, {stroke_count} strokes")
                
                # Check specifically for 人 radical
                if '人' in radkfile_data:
                    person_kanji = radkfile_data['人']['kanji']
                    has_fire = '火' in person_kanji
                    print(f"  🎯 人 radical: {len(person_kanji)} kanji, contains 火: {has_fire}")
                else:
                    print("  ❌ 人 radical not found in radkfile!")
            else:
                print("⚠️  No radkfile data found!")

            # Build radical → kanji mapping from kradfile (primary) with radkfile support
            print("\n🔧 Building radical → kanji mapping...")
            if kradfile_data:
                self.populate_radical_kanji_mapping_from_kradfile(conn, kradfile_data, radkfile_data)
                
                # Verify the database entries
                print("\n🔍 Verifying radical database entries...")
                cursor = conn.cursor()
                cursor.execute("SELECT COUNT(*) FROM radical_kanji_mapping")
                radical_count = cursor.fetchone()[0]
                print(f"  📊 Total radicals in database: {radical_count}")
                
                # Check 人 radical specifically
                cursor.execute("SELECT radical, stroke_count, kanji_list FROM radical_kanji_mapping WHERE radical = '人'")
                person_result = cursor.fetchone()
                if person_result:
                    radical, strokes, kanji_list = person_result
                    kanji_count = len(kanji_list.split(', ')) if kanji_list else 0
                    has_fire = '火' in kanji_list if kanji_list else False
                    print(f"  🎯 Database 人 radical: {kanji_count} kanji, {strokes} strokes, contains 火: {has_fire}")
                    if has_fire:
                        print("    ✅ SUCCESS: 火 found in 人 radical!")
                    else:
                        print("    ❌ ISSUE: 火 NOT found in 人 radical")
                else:
                    print("  ❌ 人 radical not found in database!")
                    
            elif radkfile_data:
                # Fallback to old method if only radkfile is available
                self.populate_radical_kanji_mapping(conn, radkfile_data)
            else:
                print("⚠️  No radical data found, skipping radical kanji mapping.")
            
            # POST-PROCESSING: Override ALL radicals with enhanced radkfile data
            if radkfile_data:
                print("\n🔧 POST-PROCESSING: Enhancing ALL radicals with radkfile data...")
                cursor = conn.cursor()
                
                updated_count = 0
                for radical, info in radkfile_data.items():
                    enhanced_kanji_list = info.get('kanji', [])
                    if enhanced_kanji_list:  # Only update if there's data
                        kanji_str = ", ".join(enhanced_kanji_list)
                        
                        # Debug 人 radical specifically
                        if radical == '人':
                            print(f"  🔍 Updating 人 radical with {len(enhanced_kanji_list)} kanji")
                            print(f"  🔍 火 in list: {'火' in enhanced_kanji_list}")
                            print(f"  🔍 First 10 kanji: {enhanced_kanji_list[:10]}")
                        
                        cursor.execute("""
                            UPDATE radical_kanji_mapping 
                            SET kanji_list = ?
                            WHERE radical = ?
                        """, (kanji_str, radical))
                        
                        if cursor.rowcount > 0:
                            updated_count += 1
                            if radical == '人':
                                print(f"  ✅ Successfully updated 人 radical (rowcount={cursor.rowcount})")
                
                conn.commit()
                print(f"  ✅ Updated {updated_count} radicals with enhanced data")
                
                # Verify 人 radical specifically
                cursor.execute("SELECT radical, stroke_count, kanji_list FROM radical_kanji_mapping WHERE radical = '人'")
                result = cursor.fetchone()
                if result:
                    radical, strokes, kanji_list = result
                    kanji_count = len(kanji_list.split(', ')) if kanji_list else 0
                    has_fire = '火' in kanji_list if kanji_list else False
                    print(f"  🎯 人 radical after update: {kanji_count} kanji, contains 火: {has_fire}")
                    if has_fire:
                        print("    ✅ SUCCESS: 火 found in 人 radical!")
                
                print("  ✅ Post-processing complete!")
                
                # Final verification before moving on
                print("\n🔍 FINAL VERIFICATION of radical data...")
                cursor.execute("SELECT radical, stroke_count, kanji_list FROM radical_kanji_mapping WHERE radical = '人'")
                final_result = cursor.fetchone()
                if final_result:
                    radical, strokes, kanji_list = final_result
                    kanji_count = len(kanji_list.split(', ')) if kanji_list else 0
                    has_fire = '火' in kanji_list if kanji_list else False
                    print(f"  🎯 FINAL CHECK - 人 radical: {kanji_count} kanji, contains 火: {has_fire}")
                    
                    # Also check a few specific kanji
                    if kanji_list:
                        kanji_array = kanji_list.split(', ')
                        print(f"  📝 First 5 kanji: {kanji_array[:5]}")
                        if '火' in kanji_array:
                            fire_index = kanji_array.index('火')
                            print(f"  🔥 火 is at position {fire_index + 1}")
                else:
                    print("  ❌ 人 radical not found in final check!")

            # RADICAL DECOMPOSITION: Load and populate makemeahanzi decomposition data
            print("\n🧩 Processing radical decomposition data...")
            decomposition_data = self.load_makemeahanzi_decomposition_data()
            if decomposition_data:
                # Get existing radicals from the database to filter valid decompositions
                cursor.execute("SELECT radical FROM radical_kanji_mapping")
                existing_radicals = {row[0] for row in cursor.fetchall()}
                print(f"📊 Found {len(existing_radicals)} existing radicals in database")
                
                self.populate_radical_decomposition_mapping(conn, decomposition_data, existing_radicals)
                
                # Verify specific decomposition example
                cursor.execute("SELECT radical, components FROM radical_decomposition_mapping WHERE radical = '丷'")
                result = cursor.fetchone()
                if result:
                    radical, components = result
                    print(f"  🎯 Example decomposition: {radical} → {components}")
                else:
                    print("  ⚠️  No decomposition example found for 丷")
            else:
                print("⚠️  No makemeahanzi decomposition data found, skipping radical decomposition.")

            # Load and populate KanjiDic data
            print("\n📖 Processing KanjiDic data...")
            kanjidic_data = self.load_kanjidic_data(kanjidic_path)
            if kanjidic_data:
                print(f"📊 Loaded {len(kanjidic_data)} kanji from KanjiDic")
                self.populate_kanji_entries(conn, kanjidic_data)
            else:
                print("⚠️  No KanjiDic data found, skipping KanjiDic entries.")

            print("\n" + "="*50)
            print("🎉 KANJI & RADICAL DATA COMPLETE!")
            print("You can test the radical search now or continue with full build...")
            print("="*50)
            
            # Process DICTIONARY data LAST (slower, for complete build)
            print("\n📖 Processing JMdict data (this will take time)...")
            
            # Load JMdict data from single file
            jmdict_data = self.load_jmdict_data(jmdict_path)
            if not jmdict_data:
                print("🚨 No JMdict data loaded. Cannot build dictionary.")
                return False

            print(f"📊 Loaded {len(jmdict_data)} entries from JMdict")

            # Load JMDict specific tags data
            tags_file_path = os.path.join(os.path.dirname(jmdict_path), "tags.json")
            tags_data_jmdict = self.load_tags_data(tags_file_path)

            # Populate dictionary data with JMdict entries
            print("🔄 Populating JMdict entries...")
            self.populate_dictionary_entries(conn, jmdict_data, tags_data_jmdict, is_jmnedict_source=False)

            # Load and populate JMnedict data (names dictionary) from single file
            print("\n📛 Processing JMnedict data...")
            jmnedict_data = self.load_jmnedict_data(jmnedict_path)
            if jmnedict_data:
                print(f"📊 Loaded {len(jmnedict_data)} entries from JMnedict")
                # JMnedict tags are embedded in the main file, pass empty dict for tags_data
                print(f"📛 Adding {len(jmnedict_data)} JMnedict entries to dictionary tables...")
                # Pass True for is_jmnedict_source for all JMnedict entries
                self.populate_dictionary_entries(conn, jmnedict_data, {}, is_jmnedict_source=True)
            else:
                print("⚠️  No JMnedict data found, skipping name entries.")

            # Load and populate KanjiDic data from single file
            print("\n🈵 Processing KanjiDic data...")
            kanjidic_data = self.load_kanjidic_data(kanjidic_path)
            if kanjidic_data:
                self.populate_kanji_entries(conn, kanjidic_data)
            else:
                print("⚠️  No KanjiDic data found, skipping kanji tables.")

            # NOTE: kradfile and radkfile processing already done at the beginning with post-processing

            # Populate FTS tables (after all data is inserted into main table)
            self.populate_fts_tables(conn)

            # Create triggers (after FTS tables are created)
            self.create_triggers(conn)

            # Verify database
            if not self.verify_database(conn):
                return False

            # Check 人 radical before optimization
            print("\n🔍 Checking 人 radical BEFORE optimization...")
            cursor = conn.cursor()
            cursor.execute("SELECT radical, stroke_count, kanji_list FROM radical_kanji_mapping WHERE radical = '人'")
            before_result = cursor.fetchone()
            if before_result:
                radical, strokes, kanji_list = before_result
                kanji_count = len(kanji_list.split(', ')) if kanji_list else 0
                has_fire = '火' in kanji_list if kanji_list else False
                print(f"  🎯 BEFORE VACUUM - 人 radical: {kanji_count} kanji, contains 火: {has_fire}")
            
            # Optimize database
            print("\n🔧 Optimizing database...")
            conn.execute("VACUUM")
            conn.execute("ANALYZE")
            
            # Check 人 radical after optimization
            print("\n🔍 Checking 人 radical AFTER optimization...")
            cursor.execute("SELECT radical, stroke_count, kanji_list FROM radical_kanji_mapping WHERE radical = '人'")
            after_result = cursor.fetchone()
            if after_result:
                radical, strokes, kanji_list = after_result
                kanji_count = len(kanji_list.split(', ')) if kanji_list else 0
                has_fire = '火' in kanji_list if kanji_list else False
                print(f"  🎯 AFTER VACUUM - 人 radical: {kanji_count} kanji, contains 火: {has_fire}")

            conn.close()

            # Get final size
            size_mb = os.path.getsize(self.output_path) / (1024 * 1024)
            print(f"📊 Final database size: {size_mb:.2f} MB")
            print(f"✅ Database built successfully: {self.output_path}")
            print(f"📅 Completed at: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")

            return True

        except Exception as e:
            print(f"❌ Database build failed: {e}")
            # print stack trace for debugging
            import traceback
            traceback.print_exc()
            return False

def main():
    """Main entry point"""
    import argparse

    parser = argparse.ArgumentParser(description="Build FTS5-enabled dictionary database")
    parser.add_argument("--jmdict", default="app/src/main/assets/jmdict.json",
                       help="Path to JMdict JSON file")
    parser.add_argument("--output", default="app/src/main/assets/databases/jmdict_fts5.db",
                       help="Output database path")
    parser.add_argument("--kanjidic", default="app/src/main/assets/kanjidic.json",
                       help="Path to KanjiDic JSON file")
    parser.add_argument("--jmnedict", default="app/src/main/assets/jmnedict.json",
                       help="Path to JMnedict JSON file")
    parser.add_argument("--kradfile", default="app/src/main/assets/kradfile.json",
                       help="Path to kradfile JSON file")
    parser.add_argument("--radkfile", default="app/src/main/assets/radkfile.json",
                       help="Path to radkfile JSON file")

    args = parser.parse_args()

    builder = DatabaseBuilder(args.output)
    success = builder.build_database(args.jmdict, args.kanjidic, args.jmnedict, args.kradfile, args.radkfile)

    if success:
        print("\n🎉 Database ready for Android deployment!")
        print(f"   Location: {args.output}")
        print("   Contains: JMdict entries + JMnedict names + KanjiDic kanji data")
        print("   The app will automatically detect and use the pre-built database.")
    else:
        print("\n❌ Database build failed!")
        sys.exit(1)

if __name__ == "__main__":
    main()