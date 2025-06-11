import 'package:flutter/material.dart';
import 'package:record/record.dart';
import 'package:path_provider/path_provider.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:process/process.dart';
import 'dart:async';
import 'dart:io';
import 'llm_service.dart';
import 'model_settings_screen.dart';

void main() {
  runApp(const AIRAApp());
}

class AIRAApp extends StatelessWidget {
  const AIRAApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'AIRA',
      theme: ThemeData(
        primarySwatch: Colors.blue,
        useMaterial3: true,
      ),
      home: const AIRAHome(),
    );
  }
}

class AIRAHome extends StatefulWidget {
  const AIRAHome({super.key});

  @override
  State<AIRAHome> createState() => _AIRAHomeState();
}

class ConversationMessage {
  final String text;
  final bool isUser;
  final DateTime timestamp;

  ConversationMessage({
    required this.text,
    required this.isUser,
    required this.timestamp,
  });
}

class _AIRAHomeState extends State<AIRAHome> {
  final AudioRecorder _audioRecorder = AudioRecorder();
  final ScrollController _scrollController = ScrollController();
  final LLMService _llmService = LLMService();
  
  bool _isListening = false;
  String _status = 'Idle';
  Timer? _recordingTimer;
  String? _audioFilePath;
  
  // Conversation state
  List<ConversationMessage> _conversation = [];
  String _currentTranscription = '';
  
  // Live transcription state
  Timer? _continuousRecordingTimer;
  bool _isProcessingAudio = false;
  
  // System prompt for the LLM
  static const String systemPrompt = '''You are AIRA, a helpful and friendly offline voice assistant. 
Keep your responses concise, natural, and conversational. 
You are running completely offline on the user's device, so you don't have access to real-time information like weather or news.
Be helpful, engaging, and maintain a warm personality in your interactions.''';

  @override
  void initState() {
    super.initState();
    _initializeApp();
  }

  Future<void> _initializeApp() async {
    // Request microphone permission
    await _requestPermission();
    
    // Get the path for saving audio files
    final directory = await getApplicationDocumentsDirectory();
    _audioFilePath = '${directory.path}/aira_record.wav';
    
    // Initialize the LLM service
    await _initializeLLM();
  }

  Future<void> _requestPermission() async {
    final status = await Permission.microphone.request();
    if (status != PermissionStatus.granted) {
      setState(() {
        _status = 'Microphone permission denied';
      });
    }
  }

  Future<void> _initializeLLM() async {
    try {
      setState(() {
        _status = 'Initializing AI model...';
      });

      await _llmService.initialize();
      
      setState(() {
        _status = 'AI ready';
      });
      
      // Add welcome message
      _addMessage(
        'Hello! I\'m AIRA, your offline voice assistant. I can transcribe your speech and respond to you in real-time, all without needing an internet connection. Start speaking to begin our conversation!',
        false,
      );
      
      await Future.delayed(const Duration(seconds: 2));
      setState(() {
        _status = 'Idle';
      });
    } catch (e) {
      setState(() {
        _status = 'AI initialization failed - using basic mode';
      });
      
      // Add fallback message
      _addMessage(
        'Hi! I\'m AIRA running in basic mode. I can still transcribe your speech and have conversations with you offline!',
        false,
      );
      
      await Future.delayed(const Duration(seconds: 2));
      setState(() {
        _status = 'Idle';
      });
    }
  }

  void _addMessage(String text, bool isUser) {
    setState(() {
      _conversation.add(ConversationMessage(
        text: text,
        isUser: isUser,
        timestamp: DateTime.now(),
      ));
    });
    
    // Auto-scroll to bottom
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scrollController.hasClients) {
        _scrollController.animateTo(
          _scrollController.position.maxScrollExtent,
          duration: const Duration(milliseconds: 300),
          curve: Curves.easeOut,
        );
      }
    });
  }

  Future<void> _startListening() async {
    if (_isListening) return;

    final hasPermission = await Permission.microphone.isGranted;
    if (!hasPermission) {
      await _requestPermission();
      return;
    }

    setState(() {
      _isListening = true;
      _status = 'Starting live transcription...';
    });

    // Start continuous recording for live transcription
    _startContinuousRecording();
  }

  void _startContinuousRecording() {
    // Record continuously in 3-second chunks with overlap
    _continuousRecordingTimer = Timer.periodic(const Duration(seconds: 2), (timer) {
      if (_isListening && !_isProcessingAudio) {
        _recordAndTranscribeLive();
      }
    });
    
    // Start first recording immediately
    _recordAndTranscribeLive();
  }

  Future<void> _recordAndTranscribeLive() async {
    if (!_isListening || _audioFilePath == null || _isProcessingAudio) return;

    _isProcessingAudio = true;
    
    try {
      setState(() {
        _status = 'Listening...';
      });

      // Check if we have permission to record
      if (await _audioRecorder.hasPermission()) {
        // Start recording
        await _audioRecorder.start(
          const RecordConfig(
            encoder: AudioEncoder.wav,
            sampleRate: 16000,
            bitRate: 128000,
          ),
          path: _audioFilePath!,
        );

        // Record for 3 seconds for better transcription quality
        await Future.delayed(const Duration(seconds: 3));

        // Stop recording
        await _audioRecorder.stop();

        // Process with Whisper
        await _transcribeAudioLive();
      } else {
        setState(() {
          _status = 'Recording permission not granted';
        });
      }
    } catch (e) {
      setState(() {
        _status = 'Recording error: ${e.toString()}';
      });
    } finally {
      _isProcessingAudio = false;
    }
  }

  Future<void> _transcribeAudioLive() async {
    if (_audioFilePath == null || !File(_audioFilePath!).existsSync()) {
      return;
    }

    try {
      setState(() {
        _status = 'Transcribing...';
      });

      // Run Whisper command for live transcription
      final ProcessManager manager = const LocalProcessManager();
      final ProcessResult result = await manager.run([
        'whisper',
        _audioFilePath!,
        '--model', 'tiny',
        '--output_format', 'txt',
        '--output_dir', '${Directory(_audioFilePath!).parent.path}',
        '--language', 'auto',
      ]);

      if (result.exitCode == 0) {
        // Read transcription from output file
        final transcriptFile = File(_audioFilePath!.replaceAll('.wav', '.txt'));
        if (transcriptFile.existsSync()) {
          final transcript = await transcriptFile.readAsString();
          final cleanTranscript = transcript.trim();
          
          if (cleanTranscript.isNotEmpty && cleanTranscript.length > 3) {
            // Only process if we have meaningful text and it's different from last
            if (cleanTranscript != _currentTranscription) {
              _currentTranscription = cleanTranscript;
              _addMessage(cleanTranscript, true);
              
              // Generate AI response using LLM service
              await _generateAIResponse(cleanTranscript);
            }
          }
          
          // Clean up transcript file
          await transcriptFile.delete();
        }
      }
      
      setState(() {
        _status = _isListening ? 'Listening...' : 'Idle';
      });
    } catch (e) {
      setState(() {
        _status = 'Transcription error: ${e.toString()}';
      });
    }
  }

  Future<void> _generateAIResponse(String userInput) async {
    try {
      setState(() {
        _status = 'AI thinking...';
      });

      // Use the LLM service to generate response
      String response = await _llmService.generateResponse(
        userInput, 
        systemPrompt: systemPrompt,
      );

      _addMessage(response, false);
      
      setState(() {
        _status = _isListening ? 'Listening...' : 'Idle';
      });
    } catch (e) {
      _addMessage('Sorry, I encountered an error processing your request.', false);
      setState(() {
        _status = _isListening ? 'Listening...' : 'Idle';
      });
    }
  }

  Future<void> _stopListening() async {
    if (!_isListening) return;

    setState(() {
      _isListening = false;
      _status = 'Stopping...';
    });

    _continuousRecordingTimer?.cancel();

    // Stop any ongoing recording
    try {
      await _audioRecorder.stop();
    } catch (e) {
      // Recording might not be active
    }

    setState(() {
      _status = 'Idle';
    });
  }

  @override
  void dispose() {
    _continuousRecordingTimer?.cancel();
    _recordingTimer?.cancel();
    _audioRecorder.dispose();
    _llmService.dispose();
    _scrollController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.grey[50],
      body: SafeArea(
        child: Column(
          children: [
            // Header
            Container(
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                color: Colors.white,
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withOpacity(0.1),
                    spreadRadius: 1,
                    blurRadius: 5,
                    offset: const Offset(0, 2),
                  ),
                ],
              ),
              child: Column(
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      const Spacer(),
                      Row(
                        children: [
                          Icon(
                            Icons.offline_bolt,
                            color: Colors.blue,
                            size: 28,
                          ),
                          const SizedBox(width: 8),
                          const Text(
                            'AIRA',
                            style: TextStyle(
                              fontSize: 28,
                              fontWeight: FontWeight.bold,
                              color: Colors.blue,
                            ),
                          ),
                        ],
                      ),
                      IconButton(
                        onPressed: () {
                          Navigator.push(
                            context,
                            MaterialPageRoute(
                              builder: (context) => const ModelSettingsScreen(),
                            ),
                          );
                        },
                        icon: const Icon(Icons.settings),
                        tooltip: 'AI Model Settings',
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  Text(
                    _status,
                    style: TextStyle(
                      fontSize: 14,
                      color: Colors.grey[600],
                    ),
                  ),
                  if (_llmService.isModelLoaded) ...[
                    const SizedBox(height: 4),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                      decoration: BoxDecoration(
                        color: Colors.green.withOpacity(0.1),
                        borderRadius: BorderRadius.circular(12),
                        border: Border.all(color: Colors.green.withOpacity(0.3)),
                      ),
                      child: const Text(
                        'AI Model Ready',
                        style: TextStyle(
                          fontSize: 11,
                          color: Colors.green,
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                    ),
                  ] else ...[
                    const SizedBox(height: 4),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                      decoration: BoxDecoration(
                        color: Colors.orange.withOpacity(0.1),
                        borderRadius: BorderRadius.circular(12),
                        border: Border.all(color: Colors.orange.withOpacity(0.3)),
                      ),
                      child: const Text(
                        'Basic Mode',
                        style: TextStyle(
                          fontSize: 11,
                          color: Colors.orange,
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                    ),
                  ],
                ],
              ),
            ),

            // Conversation area
            Expanded(
              child: _conversation.isEmpty
                  ? Center(
                      child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Icon(
                            Icons.mic_none,
                            size: 80,
                            color: Colors.grey[400],
                          ),
                          const SizedBox(height: 20),
                          Text(
                            'Tap the microphone to start\nyour conversation with AIRA',
                            textAlign: TextAlign.center,
                            style: TextStyle(
                              fontSize: 16,
                              color: Colors.grey[600],
                            ),
                          ),
                          const SizedBox(height: 12),
            Text(
                            'Live transcription + AI responses\nRunning completely offline',
                            textAlign: TextAlign.center,
                            style: TextStyle(
                              fontSize: 14,
                              color: Colors.grey[500],
                            ),
                          ),
                        ],
                      ),
                    )
                  : ListView.builder(
                      controller: _scrollController,
                      padding: const EdgeInsets.all(16),
                      itemCount: _conversation.length,
                      itemBuilder: (context, index) {
                        final message = _conversation[index];
                        return _buildMessageBubble(message);
                      },
                    ),
            ),

            // Bottom controls
            Container(
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                color: Colors.white,
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withOpacity(0.1),
                    spreadRadius: 1,
                    blurRadius: 5,
                    offset: const Offset(0, -2),
                  ),
                ],
              ),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Container(
                    width: 80,
                    height: 80,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: _isListening ? Colors.red : Colors.blue,
                      boxShadow: [
                        BoxShadow(
                          color: Colors.black.withOpacity(0.2),
                          spreadRadius: 3,
                          blurRadius: 10,
                          offset: const Offset(0, 3),
                        ),
                      ],
                    ),
                    child: Material(
                      color: Colors.transparent,
                      child: InkWell(
                        borderRadius: BorderRadius.circular(40),
                        onTap: _isListening ? _stopListening : _startListening,
                        child: Center(
                          child: _isProcessingAudio
                              ? const SizedBox(
                                  width: 30,
                                  height: 30,
                                  child: CircularProgressIndicator(
                                    color: Colors.white,
                                    strokeWidth: 3,
                                  ),
                                )
                              : Icon(
                                  _isListening ? Icons.stop : Icons.mic,
                                  size: 40,
                                  color: Colors.white,
                                ),
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildMessageBubble(ConversationMessage message) {
    return Container(
      margin: const EdgeInsets.only(bottom: 16),
      child: Row(
        mainAxisAlignment:
            message.isUser ? MainAxisAlignment.end : MainAxisAlignment.start,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (!message.isUser) ...[
            CircleAvatar(
              radius: 16,
              backgroundColor: Colors.blue,
              child: const Icon(
                Icons.smart_toy,
                size: 16,
                color: Colors.white,
              ),
            ),
            const SizedBox(width: 8),
          ],
          Flexible(
            child: Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: message.isUser ? Colors.blue : Colors.white,
                borderRadius: BorderRadius.circular(18),
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withOpacity(0.1),
                    spreadRadius: 1,
                    blurRadius: 3,
                    offset: const Offset(0, 1),
                  ),
                ],
              ),
              child: Text(
                message.text,
                style: TextStyle(
                  color: message.isUser ? Colors.white : Colors.black87,
                  fontSize: 15,
                ),
              ),
            ),
          ),
          if (message.isUser) ...[
            const SizedBox(width: 8),
            CircleAvatar(
              radius: 16,
              backgroundColor: Colors.grey[400],
              child: const Icon(
                Icons.person,
                size: 16,
                color: Colors.white,
              ),
            ),
          ],
        ],
      ),
    );
  }
}
