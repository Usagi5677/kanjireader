#!/usr/bin/env python3
"""
Dictionary Update System - GitHub Release Downloader
Downloads the latest jmdict-eng and kanjidic2-en files from jmdict-simplified repository
"""

import requests
import os
import zipfile
import json
from pathlib import Path
from typing import Dict, Optional, Tuple
import sys

class DictionaryDownloader:
    def __init__(self, base_dir: str = ".", assets_dir: str = None):
        self.base_dir = Path(base_dir)
        self.downloads_dir = self.base_dir / "downloads"
        
        # Use provided assets directory or default to project structure
        if assets_dir:
            self.assets_dir = Path(assets_dir)
        else:
            # Assume we're in dictionary_updates folder, go up to project root
            project_root = self.base_dir.parent if self.base_dir.name == "dictionary_updates" else self.base_dir
            self.assets_dir = project_root / "app" / "src" / "main" / "assets"
        
        # Ensure directories exist
        self.downloads_dir.mkdir(exist_ok=True)
        self.assets_dir.mkdir(parents=True, exist_ok=True)
        
        # GitHub API endpoint
        self.api_url = "https://api.github.com/repos/scriptin/jmdict-simplified/releases/latest"
        
        # Direct URL for complete radkfile from Kradical repository
        self.kradical_radkfile_url = "https://raw.githubusercontent.com/tim-harding/Kradical/master/assets/outputs/radk.json"
        
        # Direct URL for kradfile with proper Unicode radicals from Kradical repository
        self.kradical_kradfile_url = "https://raw.githubusercontent.com/tim-harding/Kradical/master/assets/outputs/krad.json"
        
        # Backup URL for additional kanji coverage from kensaku repository
        self.kensaku_kradfile_url = "https://raw.githubusercontent.com/jmettraux/kensaku/master/data/kradfile-u"
    
    def get_latest_release_info(self) -> Optional[Dict]:
        """Get information about the latest release from GitHub API"""
        try:
            print("Fetching latest release information...")
            response = requests.get(self.api_url, timeout=30)
            response.raise_for_status()
            
            release_data = response.json()
            print(f"Latest release: {release_data['tag_name']}")
            print(f"Published: {release_data['published_at']}")
            
            return release_data
            
        except requests.RequestException as e:
            print(f"Error fetching release info: {e}")
            return None
        except json.JSONDecodeError as e:
            print(f"Error parsing release JSON: {e}")
            return None
    
    def download_kradical_radkfile(self) -> Optional[Path]:
        """Download radkfile directly from Kradical repository and convert format"""
        try:
            print("Downloading complete radkfile from Kradical repository...")
            response = requests.get(self.kradical_radkfile_url, timeout=30)
            response.raise_for_status()
            
            # Parse the Kradical format (array of objects)
            kradical_data = response.json()
            if not isinstance(kradical_data, list):
                print("❌ Unexpected Kradical radkfile format")
                return None
            
            print(f"   Downloaded {len(kradical_data)} radical entries")
            
            # Convert to our expected format using the same logic as the provided code
            converted_data = self.convert_radicals_to_new_format(kradical_data, "converted-from-kradical")
            
            # Save converted data to assets directory as radkfile.json
            radkfile_path = self.assets_dir / "radkfile.json"
            with open(radkfile_path, 'w', encoding='utf-8') as f:
                json.dump(converted_data, f, ensure_ascii=False, separators=(',', ':'))
            
            print(f"✅ Downloaded and converted complete radkfile to {radkfile_path}")
            print(f"   Found {len(converted_data['radicals'])} radicals")
            
            # Check for 17-stroke radical
            stroke_17_radicals = []
            for radical, info in converted_data["radicals"].items():
                if info["strokeCount"] == 17:
                    stroke_17_radicals.append(radical)
            
            if stroke_17_radicals:
                print(f"   ✅ Includes 17-stroke radicals: {stroke_17_radicals}")
            
            return radkfile_path
            
        except requests.RequestException as e:
            print(f"❌ Error downloading radkfile from Kradical: {e}")
            return None
        except json.JSONDecodeError as e:
            print(f"❌ Error parsing radkfile JSON: {e}")
            return None
        except Exception as e:
            print(f"❌ Error converting/saving radkfile: {e}")
            return None
    
    def download_kradical_kradfile(self) -> Optional[Path]:
        """Download kradfile with proper Unicode radicals from Kradical repository"""
        try:
            print("Downloading kradfile from Kradical repository (proper Unicode radicals)...")
            response = requests.get(self.kradical_kradfile_url, timeout=30)
            response.raise_for_status()
            
            # Parse the JSON data directly
            kradfile_data = response.json()
            
            # Convert from Kradical's array format to our expected format
            converted_data = self.convert_kradfile_to_expected_format(kradfile_data)
            
            # Save converted data to assets directory as kradfile.json
            kradfile_path = self.assets_dir / "kradfile.json"
            with open(kradfile_path, 'w', encoding='utf-8') as f:
                json.dump(converted_data, f, ensure_ascii=False, indent=2)
            
            print(f"✅ Downloaded and converted Kradical kradfile to {kradfile_path}")
            print(f"   Found {len(converted_data['kanji'])} kanji entries")
            
            # Show some examples
            example_count = 0
            for kanji, radicals in converted_data['kanji'].items():
                if example_count < 5:
                    print(f"   Example: {kanji} → {radicals}")
                    example_count += 1
                else:
                    break
            
            return kradfile_path
            
        except requests.RequestException as e:
            print(f"❌ Error downloading kradfile from Kradical: {e}")
            return None
        except Exception as e:
            print(f"❌ Error converting/saving kradfile: {e}")
            return None

    def download_kensaku_kradfile_for_merging(self) -> Optional[dict]:
        """Download comprehensive kradfile-u from kensaku repository for merging additional kanji"""
        try:
            print("Downloading additional kanji from kensaku repository...")
            response = requests.get(self.kensaku_kradfile_url, timeout=30)
            response.raise_for_status()
            
            # Parse the kradfile-u format (text format, not JSON)
            kradfile_content = response.text
            
            # Convert to our expected format but return data instead of saving
            converted_data = self.parse_kradfile_u(kradfile_content)
            
            print(f"✅ Downloaded kensaku kradfile-u for merging")
            print(f"   Found {len(converted_data['kanji'])} additional kanji entries")
            
            return converted_data
            
        except requests.RequestException as e:
            print(f"❌ Error downloading kradfile from kensaku: {e}")
            return None
        except Exception as e:
            print(f"❌ Error parsing kensaku kradfile: {e}")
            return None
    
    def parse_kradfile_u(self, content: str) -> dict:
        """
        Parse kradfile-u format and convert to JSON format.
        
        Format expected in kradfile-u:
        - Lines starting with '#' are comments
        - After "###########################################################" line
        - Format: kanji : radical1 radical2 radical3
        - Example: 㐂 : 匕
        """
        kanji_radicals = {}
        parsing_data = False
        
        lines = content.split('\n')
        for line in lines:
            line = line.strip()
            
            # Skip empty lines
            if not line:
                continue
            
            # Check for the separator line to start parsing
            if line.startswith('###########'):
                parsing_data = True
                continue
            
            # Skip comments and header lines
            if line.startswith('#') or not parsing_data:
                continue
            
            # Parse kanji:radical format
            if ':' in line:
                try:
                    parts = line.split(':', 1)
                    if len(parts) == 2:
                        kanji = parts[0].strip()
                        radicals_str = parts[1].strip()
                        
                        # Split radicals by spaces and filter empty strings
                        radicals = [r.strip() for r in radicals_str.split() if r.strip()]
                        
                        if kanji and radicals:
                            kanji_radicals[kanji] = radicals
                            
                except Exception:
                    continue
        
        return {
            "version": "converted-from-kradfile-u",
            "kanji": kanji_radicals
        }
    
    def convert_kradfile_to_expected_format(self, input_data: list, version: str = "converted-from-kradical") -> dict:
        """
        Converts Kradical krad.json format to expected kradfile.json format.
        
        Args:
            input_data (list): A list of dictionaries with 'kanji' and 'radicals' keys
            version (str): The version string to be included in the output
        
        Returns:
            dict: A dictionary in the expected format with 'version' and 'kanji' keys
        """
        converted_format = {
            "version": version,
            "kanji": {}
        }
        
        for entry in input_data:
            kanji_char = entry.get("kanji")
            radicals_list = entry.get("radicals", [])
            
            if kanji_char and radicals_list:
                converted_format["kanji"][kanji_char] = radicals_list
        
        return converted_format
    
    def convert_radicals_to_new_format(self, input_data: list, version: str = "3.6.1") -> dict:
        """
        Converts a list of radical dictionaries into a new, nested dictionary format.
        Based on the provided conversion code.
        
        Args:
            input_data (list): A list of dictionaries, where each dictionary
                               represents a radical with keys 'radical', 'stroke', and 'kanji'.
            version (str): The version string to be included in the output.
        
        Returns:
            dict: A dictionary in the new format with 'version' and 'radicals' keys.
        """
        new_format = {
            "version": version,
            "radicals": {}
        }
        
        for radical_entry in input_data:
            radical_char = radical_entry.get("radical")
            stroke_count = radical_entry.get("stroke")
            kanji_list = radical_entry.get("kanji", [])
            
            if radical_char:
                # The 'code' field is set to null as per the desired output format
                new_format["radicals"][radical_char] = {
                    "strokeCount": stroke_count,
                    "code": None,
                    "kanji": kanji_list
                }
        
        return new_format
    
    def find_target_assets(self, release_data: Dict) -> Tuple[Optional[str], Optional[str], Optional[str], Optional[str], Optional[str]]:
        """Find the jmdict-eng, kanjidic2-en, jmnedict ZIP file URLs (kradfile from kensaku, radkfile from Kradical)"""
        jmdict_url = None
        kanjidic_url = None
        jmnedict_url = None
        kradfile_url = None  # Will be handled separately from kensaku repo
        radkfile_url = None  # Will be handled separately from Kradical repo
        
        assets = release_data.get('assets', [])
        print(f"Found {len(assets)} assets in release")
        
        for asset in assets:
            name = asset['name']
            download_url = asset['browser_download_url']
            
            # Look for jmdict-eng ZIP files (prefer full version over common)
            if 'jmdict-eng' in name and name.endswith('.zip'):
                # Prefer full version over common version
                if 'common' not in name:
                    jmdict_url = download_url
                    print(f"Found JMdict file (full): {name}")
                elif jmdict_url is None:  # Only use common if no full version found
                    jmdict_url = download_url
                    print(f"Found JMdict file (common): {name}")
            
            # Look for kanjidic2-en ZIP files  
            elif 'kanjidic2-en' in name and name.endswith('.zip'):
                kanjidic_url = download_url
                print(f"Found Kanjidic file: {name}")
            
            # Look for jmnedict ZIP files
            elif 'jmnedict' in name and name.endswith('.zip'):
                jmnedict_url = download_url
                print(f"Found JMnedict file: {name}")
            
            # Skip kradfile - will be downloaded separately from kensaku repository
            elif 'kradfile' in name and name.endswith('.zip'):
                print(f"Skipping Kradfile from jmdict-simplified (using kensaku instead): {name}")
            
            # Note: kradfile is downloaded from kensaku, radkfile from Kradical
        
        if not jmdict_url:
            print("Warning: No jmdict-eng ZIP file found")
        if not kanjidic_url:
            print("Warning: No kanjidic2-en ZIP file found")
        if not jmnedict_url:
            print("Warning: No jmnedict ZIP file found")
        # Note: kradfile is downloaded from kensaku, radkfile from Kradical
            
        return jmdict_url, kanjidic_url, jmnedict_url, kradfile_url, radkfile_url
    
    def download_file(self, url: str, filename: str) -> bool:
        """Download a file from URL to downloads directory"""
        if not url:
            return False
            
        file_path = self.downloads_dir / filename
        
        try:
            print(f"Downloading {filename}...")
            
            # Stream download for large files
            response = requests.get(url, stream=True, timeout=60)
            response.raise_for_status()
            
            total_size = int(response.headers.get('content-length', 0))
            downloaded = 0
            
            with open(file_path, 'wb') as f:
                for chunk in response.iter_content(chunk_size=8192):
                    if chunk:
                        f.write(chunk)
                        downloaded += len(chunk)
                        
                        # Progress indicator
                        if total_size > 0:
                            percent = (downloaded / total_size) * 100
                            print(f"\rProgress: {percent:.1f}%", end='', flush=True)
            
            print(f"\nDownloaded: {file_path}")
            return True
            
        except requests.RequestException as e:
            print(f"\nError downloading {filename}: {e}")
            return False
        except IOError as e:
            print(f"\nError saving {filename}: {e}")
            return False
    
    def extract_zip(self, zip_path: Path) -> Optional[Path]:
        """Extract ZIP file to downloads folder, rename properly, then move to assets"""
        try:
            print(f"Extracting {zip_path.name}...")
            
            with zipfile.ZipFile(zip_path, 'r') as zip_ref:
                # List contents
                file_list = zip_ref.namelist()
                json_files = [f for f in file_list if f.endswith('.json')]
                
                if not json_files:
                    print(f"No JSON files found in {zip_path.name}")
                    return None
                
                # Extract the JSON file(s) to downloads folder first
                for json_file in json_files:
                    # Get just the filename without any path
                    original_filename = Path(json_file).name
                    temp_extracted_path = self.downloads_dir / original_filename
                    
                    with zip_ref.open(json_file) as source, open(temp_extracted_path, 'wb') as target:
                        target.write(source.read())
                    
                    print(f"Extracted to downloads: {temp_extracted_path}")
                    
                    # Determine the proper filename for assets
                    proper_filename = self.get_proper_filename(original_filename)
                    final_path = self.assets_dir / proper_filename
                    
                    # Move and rename to assets directory (replacing if exists)
                    if final_path.exists():
                        print(f"Replacing existing {proper_filename}")
                        final_path.unlink()  # Remove existing file first (Windows requirement)
                    
                    temp_extracted_path.rename(final_path)
                    print(f"Moved to assets as: {final_path}")
                    
                    # Return the final path
                    return final_path
                    
        except zipfile.BadZipFile as e:
            print(f"Error: {zip_path.name} is not a valid ZIP file: {e}")
            return None
        except IOError as e:
            print(f"Error extracting {zip_path.name}: {e}")
            return None
    
    def get_proper_filename(self, original_filename: str) -> str:
        """Convert downloaded filename to proper assets filename"""
        filename_lower = original_filename.lower()
        
        if 'jmdict-eng' in filename_lower and filename_lower.endswith('.json'):
            return 'jmdict.json'
        elif 'kanjidic2-en' in filename_lower and filename_lower.endswith('.json'):
            return 'kanjidic.json'
        elif 'jmnedict' in filename_lower and filename_lower.endswith('.json'):
            return 'jmnedict.json'
        elif 'kradfile' in filename_lower and filename_lower.endswith('.json'):
            return 'kradfile.json'
        elif 'radkfile' in filename_lower and filename_lower.endswith('.json'):
            return 'radkfile.json'
        else:
            # Fallback: return original filename
            print(f"Warning: Unrecognized filename pattern: {original_filename}")
            return original_filename
    
    def cleanup_downloads(self):
        """Remove downloaded ZIP and JSON files to save space"""
        try:
            # Clean up ZIP files
            for zip_file in self.downloads_dir.glob("*.zip"):
                zip_file.unlink()
                print(f"Cleaned up: {zip_file.name}")
            
            # Clean up any remaining JSON files in downloads
            for json_file in self.downloads_dir.glob("*.json"):
                json_file.unlink()
                print(f"Cleaned up: {json_file.name}")
        except OSError as e:
            print(f"Warning: Could not clean up downloads: {e}")
    
    def download_latest_dictionaries(self, cleanup: bool = True) -> Tuple[Optional[Path], Optional[Path], Optional[Path], Optional[Path], Optional[Path]]:
        """
        Main method to download and extract the latest dictionaries
        Returns: (jmdict_json_path, kanjidic_json_path, jmnedict_json_path, kradfile_json_path, radkfile_json_path)
        """
        print("=== Dictionary Update System ===")
        print("Downloading latest dictionaries from jmdict-simplified...")
        
        # Get release information
        release_data = self.get_latest_release_info()
        if not release_data:
            return None, None, None, None, None
        
        # Find target files
        jmdict_url, kanjidic_url, jmnedict_url, kradfile_url, radkfile_url = self.find_target_assets(release_data)
        
        # Download files
        jmdict_path = None
        kanjidic_path = None
        jmnedict_path = None
        kradfile_path = None
        radkfile_path = None
        
        if jmdict_url:
            jmdict_filename = jmdict_url.split('/')[-1]
            if self.download_file(jmdict_url, jmdict_filename):
                jmdict_zip_path = self.downloads_dir / jmdict_filename
                jmdict_path = self.extract_zip(jmdict_zip_path)
        
        if kanjidic_url:
            kanjidic_filename = kanjidic_url.split('/')[-1]
            if self.download_file(kanjidic_url, kanjidic_filename):
                kanjidic_zip_path = self.downloads_dir / kanjidic_filename
                kanjidic_path = self.extract_zip(kanjidic_zip_path)
        
        if jmnedict_url:
            jmnedict_filename = jmnedict_url.split('/')[-1]
            if self.download_file(jmnedict_url, jmnedict_filename):
                jmnedict_zip_path = self.downloads_dir / jmnedict_filename
                jmnedict_path = self.extract_zip(jmnedict_zip_path)
        
        # Download kradfile with proper Unicode radicals from Kradical repository
        kradfile_path = self.download_kradical_kradfile()
        
        # Download complete radkfile from Kradical repository
        radkfile_path = self.download_kradical_radkfile()
        
        # Cleanup if requested
        if cleanup:
            self.cleanup_downloads()
        
        # Summary
        print("\n=== Download Summary ===")
        print(f"Files renamed and moved to: {self.assets_dir}")
        if jmdict_path:
            print(f"JMdict: {jmdict_path}")
        else:
            print("JMdict: Failed to download/extract")
            
        if kanjidic_path:
            print(f"Kanjidic: {kanjidic_path}")
        else:
            print("Kanjidic: Failed to download/extract")
        
        if jmnedict_path:
            print(f"JMnedict: {jmnedict_path}")
        else:
            print("JMnedict: Failed to download/extract")
        
        if kradfile_path:
            print(f"Kradfile (from Kradical): {kradfile_path}")
        else:
            print("Kradfile (from Kradical): Failed to download")
        
        if radkfile_path:
            print(f"Radkfile (from Kradical): {radkfile_path}")
        else:
            print("Radkfile (from Kradical): Failed to download")
        
        return jmdict_path, kanjidic_path, jmnedict_path, kradfile_path, radkfile_path


def main():
    """Command line interface"""
    import argparse
    
    parser = argparse.ArgumentParser(description='Download latest dictionaries from jmdict-simplified')
    parser.add_argument('--no-cleanup', action='store_true', 
                       help='Keep downloaded ZIP files after extraction')
    parser.add_argument('--dir', default='.', 
                       help='Base directory for downloads (default: current directory)')
    parser.add_argument('--assets-dir', 
                       help='Assets directory to extract files to (default: auto-detect from project structure)')
    
    args = parser.parse_args()
    
    downloader = DictionaryDownloader(args.dir, args.assets_dir)
    jmdict_path, kanjidic_path, jmnedict_path, kradfile_path, radkfile_path = downloader.download_latest_dictionaries(
        cleanup=not args.no_cleanup
    )
    
    # Exit with appropriate code
    downloaded_count = sum(1 for path in [jmdict_path, kanjidic_path, jmnedict_path, kradfile_path, radkfile_path] if path)
    
    if downloaded_count == 5:
        print("\n✓ All dictionaries downloaded successfully!")
        sys.exit(0)
    elif downloaded_count >= 3:
        print("\n⚠ Partial success - most dictionaries downloaded")
        sys.exit(1)
    elif downloaded_count >= 1:
        print("\n⚠ Limited success - some dictionaries downloaded")
        sys.exit(1)
    else:
        print("\n✗ Failed to download dictionaries")
        sys.exit(2)


if __name__ == "__main__":
    main()