package com.github.flydzen.idevoiceassistant.services

import com.github.flydzen.idevoiceassistant.VoiceAssistantBundle
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withBackgroundProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.atomic.AtomicBoolean

@Service(Service.Level.PROJECT)
class RecordAudioService(private val project: Project, private val scope: CoroutineScope) {
    private val isActive = AtomicBoolean(false)

    private val _inputFlow = MutableStateFlow<Byte?>(null)
    private val inputFlow = _inputFlow.asSharedFlow()

    private val mutex = Mutex()

    private var captureJob: Job? = null

    fun execute(action: suspend () -> Unit) {
        scope.launch {
            if (mutex.tryLock()) {
                try {
                    withBackgroundProgress(project, VoiceAssistantBundle.message("recording.audio")) {
                        action()
                    }
                } finally {
                    mutex.unlock()
                }
            } else {
                LOG.warn("RecordAudioService is busy")
            }
        }
    }

    fun stop() {

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