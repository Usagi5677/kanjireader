#!/usr/bin/env python3
"""
Dictionary Update System - Main Orchestrator
Automates the complete dictionary update process
"""

import os
import sys
import subprocess
from pathlib import Path
from typing import Optional, Tuple
import shutil
import tempfile

# Add the current directory to Python path for importing our modules
sys.path.insert(0, str(Path(__file__).parent))

try:
    from download_latest import DictionaryDownloader
    from preserve_modifications import ModificationPreserver
except ImportError as e:
    print(f"Error importing required modules: {e}")
    print("Make sure download_latest.py and preserve_modifications.py are in the same directory")
    sys.exit(1)

class DictionaryUpdateOrchestrator:
    def __init__(self, project_root: str):
        self.project_root = Path(project_root)
        self.update_dir = self.project_root / "dictionary_updates"
        self.assets_dir = self.project_root / "app" / "src" / "main" / "assets"
        
        # Ensure directories exist
        self.update_dir.mkdir(exist_ok=True)
        
        # Initialize components
        self.downloader = DictionaryDownloader(str(self.update_dir), str(self.assets_dir))
        self.preserver = ModificationPreserver(str(self.assets_dir))
        
        # No longer need splitters for single file approach
    
    
    def run_python_script(self, script_path: Path, args: list = None) -> bool:
        """Run a Python script with arguments"""
        if not script_path.exists():
            print(f"Error: Script {script_path} not found")
            return False
        
        cmd = [sys.executable, str(script_path)]
        if args:
            cmd.extend(args)
        
        try:
            print(f"Running: {' '.join(cmd)}")
            result = subprocess.run(cmd, check=True, capture_output=True, text=True)
            print(result.stdout)
            if result.stderr:
                print(f"Warnings: {result.stderr}")
            return True
            
        except subprocess.CalledProcessError as e:
            print(f"Error running {script_path}: {e}")
            if e.stdout:
                print(f"Output: {e.stdout}")
            if e.stderr:
                print(f"Error output: {e.stderr}")
            return False
    
    
    
    
    def update_dictionaries(self, jmdict_only: bool = False, preserve_custom: bool = True, enhance_makemeahanzi: bool = True) -> bool:
        """
        Main update process - downloads directly to assets folder
        
        Args:
            jmdict_only: Only update JMdict, skip Kanjidic
            preserve_custom: Apply custom modifications after update
            enhance_makemeahanzi: Enhance radkfile with makemeahanzi decomposition data (default: True)
        """
        print("=== Dictionary Update System ===")
        print("Starting automated dictionary update process...")
        
        # Step 1: Download latest dictionaries 
        print("\n[1/4] Downloading, extracting, and renaming dictionaries...")
        jmdict_path, kanjidic_path, jmnedict_path, kradfile_path, radkfile_path = self.downloader.download_latest_dictionaries()
        
        if not jmdict_path:
            print("✗ Failed to download JMdict")
            return False
        
        if not jmdict_only and not kanjidic_path:
            print("✗ Failed to download Kanjidic")
            return False
        
        print(f"✓ Files renamed and moved to {self.assets_dir}")
        
        # Step 2: Create merged kradfile from Kradical + kensaku sources
        print("\n[2/4] Creating merged kradfile...")
        if not self.preserver.create_merged_kradfile(self.downloader):
            print("✗ Failed to create merged kradfile")
            return False
        print("✓ Merged kradfile created successfully")
        
        # Step 3: Apply custom modifications (if requested)
        if preserve_custom:
            print("\n[3/4] Applying custom modifications...")
            if not self.preserver.preserve_and_apply(enhance_with_makemeahanzi=enhance_makemeahanzi):
                print("✗ Failed to apply custom modifications")
                return False
        else:
            print("\n[3/4] Skipping custom modifications")
        
        # Step 4: Cleanup
        print("\n[4/4] Cleaning up temporary files...")
        try:
            # Remove downloaded ZIP files to save space (already done by downloader)
            print("  Temporary files cleaned up")
        except Exception as e:
            print(f"Warning: Cleanup error: {e}")
        
        print("\nDictionary update completed successfully!")
        
        # Show summary
        self.show_update_summary()
        
        return True
    
    def show_update_summary(self):
        """Show a summary of the update"""
        print("\n=== Update Summary ===")
        
        # Check file sizes for single JSON files
        assets_files = ["jmdict.json", "jmnedict.json", "kanjidic.json", "kradfile.json", "radkfile.json"]
        
        for filename in assets_files:
            filepath = self.assets_dir / filename
            if filepath.exists():
                size_mb = filepath.stat().st_size / (1024 * 1024)
                print(f"  {filename}: {size_mb:.1f} MB")
        
        # Check if modifications are present
        if self.preserver.verify_modifications():
            print("  Custom modifications: Applied")
        else:
            print("  Custom modifications: Not found")
        
        print("You can now rebuild the database using build_database.py")
    
    def quick_verify(self) -> bool:
        """Quick verification that the update was successful"""
        print("=== Quick Verification ===")
        
        # Check that main dictionary file exists
        jmdict_file = self.assets_dir / "jmdict.json"
        if not jmdict_file.exists():
            print("Missing: jmdict.json")
            return False
        
        # Check file is not empty
        if jmdict_file.stat().st_size < 1000:  # Less than 1KB is suspicious
            print("Suspicious file size: jmdict.json")
            return False
        
        # Check optional files
        optional_files = ["jmnedict.json", "kanjidic.json", "kradfile.json", "radkfile.json"]
        for filename in optional_files:
            filepath = self.assets_dir / filename
            if filepath.exists():
                print(f"Found: {filename}")
        
        # Verify custom modifications
        if not self.preserver.verify_modifications():
            print("Custom modifications verification failed")
            return False
        
        print("All checks passed")
        return True


def main():
    """Command line interface"""
    import argparse
    
    parser = argparse.ArgumentParser(description='Automated dictionary update system')
    parser.add_argument('--project-root', default='.',
                       help='Path to project root directory (default: current directory)')
    parser.add_argument('--jmdict-only', action='store_true',
                       help='Only update JMdict, skip Kanjidic')
    parser.add_argument('--no-custom', action='store_true',
                       help='Skip applying custom modifications')
    parser.add_argument('--no-makemeahanzi', action='store_true',
                       help='Skip makemeahanzi decomposition enhancement (enabled by default)')
    parser.add_argument('--verify-only', action='store_true',
                       help='Only run verification, do not update')
    
    args = parser.parse_args()
    
    # Verify project structure
    project_root = Path(args.project_root).resolve()
    assets_dir = project_root / "app" / "src" / "main" / "assets"
    
    if not assets_dir.exists():
        print(f"Error: Assets directory not found at {assets_dir}")
        print("Please run this script from the project root or specify --project-root")
        sys.exit(1)
    
    # Create orchestrator
    orchestrator = DictionaryUpdateOrchestrator(str(project_root))
    
    
    # Verify-only mode
    if args.verify_only:
        success = orchestrator.quick_verify()
        sys.exit(0 if success else 1)
    
    # Run the update
    success = orchestrator.update_dictionaries(
        jmdict_only=args.jmdict_only,
        preserve_custom=not args.no_custom,
        enhance_makemeahanzi=not args.no_makemeahanzi
    )
    
    sys.exit(0 if success else 1)


if __name__ == "__main__":
    main()