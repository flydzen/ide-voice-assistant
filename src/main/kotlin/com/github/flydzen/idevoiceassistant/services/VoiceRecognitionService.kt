package com.github.flydzen.idevoiceassistant.services

import com.github.flydzen.idevoiceassistant.Utils
import com.github.flydzen.idevoiceassistant.executor.CommandExecutor
import com.github.flydzen.idevoiceassistant.openai.CommandHistoryStorage
import com.github.flydzen.idevoiceassistant.openai.OpenAIClient
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
                project.service<StageService>().setStage(Stage.Parsing)
                val text = Utils.timeIt("STT") {
                    OpenAIClient.speech2Text(filePath.toFile())
                }
                if (text.isEmpty()) return@collectLatest
                println("recognized: $text")
                _recognizedText.emit(text)
                project.service<StageService>().setStage(Stage.Thinking)
                project.service<CommandHistoryStorage>().addCommand(text)
                val commands = Utils.timeIt("TTC") {
                    OpenAIClient.textToCommand(project, text)
                }
                println("command: ${commands.firstOrNull()}")
                project.service<CommandExecutor>().execute(project, commands)
                project.service<StageService>().setStage(Stage.Ready)
            }
        }
    }

    fun startRecognition() {
        project.service<RecordAudioService>().start()
        project.service<StageService>().setStage(Stage.Ready)
        if (_isRecognitionActive.value) {
            return
        }

        _isRecognitionActive.value = true
    }

    fun stopRecognition() {
        project.service<StageService>().setStage(Stage.Disabled)
        project.service<RecordAudioService>().stop()
        _isRecognitionActive.value = false
        recognitionJob?.cancel()
        recognitionJob = null
    }

    override fun dispose() {
        scope.cancel()
    }
}