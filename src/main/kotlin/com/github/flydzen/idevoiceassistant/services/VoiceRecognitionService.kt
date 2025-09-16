
package com.github.flydzen.idevoiceassistant.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

@Service(Service.Level.PROJECT)
class VoiceRecognitionService(private val project: Project, private val scope: CoroutineScope): Disposable {

    private val _recognizedText = MutableSharedFlow<String>()
    val recognizedText: SharedFlow<String> = _recognizedText.asSharedFlow()

    private val _isRecognitionActive = MutableStateFlow(false)
    val isRecognitionActive: StateFlow<Boolean> = _isRecognitionActive.asStateFlow()

    private var recognitionJob: Job? = null

    fun startRecognition() {
        if (_isRecognitionActive.value) {
            return
        }

        _isRecognitionActive.value = true

        recognitionJob = scope.launch {
            try {
                // For now, simulate recognition with delay

                delay(500)

                if (scope.isActive) {
                    val recognizedText = "Hello, this is recognized text from the service!"

                    _recognizedText.emit(recognizedText)
                }
            } catch (e: CancellationException) {
            }
        }
    }

    fun stopRecognition() {
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