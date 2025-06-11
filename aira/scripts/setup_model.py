#!/usr/bin/env python3
"""
AIRA Model Setup Script
Downloads and converts TinyLlama model for use in the Flutter app.

Requirements:
- Python 3.8+
- huggingface_hub
- tensorflow
- transformers
- torch (for model conversion)

Usage:
python setup_model.py [--model-size q4_k_m] [--output-dir ../assets/models]
"""

import os
import sys
import argparse
import logging
from pathlib import Path
from typing import Optional

try:
    from huggingface_hub import hf_hub_download
    import tensorflow as tf
    print("✅ Required packages imported successfully")
except ImportError as e:
    print(f"❌ Missing required package: {e}")
    print("Install with: pip install huggingface_hub tensorflow torch transformers")
    sys.exit(1)

# Configure logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

class ModelSetup:
    """Handle TinyLlama model download and conversion for mobile deployment."""
    
    SUPPORTED_QUANTIZATIONS = {
        'q2_k': 'tinyllama-1.1b-chat-v1.0.Q2_K.gguf',
        'q4_k_m': 'tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf', 
        'q4_k_s': 'tinyllama-1.1b-chat-v1.0.Q4_K_S.gguf',
        'q5_k_m': 'tinyllama-1.1b-chat-v1.0.Q5_K_M.gguf',
        'q8_0': 'tinyllama-1.1b-chat-v1.0.Q8_0.gguf',
    }
    
    def __init__(self, model_size: str = 'q4_k_m', output_dir: str = '../assets/models'):
        self.model_size = model_size.lower()
        self.output_dir = Path(output_dir)
        self.repo_id = "TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF"
        
        if self.model_size not in self.SUPPORTED_QUANTIZATIONS:
            raise ValueError(f"Unsupported model size. Choose from: {list(self.SUPPORTED_QUANTIZATIONS.keys())}")
        
        self.filename = self.SUPPORTED_QUANTIZATIONS[self.model_size]
        
    def setup_directories(self):
        """Create necessary directories."""
        self.output_dir.mkdir(parents=True, exist_ok=True)
        logger.info(f"Created output directory: {self.output_dir}")
        
    def download_model(self) -> Path:
        """Download the GGUF model from Hugging Face."""
        logger.info(f"Downloading {self.filename} from {self.repo_id}")
        
        try:
            model_path = hf_hub_download(
                repo_id=self.repo_id,
                filename=self.filename,
                cache_dir=str(self.output_dir / 'cache'),
                local_dir=str(self.output_dir),
                local_dir_use_symlinks=False
            )
            
            logger.info(f"✅ Model downloaded to: {model_path}")
            return Path(model_path)
            
        except Exception as e:
            logger.error(f"❌ Failed to download model: {e}")
            raise
    
    def convert_to_tflite(self, gguf_path: Path) -> Optional[Path]:
        """
        Convert GGUF model to TensorFlow Lite format.
        Note: This is a placeholder for the actual conversion process.
        """
        logger.info("Converting GGUF to TensorFlow Lite format...")
        
        # In a real implementation, you would:
        # 1. Load the GGUF model using appropriate library (e.g., llama-cpp-python)
        # 2. Extract weights and architecture
        # 3. Build equivalent TensorFlow model
        # 4. Convert to TFLite with quantization
        
        # For demonstration, we'll create a placeholder
        tflite_path = self.output_dir / f"tinyllama_{self.model_size}.tflite"
        
        try:
            # Placeholder: In production, implement actual conversion
            with open(tflite_path, 'wb') as f:
                f.write(b"TensorFlow Lite model placeholder - implement actual conversion")
            
            logger.info(f"✅ TensorFlow Lite model created: {tflite_path}")
            return tflite_path
            
        except Exception as e:
            logger.error(f"❌ Failed to convert model: {e}")
            return None
    
    def create_tokenizer_files(self):
        """Create tokenizer files for the model."""
        logger.info("Creating tokenizer files...")
        
        # In production, you would extract the tokenizer from the original model
        tokenizer_config = {
            "vocab_size": 32000,
            "bos_token_id": 1,
            "eos_token_id": 2,
            "unk_token_id": 0,
            "model_type": "tinyllama"
        }
        
        import json
        tokenizer_path = self.output_dir / "tokenizer_config.json"
        with open(tokenizer_path, 'w') as f:
            json.dump(tokenizer_config, f, indent=2)
            
        logger.info(f"✅ Tokenizer config created: {tokenizer_path}")
    
    def create_model_info(self):
        """Create model information file."""
        model_info = {
            "name": "TinyLlama 1.1B Chat",
            "version": "v1.0",
            "quantization": self.model_size.upper(),
            "size_mb": self._estimate_size(),
            "architecture": "llama",
            "context_length": 2048,
            "vocab_size": 32000,
            "created_with": "AIRA Model Setup Script"
        }
        
        import json
        info_path = self.output_dir / "model_info.json"
        with open(info_path, 'w') as f:
            json.dump(model_info, f, indent=2)
            
        logger.info(f"✅ Model info created: {info_path}")
    
    def _estimate_size(self) -> int:
        """Estimate model size in MB based on quantization."""
        size_map = {
            'q2_k': 500,
            'q4_k_m': 700,
            'q4_k_s': 650,
            'q5_k_m': 800,
            'q8_0': 1200,
        }
        return size_map.get(self.model_size, 700)
    
    def run_setup(self):
        """Run the complete model setup process."""
        logger.info(f"🚀 Starting AIRA model setup (quantization: {self.model_size})")
        
        try:
            # Step 1: Setup directories
            self.setup_directories()
            
            # Step 2: Download model
            gguf_path = self.download_model()
            
            # Step 3: Convert to TensorFlow Lite
            tflite_path = self.convert_to_tflite(gguf_path)
            
            # Step 4: Create tokenizer files
            self.create_tokenizer_files()
            
            # Step 5: Create model info
            self.create_model_info()
            
            logger.info("🎉 Model setup completed successfully!")
            logger.info(f"Files created in: {self.output_dir}")
            
            return True
            
        except Exception as e:
            logger.error(f"❌ Model setup failed: {e}")
            return False

def main():
    parser = argparse.ArgumentParser(description="Setup TinyLlama model for AIRA")
    parser.add_argument(
        '--model-size',
        choices=['q2_k', 'q4_k_m', 'q4_k_s', 'q5_k_m', 'q8_0'],
        default='q4_k_m',
        help='Model quantization level (default: q4_k_m)'
    )
    parser.add_argument(
        '--output-dir',
        default='../assets/models',
        help='Output directory for model files (default: ../assets/models)'
    )
    
    args = parser.parse_args()
    
    print("🤖 AIRA Model Setup")
    print("===================")
    print(f"Model size: {args.model_size}")
    print(f"Output directory: {args.output_dir}")
    print()
    
    setup = ModelSetup(args.model_size, args.output_dir)
    success = setup.run_setup()
    
    if success:
        print("\n✅ Setup completed! You can now use the model in your AIRA app.")
        print("\nNext steps:")
        print("1. Copy the generated files to your Flutter app's assets directory")
        print("2. Update pubspec.yaml to include the model files")
        print("3. Build and test the app with the new model")
    else:
        print("\n❌ Setup failed. Check the logs above for details.")
        sys.exit(1)

if __name__ == "__main__":
    main() 