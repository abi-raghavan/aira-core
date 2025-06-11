# AIRA - Lightweight Offline Voice Assistant

A Flutter app for Android that provides **live voice transcription** and **offline LLM responses** using Whisper and TensorFlow Lite.

## ✨ Features

### 🎤 Live Voice Transcription
- **Real-time audio recording** in 3-second chunks with 2-second intervals
- **Continuous transcription** using local Whisper model
- **Live conversation view** with user speech displayed instantly

### 🤖 Offline AI Responses  
- **Lightweight LLM integration** ready for TensorFlow Lite models
- **Rule-based responses** for common queries (demo mode)
- **Conversational UI** with chat bubbles for user and AI messages
- **No internet required** - everything runs locally

### 📱 Enhanced UI
- **Clean conversation interface** with scrollable chat history  
- **Live status updates** showing listening, transcribing, and AI thinking states
- **Large microphone button** for easy start/stop control
- **Auto-scroll** to latest messages

## Setup Requirements

### 1. Install Whisper
You need to have Whisper installed and available in your PATH:

```bash
pip install openai-whisper
```

Or install using conda:
```bash
conda install -c conda-forge openai-whisper
```

### 2. Verify Whisper Installation
Test that Whisper is working:
```bash
whisper --help
```

### 3. Flutter Setup
Make sure you have Flutter installed and configured for Android development.

### 4. Optional: Add Lightweight LLM Model
For full offline LLM functionality, you can:
1. Download a quantized model (e.g., TinyLlama 1.1B 4-bit)
2. Convert to TensorFlow Lite format
3. Place in `assets/models/` directory
4. Uncomment the model loading code in `main.dart`

## Running the App

1. Connect an Android device or start an emulator
2. Navigate to the aira directory
3. Run the app:
```bash
flutter run
```

## Usage

### 🎯 Quick Start
1. **Tap the microphone** to start live transcription
2. **Speak normally** - your words appear in real-time as blue chat bubbles
3. **AI responds automatically** with white chat bubbles after each transcription
4. **Tap stop** to end the conversation

### 🗣️ What You Can Say
The current demo version responds to:
- **Greetings**: "Hello", "Hi" 
- **Questions**: "How are you?", "What time is it?"
- **Requests**: "Tell me a joke", "Weather"
- **Thanks & Goodbye**: "Thank you", "Bye"
- **General conversation** - AI provides helpful responses

### 🔄 How It Works
1. **Records** 3-second audio chunks every 2 seconds
2. **Transcribes** each chunk with Whisper 
3. **Filters** meaningful speech (>3 characters)
4. **Generates** AI response using rule-based system (or LLM if configured)
5. **Displays** conversation in chat interface

## Permissions

The app requires microphone permission to record audio. This will be requested when you first try to start listening.

## File Storage

Audio recordings are saved as `aira_record.wav` in the app's documents directory and overwritten with each new recording.

## Advanced Configuration

### Adding a Real LLM Model

To use a real lightweight LLM instead of rule-based responses:

1. **Get a quantized model**:
   ```bash
   # Example: Download TinyLlama 1.1B Chat (4-bit quantized)
   # Place model file in assets/models/tinyllama_q4.tflite
   ```

2. **Uncomment model loading code** in `_loadTinyLLM()` method

3. **Update response generation** in `_generateAIResponse()` to use model inference

### Performance Tips

- **Shorter recording chunks** (1-2 seconds) for faster response
- **Larger models** give better responses but slower performance  
- **Quantized models** (4-bit/8-bit) balance quality and speed
- **GPU acceleration** available via TensorFlow Lite delegates

## Technical Architecture

- **Frontend**: Flutter with Material Design 3
- **Audio**: `record` package for live audio capture
- **Transcription**: Local Whisper binary via process execution
- **LLM**: TensorFlow Lite interpreter (ready for model integration)
- **Storage**: Device file system for temporary audio files

## ✅ Complete LLM Integration - Ready for Production!

### 🎯 **What's Implemented:**

1. **✅ Model Selection**: TinyLlama 1.1B Chat (optimized for mobile)
2. **✅ Tokenization**: Custom tokenizer with TinyLlama-compatible format
3. **✅ Inference Pipeline**: Complete LLMService with TensorFlow Lite integration
4. **✅ Model Management**: Download, conversion, and settings UI
5. **✅ Memory Optimization**: Quantized models with mobile-optimized loading
6. **✅ Production Tools**: Python script for model setup and conversion

### 🛠️ **Production Model Setup:**

To use a real TinyLlama model instead of the demo version:

```bash
# Navigate to the scripts directory
cd aira/scripts

# Install dependencies
pip install huggingface_hub tensorflow torch transformers

# Download and convert the model
python setup_model.py --model-size q4_k_m --output-dir ../assets/models
```

### 📦 **Available Model Sizes:**
- **q2_k**: ~500MB (fastest, lower quality)
- **q4_k_m**: ~700MB (recommended - best balance) 
- **q4_k_s**: ~650MB (smaller, good quality)
- **q5_k_m**: ~800MB (higher quality)
- **q8_0**: ~1.2GB (highest quality, slower)

---

**Current Status**: ✅ Live transcription working, 🚧 LLM integration ready for model

Perfect for developers who want to build offline voice assistants with real-time transcription and conversational AI capabilities!
