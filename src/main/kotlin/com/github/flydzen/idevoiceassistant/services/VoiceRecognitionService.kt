package com.github.flydzen.idevoiceassistant.services

import com.github.flydzen.idevoiceassistant.TimeIt
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
                val text = TimeIt.timeIt("STT") {
                    OpenAIClient.speech2Text(filePath.toFile())
                }
                if (text.isEmpty()) return@collectLatest
                println("recognized: $text")
                _recognizedText.emit(text)
                val commands = TimeIt.timeIt("TTC") {
                    OpenAIClient.textToCommands(project, text)
                }
                println("command: ${commands.firstOrNull()}")
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