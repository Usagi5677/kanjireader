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
    
    def find_target_assets(self, release_data: Dict) -> Tuple[Optional[str], Optional[str], Optional[str], Optional[str], Optional[str]]:
        """Find the jmdict-eng, kanjidic2-en, jmnedict, kradfile, and radkfile ZIP file URLs"""
        jmdict_url = None
        kanjidic_url = None
        jmnedict_url = None
        kradfile_url = None
        radkfile_url = None
        
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
            
            # Look for kradfile ZIP files
            elif 'kradfile' in name and name.endswith('.zip'):
                kradfile_url = download_url
                print(f"Found Kradfile file: {name}")
            
            # Look for radkfile ZIP files
            elif 'radkfile' in name and name.endswith('.zip'):
                radkfile_url = download_url
                print(f"Found Radkfile file: {name}")
        
        if not jmdict_url:
            print("Warning: No jmdict-eng ZIP file found")
        if not kanjidic_url:
            print("Warning: No kanjidic2-en ZIP file found")
        if not jmnedict_url:
            print("Warning: No jmnedict ZIP file found")
        if not kradfile_url:
            print("Warning: No kradfile ZIP file found")
        if not radkfile_url:
            print("Warning: No radkfile ZIP file found")
            
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
        
        if kradfile_url:
            kradfile_filename = kradfile_url.split('/')[-1]
            if self.download_file(kradfile_url, kradfile_filename):
                kradfile_zip_path = self.downloads_dir / kradfile_filename
                kradfile_path = self.extract_zip(kradfile_zip_path)
        
        if radkfile_url:
            radkfile_filename = radkfile_url.split('/')[-1]
            if self.download_file(radkfile_url, radkfile_filename):
                radkfile_zip_path = self.downloads_dir / radkfile_filename
                radkfile_path = self.extract_zip(radkfile_zip_path)
        
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
            print(f"Kradfile: {kradfile_path}")
        else:
            print("Kradfile: Failed to download/extract")
        
        if radkfile_path:
            print(f"Radkfile: {radkfile_path}")
        else:
            print("Radkfile: Failed to download/extract")
        
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