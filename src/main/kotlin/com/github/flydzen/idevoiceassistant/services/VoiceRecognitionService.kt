package com.github.flydzen.idevoiceassistant.services

import com.github.flydzen.idevoiceassistant.executor.CommandExecutor
import com.github.flydzen.idevoiceassistant.openai.OpenAIClient
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.io.path.exists

@Service(Service.Level.PROJECT)
class VoiceRecognitionService(private val project: Project, private val scope: CoroutineScope) : Disposable {

    private val _recognizedText = MutableSharedFlow<String>()
    val recognizedText: SharedFlow<String> = _recognizedText.asSharedFlow()

    private val _isRecognitionActive = MutableStateFlow(false)
    val isRecognitionActive: StateFlow<Boolean> = _isRecognitionActive.asStateFlow()

    private var recognitionJob: Job? = null

    init {
        scope.launch {
            project.service<VADService>().outputChannel.receiveAsFlow().collectLatest { filePath ->
                if (!filePath.exists()) return@collectLatest
                val text = OpenAIClient.speech2Text(filePath.toFile())
                _recognizedText.emit(text)
                val commands = OpenAIClient.textToCommands(project, text)
                println(commands)
                CommandExecutor().execute(commands)
            }
        }
    }

    fun startRecognition() {
        project.service<RecordAudioService>().start()

        if (_isRecognitionActive.value) {
            return
        }

        _isRecognitionActive.value = true

        recognitionJob = scope.launch {
            delay(500)

            if (scope.isActive) {
                val recognizedText = "Hello, this is recognized text from the service!"

                _recognizedText.emit(recognizedText)
            }
        }
    }

    fun stopRecognition() {
        project.service<RecordAudioService>().stop()
        _isRecognitionActive.value = false
        recognitionJob?.cancel()
        recognitionJob = null
    }

    override fun dispose() {
        scope.cancel()
    }
}