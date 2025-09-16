package com.github.flydzen.idevoiceassistant.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@Service(Service.Level.PROJECT)
class VoiceRecognitionService(private val project: Project, private val scope: CoroutineScope) : Disposable {

    private val _recognizedText = MutableSharedFlow<String>()
    val recognizedText: SharedFlow<String> = _recognizedText.asSharedFlow()

    private val _isRecognitionActive = MutableStateFlow(false)
    val isRecognitionActive: StateFlow<Boolean> = _isRecognitionActive.asStateFlow()

    private var recognitionJob: Job? = null

    fun startRecognition() {
        project.service<RecordAudioService>().start()

        if (_isRecognitionActive.value) {
            return
        }

        _isRecognitionActive.value = true

        recognitionJob = scope.launch {
            // For now, simulate recognition with delay
//                val record = project.service<RecordAudioService>()
//                record.start()
//
//                val vad = VadService.getInstance()
//                vad.startListening(record.inputFlow)

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


    companion object {
        fun getInstance(project: Project): VoiceRecognitionService = project.service()
    }
}