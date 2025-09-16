package com.github.flydzen.idevoiceassistant.services

import com.github.flydzen.idevoiceassistant.audio.AudioCaptureTask
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

@Service(Service.Level.PROJECT)
class RecordAudioService(private val project: Project, private val scope: CoroutineScope) {
    private val isActive = AtomicBoolean(false)

    private val _inputFlow = MutableStateFlow<Byte?>(null)
    val inputFlow = _inputFlow.asSharedFlow()

    private var captureJob: Job? = null

    fun start(task: AudioCaptureTask? = null) {
        if (!lock()) {
            LOG.warn("RecordAudioService is already active")
            return
        }
        captureJob = scope.launch {
            val audioTask = task ?: AudioCaptureTask(4)
            audioTask.run()
        }
    }

    fun stop() {
        captureJob?.cancel()
        captureJob = null
        unlock()
    }

    private fun lock(): Boolean {
        return isActive.compareAndSet(false, true)
    }

    private fun unlock() {
        isActive.set(false)
    }

    companion object {
        private val LOG: Logger = thisLogger()
    }
}