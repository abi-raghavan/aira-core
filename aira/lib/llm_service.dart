import 'dart:io';
import 'dart:typed_data';
import 'package:tflite_flutter/tflite_flutter.dart';
import 'package:path_provider/path_provider.dart';

class LLMService {
  Interpreter? _interpreter;
  bool _isModelLoaded = false;
  Map<String, int>? _tokenToId;
  Map<int, String>? _idToToken;
  
  // TinyLlama specific constants
  static const int maxContextLength = 2048;
  static const int vocabSize = 32000;
  static const String modelUrl = 'https://huggingface.co/Mozilla/TinyLlama-1.1B-Chat-v1.0-llamafile/resolve/main/TinyLlama-1.1B-Chat-v1.0.Q4_K_M.llamafile';
  
  // Special tokens
  static const int bosTokenId = 1;
  static const int eosTokenId = 2;
  static const int unkTokenId = 0;
  
  // Chat template tokens for TinyLlama
  static const String systemStart = '<|im_start|>system\n';
  static const String systemEnd = '<|im_end|>\n';
  static const String userStart = '<|im_start|>user\n';
  static const String userEnd = '<|im_end|>\n';
  static const String assistantStart = '<|im_start|>assistant\n';
  static const String assistantEnd = '<|im_end|>\n';

  bool get isModelLoaded => _isModelLoaded;

  Future<void> initialize() async {
    try {
      await _loadModel();
      await _loadTokenizer();
      _isModelLoaded = true;
    } catch (e) {
      throw Exception('Failed to initialize LLM: $e');
    }
  }

  Future<void> _loadModel() async {
    try {
      // Check if model exists locally
      final directory = await getApplicationDocumentsDirectory();
      final modelPath = '${directory.path}/tinyllama_q4.tflite';
      final modelFile = File(modelPath);
      
      if (!modelFile.existsSync()) {
        // For demo purposes, we'll simulate having the model
        // In a real implementation, you would:
        // 1. Download the model from Hugging Face
        // 2. Convert from GGUF to TensorFlow Lite format
        // 3. Save it locally
        
        // Model not found. In production, download and convert model here.
        // await _downloadAndConvertModel(modelPath);
      } else {
        // Load the TensorFlow Lite model
        _interpreter = Interpreter.fromFile(modelFile);
        _interpreter!.allocateTensors();
      }
    } catch (e) {
      throw Exception('Failed to load model: $e');
    }
  }

  Future<void> _loadTokenizer() async {
    // Simplified tokenizer for demo
    // In a real implementation, you would load the actual TinyLlama tokenizer
    _tokenToId = _createSimpleTokenizer();
    _idToToken = _tokenToId!.map((key, value) => MapEntry(value, key));
  }

  Map<String, int> _createSimpleTokenizer() {
    // Simplified tokenizer - in reality you'd load from model files
    final tokenizer = <String, int>{};
    
    // Special tokens
    tokenizer['<unk>'] = unkTokenId;
    tokenizer['<s>'] = bosTokenId;
    tokenizer['</s>'] = eosTokenId;
    
    // Common words and characters
    final commonTokens = [
      ' ', '.', ',', '!', '?', '\n', 'the', 'and', 'to', 'a', 'of', 'is', 'it',
      'you', 'that', 'he', 'was', 'for', 'on', 'are', 'as', 'with', 'his',
      'they', 'at', 'be', 'this', 'have', 'from', 'or', 'one', 'had', 'by',
      'word', 'but', 'not', 'what', 'all', 'were', 'we', 'when', 'your', 'can',
      'said', 'there', 'each', 'which', 'she', 'do', 'how', 'their', 'if',
      'will', 'up', 'other', 'about', 'out', 'many', 'then', 'them', 'these',
      'so', 'some', 'her', 'would', 'make', 'like', 'into', 'him', 'time',
      'has', 'two', 'more', 'very', 'after', 'words', 'first', 'where', 'much',
      'way', 'been', 'who', 'its', 'now', 'find', 'long', 'down', 'day', 'did',
      'get', 'come', 'made', 'may', 'part'
    ];
    
    for (int i = 0; i < commonTokens.length; i++) {
      tokenizer[commonTokens[i]] = i + 3; // Start after special tokens
    }
    
    // Add alphabet
    for (int i = 0; i < 26; i++) {
      tokenizer[String.fromCharCode(97 + i)] = commonTokens.length + 3 + i; // a-z
      tokenizer[String.fromCharCode(65 + i)] = commonTokens.length + 3 + 26 + i; // A-Z
    }
    
    return tokenizer;
  }

  List<int> tokenize(String text) {
    if (_tokenToId == null) return [];
    
    final tokens = <int>[];
    tokens.add(bosTokenId); // Start token
    
    // Simple word-based tokenization
    final words = text.toLowerCase().split(RegExp(r'\s+'));
    
    for (final word in words) {
      if (_tokenToId!.containsKey(word)) {
        tokens.add(_tokenToId![word]!);
      } else {
        // Character-level fallback
        for (int i = 0; i < word.length; i++) {
          final char = word[i];
          if (_tokenToId!.containsKey(char)) {
            tokens.add(_tokenToId![char]!);
          } else {
            tokens.add(unkTokenId);
          }
        }
      }
    }
    
    return tokens;
  }

  String detokenize(List<int> tokens) {
    if (_idToToken == null) return '';
    
    final words = <String>[];
    for (final tokenId in tokens) {
      if (tokenId == bosTokenId || tokenId == eosTokenId) continue;
      if (_idToToken!.containsKey(tokenId)) {
        words.add(_idToToken![tokenId]!);
      }
    }
    
    return words.join(' ').trim();
  }

  String formatChatPrompt(String userMessage, {String? systemPrompt}) {
    final buffer = StringBuffer();
    
    if (systemPrompt != null) {
      buffer.write(systemStart);
      buffer.write(systemPrompt);
      buffer.write(systemEnd);
    }
    
    buffer.write(userStart);
    buffer.write(userMessage);
    buffer.write(userEnd);
    
    buffer.write(assistantStart);
    
    return buffer.toString();
  }

  Future<String> generateResponse(String userInput, {String? systemPrompt}) async {
    try {
      if (!_isModelLoaded || _interpreter == null) {
        // Fallback to enhanced rule-based responses
        return await _generateEnhancedResponse(userInput);
      }
      
      // Format the prompt using TinyLlama's chat template
      final prompt = formatChatPrompt(userInput, systemPrompt: systemPrompt);
      
      // Tokenize the input
      final inputTokens = tokenize(prompt);
      
      if (inputTokens.length > maxContextLength - 100) {
        // Truncate if too long, leaving room for generation
        inputTokens.removeRange(maxContextLength - 100, inputTokens.length);
      }
      
      // Run inference
      final outputTokens = await _runInference(inputTokens);
      
      // Detokenize and clean up the response
      final response = detokenize(outputTokens);
      return _cleanResponse(response);
      
    } catch (e) {
      // Fallback to enhanced rule-based responses
      return await _generateEnhancedResponse(userInput);
    }
  }

  Future<List<int>> _runInference(List<int> inputTokens) async {
    // Prepare input tensor
    final inputTensor = Float32List(maxContextLength);
    for (int i = 0; i < inputTokens.length && i < maxContextLength; i++) {
      inputTensor[i] = inputTokens[i].toDouble();
    }
    
    // Run inference
    final outputTensor = Float32List(vocabSize);
    _interpreter!.run([inputTensor], [outputTensor]);
    
    // Simple greedy sampling - pick the token with highest probability
    int nextTokenId = 0;
    double maxProb = outputTensor[0];
    for (int i = 1; i < outputTensor.length; i++) {
      if (outputTensor[i] > maxProb) {
        maxProb = outputTensor[i];
        nextTokenId = i;
      }
    }
    
    // For demo, generate a few tokens
    final outputTokens = <int>[nextTokenId];
    
    // In a real implementation, you would continue generating until EOS token
    // or max length is reached
    
    return outputTokens;
  }

  String _cleanResponse(String response) {
    // Remove chat template artifacts and clean up
    return response
        .replaceAll(assistantStart, '')
        .replaceAll(assistantEnd, '')
        .replaceAll(systemStart, '')
        .replaceAll(systemEnd, '')
        .replaceAll(userStart, '')
        .replaceAll(userEnd, '')
        .trim();
  }

  Future<String> _generateEnhancedResponse(String input) async {
    // Enhanced rule-based responses while we don't have the full model
    await Future.delayed(const Duration(milliseconds: 800)); // Simulate processing
    
    final lowerInput = input.toLowerCase();
    
    // Greeting responses
    if (lowerInput.contains('hello') || lowerInput.contains('hi') || lowerInput.contains('hey')) {
      final greetings = [
        'Hello! I\'m AIRA, your offline voice assistant. How can I help you today?',
        'Hi there! What would you like to talk about?',
        'Hey! I\'m here and ready to assist you. What\'s on your mind?',
      ];
      return greetings[DateTime.now().millisecond % greetings.length];
    }
    
    // Question about identity
    if (lowerInput.contains('who are you') || lowerInput.contains('what are you')) {
      return 'I\'m AIRA, a lightweight offline voice assistant. I can transcribe your speech and respond to your questions, all without needing an internet connection!';
    }
    
    // How are you
    if (lowerInput.contains('how are you')) {
      final responses = [
        'I\'m doing great! Thanks for asking. I\'m here and ready to help you with anything.',
        'I\'m functioning perfectly! How are you doing today?',
        'I\'m excellent! Always happy to chat with you. What would you like to know?',
      ];
      return responses[DateTime.now().millisecond % responses.length];
    }
    
    // Time queries
    if (lowerInput.contains('time') && (lowerInput.contains('what') || lowerInput.contains('current'))) {
      final now = DateTime.now();
      final timeStr = '${now.hour}:${now.minute.toString().padLeft(2, '0')}';
      return 'The current time is $timeStr.';
    }
    
    // Date queries
    if (lowerInput.contains('date') && (lowerInput.contains('what') || lowerInput.contains('today'))) {
      final now = DateTime.now();
      final months = ['January', 'February', 'March', 'April', 'May', 'June',
                     'July', 'August', 'September', 'October', 'November', 'December'];
      return 'Today is ${months[now.month - 1]} ${now.day}, ${now.year}.';
    }
    
    // Weather (offline limitation)
    if (lowerInput.contains('weather')) {
      return 'I don\'t have access to current weather data since I\'m running offline. But I can help you with many other things!';
    }
    
    // Jokes
    if (lowerInput.contains('joke') || lowerInput.contains('funny')) {
      final jokes = [
        'Why don\'t scientists trust atoms? Because they make up everything!',
        'What do you call a fake noodle? An impasta!',
        'Why did the scarecrow win an award? He was outstanding in his field!',
        'What do you call a bear with no teeth? A gummy bear!',
        'Why don\'t eggs tell jokes? They\'d crack each other up!',
        'What do you call a sleeping bull? A bulldozer!',
      ];
      return jokes[DateTime.now().millisecond % jokes.length];
    }
    
    // Technology questions
    if (lowerInput.contains('ai') || lowerInput.contains('artificial intelligence')) {
      return 'AI is fascinating! I\'m a small example of AI running completely offline on your device. It\'s amazing how far the technology has come!';
    }
    
    // Capabilities
    if (lowerInput.contains('what can you do') || lowerInput.contains('help me')) {
      return 'I can transcribe your speech in real-time, answer questions, tell jokes, give you the time and date, and have conversations with you - all offline! Just speak naturally and I\'ll respond.';
    }
    
    // Thanks
    if (lowerInput.contains('thank')) {
      final responses = [
        'You\'re very welcome! I\'m happy to help.',
        'My pleasure! Is there anything else you\'d like to know?',
        'You\'re welcome! Feel free to ask me anything else.',
      ];
      return responses[DateTime.now().millisecond % responses.length];
    }
    
    // Goodbye
    if (lowerInput.contains('bye') || lowerInput.contains('goodbye') || lowerInput.contains('see you')) {
      final responses = [
        'Goodbye! It was great talking with you. Come back anytime!',
        'See you later! I\'ll be here whenever you need me.',
        'Bye! Take care, and feel free to chat with me again soon!',
      ];
      return responses[DateTime.now().millisecond % responses.length];
    }
    
    // Math questions (simple)
    if (lowerInput.contains('plus') || lowerInput.contains('add') || lowerInput.contains('+')) {
      return 'I can help with simple math! For complex calculations, I\'d recommend using a calculator app for the most accurate results.';
    }
    
    // Learning and education
    if (lowerInput.contains('learn') || lowerInput.contains('teach') || lowerInput.contains('explain')) {
      return 'I love helping people learn! What topic would you like to explore? I can explain concepts in simple terms.';
    }
    
    // Generic conversational responses
    final genericResponses = [
      'That\'s interesting! Can you tell me more about that?',
      'I understand. What would you like to know more about?',
      'That sounds important. How can I help you with that?',
      'Thanks for sharing that with me. What else is on your mind?',
      'I see. Is there anything specific you\'d like help with?',
      'That\'s a good point. What are your thoughts on it?',
      'Interesting perspective! What made you think about that?',
      'I appreciate you telling me that. What else would you like to discuss?',
    ];
    
    return genericResponses[DateTime.now().millisecond % genericResponses.length];
  }

  void dispose() {
    _interpreter?.close();
    _interpreter = null;
    _isModelLoaded = false;
  }
} 