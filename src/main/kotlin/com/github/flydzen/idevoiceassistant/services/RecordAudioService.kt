package com.github.flydzen.idevoiceassistant.services

import com.github.flydzen.idevoiceassistant.MyBundle
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withBackgroundProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

@Service(Service.Level.PROJECT)
class RecordAudioService(private val project: Project, private val scope: CoroutineScope) {
    private val mutex = Mutex()

    fun execute(action: suspend () -> Unit) {
        scope.launch {
            if (mutex.tryLock()) {
                try {
                    withBackgroundProgress(project, MyBundle.message("recording.audio")) {
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

    companion object {
        private val LOG: Logger = thisLogger()
    }
}