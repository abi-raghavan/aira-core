#!/bin/bash

# AIRA Setup Script
# Automates the setup process for the AIRA offline voice assistant

set -e

echo "🤖 AIRA - Offline Voice Assistant Setup"
echo "======================================"

# Check if we're in the right directory
if [ ! -d "aira" ]; then
    echo "❌ Error: Please run this script from the aira-core root directory"
    exit 1
fi

echo "📁 Changing to aira directory..."
cd aira

echo "📦 Installing Flutter dependencies..."
flutter pub get

echo "🐍 Setting up Python environment for model download..."
echo "Using system Python 3..."

# Check if Python 3 is available
if ! command -v /usr/bin/python3 &> /dev/null; then
    echo "❌ Error: Python 3 not found at /usr/bin/python3"
    echo "Please install Python 3.9+ or update the script with your Python path"
    exit 1
fi

echo "📥 Installing Python dependencies..."
/usr/bin/python3 -m pip install --user huggingface_hub tensorflow

echo "🤖 Downloading TinyLlama model (669MB)..."
echo "This may take several minutes depending on your internet connection..."

if [ -f "scripts/setup_model.py" ]; then
    /usr/bin/python3 scripts/setup_model.py
else
    echo "❌ Error: Model setup script not found"
    exit 1
fi

echo "🔧 Checking model files..."
if [ -f "assets/models/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf" ]; then
    echo "✅ Model files downloaded successfully"
else
    echo "❌ Error: Model download failed"
    exit 1
fi

echo "🔨 Building release APK..."
flutter build apk --release

if [ -f "build/app/outputs/flutter-apk/app-release.apk" ]; then
    APK_SIZE=$(du -h build/app/outputs/flutter-apk/app-release.apk | cut -f1)
    echo "✅ APK built successfully!"
    echo "📱 APK location: build/app/outputs/flutter-apk/app-release.apk"
    echo "📊 APK size: $APK_SIZE"
    echo ""
    echo "🎉 Setup complete!"
    echo ""
    echo "Next steps:"
    echo "1. Transfer the APK to your Android device"
    echo "2. Enable 'Install from unknown sources' in Android settings"
    echo "3. Install the APK"
    echo "4. Grant microphone permissions when prompted"
    echo "5. Start using AIRA!"
else
    echo "❌ Error: APK build failed"
    exit 1
fi 