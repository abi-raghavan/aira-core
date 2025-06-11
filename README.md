# AIRA - Offline Voice Assistant

AIRA is a lightweight offline voice assistant for Android that provides real-time voice transcription and AI-powered responses using a local TinyLlama 1.1B model.

## Features

- **🎙️ Live Voice Recording**: Continuous 3-second audio chunks with 2-second intervals
- **📝 Real-time Transcription**: Offline speech-to-text using local Whisper model
- **🤖 AI Responses**: Lightweight TinyLlama 1.1B model for conversational AI
- **💬 Conversation UI**: Chat-like interface showing user speech and AI responses
- **⚙️ Model Management**: Built-in model download and management system
- **📱 Offline-First**: Works completely offline after initial model download
- **🔒 Privacy-Focused**: All processing happens locally on device

## Technical Stack

- **Framework**: Flutter 3.24.5
- **Language**: Dart
- **AI Model**: TinyLlama 1.1B Chat (Q4_K_M quantized)
- **Model Format**: GGUF (669MB) converted to TensorFlow Lite
- **Audio**: Native Android audio recording
- **Transcription**: Whisper via local inference
- **UI**: Material 3 design with conversation bubbles

## Project Structure

```
aira/
├── lib/
│   ├── main.dart                     # Main app with live transcription
│   ├── llm_service.dart             # TinyLlama integration & chat handling
│   ├── model_downloader.dart        # Model download & management
│   └── model_settings_screen.dart   # UI for model management
├── assets/
│   └── models/                      # AI models (gitignored)
│       ├── tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf
│       ├── tokenizer_config.json
│       └── model_info.json
├── android/
│   └── app/
│       ├── build.gradle.kts         # Android build config
│       └── proguard-rules.pro       # ProGuard rules for TensorFlow Lite
└── scripts/
    └── setup_model.py               # Python script for model setup
```

## Installation

### Prerequisites

- Flutter SDK 3.24.5+
- Android SDK 21+ (Android 5.0+)
- Python 3.9+ (for model setup)
- 2GB+ free storage space

### Setup Instructions

1. **Clone the repository**
   ```bash
   git clone https://github.com/yourusername/aira-core.git
   cd aira-core/aira
   ```

2. **Install Flutter dependencies**
   ```bash
   flutter pub get
   ```

3. **Download AI models** (requires internet)
   ```bash
   cd ../
   /usr/bin/python3 -m pip install huggingface_hub tensorflow
   /usr/bin/python3 scripts/setup_model.py
   ```

4. **Build for Android**
   ```bash
   cd aira
   flutter build apk --release
   ```

5. **Install APK**
   - Transfer `build/app/outputs/flutter-apk/app-release.apk` to your Android device
   - Enable "Install from unknown sources" in Android settings
   - Install the APK

## Dependencies

### Flutter Packages
```yaml
dependencies:
  record: ^5.1.2              # Audio recording
  path_provider: ^2.1.4       # File system access
  permission_handler: ^11.3.1 # Runtime permissions
  process: ^5.0.3             # Process management
  tflite_flutter: ^0.11.0     # TensorFlow Lite inference
  http: ^1.2.2                # HTTP requests for model download
```

### Python Dependencies (for model setup)
```bash
huggingface_hub>=0.19.0
tensorflow>=2.13.0
```

## Usage

1. **First Launch**: 
   - Grant microphone permissions
   - The app will check for downloaded models

2. **Model Management**:
   - Tap the settings icon to access model management
   - Download models if not already present
   - View model status and info

3. **Voice Interaction**:
   - Tap "Start Listening" to begin voice recording
   - Speak naturally - your words will appear in real-time
   - AIRA will respond with AI-generated text
   - Tap "Stop Listening" to pause

## Model Information

- **Model**: TinyLlama-1.1B-Chat-v1.0
- **Size**: 669MB (Q4_K_M quantized)
- **Parameters**: 1.1 billion
- **Quantization**: 4-bit for mobile optimization
- **Performance**: ~2-3 tokens/second on modern Android devices
- **Memory Usage**: ~1.5GB RAM during inference

## Build Configuration

### Release Build Settings
- **Minification**: Disabled (to avoid TensorFlow Lite issues)
- **Obfuscation**: Disabled
- **Shrinking**: Disabled
- **APK Size**: ~700MB (includes model)

### ProGuard Rules
Custom rules added for TensorFlow Lite compatibility:
```proguard
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.gpu.** { *; }
```

## Performance

### Device Requirements
- **RAM**: 3GB+ recommended
- **Storage**: 2GB+ free space
- **CPU**: ARM64 preferred
- **Android**: 5.0+ (API 21+)

### Expected Performance
- **Cold Start**: 5-10 seconds (model loading)
- **Response Time**: 2-5 seconds per response
- **Audio Processing**: Real-time (3s chunks)
- **Battery Impact**: Moderate during active use

## Troubleshooting

### Common Issues

1. **"No module named 'huggingface_hub'"**
   ```bash
   /usr/bin/python3 -m pip install huggingface_hub tensorflow
   ```

2. **APK build fails with R8 errors**
   - Ensure `isMinifyEnabled = false` in `build.gradle.kts`
   - Check ProGuard rules are applied

3. **App crashes on model inference**
   - Verify model files are downloaded correctly
   - Check device has sufficient RAM (3GB+)
   - Ensure TensorFlow Lite version compatibility

4. **Microphone permissions denied**
   - Go to Android Settings > Apps > AIRA > Permissions
   - Enable Microphone permission

### Debug Mode
```bash
flutter run --debug  # For development
flutter logs         # View runtime logs
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test on physical Android device
5. Submit a pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgments

- **TinyLlama**: Microsoft's efficient 1.1B parameter language model
- **Hugging Face**: Model hosting and transformers library
- **Flutter Team**: Cross-platform framework
- **TensorFlow Lite**: On-device ML inference

## Roadmap

- [ ] iOS support
- [ ] Multiple model options (Phi-3, Llama-3.2)
- [ ] Voice response synthesis (TTS)
- [ ] Custom wake word detection
- [ ] Conversation memory and context
- [ ] Plugin system for extensions

---

**Note**: This is an offline-first application. After initial setup, AIRA works completely without internet connectivity, ensuring privacy and reliability. 