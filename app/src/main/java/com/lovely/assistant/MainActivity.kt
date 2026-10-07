package com.lovely.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : ComponentActivity() {

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null

    private var transcriptionState by mutableStateOf("Waiting for your voice…")
    private var statusState by mutableStateOf("Ready")
    private var isListeningState by mutableStateOf(false)

    private val microphonePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startListening()
            } else {
                transcriptionState = "Microphone permission was denied."
                statusState = "Permission required"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        textToSpeech = TextToSpeech(this) {
            if (it == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.getDefault()
            }
        }

        setupSpeechRecognizer()

        setContent {
            LovelyApp(
                transcription = transcriptionState,
                status = statusState,
                isListening = isListeningState,
                onMic = { handleMicrophone() },
                onStop = { stopListening() }
            )
        }
    }

    private fun setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            statusState = "Speech recognition unavailable"
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListeningState = true
                statusState = "Listening"
                transcriptionState = "Listening…"
            }

            override fun onBeginningOfSpeech() {
                isListeningState = true
                statusState = "Listening"
            }

            override fun onRmsChanged(rmsdB: Float) = Unit

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                isListeningState = false
                statusState = "Processing"
            }

            override fun onError(error: Int) {
                isListeningState = false
                statusState = "Ready"
                transcriptionState = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH ->
                        "I couldn't understand that. Please try again."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        "I didn't hear anything. Tap the microphone and try again."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        "Microphone permission is required."
                    else ->
                        "Speech recognition failed. Please try again."
                }
            }

            override fun onResults(results: Bundle?) {
                isListeningState = false
                statusState = "Ready"

                val matches =
                    results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)

                val text = matches?.firstOrNull().orEmpty()

                if (text.isNotBlank()) {
                    transcriptionState = text
                    speak("I heard you say $text")
                } else {
                    transcriptionState = "I couldn't understand that."
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches =
                    partialResults?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )

                val text = matches?.firstOrNull()

                if (!text.isNullOrBlank()) {
                    transcriptionState = text
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
    }

    private fun handleMicrophone() {
        if (isListeningState) {
            stopListening()
            return
        }

        val permission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        )

        if (permission != PackageManager.PERMISSION_GRANTED) {
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            startListening()
        }
    }

    private fun startListening() {
        if (speechRecognizer == null) {
            setupSpeechRecognizer()
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        isListeningState = true
        statusState = "Starting…"

        speechRecognizer?.startListening(intent)
    }

    private fun stopListening() {
        speechRecognizer?.stopListening()
        speechRecognizer?.cancel()
        isListeningState = false
        statusState = "Ready"
    }

    private fun speak(text: String) {
        textToSpeech?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "lovely-response"
        )
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null

        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null

        super.onDestroy()
    }
}

@Composable
fun LovelyApp(
    transcription: String,
    status: String,
    isListening: Boolean,
    onMic: () -> Unit,
    onStop: () -> Unit
) {
    var showSettings by remember { mutableStateOf(false) }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            if (showSettings) {
                LovelySettings(
                    onBack = { showSettings = false }
                )
            } else {
                LovelyHome(
                    transcription = transcription,
                    status = status,
                    isListening = isListening,
                    onSettings = { showSettings = true },
                    onMic = onMic,
                    onStop = onStop
                )
            }
        }
    }
}

@Composable
fun LovelyHome(
    transcription: String,
    status: String,
    isListening: Boolean,
    onSettings: () -> Unit,
    onMic: () -> Unit,
    onStop: () -> Unit
) {
    val background = Brush.verticalGradient(
        listOf(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.surfaceContainerHighest
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Lovely",
                    fontSize = 28.sp,
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "Your AI Agent",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            IconButton(onClick = onSettings) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings"
                )
            }
        }

        Spacer(modifier = Modifier.height(35.dp))

        Box(
            modifier = Modifier
                .size(190.dp)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Lovely",
                fontSize = 30.sp
            )
        }

        Spacer(modifier = Modifier.height(30.dp))

        Text(
            text = if (isListening) "Listening…" else "I'm listening",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Tap the microphone and tell me what to do.",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(25.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Live transcription",
                    style = MaterialTheme.typography.labelLarge
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = transcription,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        Spacer(modifier = Modifier.height(15.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Lovely",
                    style = MaterialTheme.typography.labelLarge
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = when (status) {
                        "Listening" -> "I'm listening to you."
                        "Processing" -> "Processing your voice…"
                        else -> "Ready to help."
                    },
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onStop) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Stop"
                )
            }

            Surface(
                modifier = Modifier.size(82.dp),
                shape = CircleShape,
                tonalElevation = 8.dp
            ) {
                IconButton(onClick = onMic) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Microphone",
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = status,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
fun LovelySettings(onBack: () -> Unit) {
    var apiKey by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back"
                )
            }

            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineSmall
            )
        }

        Spacer(modifier = Modifier.height(30.dp))

        Text(
            text = "AI API Key",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = apiKey,
            onValueChange = {
                apiKey = it
                saved = false
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("API Key") },
            placeholder = { Text("Paste your API key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                saved = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save")
        }

        if (saved) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "API key saved for this session.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
